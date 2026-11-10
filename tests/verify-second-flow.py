# -*- coding: utf-8 -*-
"""二面三席链路复验：一面报名->签到->叫号->三席打分->推进二面->二面签到->分配组->二面叫号->二面三席打分->大屏二面视图。"""
import json
from api_client import ApiClient

BASE = "http://localhost:8080/api/candidates"
ADMIN = "admin"
A_SEATS = ["面试官A1", "面试官A2", "面试官A3"]

results = []


call = ApiClient(BASE).call


def check(label, cond, extra=""):
    results.append((label, cond, extra))
    print(("PASS" if cond else "FAIL"), label, extra)


def main():
    # 1. 报名
    status, cand = call("POST", "/pre-register/public", {
        "name": "VerifySecond", "gender": "男", "className": "VerifyClass",
        "phone": "13900000001", "firstDepartment": "部门A",
        "secondDepartment": "部门B", "acceptAdjust": "是",
    })
    check("报名成功", status == 200 and cand.get("id"), f"status={status} {cand.get('message','')}")
    cid = cand["id"]

    # 2. 一面签到
    status, cand = call("POST", "/public/check-in", {"candidateId": cid})
    check("一面签到", status == 200 and cand.get("status") == "waiting", f"status={cand.get('status')}")

    # 3. 分配 A 组
    status, cand = call("POST", "/assign-group", {"candidateId": cid, "groupName": "A", "operatorName": ADMIN})
    check("一面分配A组", status == 200 and cand.get("groupName") == "A", f"group={cand.get('groupName')}")

    # 4. 一面叫号
    status, cand = call("PATCH", f"/{cid}/status", {"status": "interviewing", "interviewerName": ADMIN})
    check("一面叫号", status == 200 and cand.get("status") == "interviewing", f"status={cand.get('status')}")

    # 5. 一面三席打分
    scores = [{"interviewerName": n, "score": 8.5, "judgeComment": "一面备注"} for n in A_SEATS]
    status, cand = call("POST", f"/{cid}/scores", {"operatorName": ADMIN, "scores": scores})
    check("一面三席打分", status == 200 and cand.get("status") == "first_completed",
          f"status={cand.get('status')} avg={cand.get('averageScore')} scores={len(cand.get('scores') or [])}")
    check("一面平均分计算", cand.get("averageScore") is not None)

    # 6. 推进二面
    status, cand = call("POST", "/second/move-to", {"candidateId": cid, "operatorName": ADMIN})
    check("推进二面", status == 200 and cand.get("status") == "second_waiting", f"status={cand.get('status')}")

    # 7. 二面扫码签到
    status, cand = call("POST", "/second/check-in", {"candidateId": cid})
    check("二面签到", status == 200, f"status={cand.get('status')} {cand.get('message','')}")

    # 8. 分配二面 A 组
    status, cand = call("POST", "/second/assign-group", {"candidateId": cid, "groupName": "A", "operatorName": ADMIN})
    check("二面分配A组", status == 200 and cand.get("groupName") == "A", f"group={cand.get('groupName')}")

    # 二面官只能在二面场次叫号，先切换全局场次。
    status, _ = call("POST", "/display/toggle-mode", {"operatorName": ADMIN, "mode": "second"})
    check("大屏切二面模式", status == 200)

    # 9. 二面叫号（二面官身份）
    status, cand = call("POST", "/second/start", {"candidateId": cid, "interviewerName": A_SEATS[0]})
    check("二面叫号", status == 200 and cand.get("status") == "second_interviewing",
          f"status={cand.get('status')} {cand.get('message','')}")

    # 10. 核验 A-D 分组、面试中考生上大屏
    status, big = call("GET", "/display/big-screen")
    groups = [g["groupName"] for g in big.get("groups", [])]
    check("大屏二面为A-D组", sorted(groups) == ["A", "B", "C", "D"], f"groups={groups}")
    a_group = next((g for g in big.get("groups", []) if g["groupName"] == "A"), None)
    a_current = (a_group or {}).get("current")
    check("大屏二面显示面试中考生", a_current is not None,
          f"A组current={a_current}")

    # 11. 二面三席打分
    second_scores = [{"interviewerName": n, "score": 9.0, "judgeComment": "二面备注"} for n in A_SEATS]
    status, cand = call("POST", f"/{cid}/scores", {"operatorName": ADMIN, "scores": second_scores})
    check("二面三席打分完成", status == 200 and cand.get("status") == "second_completed",
          f"status={cand.get('status')} secondScores={len(cand.get('secondScores') or [])} "
          f"secondAvg={cand.get('secondAverageScore')}")
    check("二面明细落库", len(cand.get("secondScores") or []) == 3)
    check("二面平均分计算", cand.get("secondAverageScore") is not None)

    # 12. 二面队列
    status, queue = call("GET", "/second/queue")
    check("二面队列可获取", status == 200, f"waiting={len(queue.get('waiting', []))} interviewing={len(queue.get('interviewing', []))}")

    # 13. 二面统计
    status, stats = call("GET", "/second/stats")
    check("二面统计返回", status == 200 and "admitted" not in stats and "rejected" not in stats,
          f"stats={json.dumps(stats, ensure_ascii=False)}")

    # 恢复一面模式
    call("POST", "/display/toggle-mode", {"operatorName": ADMIN, "mode": "first"})

    failed = [r for r in results if not r[1]]
    print("\n==== 汇总: %d 通过 / %d 失败 ====" % (len(results) - len(failed), len(failed)))
    if failed:
        for label, _, extra in failed:
            print("FAILED:", label, extra)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
