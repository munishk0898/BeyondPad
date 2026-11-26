# BeyondPad

External display manager and desktop launcher for the **OnePlus Pad 3** (OxygenOS 16 / Android 16).
Kotlin + Jetpack Compose, public Android APIs only (no hidden APIs, no root).

BeyondPad detects a monitor connected through a USB-C to HDMI adapter. It tests what the installed
OxygenOS really allows and reports the result. If the system lets apps use the monitor as a
separate display, BeyondPad opens a desktop shell there (Start menu, taskbar, pinned apps) and
opens real apps on the monitor while the tablet keeps working on its own.

---

## 1. What is BeyondPad and what is OxygenOS

| Feature | Provided by | Notes |
|---|---|---|
| Video output over USB-C (DisplayPort Alt Mode) | Hardware / OxygenOS | Needs an adapter that supports DP Alt Mode. Reports say output runs at 60 Hz. |
| Mirroring vs. a separate display | **OxygenOS** | If OxygenOS only mirrors at system level, the monitor is invisible to apps and no APK can change that. BeyondPad tells you when this happens. |
| Free-form, resizable windows on the monitor | **OxygenOS / Android** (desktop windowing) | BeyondPad asks for windows (`setLaunchBounds`), but only the OS can grant them. |
| Moving the mouse pointer between screens, keyboard focus | **OxygenOS / Android** input system | BeyondPad's desktop supports mouse (right-click menus) and keyboard (Meta or Ctrl+Space for Start, Enter, Esc). |
| Detecting connect / disconnect, reading monitor details | **BeyondPad** | `DisplayManager` + `DisplayListener`, foreground monitor service. |
| Telling "hub without video" from "monitor hidden by OxygenOS" | **BeyondPad** | HDMI/DP audio sink (`AudioManager`), USB devices on the port (`UsbManager`), and a scan for disabled or unlisted displays. |
| Capability test (is the monitor usable, are windows free-form?) | **BeyondPad** | Opens a test window on the monitor and measures where it landed and its size. |
| Desktop shell on the monitor: Start menu, search, taskbar, pinned apps, running indicators | **BeyondPad** | `DesktopActivity`, shown on the external display. |
| Opening apps on the monitor or tablet, new windows | **BeyondPad** (request) + **OS** (decision) | `ActivityOptions.setLaunchDisplayId()` through `LauncherApps`. |
| Automatic / manual resolution and refresh-rate selection | **BeyondPad** (request) + **OS** (decision) | Uses `preferredDisplayModeId` on BeyondPad's window on the monitor. A change is reported as applied only after `Display.getMode()` confirms it. |
| Presentation output (fallback) | **BeyondPad** | `Presentation` API, BeyondPad's own content only. It is labelled as **not** a desktop. |
| Toggling AOSP developer desktop settings | **Android** settings, switched by BeyondPad | Only after a one-time ADB grant (see section 4). |

### Verdicts the dashboard can show

| Verdict | Meaning |
|---|---|
| **System desktop windowing is active** | Apps opened on the monitor get resizable windows from the OS. This is the PC-like experience. |
| **Independent display, full-screen apps** | The monitor is a separate screen and apps run there independently of the tablet, but full screen, one at a time. Switch apps from the taskbar. |
| **Presentation output only** | The OS refuses to open apps on the monitor. Only BeyondPad's own presentation content can be shown. This is **not** an extended desktop. |
| **No external display visible to apps** | Nothing is connected, or OxygenOS mirrors at system level without exposing the monitor to apps. |

What a normal APK **cannot** do: switch OxygenOS from mirroring to extending if it does not expose
the display, move or resize other apps' existing windows, read which display another app is on,
change the system's global output mode, or override OEM restrictions. These need system privileges.

---

## 2. Install

1. Download `BeyondPad-1.2.0-release.apk` to the tablet.
2. Open it and allow "Install unknown apps" for your browser or file manager when asked.
3. Open BeyondPad and allow notifications.
4. Optional:
   - **Display over other apps**: lets BeyondPad open the desktop on the monitor by itself when you plug it in. Without it, tap the notification instead.
   - **Usage access**: shows which taskbar apps are active right now.

The debug APK (`BeyondPad-1.2.0-debug.apk`, package `com.beyondpad.app.debug`) can be installed next to the release build.

Install from a computer instead:

```
adb install -r BeyondPad-1.2.0-release.apk
```

## 3. Why the monitor stayed dark, and the one-time pairing

Diagnostics from the Pad 3 (OxygenOS 16.0.10.600) showed that video does reach the monitor
(SurfaceFlinger lists it), but Android 16 creates the monitor as a **disabled** display and waits
for a confirmation (`enable_waiting_confirmation_before_mirroring`) that OxygenOS never shows. Apps
can't see a disabled display, and only the shell user may enable it (`cmd display enable-display`).

BeyondPad 1.2.0 can do this itself through the tablet's own Wireless debugging, the same way
`adb` on a computer does. Pair once:

1. Connect to Wi-Fi. Turn on **Developer options > Wireless debugging**.
2. In BeyondPad, Overview > Monitor switch > **Start pairing**.
3. Tap **Pair device with pairing code**, keep the dialog open, pull down the BeyondPad
   notification and type the 6-digit code into it.

After pairing BeyondPad also grants itself `WRITE_SECURE_SETTINGS`, and it switches the monitor
on automatically whenever a monitor or hub is plugged in, as long as Wireless debugging stays on.
If Android forgets the pairing (for example after it switches Wireless debugging off), pair again.

## 4. First run on the Pad 3

