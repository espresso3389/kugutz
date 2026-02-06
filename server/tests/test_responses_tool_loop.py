import json
import os
import sys
import unittest
from pathlib import Path


class _FakeStorage:
    def __init__(self):
        self._settings = {}
        self._creds = {}

    def get_setting(self, key: str):
        return self._settings.get(key)

    def set_setting(self, key: str, value: str):
        self._settings[key] = value

    def get_credential(self, name: str):
        return self._creds.get(name)

    def set_credential(self, name: str, value: str):
        self._creds[name] = {"name": name, "value": value, "updated_at": 0}


class ResponsesToolLoopTest(unittest.TestCase):
    def test_responses_tool_loop_executes_shell_exec_with_env_key(self):
        # Import inside test so monkeypatching is isolated.
        server_dir = Path(__file__).resolve().parents[1]
        if str(server_dir) not in sys.path:
            sys.path.insert(0, str(server_dir))
        from agents import runtime as rt

        storage = _FakeStorage()
        logs = []
        shell_calls = []
        post_calls = []

        def emit_log(event: str, data: dict):
            logs.append((event, data))

        def shell_exec(cmd: str, args: str, cwd: str):
            shell_calls.append((cmd, args, cwd))
            return {"status": "ok", "code": 0, "output": "OK\n"}

        def tool_invoke(tool: str, args: dict, request_id, detail: str):
            return {"status": "ok", "tool": tool, "args": args, "detail": detail}

        # Env var fallback is the point of this test.
        os.environ["OPENAI_API_KEY"] = "sk-test-env"

        # Mock requests.post used by BrainRuntime.
        def fake_post(url, headers=None, data=None, timeout=None):
            post_calls.append(
                {
                    "url": url,
                    "headers": dict(headers or {}),
                    "body": json.loads(data or "{}"),
                }
            )
            call_idx = len(post_calls)
            if call_idx == 1:
                payload = {
                    "id": "resp_1",
                    "output": [
                        {
                            "type": "function_call",
                            "name": "shell_exec",
                            "call_id": "call_1",
                            "arguments": json.dumps({"cmd": "python", "args": "-c \"print(123)\"", "cwd": ""}),
                        }
                    ],
                }
            else:
                payload = {
                    "id": "resp_2",
                    "output": [
                        {
                            "type": "message",
                            "content": [{"type": "output_text", "text": "Done."}],
                        }
                    ],
                }

            class _Resp:
                def raise_for_status(self):
                    return None

                def json(self):
                    return payload

            return _Resp()

        original_post = rt.requests.post
        rt.requests.post = fake_post
        try:
            user_dir = Path("/tmp/kugutz-test-user")
            user_dir.mkdir(parents=True, exist_ok=True)
            brain = rt.BrainRuntime(
                user_dir=user_dir,
                storage=storage,
                emit_log=emit_log,
                shell_exec=shell_exec,
                tool_invoke=tool_invoke,
            )
            # Minimal config for responses tool loop.
            brain.update_config(
                {
                    "enabled": True,
                    "model": "gpt-test",
                    "provider_url": "https://api.openai.com/v1/responses",
                    "api_key_credential": "openai_api_key",
                }
            )

            item = {"id": "chat_1", "kind": "chat", "text": "Run python", "meta": {}, "created_at": 0}
            brain._process_with_responses_tools(item)

            self.assertEqual(len(shell_calls), 1)
            self.assertEqual(shell_calls[0][0], "python")
            # Assert we used env key in the Authorization header without printing it.
            self.assertTrue(post_calls)
            self.assertEqual(post_calls[0]["headers"].get("Authorization"), "Bearer sk-test-env")

            msgs = brain.list_messages(limit=50)
            self.assertTrue(any(m.get("role") == "assistant" and "Done." in m.get("text", "") for m in msgs))
            self.assertTrue(any(m.get("role") == "tool" for m in msgs))
        finally:
            rt.requests.post = original_post


if __name__ == "__main__":
    unittest.main()
