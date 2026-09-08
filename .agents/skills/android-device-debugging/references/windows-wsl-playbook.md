# Windows + WSL Android debugging playbook

Use this reference when exact commands or a deeper fault split is needed. Replace every placeholder; never invent a serial, package, component, runner, or port.

## 0. Use the bundled helper

From the project root in WSL:

```bash
skill_script="$(wslpath -w "$PWD/.agents/skills/android-device-debugging/scripts/android-device-debug.ps1")"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$skill_script" -Action devices
```

Continue with the serial returned by that command:

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$skill_script" \
  -Action inspect -Serial '<serial>'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$skill_script" \
  -Action reverse -Serial '<serial>' -DevicePort 19180 -HostPort 19181
```

The helper also supports `install`, `reverse-list`, `reverse-clear`, `instrument`, `start`, and `force-stop`. Read its parameter block before use. `install` always uses replacement mode (`adb install -r`) and never uninstalls first.

## 1. Establish one ADB owner

Run USB operations through Windows ADB:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb version
& $adb devices -l
```

If more than one device is listed, choose the intended serial explicitly and use `-s $serial` on every command. Do not run `adb kill-server` until checking whether another active task depends on it.

Useful read-only inspection:

```powershell
& $adb -s $serial get-state
& $adb -s $serial shell getprop ro.product.manufacturer
& $adb -s $serial shell getprop ro.product.model
& $adb -s $serial shell getprop ro.build.version.release
& $adb -s $serial shell getprop ro.build.version.oplusrom
& $adb -s $serial dumpsys power
& $adb -s $serial reverse --list
```

Interpret `mWakefulness=Awake|Asleep`; do not infer it from cable charging state.

## 2. Install without losing data

Prefer a debug package that can coexist with release:

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        versionNameSuffix = "-debug"
    }
}
```

Builds still require Android signing. The normal debug keystore signs automatically; the phone does not need a custom certificate installed. To preserve update compatibility, keep the same application ID and debug signer across rebuilds.

Install or update:

```powershell
& $adb -s $serial install -r 'C:\path\app-debug.apk'
& $adb -s $serial install -r 'C:\path\app-debug-androidTest.apk'
```

From WSL, turn an APK path into a Windows UNC path:

```bash
wslpath -w /absolute/path/to/app-debug.apk
```

Interpret failures:

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: same package, different signer. Do not uninstall by default.
- ColorOS installation guide: ask the user to confirm the first install. Retry `install -r` before weakening device security; same-signer updates often become automatic.
- Empty or hanging install output: inspect the top window for `PackageInstaller` or `InstallGuideActivity` before blaming ADB.
- Runtime behavior contradicting the freshly built variant: the installed APK may be stale. Pull it from the `pm path` location and grep its dex for a known constant (for example the base URL). Note that a test APK can mask a stale main APK when `const val` values are inlined at test-compile time, so verify the main APK itself, not only what a probe prints.

## 3. Backend and reverse

Prove the backend from Windows:

```powershell
Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
  "http://127.0.0.1:$hostPort/healthz"
```

Create the mapping and read it back:

```powershell
& $adb -s $serial reverse "tcp:$devicePort" "tcp:$hostPort"
& $adb -s $serial reverse --list
```

The first port is used by the phone/App; the second is opened by the Windows host. They need not be equal.

Recreate reverse after:

- unplugging or replugging the cable;
- changing USB mode, such as charging → file transfer;
- restarting the ADB server or switching transport IDs.

Rule presence is not payload proof. For a discriminating transport test, start a Windows `TcpListener`, establish a fresh temporary reverse, then cold-start a browser URL through the device port. Capture an actual HTTP request and return a small response. A browser payload proves adbd transport independently of the app and backend.

## 4. App-owned probe

Compile the debug base URL explicitly for a physical device when the default points at emulator `10.0.2.2`:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  -PdebugApiBaseUrl=http://127.0.0.1:19180
```

The property name is project-specific; discover it from Gradle rather than assuming this example.

Run one instrumentation method:

```powershell
& $adb -s $serial shell am instrument -w -r `
  -e class 'com.example.DeviceProbeTest#probe' `
  'com.example.debug.test/androidx.test.runner.AndroidJUnitRunner'
```

Check the final merged debug manifest, not only source manifests:

- `android.permission.INTERNET` present;
- debug-only cleartext allowed when using local HTTP;
- instrumentation target package matches the debug application ID.

## 5. Xiaomi foreground choreography

First inspect power state. If asleep, ask the user to unlock. A failed `adb shell input keyevent 224` with `INJECT_EVENTS` is a device security decision; stop retrying it.

If browser payload passes but headless instrumentation times out, start instrumentation asynchronously and bring the real Activity to the foreground shortly afterward:

