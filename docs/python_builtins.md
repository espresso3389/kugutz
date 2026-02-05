# Python Built-ins (Worker)

These built-ins are **Python-facing APIs** that either use native Python
packages directly or call Kotlin endpoints for platform integration.

## Permission Flow
- TFLite: no special permission; use Python packages directly.
- USB/UVC: Android requires explicit USB device permission (system prompt).
- STT: Android microphone permission is required.
- TTS: no runtime permission required.

## Built-ins

### TFLite
Use Python’s TFLite package directly (no Kotlin endpoint). Ensure the runtime
includes TFLite and that models live under `files/user/models/`.

### USB / UVC
Use Python ffi to `libusb` / `libuvc` directly. Android requires a USB device
permission grant before device access will work.

### Android TTS
**Endpoint:** `POST /builtins/tts`  
**Python:** `builtins.tts(text, voice=None)`  

Payload:
```json
{
  "permission_id": "p_...",
  "text": "Hello",
  "voice": "en-US"
}
```

### Android STT
**Endpoint:** `POST /builtins/stt`  
**Python:** `builtins.stt(audio_path, locale=None)`  

Payload:
```json
{
  "audio_path": "files/user/audio/input.wav",
  "locale": "en-US"
}
```

## Notes
- TTS/STT endpoints are currently stubs and return `not_implemented`.
- Kotlin owns platform permissions and should prompt the user (USB, mic).
- The worker should treat these calls as **best effort** and handle `not_implemented`.
