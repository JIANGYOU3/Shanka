---
name: android-device-debugging
description: Install or update Android APKs, build and run the app in the Windows-side Android Emulator from WSL with screenshots, and diagnose physical-device ADB, signing, adb reverse, Windows-to-WSL backend access, instrumentation, screen state, and vendor background restrictions. Use for real phones connected to a Windows host AND for any emulator request on this WSL2 machine (launch emulator, run app, screencap) — the android-emulator plugin's desktop-emulator flow is unsupported on Linux hosts; do not use for production release-signing design.
---

# Android Device Debugging

Build an evidence chain instead of labeling every timeout an ADB, USB, backend, or app failure.

## Operating boundary

- Let the Windows SDK `adb.exe` own device transports (USB phones and the Windows-side emulator). Do not start a competing WSL ADB server.
- Enumerate `adb devices -l` first and select the exact serial. Never guess when multiple devices exist.
- Treat source tests, APK build, package installation, reverse transport, app networking, and business acceptance as separate evidence layers.
- Do not uninstall, clear data, revoke permissions, change AppOps, disable verification, or alter device-wide security settings without explicit authorization.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed package name matches but its signing certificate does not. Preserve user data: use a separate debug application ID or obtain a matching signer; do not default to uninstalling.

## Preferred workflow

1. Locate Windows ADB at `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` and enumerate devices.
2. Inspect manufacturer, Android version, power state, installed package, and existing reverse rules.
3. Confirm the host backend from Windows itself before involving the phone.
4. Install with `adb install -r` only when package name and signer are expected to match. A debug `applicationIdSuffix` should coexist with release.
5. Recreate `adb reverse` after every cable reconnect or USB-mode change; reverse rules are transport state and may disappear normally.
6. Prove payload flow with a real HTTP request. A listed rule or accepted TCP connection with zero bytes is not enough.
7. Run an app-owned OkHttp/instrumentation probe. If a browser passes but the app fails, inspect app/process state instead of blaming adbd.
8. Run business/offline acceptance only after the transport probe passes.

Use [scripts/android-device-debug.ps1](scripts/android-device-debug.ps1) for routine Windows-side actions. From WSL, convert its path with `wslpath -w` and invoke Windows PowerShell; pass Windows or UNC APK paths.

## Windows-side emulator (WSL host)

The `android-emulator` plugin's desktop-emulator workflow is unusable here: its `android_preflight` rejects Linux hosts, and installing an emulator inside WSL is not the fix. Run the emulator on Windows and drive it like any other adb target with `adb.exe -s emulator-5554`.

Gradle never runs in WSL on this machine: the only SDK/NDK is Windows-side (a WSL build dies in `stripDebugSymbols`), and Gradle over `\\wsl.localhost` UNC fails (`函数不正确`). Copy sources to an NTFS directory and build with `gradlew.bat` there — full sequence in playbook §9.

This machine has a known host fault: the SDK `emulator/` directory is missing `opengl32sw.dll`, so the default graphics stack wedges the guest — the framebuffer stays black, boot never completes, and adb sits in `unauthorized` for minutes because the RSA dialog itself cannot render. Treat "unauthorized + black guest frame + no boot progress" as that host fault, not a slow boot: kill the emulator and relaunch with `-gpu swiftshader_indirect` (full sequence in playbook §9). Do not wipe data or recreate the AVD for this.

Guest-frame evidence when adb is not yet authorized: console over TCP `127.0.0.1:5554`, `auth` with the token from `C:\Users\<user>\.emulator_console_auth_token` (user root, not `.android`), then `screenrecord screenshot`. Once authorized, plain `adb exec-out screencap -p` is enough.

## Device-specific routing

### Xiaomi / MIUI

Check `dumpsys power` first. If `mWakefulness=Asleep`, ask the user to unlock the phone before deeper diagnosis. MIUI may reject ADB wake injection with `INJECT_EVENTS`; do not keep retrying it.

If a forced browser HTTP request traverses reverse but headless instrumentation times out, the USB/adbd path is healthy. Bring the real debug Activity to `TOP` while the probe runs, or redesign the acceptance harness to launch through a foreground component. Do not change AppOps before proving a UID policy actually blocks traffic.