```powershell
$probe = Start-Process $adb -PassThru -ArgumentList @(
  '-s', $serial, 'shell', 'am', 'instrument', '-w', '-r',
  '-e', 'class', $testMethod, $runner
)
Start-Sleep -Milliseconds 2500
& $adb -s $serial shell am start -W -n $debugComponent
$probe.WaitForExit()
```

Capture stdout/stderr in real automation. This choreography is justified only after the browser/app split proves a foreground issue. Do not generalize it to OPPO or every Android device.

Two timing details that decide success: launch the activity 2–3 seconds after starting instrumentation, never sub-second — instrumentation force-restarts the target process, and an `am start` issued inside that window dies with it. And if the harness must wait for the foreground state itself, skip `startActivitySync` (its idle detection never settles under a continuously repainting Compose UI) and block on an application-wide RESUMED latch via `registerActivityLifecycleCallbacks`.

Foreground choreography cannot help tests that launch their own activity. While the process hosts an instrumentation, MIUI aborts every activity start from the app's own uid — `ActivityTaskManager: Abort background activity starts … result code=102` — even with the app already foregrounded via shell; `ActivityScenario`-based rules then hang forever instead of failing. Detect it once per process with a tagged canary launch (launch intent carrying a private extra, RESUMED latch awaiting that extra, short timeout), cache the result, and let those tests assume-skip with an explicit reason; keep one read-only network probe as the test that always executes. The abort can also finish the task to the launcher (`CLOSE_TO_HOME`), displacing an activity the shell started earlier.

## 6. Offline acceptance

Drive stages explicitly and force-stop only the debug package between them:

1. Online provision/hydrate.
2. Remove reverse and rate locally.
3. Force-stop/relaunch while still offline and verify persisted cache/outbox.
4. Restore reverse and drain.
5. Force-stop/relaunch and verify stable completion plus server-side event count.

Use real business scope. For example, an independent deck may belong to `/decks/{id}/review` and not `/study/today`, which may require a configured project plan. A transport success does not validate a mistaken business fixture.

## 7. Cleanup

Safe routine cleanup:

```powershell
& $adb -s $serial shell am force-stop $debugPackage
& $adb -s $serial reverse --remove-all
& $adb -s $serial reverse --list
```

Do not uninstall or clear package data unless the user explicitly authorizes losing that debug state. Never use debug cleanup commands against the release package by substituting names casually.

## 8. Reading instrument results and verifying runner args

The summary line lies about execution: `OK (13 tests)` includes assumption-skipped tests. Count real executions from the per-test status codes in the `-r` stream:

| `INSTRUMENTATION_STATUS_CODE` | Meaning |
| --- | --- |
| `1` | test started |
| `0` | passed |
| `-1` | failed (the paired `stack=` holds the reason) |
| other negative (`-2`, `-4`, …) | assumption-violated / skipped |

```bash
grep -E 'INSTRUMENTATION_STATUS: (class|test)=|INSTRUMENTATION_STATUS_CODE' run.out
```

Choreograph inside one device-side shell when timing matters — each WSL→`adb.exe` call costs ~0.5–1 s of interop, too coarse for second-level choreography:

```bash
"$adb" -s $serial shell 'am instrument -w -r <pkg>.test/androidx.test.runner.AndroidJUnitRunner \
  > /data/local/tmp/run.out 2>&1 & sleep <N>; am start -W -n <pkg>/<activity>; wait'
"$adb" -s $serial shell 'tail -5 /data/local/tmp/run.out'
```

Verify a runner arg exists before building timing on it — delay args are version-specific and silently ignored when the name does not match:

```bash
unzip -p <gradle-cache>/androidx.test/runner/<ver>/<hash>/runner-<ver>.aar classes.jar > /tmp/c.jar
unzip -p /tmp/c.jar androidx/test/runner/AndroidJUnitRunner.class | strings | grep -i delay
# then confirm it actually delayed: compare "Start proc" vs "TestRunner: run started" timestamps in logcat
```

## 9. Windows-side emulator target (validated 2026-09-06)

Where Gradle must run on this machine: NOT in WSL. The only SDK/NDK lives on Windows (`prebuilt/windows-x86_64` only), so a WSL Gradle run dies in `stripDebugSymbols` (`...prebuilt/linux-x86_64/bin/llvm-strip: A problem occurred starting process`), and building directly over `\\wsl.localhost` UNC fails with `java.io.IOException: 函数不正确` in the FileHasher (and `.bat` scripts cannot run with a UNC working directory anyway). Copy sources to an NTFS directory and build there — the Windows Gradle cache is warm on this machine:

