# -*- coding: utf-8 -*-
"""30 人全链路回归（合并版）：报名->签到->分组->一面叫号/三席打分->切二面场次->推进二面->
二面签到/分组/叫号/三席打分->录取/淘汰。覆盖 waiting/interviewing/first_completed/
second_waiting/second_completed/final_admitted/final_rejected 分支，并校验一面分不被二面污染。"""
import collections
from api_client import ApiClient

BASE = "http://localhost:8080/api/candidates"
ADMIN = "admin"
SEATS = {
    "A": ["面试官A1", "面试官A2", "面试官A3"],
    "B": ["面试官B1", "面试官B2", "面试官B3"],
    "C": ["面试官C1", "面试官C2", "面试官C3"],
    "D": ["面试官D1", "面试官D2", "面试官D3"],
}
GROUPS = ["A", "B", "C", "D"]
DEPTS = ["部门A", "部门B", "部门C"]
fails = []

def check(label, cond, extra=""):
    print(("PASS" if cond else "FAIL"), label, extra)
    if not cond:
        fails.append((label, extra))

call = ApiClient(BASE).call

# ---------- 报名 30 ----------
cands = []
for i in range(1, 31):
    g = GROUPS[(i - 1) % 4]
    dept = DEPTS[(i - 1) % 3]
    body = {"name": f"考生{i:02d}", "gender": "男" if i % 2 else "女",
            "className": "软工1班", "phone": f"1390000{i:04d}",
            "firstDepartment": dept, "secondDepartment": dept, "acceptAdjust": "是"}
    st, res = call("POST", "/pre-register/public", body)
    if st == 200 and res.get("id"):
        res["_group"], res["_dept"] = g, dept
        cands.append(res)
    else:
        print("REGISTER FAIL", i, st, res.get("message", res))
check("报名 30 人", len(cands) == 30, f"n={len(cands)}")

# ---------- 签到 + 分组 ----------
for c in cands:
    call("POST", "/public/check-in", {"candidateId": c["id"]})
    st, res = call("POST", "/assign-group",
                   {"candidateId": c["id"], "groupName": c["_group"], "operatorName": ADMIN})
    if not (st == 200 and res.get("groupName") == c["_group"]):
        print("ASSIGN FAIL", c["name"], st, res.get("message", res))

