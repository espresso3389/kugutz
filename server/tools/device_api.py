import json
import os
import urllib.error
import urllib.request
from typing import Any, Dict, Optional


class DeviceApiTool:
    _ACTIONS: Dict[str, Dict[str, Any]] = {
        "python.status": {"method": "GET", "path": "/python/status", "permission": False},
        "python.restart": {"method": "POST", "path": "/python/restart", "permission": True},
        "ssh.status": {"method": "GET", "path": "/ssh/status", "permission": False},
        "ssh.config": {"method": "POST", "path": "/ssh/config", "permission": True},
        "ssh.pin.status": {"method": "GET", "path": "/ssh/pin/status", "permission": False},
        "ssh.pin.start": {"method": "POST", "path": "/ssh/pin/start", "permission": True},
        "ssh.pin.stop": {"method": "POST", "path": "/ssh/pin/stop", "permission": True},
        "shell.exec": {"method": "POST", "path": "/shell/exec", "permission": True},
        "brain.memory.get": {"method": "GET", "path": "/brain/memory", "permission": False},
        "brain.memory.set": {"method": "POST", "path": "/brain/memory", "permission": True},
    }

    def __init__(self, base_url: str = "http://127.0.0.1:8765"):
        self.base_url = base_url.rstrip("/")
        self._identity = (os.environ.get("KUGUTZ_IDENTITY") or os.environ.get("KUGUTZ_SESSION_ID") or "").strip() or "default"
        # Cache approvals (in-memory). Kotlin also reuses approvals server-side by identity/capability.
        self._permission_ids: Dict[str, str] = {}

    def set_identity(self, identity: str) -> None:
        self._identity = str(identity or "").strip() or "default"

    def run(self, args: Dict[str, Any]) -> Dict[str, Any]:
        action = str(args.get("action") or "").strip()
        if not action:
            return {"status": "error", "error": "missing_action"}
        spec = self._ACTIONS.get(action)
        if not spec:
            return {"status": "error", "error": "unknown_action"}

        payload = args.get("payload")
        if payload is None:
            payload = {}
        if not isinstance(payload, dict):
            return {"status": "error", "error": "invalid_payload"}

        if action == "shell.exec":
            cmd = str(payload.get("cmd") or "")
            if cmd not in {"python", "pip", "uv", "curl"}:
                return {"status": "error", "error": "command_not_allowed"}

        if spec["permission"]:
            detail = str(args.get("detail") or "").strip()
            if not detail:
                detail = f"{action}: {json.dumps(payload, ensure_ascii=True)[:240]}"
            # We intentionally do not block/wait here; the runtime/UI will ask the user to approve.
            perm_tool, perm_capability, perm_scope = self._permission_profile_for_action(action)
            pid, req = self._get_or_request_permission(perm_tool, perm_capability, perm_scope, detail)
            if not pid:
                return {"status": "permission_required", "request": req}
            # Pass through permission_id so Kotlin endpoints can enforce if they choose to.
            if spec["method"] == "POST" and isinstance(payload, dict) and "permission_id" not in payload:
                payload["permission_id"] = pid

        body = payload if spec["method"] == "POST" else None
        return self._request_json(spec["method"], spec["path"], body)

    def _permission_profile_for_action(self, action: str) -> tuple[str, str, str]:
        a = (action or "").strip()
        if a.startswith("ssh.pin."):
            return "ssh_pin", "ssh.pin", "session"
        # Default to long-lived approvals to avoid repeated prompts during agentic workflows.
        return "device_api", "device_api", "persistent"

    def _get_or_request_permission(self, tool: str, capability: str, scope: str, detail: str) -> tuple[str, Dict[str, Any]]:
        # If we already have an approved permission id, keep using it.
        cache_key = f"{tool}::{capability}::{scope}"
        cached = self._permission_ids.get(cache_key, "")
        if cached and self._is_approved(cached):
            return cached, {"id": cached, "status": "approved"}

        # Ask Kotlin to create/reuse a session-scoped permission.
        req = self._request_permission(tool=tool, capability=capability, scope=scope, detail=detail)
        pid = str(req.get("id") or "").strip()
        if pid:
            self._permission_ids[cache_key] = pid
            if self._is_approved(pid):
                return pid, req
        return "", req

    def _is_approved(self, permission_id: str) -> bool:
        pid = str(permission_id or "").strip()
        if not pid:
            return False
        resp = self._request_json("GET", f"/permissions/{pid}", None)
        body = resp.get("body") if isinstance(resp, dict) else None
        if isinstance(body, dict):
            return str(body.get("status") or "") == "approved"
        return False

    def _request_permission(self, *, tool: str, capability: str, scope: str, detail: str) -> Dict[str, Any]:
        resp = self._request_json(
            "POST",
            "/permissions/request",
            {
                "tool": tool,
                "detail": detail,
                # "once" makes agent usage unbearable; keep a short-lived approval.
                "scope": scope,
                "identity": self._identity,
                "capability": capability,
            },
        )
        if resp.get("status") != "ok":
            return {"status": "error", "error": "permission_request_failed", "detail": resp}
        body = resp.get("body")
        return body if isinstance(body, dict) else {"status": "error", "error": "invalid_permission_response"}

    def _request_json(self, method: str, path: str, body: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self._identity:
            headers["X-Kugutz-Identity"] = self._identity
        req = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=12) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                parsed = json.loads(raw) if raw else {}
                return {"status": "ok", "http_status": int(resp.status), "body": parsed}
        except urllib.error.HTTPError as ex:
            raw = ex.read().decode("utf-8", errors="replace")
            try:
                parsed = json.loads(raw) if raw else {}
            except Exception:
                parsed = {"raw": raw}
            return {"status": "http_error", "http_status": int(ex.code), "body": parsed}
        except Exception as ex:
            return {"status": "error", "error": str(ex)}