```bash
DEST=/mnt/c/Users/admin/AppData/Local/Temp/shanka_front_build
rm -rf "$DEST" && mkdir -p "$DEST"
tar -C /home/jiangyou3/jiangyou3_5/shanka_app/Front \
  --exclude='./build' --exclude='./.gradle' --exclude='./app/build' \
  --exclude='./local.properties' -cf - . | tar -C "$DEST" -xf -
printf 'sdk.dir=C\\:\\\\Users\\\\admin\\\\AppData\\\\Local\\\\Android\\\\Sdk\n' > "$DEST/local.properties"
cmd.exe /c "pushd C:\Users\admin\AppData\Local\Temp\shanka_front_build && gradlew.bat assembleDebug --console=plain"
cp "$DEST/app/build/outputs/apk/debug/app-debug.apk" \
  /home/jiangyou3/jiangyou3_5/shanka_app/Front/app/build/outputs/apk/debug/app-debug.apk
# debug appId com.qiuzhao.flashcards.debug — adapt to the actual project
```

When no phone is needed, run the desktop emulator on Windows and treat it as just another adb target. This machine's SDK `emulator/opengl32sw.dll` is missing, so the default graphics stack wedges the guest: black framebuffer, no boot completion, adb stuck in `unauthorized` because the RSA dialog cannot render. Launch with the software GPU every time:

```bash
ADB="/mnt/c/Users/admin/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# 1. Launch detached in background; -gpu swiftshader_indirect and -memory 4096 are mandatory here.
#    HOME/ANDROID_AVD_HOME must point at the Windows profile: launched from WSL the
#    emulator otherwise inherits the WSL HOME and finds no AVDs ("Unknown AVD name").
HOME='C:\Users\admin' ANDROID_AVD_HOME='C:\Users\admin\.android\avd' \
/mnt/c/Users/admin/AppData/Local/Android/Sdk/emulator/emulator.exe \
  -avd Pixel_9_Pro -gpu swiftshader_indirect -memory 4096 \
  -no-snapshot -no-boot-anim &

# 2. Poll boot; expect state=device and boot_completed=1 within ~20 s (auto-authorizes:
#    the Windows adbkey is already trusted in this AVD's data)
"$ADB" devices
"$ADB" -s emulator-5554 shell getprop sys.boot_completed

# 3. Install the APK built per the Windows-side build block above
"$ADB" -s emulator-5554 install -r 'C:\Users\admin\AppData\Local\Temp\shanka_front_build\app\build\outputs\apk\debug\app-debug.apk'

# 4. Launch and capture
"$ADB" -s emulator-5554 shell am start -n com.qiuzhao.flashcards.debug/com.qiuzhao.flashcards.MainActivity
"$ADB" -s emulator-5554 exec-out screencap -p > /tmp/emulator_screen.png
```

Fault split when the guest looks dead:

- The Qt desktop window itself can kill the emulator on this host (`Critical: UpdateLayeredWindowIndirect failed … ` then a graceful self-shutdown, or silent qemu death minutes later) because `opengl32sw.dll` is missing and the UI falls back to system OpenGL. For unattended runs launch with `-no-window` added; `adb`, `screencap`, and `uiautomator` all work headless. Reboot of the headless AVD takes ~30 s and installed apps/userdata persist.
- Gboard on this AVD starts in Chinese mode and converts `adb shell input text` ASCII into fullwidth (`@`→`＠`, `.`→`。`), and `settings put secure selected_input_method_subtype <en_US hash>` does not stick. Bypass: `adb shell ime set com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService` — the auxiliary voice IME shows no keyboard and does not intercept key events, so `input text` commits raw ASCII. Switch back with `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.

- `unauthorized` + black guest frame + no boot progress for minutes → the host render-stack fault above, not a slow boot. Kill `qemu-system-x86_64` (and the `emulator` launcher) via `taskkill.exe /PID <pid> /F`, relaunch with `-gpu swiftshader_indirect`. A guest that boots this way does not need its data wiped.
- A physical phone is often attached to the same adb server; every command needs `-s`. `more than one device/emulator` is a targeting error, not an adb failure.
- Guest-frame capture while adb is not yet authorized: TCP console on `127.0.0.1:5554` (run from Windows PowerShell), `auth` with the token at `C:\Users\admin\.emulator_console_auth_token` (user root, **not** `.android\`), then `screenrecord screenshot C:\Users\admin\emu_frame.png`.
- If a clean boot still stops at `unauthorized`, the RSA dialog may be visible on the now-rendering emulator window — check it before deeper diagnosis; `Always allow` persists the key for future boots.
- Backend connectivity from the emulator uses the guest's `10.0.2.2` host alias (or `adb reverse tcp:<port> tcp:<port>`); verify it as a separate layer per the workflow above.
- Tap coordinates must come from `uiautomator dump` bounds (device pixels). Never eyeball them off a screencap in the conversation UI: the image is downscaled, so a point read off the picture lands off-target and silently taps the wrong element. Read rendered text bounds (not just the label) for buttons whose hit area is larger than their text.
