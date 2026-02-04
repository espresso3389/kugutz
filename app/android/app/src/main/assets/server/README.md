# Local Python Service Scaffold

This is a minimal local HTTP server intended to run on-device.

## Endpoints (stub)
- GET /health
- GET /ui/version
- POST /ssh/start
- POST /ssh/stop
- GET /ssh/status
- POST /ssh/keys
- GET /ssh/keys
- DELETE /ssh/keys/{fingerprint}

## Dropbear Binary
Bundled per-ABI at:
  app/android/app/src/main/assets/bin/<abi>/dropbear
  app/android/app/src/main/jniLibs/<abi>/libdropbear.so

At runtime it is extracted to:
  files/bin/dropbear

The server prefers the native library copy (DROPBEAR_BIN) so it can
execute under Android SELinux restrictions. Use:
  scripts/build_dropbear.sh
- POST /tools/{tool}/invoke
- POST /permissions/request
- GET /permissions/pending
- POST /permissions/{id}/approve
- POST /permissions/{id}/deny
- GET /logs/stream (SSE)
- POST /programs/start
- POST /programs/{id}/stop
- GET /programs
- GET /audit/recent
- GET /vault/credentials
- GET /vault/credentials/{name}
- POST /vault/credentials/{name}
- DELETE /vault/credentials/{name}
- POST /services/register
- GET /services
- GET /services/{name}
- GET /services/{name}/vault/{credential}

## Run (desktop)
```
python app.py
```

## Next Steps
- Wire log streaming to UI.
- Implement tool routing + permission checks.
- Replace SQLite with a more robust store if needed.
- Add tool implementations (filesystem/shell) behind permission checks.
- Enable SQLCipher by ensuring pysqlcipher3 is available and SQLCIPHER_KEY_FILE is set.
- Credential vault endpoints require an approved permission_id from /permissions/request.
- Service registration requires a credential permission and creates a service-specific vault snapshot.
- Service vault uses per-service Android Keystore keys via the local vault server on 127.0.0.1:8766.
