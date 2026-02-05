import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer


class WorkerHandler(BaseHTTPRequestHandler):
    server_version = "KugutzWorker/0.1"

    def do_GET(self):
        if self.path == "/health":
            self._send_json({"status": "ok"})
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        if self.path == "/shutdown":
            self._send_json({"status": "stopping"})
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return
        self.send_response(404)
        self.end_headers()

    def log_message(self, format, *args):
        return

    def _send_json(self, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main():
    server = HTTPServer(("127.0.0.1", 8766), WorkerHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
