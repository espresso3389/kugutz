import json
import os
import time
import urllib.error
import urllib.request
from typing import Any, Dict


class CloudRequestTool:
    """
    Thin Python wrapper around Kotlin's /cloud/request endpoint.

    The model crafts the request template; Kotlin expands placeholders and injects secrets
    server-side (vault/config/file expansion) and enforces permission prompts.
    """

    def __init__(self, base_url: str = "http://127.0.0.1:8765"):
        self.base_url = base_url.rstrip("/")
        self._identity = (os.environ.get("KUGUTZ_IDENTITY") or os.environ.get("KUGUTZ_SESSION_ID") or "").strip() or "default"

    def set_identity(self, identity: str) -> None:
        self._identity = str(identity or "").strip() or "default"

    def _request_json(self, method: str, path: str, body: Dict[str, Any] | None) -> Dict[str, Any]:
        url = self.base_url + path
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self._identity:
            headers["X-Kugutz-Identity"] = self._identity
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=40) as resp:
                raw = resp.read()
                try:
                    parsed = json.loads(raw.decode("utf-8")) if raw else {}
                except Exception:
                    parsed = {"raw": raw.decode("utf-8", errors="replace")}
                return {"status": "ok", "http_status": resp.status, "body": parsed}
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                parsed = json.loads(raw.decode("utf-8")) if raw else {}
            except Exception:
                parsed = {"raw": raw.decode("utf-8", errors="replace")}
            return {"status": "ok", "http_status": e.code, "body": parsed}
        except Exception as e:
            return {"status": "error", "error": "request_failed", "detail": str(e)}

    def _wait_for_permission(self, permission_id: str, timeout_s: float = 45.0) -> str:
        pid = str(permission_id or "").strip()
        if not pid:
            return "invalid"
        deadline = time.time() + max(1.0, float(timeout_s or 45.0))
        while time.time() < deadline:
            resp = self._request_json("GET", f"/permissions/{pid}", None)
            body = resp.get("body") if isinstance(resp, dict) else None
            if isinstance(body, dict):
                st = str(body.get("status") or "").strip()
                if st in {"approved", "denied", "used"}:
                    return st
            time.sleep(0.8)
        return "timeout"

    def run(self, args: Dict[str, Any]) -> Dict[str, Any]:
        identity = str(args.get("identity") or args.get("session_id") or "").strip()
        if identity:
            self.set_identity(identity)

        req = args.get("request")
        payload = req if isinstance(req, dict) else args
        if not isinstance(payload, dict):
            return {"status": "error", "error": "invalid_request"}

        permission_id = str(payload.get("permission_id") or "").strip()

        def do(pid: str) -> Dict[str, Any]:
            p = dict(payload)
            if self._identity and "identity" not in p:
                p["identity"] = self._identity
            if pid:
                p["permission_id"] = pid
            return self._request_json("POST", "/cloud/request", p)

        r = do(permission_id)
        if r.get("status") != "ok":
            return r
        http_status = int(r.get("http_status") or 0)
        body = r.get("body")

        if http_status == 403 and isinstance(body, dict) and body.get("status") == "permission_required":
            req_obj = body.get("request") if isinstance(body.get("request"), dict) else {}
            pid = str(req_obj.get("id") or "").strip()
            if not pid:
                return {"status": "error", "error": "permission_required", "detail": "missing permission id"}
            wait = self._wait_for_permission(pid, timeout_s=45.0)
            if wait == "approved":
                r2 = do(pid)
                if r2.get("status") != "ok":
                    return r2
                return r2.get("body") if isinstance(r2.get("body"), dict) else {"status": "error", "error": "invalid_response"}
            if wait == "denied":
                return {"status": "error", "error": "permission_denied"}
            return {"status": "error", "error": "permission_timeout"}

        return body if isinstance(body, dict) else {"status": "error", "error": "invalid_response"}