1. Connect the USB-C to HDMI adapter and the monitor.
2. Look at **Overview**. The drawing and the headline show one of four states: second screen ready, monitor hidden by OxygenOS, hub with no video signal, or nothing connected. The **Next** list says what to do for that state.
3. Under **Advanced**, tap **Run capability test**.
4. Tap **Open desktop on monitor**. Use the Start button (or Meta / Ctrl+Space) and click an app.
5. Right-click or long-press any app for: open in a window, open full screen, open a new window, open on the tablet, pin.

## 5. ADB commands (optional)

Turn on Developer options (Settings > About tablet > tap Build number 7 times) and USB debugging.

Let BeyondPad switch the AOSP desktop developer settings itself (one time):

```
adb shell pm grant com.beyondpad.app android.permission.WRITE_SECURE_SETTINGS
```

Or set them yourself:

```
adb shell settings put global force_desktop_mode_on_external_displays 1
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb reboot
```

To undo, run the same commands with `0` and reboot. These are standard Android developer options.
Whether OxygenOS 16 honours them on the Pad 3 can only be confirmed on the device. Run the
capability test before and after.

Useful diagnostics:

```
adb shell dumpsys display | grep -iE "mDisplayId|DisplayDeviceInfo|HDMI|uniqueId"   # what the OS sees
adb shell cmd package list features | grep -iE "freeform|pc"                        # windowing features
adb shell am start --display <ID> -n com.android.settings/.Settings                 # open an app on display <ID>
adb shell wm density 160 -d <ID>                                                    # UI scale on the monitor (reset: wm density reset -d <ID>)
adb shell getprop ro.build.display.id                                               # exact OxygenOS build
```

## 6. OnePlus Pad 3 testing guide

Write down the OxygenOS build (Settings > About tablet) and the monitor model before testing.

| # | Test | Steps | Expected |
|---|---|---|---|
| 1 | HDMI connected before launch | Plug in the monitor, then open BeyondPad | Connection card shows the monitor at once; the event log says "Already connected at start". |
| 2 | HDMI connected after launch | Open BeyondPad, then plug in | Card updates within about a second; "Monitor connected" notification; the desktop opens by itself if auto-open and "Display over other apps" are allowed. |
| 3 | Capability test | Tap Run capability test | One of the four verdicts. Note it and the "Window W×H on display N" line. |
| 4 | Independent operation | Desktop open on the monitor; use apps on the tablet | The tablet keeps its own home screen and apps; the monitor stays on the BeyondPad desktop. |
| 5 | Open apps on either display | Start menu > app; right-click > Open on the tablet | App appears on the chosen screen. If it does not, note the toast and the event log. |
| 6 | Move between displays | Open an app on the monitor, then use Open on the tablet | Android decides whether to move the running task or bring it forward. Note the behaviour. |
| 7 | Multiple windows | Open three apps "in a window" | With system desktop windowing: three cascaded windows. Without it: the last app is full screen and the others are in the taskbar. |
| 8 | 1080p monitor | Connect a 1080p screen | Native 1920 × 1080 is marked; the active mode matches after Verify. |
| 9 | 4K monitor | Connect a 4K screen | 3840 × 2160 listed if the adapter and OxygenOS offer it. If only 1080p appears, the adapter or OS is limiting output. |
| 10 | Refresh rates | Turn off Automatic, pick a different mode | The status line says "Verified" only if the monitor really switched; otherwise "The system did not switch modes". |
| 11 | HDMI disconnect | Unplug while the desktop is open | Desktop closes (it does not take over the tablet); "Monitor disconnected" notification; the tablet works normally. |
| 12 | Reconnect | Plug back in | Detected again; the selected mode is applied when the desktop opens. |
| 13 | ADB settings | Grant WRITE_SECURE_SETTINGS, turn on Force desktop mode, reboot, run test 3 again | Compare the verdict before and after. |

If something misbehaves, take a screenshot of the Diagnostics card (all displays plus the event log).

## 7. Build from source

Requirements: JDK 17 and Android SDK platform 36. Android Studio Narwhal or newer opens the project directly.

```
./gradlew assembleDebug                 # app/build/outputs/apk/debug/
./gradlew assembleRelease               # unsigned unless signing env vars are set
```

Release signing reads `BEYONDPAD_KEYSTORE`, `BEYONDPAD_KEYSTORE_PASSWORD`, `BEYONDPAD_KEY_ALIAS` and
`BEYONDPAD_KEY_PASSWORD` from the environment. No key is stored in this repo.

GitHub Actions (`.github/workflows/build.yml`) builds both APKs on every push and publishes them,
with the build log, to the `ci-output` branch.

## 8. Project layout

```
app/src/main/java/com/beyondpad/app/
  BeyondPadApp.kt               app start: prefs, display repository, monitor service
  Prefs.kt                      settings (auto resolution, auto-open, pinned apps…)
  display/
    DisplayRepository.kt        DisplayManager + DisplayListener, event log
    ExternalDisplayInfo.kt      monitor details, modes, HDR, density, native-mode picker
    CapabilityProbe.kt          feature checks, launch test, verdict
    ModeController.kt           preferredDisplayModeId request + verification
  desktop/
    DesktopActivity.kt          desktop shell on the monitor (Start menu, taskbar, shortcuts)
    DesktopLauncher.kt          open/close the shell on a display
    AppLauncher.kt              setLaunchDisplayId / setLaunchBounds launches
    AppRepository.kt            LauncherApps app list, usage-based activity
    LaunchProbeActivity.kt      test window used by the capability test
    StatusPresentation.kt       Presentation fallback
  service/
    DisplayMonitorService.kt    foreground service: connect/disconnect, auto-open
    BootReceiver.kt
  system/SystemAccess.kt        developer settings, overlay/usage permissions
  adb/                          one-time Wireless debugging pairing, enables the disabled monitor
  ui/                           dashboard (Compose, dark theme with blue accents)
```
