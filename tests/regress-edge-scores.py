# -*- coding: utf-8 -*-
"""分数边界：越界分数、缺分、同批重复打分应被拒绝且不写库。"""
import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api/candidates"
ADMIN = "admin"
fails = []

def check(label, cond, extra=""):
    print(("PASS" if cond else "FAIL"), label, extra)
    if not cond:
        fails.append((label, extra))

def call(method, path, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"message": raw}

st, res = call("GET", "?pageSize=100")
items = res.get("items", [])
iv = next((c for c in items if c.get("status") == "interviewing"), None)
wait = next((c for c in items if c.get("status") == "waiting"), None)
print("interviewing=", iv["name"] if iv else None, "waiting=", wait["name"] if wait else None)

if iv:
    # 分数 >10
    scores = [{"interviewerName": "面试官A1", "score": 11, "judgeComment": "x"}]
    st, res = call("POST", f"/{iv['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("分数>10 被拒", st == 400, f"st={st} {res.get('message','')}")
    # 分数 <0
    scores = [{"interviewerName": "面试官A1", "score": -1, "judgeComment": "x"}]
    st, res = call("POST", f"/{iv['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("分数<0 被拒", st == 400, f"st={st} {res.get('message','')}")
    # 缺分数
    scores = [{"interviewerName": "面试官A1", "judgeComment": "x"}]
    st, res = call("POST", f"/{iv['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("缺分数被拒", st == 400, f"st={st} {res.get('message','')}")
    # 同批重复同一面试官
    scores = [
        {"interviewerName": "面试官A1", "score": 8.0, "judgeComment": "x"},
        {"interviewerName": "面试官A1", "score": 8.0, "judgeComment": "y"},
    ]
    st, res = call("POST", f"/{iv['id']}/scores", {"operatorName": ADMIN, "scores": scores})
    check("同批重复打分被拒", st == 400, f"st={st} {res.get('message','')}")

# 复核 interviewing 考生一面分未被越界请求污染
if iv:
    st, res = call("GET", f"/{iv['id']}")
    # 列表接口回查
    st, res = call("GET", "?pageSize=100")
    again = next((c for c in res.get("items", []) if c["id"] == iv["id"]), None)
    if again:
        check("越界请求未污染评分", again.get("scoredCount") in (0, 1),
              f"scoredCount={again.get('scoredCount')} avg={again.get('averageScore')}")

print("\n==== 边界2 汇总: %d 失败 ====" % len(fails))
for label, extra in fails:
    print("FAILED:", label, extra)
if fails:
    raise SystemExit(1)