# ---------- 一面：24 人叫号 + 三席打分 ----------
def first_round(c):
    st, res = call("PATCH", f"/{c['id']}/status",
                   {"status": "interviewing", "interviewerName": ADMIN})
    if res.get("status") != "interviewing":
        return f"叫号失败 {st} {res.get('message','')}"
    scores = [{"interviewerName": n, "score": 8.5, "judgeComment": "一面备注"} for n in SEATS[c["_group"]]]
    st, res = call("POST", f"/{c['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    if res.get("status") != "first_completed":
        return f"打分失败 {st} {res.get('message','')} status={res.get('status')}"
    c["_avg"] = res.get("averageScore")
    c["_firstScores"] = len(res.get("scores") or [])
    return None

first_done = cands[:24]
ferr = 0
for c in first_done:
    err = first_round(c)
    if err:
        ferr += 1; print("FIRST ERR", c["name"], err)
check("一面 24 人三席完成", ferr == 0, f"fail={ferr}")
check("一面平均分=8.5 且 3 条一面分",
      all(c.get("_avg") == 8.5 and c.get("_firstScores") == 3 for c in first_done),
      f"sample={[(c['name'], c.get('_avg'), c.get('_firstScores')) for c in first_done[:3]]}")

# ---------- 切到二面场次 ----------
st, res = call("POST", "/display/toggle-mode", {"operatorName": ADMIN, "mode": "second"})
check("切换到二面场次", st == 200 and res.get("mode") == "second")

# ---------- 二面：20 人完整 ----------
def second_full(c):
    st, res = call("POST", "/second/move-to", {"candidateId": c["id"], "operatorName": ADMIN})
    if res.get("status") != "second_waiting":
        return f"推进失败 {st} {res.get('message','')}"
    st, res = call("POST", "/second/check-in", {"candidateId": c["id"]})
    if st != 200:
        return f"二面签到失败 {st} {res.get('message','')}"
    st, res = call("POST", "/second/assign-group",
                   {"candidateId": c["id"], "groupName": c["_group"], "operatorName": ADMIN})
    if res.get("groupName") != c["_group"]:
        return f"二面分组失败 {st} {res.get('message','')}"
    st, res = call("POST", "/second/start",
                   {"candidateId": c["id"], "interviewerName": SEATS[c["_group"]][0]})
    if res.get("status") != "second_interviewing":
        return f"二面叫号失败 {st} {res.get('message','')}"
    scores = [{"interviewerName": n, "score": 9.0, "judgeComment": "二面备注"} for n in SEATS[c["_group"]]]
    st, res = call("POST", f"/{c['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    if res.get("status") != "second_completed":
        return f"二面打分失败 {st} {res.get('message','')} status={res.get('status')}"
    c["_secAvg"] = res.get("secondAverageScore")
    c["_secScores"] = len(res.get("secondScores") or [])
    return None

full = cands[:20]
serr = 0
for c in full:
    err = second_full(c)
    if err:
        serr += 1; print("SECOND ERR", c["name"], err)
check("20 人二面三席完成", serr == 0, f"fail={serr}")
check("二面平均分=9.0 且 3 条二面分、一面分未被污染",
      all(c.get("_secAvg") == 9.0 and c.get("_secScores") == 3 and c.get("_avg") == 8.5 for c in full),
      f"sample={[(c['name'], c.get('_avg'), c.get('_secAvg'), c.get('_secScores')) for c in full[:3]]}")

# ---------- 录取 10 / 淘汰 10 ----------
bad = 0
for c in full[:10]:
    st, res = call("POST", f"/{c['id']}/admit",
                   {"candidateId": c["id"], "decision": "admitted",
                    "finalDepartment": c["_dept"], "operatorName": ADMIN})
    if res.get("status") != "final_admitted" or not res.get("finalDepartment"):
        bad += 1; print("ADMIT FAIL", c["name"], st, res.get("message", res))
check("10 人录取且落部门", bad == 0)

bad = 0
for c in full[10:20]:
    st, res = call("POST", f"/{c['id']}/admit",
                   {"candidateId": c["id"], "decision": "rejected", "operatorName": ADMIN})
    if res.get("status") != "final_rejected" or res.get("finalDepartment") is not None:
        bad += 1; print("REJECT FAIL", c["name"], st, res.get("message", res))
check("10 人淘汰且部门清空", bad == 0)

# ---------- 2 人 second_waiting ----------
wbad = 0
for c in cands[20:22]:
    call("POST", "/second/move-to", {"candidateId": c["id"], "operatorName": ADMIN})
    st, res = call("POST", "/second/check-in", {"candidateId": c["id"]})
    if res.get("status") != "second_waiting":
        wbad += 1; print("WAIT FAIL", c["name"], st, res.get("message", res))
check("2 人停在二面待面试", wbad == 0)

# ---------- 2 人 second_completed 待决策 ----------
cbad = 0
for c in cands[22:24]:
    err = second_full(c)
    if err:
        cbad += 1; print("COMPLETED FAIL", c["name"], err)
check("2 人二面完成待决策", cbad == 0)

# ---------- 最后 6 人留一面：2 interviewing，4 waiting ----------
for c in cands[24:26]:
    call("PATCH", f"/{c['id']}/status", {"status": "interviewing", "interviewerName": ADMIN})

# ---------- 汇总状态分布 ----------
st, res = call("GET", "?pageSize=100")
dist = collections.Counter(x.get("status") for x in res.get("items", []))
print("状态分布:", dict(dist))

# 切回一面场次
call("POST", "/display/toggle-mode", {"operatorName": ADMIN, "mode": "first"})

print("\n==== 汇总: %d 失败 ====" % len(fails))
for label, extra in fails:
    print("FAILED:", label, extra)
if fails:
    raise SystemExit(1)
