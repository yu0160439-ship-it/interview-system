# -*- coding: utf-8 -*-
"""Authenticated JSON client shared by the database regression scripts.

Identity fields select a test account only; the server authorizes the JWT obtained
from a real login. Public student endpoints are called anonymously.
"""
import json
import os
import urllib.error
import urllib.parse
import urllib.request


class ApiClient:
    PUBLIC_ROUTES = {
        ("POST", "/pre-register/public"),
        ("POST", "/public/search-by-name"),
        ("POST", "/public/check-in"),
        ("POST", "/public/update-departments"),
        ("POST", "/second/check-in"),
        ("POST", "/interviewer-login"),
        ("POST", "/second/login"),
        ("GET", "/display/big-screen"),
        ("GET", "/group-counts"),
        ("GET", "/display/stream"),
        ("GET", "/display/mode"),
    }

    def __init__(self, base_url="http://localhost:8080/api/candidates", timeout=10):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self._tokens = {}

    @classmethod
    def _is_public(cls, method, path):
        route = urllib.parse.urlsplit(path).path
        if (method, route) in cls.PUBLIC_ROUTES:
            return True
        prefix = "/public/detail/"
        if method == "GET" and route.startswith(prefix):
            candidate_id = route[len(prefix):]
            return bool(candidate_id.strip()) and "/" not in candidate_id
        return False

    @staticmethod
    def _decode(raw):
        raw = raw.decode("utf-8", "replace")
        if not raw:
            return {}
        try:
            return json.loads(raw)
        except ValueError:
            return {"message": raw}

    def _send(self, method, path, body=None, token=None):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
        headers = {"Content-Type": "application/json; charset=utf-8"}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return response.status, self._decode(response.read())
        except urllib.error.HTTPError as error:
            return error.code, self._decode(error.read())

    def _token_for(self, account):
        if account not in self._tokens:
            password = os.environ.get("ADMIN_PASSWORD", "change-me") if account == "admin" \
                else os.environ.get("STAFF_PASSWORD", "123456")
            status, result = self._send("POST", "/interviewer-login", {"name": account, "password": password})
            if status != 200 or not result.get("success") or not result.get("token"):
                raise RuntimeError("测试账号 %s 登录失败 (%s)：%s" %
                                   (account, status, result.get("message", "未返回有效 JWT")))
            self._tokens[account] = result["token"]
        return self._tokens[account]

    def call(self, method, path, body=None, *, account=None):
        method = method.upper()
        if self._is_public(method, path):
            return self._send(method, path, body)
        if account is None:
            identity = body if isinstance(body, dict) and method not in ("GET", "HEAD") else {}
            account = identity.get("operatorName") or identity.get("interviewerName") or "admin"
        token = self._token_for(account)
        status, result = self._send(method, path, body, token)
        if status == 401:
            # Long regressions may cross token expiry; retry once with a fresh login.
            self._tokens.pop(account, None)
            status, result = self._send(method, path, body, self._token_for(account))
        return status, result
