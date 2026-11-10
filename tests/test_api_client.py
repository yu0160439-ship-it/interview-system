import io
import json
import os
import unittest
import urllib.error
from unittest.mock import patch

from api_client import ApiClient


class JsonResponse(io.BytesIO):
    def __init__(self, body, status=200):
        super().__init__(json.dumps(body).encode("utf-8"))
        self.status = status


def login(token):
    return JsonResponse({"success": True, "token": token})


def http_error(status):
    return urllib.error.HTTPError("http://example.invalid", status, "error", {},
                                  io.BytesIO(json.dumps({"message": "rejected"}).encode("utf-8")))


class ApiClientTests(unittest.TestCase):
    def setUp(self):
        self.client = ApiClient("http://example.invalid/api/candidates")
        self.opener = patch("api_client.urllib.request.urlopen").start()
        self.addCleanup(patch.stopall)

    def requests(self):
        return [entry.args[0] for entry in self.opener.call_args_list]

    def test_public_student_requests_skip_login_and_authorization(self):
        routes = [("POST", "/pre-register/public"), ("POST", "/public/check-in"),
                  ("POST", "/second/check-in"), ("GET", "/public/detail/id?refresh=1")]
        self.opener.side_effect = [JsonResponse({}) for _ in routes]
        for method, path in routes:
            self.client.call(method, path, {"operatorName": "admin"})
        self.assertEqual(len(self.requests()), len(routes))
        self.assertTrue(all(request.get_header("Authorization") is None for request in self.requests()))

    def test_staff_reads_login_admin_once_and_cache_token(self):
        self.opener.side_effect = [login("admin-token"), JsonResponse({"items": []}), JsonResponse({"items": []})]
        self.client.call("GET", "?pageSize=100")
        self.client.call("GET", "/stats")
        requests = self.requests()
        self.assertEqual(len(requests), 3)
        self.assertTrue(requests[0].full_url.endswith("/interviewer-login"))
        self.assertEqual(requests[1].get_header("Authorization"), "Bearer admin-token")
        self.assertEqual(requests[2].get_header("Authorization"), "Bearer admin-token")

    def test_floating_and_interviewer_checks_use_their_own_real_tokens(self):
        self.opener.side_effect = [login("floating-token"), JsonResponse({}),
                                   login("interviewer-token"), JsonResponse({})]
        self.client.call("POST", "/id/scores", {"operatorName": "流动人员1", "scores": [
            {"interviewerName": "面试官A1", "score": 8}]})
        self.client.call("POST", "/second/start", {"interviewerName": "面试官A1"})
        requests = self.requests()
        self.assertEqual(json.loads(requests[0].data)["name"], "流动人员1")
        self.assertEqual(requests[1].get_header("Authorization"), "Bearer floating-token")
        self.assertEqual(json.loads(requests[2].data)["name"], "面试官A1")
        self.assertEqual(requests[3].get_header("Authorization"), "Bearer interviewer-token")

    def test_environment_passwords_are_used_for_login(self):
        self.opener.side_effect = [login("admin-token"), JsonResponse({}), login("staff-token"), JsonResponse({})]
        with patch.dict(os.environ, {"ADMIN_PASSWORD": "configured-admin", "STAFF_PASSWORD": "configured-staff"}):
            self.client.call("GET", "?pageSize=100")
            self.client.call("POST", "/check-in", {"operatorName": "流动人员1"})
        self.assertEqual(json.loads(self.requests()[0].data)["password"], "configured-admin")
        self.assertEqual(json.loads(self.requests()[2].data)["password"], "configured-staff")

    def test_explicit_login_bypasses_authentication_and_preserves_failure_result(self):
        self.opener.return_value = JsonResponse({"success": False, "message": "wrong password"})
        status, result = self.client.call("POST", "/interviewer-login", {"name": "unknown", "password": "wrong"})
        self.assertEqual(status, 200)
        self.assertFalse(result["success"])
        self.assertEqual(len(self.requests()), 1)
        self.assertIsNone(self.requests()[0].get_header("Authorization"))

    def test_failed_client_login_does_not_send_business_request(self):
        self.opener.return_value = JsonResponse({"success": False, "message": "wrong password"})
        with self.assertRaisesRegex(RuntimeError, "登录失败"):
            self.client.call("GET", "?pageSize=100")
        self.assertEqual(len(self.requests()), 1)

    def test_expired_staff_token_is_refreshed_once(self):
        self.opener.side_effect = [login("old-token"), http_error(401), login("new-token"), JsonResponse({"ok": True})]
        status, result = self.client.call("POST", "/check-in", {"operatorName": "流动人员1"})
        self.assertEqual((status, result), (200, {"ok": True}))
        self.assertEqual(self.requests()[1].get_header("Authorization"), "Bearer old-token")
        self.assertEqual(self.requests()[3].get_header("Authorization"), "Bearer new-token")

    def test_role_rejection_is_returned_without_retrying_as_admin(self):
        self.opener.side_effect = [login("floating-token"), http_error(403)]
        status, _ = self.client.call("POST", "/second/move-to", {"operatorName": "流动人员1"})
        self.assertEqual(status, 403)
        self.assertEqual(len(self.requests()), 2)
        self.assertEqual(self.requests()[1].get_header("Authorization"), "Bearer floating-token")

    def test_unknown_public_prefix_and_wrong_method_still_use_authentication(self):
        for method, path in [("DELETE", "/public/detail/id"), ("GET", "/public/unknown"),
                             ("GET", "/public/detail/id/extra")]:
            self.assertFalse(ApiClient._is_public(method, path))

    def test_account_override_can_test_forged_identity_parameters(self):
        self.opener.side_effect = [login("floating-token"), http_error(403)]
        status, _ = self.client.call("POST", "/display/toggle-mode", {"operatorName": "admin", "mode": "second"},
                                     account="流动人员1")
        self.assertEqual(status, 403)
        self.assertEqual(self.requests()[1].get_header("Authorization"), "Bearer floating-token")


if __name__ == "__main__":
    unittest.main()
