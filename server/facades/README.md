# Python Facade Packages (Android)

These are **pure-Python facade packages** used to satisfy `pip` dependency resolution on Android
when the app already bundles the real native libraries (`.so`) via `jniLibs/`.

The facades:
- provide a stable import surface (`import libusb`, `import libuvc`, `import opencv_android`)
- offer a minimal `ctypes` loader helper
- are shipped as wheels inside the APK wheelhouse so `pip install ...` can succeed offline

Build wheels:
`python3 scripts/build_facade_wheels.py`