Foreground choreography fixes network probes; it cannot fix test rules that launch their own activity. MIUI additionally aborts every activity start that originates from the app's own uid while the process hosts an instrumentation — logcat shows `ActivityTaskManager: Abort background activity starts` with `result code=102` — even when a shell `am start` already has the app foregrounded. Rules built on `ActivityScenario` (e.g. `createAndroidComposeRule`) then block forever instead of failing. Where this policy is active, probe the capability once per process (tagged launch + RESUMED latch with a short timeout) and let activity-launching tests assume-skip with an explicit reason. The abort can also finish the task to the launcher (`CLOSE_TO_HOME`), displacing an activity the shell started earlier.

Choreography details that decide success: launch the activity a couple of seconds after starting instrumentation — a sub-second `am start` loses the race with `am instrument`'s own process restart and the activity never resumes. Avoid `Instrumentation.startActivitySync` on an animating Compose UI (its idle detection never settles); have the harness block on an application-wide RESUMED latch via `registerActivityLifecycleCallbacks` instead.

### OPPO / ColorOS

Try the ordinary headless path first. ColorOS commonly allows reverse and instrumentation without foreground choreography. The first USB install of a new package may open an `InstallGuideActivity` and require one user confirmation. Once the same package and signer are installed, `adb install -r` should be tried before changing any security setting; repeated updates are commonly automatic.

## Discriminating evidence

- `adb reverse --list`: configuration only.
- Browser request bytes received by a Windows native listener: phone localhost → adbd → USB → Windows ADB payload is working.
- Windows `Invoke-WebRequest http://127.0.0.1:<port>/healthz` returning 200: Windows → WSL/backend is working.
- App probe 200: application network config, cleartext policy, reverse, and backend route work together.
- Browser passes while app fails: inspect package permissions, final merged manifest, process foreground state, VPN/vendor policy, and test harness.
- `toybox nc` connects but sends zero bytes: inconclusive; retest with browser HTTP or an app-owned request.
- An instrumentation probe that reads a `const val` (e.g. a `BuildConfig` base URL) reports the value inlined at test-compile time, not the installed app's runtime constant. When the probe and the app disagree on an endpoint, pull both installed APKs and grep their dex for the baked constant to expose a stale main APK.
- A test's `println` evidence lands in logcat (`System.out`), not in the `am instrument -r` status stream; grep logcat for a marker tag rather than parsing the runner stream.
- `OK (13 tests)` from `am instrument` counts assumption-skips as run. Count what actually executed from the per-test `INSTRUMENTATION_STATUS_CODE`: `1` started, `0` passed, `-1` failed, other negative values assumption-skipped. Never report the summary line as the executed count.
- A shell `am start` succeeding (`callingPackage com.android.shell`, result code 0) while the app's own start aborts (102) identifies a vendor background-activity-start policy; the activity, the launch intent, and the test are all fine.
- An app-side `ActivityManager.getRunningTasks` result is empty for ordinary apps on modern Android; verify foreground with lifecycle callbacks app-side or `dumpsys activity` shell-side, never that API.

## Windows and WSL quirks

- If WSL calling `powershell.exe` reports `UtilBindVsockAnyPort: socket failed 1`, run ADB commands in Windows PowerShell; this is WSL interop, not a phone diagnosis.
- If nested PowerShell swallows direct `adb.exe` output, use the bundled script, which captures child stdout/stderr.
- If WSL `curl` unexpectedly targets a proxy, use `curl --noproxy '*'` for localhost checks.
- Each WSL call into Windows `adb.exe` pays roughly 0.5–1 s of interop overhead. Choreography that needs precise timing between two device commands must run inside one `adb shell` script (start the instrument run in the background, `sleep`, then `am start`) rather than as two host-side invocations.
- Runner instrumentation args vary by version, and unsupported ones are silently ignored. Before building timing on a delay arg, confirm it exists in the runner you actually ship (inspect the runner AAR) and confirm it took effect by comparing logcat timestamps of process start vs `run started`.
- A local backend that stopped between probes was usually shut down gracefully, not crashed: uvicorn logs `Shutting down` … `Finished server process [pid]`, and a disappeared SQLite `-wal` file means a clean checkpoint. Read the log tail before re-diagnosing transport.
- Keep secrets out of command lines and test output. Device diagnostics need no API keys.

For exact commands, foreground instrumentation choreography, runner-result parsing, signing checks, and cleanup, read [references/windows-wsl-playbook.md](references/windows-wsl-playbook.md).

## Completion report

State exactly which layers passed, which device and serial were used, whether the screen/Activity had to be foreground, whether installation required confirmation, and what remains unverified. Do not call a JVM benchmark a real-device performance result.
