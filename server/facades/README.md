# Python Facade Packages (Android)

This folder holds **pure-Python facades** (and related notes) for Android, where the app already
bundles the real native libraries (`.so`) via `jniLibs/`.

Policy:
- Prefer bundling upstream wheels for pure-Python packages (e.g. `pyusb`) so imports work normally.
- Use facades only when upstream doesn't ship Android wheels but dependency resolution expects the
  distribution to exist (e.g. `opencv-python`).

Build wheels:
`python3 scripts/build_facade_wheels.py`

Notes:
- `pyusb` is bundled as an upstream wheel (not a facade).
- OpenCV is shipped as an `opencv-python` facade distribution so normal `pip` deps can resolve.
- This facade does not provide real `cv2` bindings; `import cv2` raises a clear `ImportError`.
