# Dropbear Modifications (Android App Sandbox)

This project bundles Dropbear SSH server to provide on-device SSH access. Dropbear’s default server assumes a traditional Linux user database, full PTY/tty ownership control, and system-level permissions. On Android (non-root, app sandbox), those assumptions do not hold. We apply a small set of source patches at build time to make Dropbear usable inside the app’s private sandbox.

Below is a concise explanation of what we change and why.

## Summary of Changes

1) **Auth user fallback (no /etc/passwd)**
   - **Files patched:** `src/svr-auth.c`, `src/loginrec.c`
   - **Why:** Android app sandboxes do not have real system users or `/etc/passwd` entries for arbitrary usernames. Dropbear normally rejects logins if `getpwnam()` fails. We accept the username, and if there is no passwd entry:
     - Use the app’s effective UID/GID.
     - Provide a minimal default home and shell.
   - **Impact:** Users can authenticate with authorized keys even when the username is not a system account (e.g., `kawasaki`).

2) **Disable strict pubkey file permission checks**
   - **Files patched:** `src/svr-authpubkey.c`
   - **Why:** Dropbear expects `authorized_keys` and its parent directories to be owned by the target user and to have restrictive modes. In the Android app sandbox, the ownership and permission model is constrained and can fail these checks even when keys are secure. We bypass the strict permission checks and rely on the app’s private storage permissions instead.
   - **Impact:** Authorized keys stored in app-private storage are accepted.

3) **Avoid `setegid` / `seteuid`**
   - **Files patched:** `src/svr-authpubkey.c`
   - **Why:** Android’s app sandbox + seccomp policies can block `setegid()`/`seteuid()` in this context. Calls were causing child crashes (`SIGSYS`).
   - **Impact:** Dropbear no longer attempts to swap IDs when reading `authorized_keys`; it runs as the app user throughout.

4) **Relax login record user lookup**
   - **Files patched:** `src/loginrec.c`
   - **Why:** `login_init_entry()` calls `getpwnam()` and hard-exits if it fails, which it will for app sandbox users. We fall back to the current effective UID instead of exiting.
   - **Impact:** Avoids aborts during login tracking.

5) **Graceful PTY handling in Android**
   - **Files patched:** `src/sshpty.c`, `src/svr-chansession.c`
   - **Why:** PTY allocation and ownership operations assume access to `/dev/pts`, chown/chmod, and controlling terminal ops. These are not permitted in the app sandbox and cause errors like:
     - `ioctl(TIOCSCTTY): I/O error`
     - `open /dev/tty failed`
     - `chown/chmod ... Permission denied`
   - **Fixes:**
     - Add Android-specific PTMX path (best-effort).
     - If PTY allocation or setup fails, **fall back to no-pty** instead of aborting the session.
     - Downgrade `pty_setowner` fatal errors to warnings.
     - Skip `pty_setowner` if there is no passwd entry.
   - **Impact:** Non-interactive SSH commands work reliably, and PTY requests degrade to no-pty rather than crashing.

6) **Disable agent forwarding**
   - **Files patched:** `localoptions.h`
   - **Why:** Reduces attack surface and avoids features that depend on broader OS integration.

## Where the Patches Live

All modifications are applied during Dropbear build in:

- `scripts/build_dropbear.sh`

That script:
1. Downloads the latest Dropbear tarball.
2. Applies the patch set using inline Python edits.
3. Builds Dropbear for Android ABIs and installs binaries into:
   - `app/android/app/src/main/assets/bin/<abi>/`
   - `app/android/app/src/main/jniLibs/<abi>/`

## Behavior Tradeoffs

- **No true interactive TTY**: Interactive shells that require a PTY may not behave perfectly. Non-PTY exec commands work normally.
- **Security model**: We rely on app-private storage + user consent for SSH enablement rather than OS-level user/perm checks.
- **Username mismatch**: The SSH username does not map to a system user; it is treated as a label only.

## Rationale

The goal is to provide a usable, secure SSH entry point within Android’s app sandbox **without root**. The changes are conservative: they remove assumptions that do not apply in this environment and avoid crashing on normal SSH flows.

If you want to review or adjust any of these patches, check `scripts/build_dropbear.sh` and search for the patch blocks.

