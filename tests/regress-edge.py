# -*- coding: utf-8 -*-
"""异常边界回归：重复报名/重复签到/错误状态打分/跨组打分/非法流转/非法分组/越权角色/非法录取决策。
断言这些请求被后端以 400/403/409 拒绝，且不产生错误数据。"""
from api_client import ApiClient

BASE = "http://localhost:8080/api/candidates"
ADMIN = "admin"
fails = []

def check(label, cond, extra=""):
    print(("PASS" if cond else "FAIL"), label, extra)
    if not cond:
        fails.append((label, extra))

call = ApiClient(BASE).call

# 拉取当前考生
st, res = call("GET", "?pageSize=100")
items = res.get("items", [])
by = {}
for c in items:
    by.setdefault(c.get("status"), []).append(c)

waiting = by.get("waiting", [])
interviewing = by.get("interviewing", [])
swaiting = by.get("second_waiting", [])
scompleted = by.get("second_completed", [])
print("waiting=", len(waiting), "interviewing=", len(interviewing),
      "second_waiting=", len(swaiting), "second_completed=", len(scompleted))

def rejected(st, res):
    return st in (400, 403, 409)

# 1. 重复手机号报名
dup = {"name": "重复考生", "gender": "男", "className": "软工1班", "phone": "13900000001",
       "firstDepartment": "部门A", "secondDepartment": "部门A", "acceptAdjust": "是"}
st, res = call("POST", "/pre-register/public", dup)
check("重复手机号报名被拒(409)", rejected(st, res) and st == 409, f"st={st} {res.get('message','')}")

# 2. 空姓名
st, res = call("POST", "/pre-register/public",
               {"name": " ", "gender": "男", "className": "班", "phone": "13700000002",
                "firstDepartment": "部门A", "acceptAdjust": "是"})
check("空姓名报名被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 3. 非法手机号
st, res = call("POST", "/pre-register/public",
               {"name": "号码错", "gender": "男", "className": "班", "phone": "12345",
                "firstDepartment": "部门A", "acceptAdjust": "是"})
check("非法手机号被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 4. 重复签到
if waiting:
    c = waiting[0]
    st, res = call("POST", "/public/check-in", {"candidateId": c["id"]})
    check("重复签到被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 5. waiting 状态打分（一面未叫号）-> 应拒绝
if waiting:
    c = waiting[0]
    scores = [{"interviewerName": "面试官A1", "score": 8.0, "judgeComment": "x"}]
    st, res = call("POST", f"/{c['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("待面试状态打分被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 6. second_waiting 状态打二面分 -> 应拒绝
if swaiting:
    c = swaiting[0]
    scores = [{"interviewerName": "面试官A1", "score": 9.0, "judgeComment": "x"}]
    st, res = call("POST", f"/{c['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("二面待面试状态打分被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 7. 跨组打分：A 组面试中考生用 B 组官打分 -> 拒绝
a_iv = next((c for c in interviewing if c.get("groupName") == "A"), None)
if a_iv:
    scores = [{"interviewerName": "面试官B1", "score": 8.0, "judgeComment": "x"}]
    st, res = call("POST", f"/{a_iv['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("跨组打分被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 8. 非法状态流转 waiting -> second_interviewing
if waiting:
    c = waiting[0]
    st, res = call("PATCH", f"/{c['id']}/status",
                   {"status": "second_interviewing", "interviewerName": ADMIN})
    check("非法状态流转被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 9. 一面非法分组 E
if waiting:
    c = waiting[0]
    st, res = call("POST", "/assign-group",
                   {"candidateId": c["id"], "groupName": "E", "operatorName": ADMIN})
    check("非法分组被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 10. floating 打分 -> 403
if interviewing:
    c = interviewing[0]
    scores = [{"interviewerName": "面试官A1", "score": 8.0, "judgeComment": "x"}]
    st, res = call("POST", f"/{c['id']}/scores", {"operatorName": "流动人员1", "scores": scores})
    check("流动人员打分被拒(403)", rejected(st, res) and st == 403, f"st={st} {res.get('message','')}")

# 11. floating 改状态 -> 403
if waiting:
    c = waiting[0]
    st, res = call("PATCH", f"/{c['id']}/status",
                   {"status": "interviewing", "interviewerName": "流动人员1"})
    check("流动人员改状态被拒(403)", rejected(st, res) and st == 403, f"st={st} {res.get('message','')}")

# 12. waiting 考生做录取决策 -> 拒绝
if waiting:
    c = waiting[0]
    st, res = call("POST", f"/{c['id']}/admit",
                   {"candidateId": c["id"], "decision": "admitted",
                    "finalDepartment": "部门A", "operatorName": ADMIN})
    check("未完成二面录取被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 13. 二面完成但录取不传部门 -> 拒绝
if scompleted:
    c = scompleted[0]
    st, res = call("POST", f"/{c['id']}/admit",
                   {"candidateId": c["id"], "decision": "admitted", "operatorName": ADMIN})
    check("录取缺部门被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 14. 本组已有面试者，再叫同组下一位 -> 拒绝
a_wait = next((c for c in waiting if c.get("groupName") == "A"), None)
if a_wait and a_iv:
    st, res = call("PATCH", f"/{a_wait['id']}/status",
                   {"status": "interviewing", "interviewerName": ADMIN})
    check("本组已有面试者重复叫号被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 15. 二面非法分组 E
if swaiting:
    c = swaiting[0]
    st, res = call("POST", "/second/assign-group",
                    {"candidateId": c["id"], "groupName": "E", "operatorName": ADMIN})
    check("二面非法分组被拒", rejected(st, res), f"st={st} {res.get('message','')}")

# 16. 未登记账号登录
st, res = call("POST", "/interviewer-login", {"name": "不存在的人", "password": "123456"})
check("未登记账号登录失败", res.get("success") is False, f"st={st} {res.get('message','')}")

# 17. 权限口径：floating 不可推进二面（仅 admin）
if swaiting:
    c = swaiting[0]
    st, res = call("POST", "/second/move-to",
                   {"candidateId": c["id"], "operatorName": "流动人员1"})
    check("流动人员移入二面被拒(403)", st == 403, f"st={st} {res.get('message','')}")

# 18. 权限口径：floating 可二面分组/改派（鉴权放行，非 403）
if swaiting:
    c = swaiting[0]
    grp = c.get("groupName") or "A"
    st, res = call("POST", "/second/assign-group",
                   {"candidateId": c["id"], "groupName": grp, "operatorName": "流动人员1"})
    check("流动人员二面分组鉴权放行(非403)", st != 403, f"st={st} {res.get('message','')}")

# 19. 权限口径：floating 不可录取/淘汰决策（仅 admin）
if scompleted:
    c = scompleted[0]
    st, res = call("POST", f"/{c['id']}/admit",
                   {"candidateId": c["id"], "decision": "admitted",
                    "finalDepartment": "部门A", "operatorName": "流动人员1"})
    check("流动人员录取决策被拒(403)", st == 403, f"st={st} {res.get('message','')}")

# 20. 权限口径：floating 一面分组/改派鉴权放行（非 403，落到业务校验）
if waiting:
    c = waiting[0]
    st, res = call("POST", "/reassign-group",
                   {"candidateId": c["id"], "groupName": c.get("groupName"),
                    "operatorName": "流动人员1"})
    check("流动人员一面分组未被 403 拦截", st != 403, f"st={st} {res.get('message','')}")

print("\n==== 边界汇总: %d 失败 ====" % len(fails))
for label, extra in fails:
    print("FAILED:", label, extra)
if fails:
    raise SystemExit(1)
