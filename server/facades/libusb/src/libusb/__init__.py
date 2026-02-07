import ctypes
import os
from typing import Optional


def _try_cdll(names: list[str]) -> Optional[ctypes.CDLL]:
    last: Optional[BaseException] = None
    for name in names:
        try:
            return ctypes.CDLL(name, mode=getattr(ctypes, "RTLD_GLOBAL", 0))
        except BaseException as ex:  # noqa: BLE001 - surface the last error if all probes fail
            last = ex
    if last is not None:
        raise OSError(f"Failed to load any of: {names}") from last
    return None


def load() -> ctypes.CDLL:
    """
    Load the app-bundled libusb shared library.

    Expected to be present in Android nativeLibraryDir as `libusb1.0.so`.
    """
    # First let the dynamic loader resolve via DT_RUNPATH / nativeLibraryDir.
    probes = ["libusb1.0.so"]

    # Fallback: explicit native lib dir if provided by the app runtime.
    nlib = os.environ.get("KUGUTZ_NATIVELIB")
    if nlib:
        probes.append(os.path.join(nlib, "libusb1.0.so"))

    return _try_cdll(probes)  # type: ignore[return-value]


__all__ = ["load"]

