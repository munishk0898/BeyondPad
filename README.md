# BeyondPad

External display manager and desktop launcher for the **OnePlus Pad 3** (OxygenOS 16 / Android 16).
Kotlin + Jetpack Compose, public Android APIs only. No developer options, no ADB, no root,
and no internet permission.

BeyondPad detects a monitor connected through a USB-C to HDMI adapter or hub. It tells you what the
tablet really sees, and when Android makes the monitor available to apps it opens a desktop there
(Start menu, taskbar, pinned apps). Apps run on the monitor while the tablet keeps working on its own.

## 1. What BeyondPad does and what OxygenOS controls

| Feature | Provided by | Notes |
|---|---|---|
| Video output over USB-C (DisplayPort Alt Mode) | Hardware / OxygenOS | The adapter or hub must support DP Alt Mode. |
| Switching a newly connected monitor on | **OxygenOS / Android** | See section 3. |
| Free-form, resizable windows on the monitor | **OxygenOS / Android** | BeyondPad asks for windows; only the OS can grant them. |
| Mouse pointer between screens, keyboard focus | **OxygenOS / Android** | BeyondPad's desktop supports mouse (right-click menus) and keyboard (Meta or Ctrl+Space for Start, Enter, Esc). |
| Detecting connect and disconnect, monitor details | **BeyondPad** | `DisplayManager` + `DisplayListener`, background monitor service. |
| Telling "hub without video" from "monitor switched off by Android" | **BeyondPad** | Monitor audio sink (`AudioManager`), USB devices on the port (`UsbManager`), scan for disabled or unlisted displays. |
| Capability test (do apps get windows or full screen?) | **BeyondPad** | Opens a test window on the monitor and measures it. |
| Desktop on the monitor: Start menu, search, taskbar, pinned apps, running indicators | **BeyondPad** | `DesktopActivity`. |
| Opening apps on the monitor or the tablet, new windows | **BeyondPad** (request) + **OS** (decision) | `ActivityOptions.setLaunchDisplayId()` through `LauncherApps`. |
| Automatic or manual resolution and refresh rate | **BeyondPad** (request) + **OS** (decision) | `preferredDisplayModeId` on BeyondPad's window; reported as applied only after the monitor confirms it. |
| Presentation output (fallback) | **BeyondPad** | `Presentation` API, BeyondPad's own content only, labelled as not a desktop. |

## 2. Install

1. Copy `BeyondPad-1.6.0-release.apk` to the tablet and open it. Allow "Install unknown apps" when asked.
2. Open BeyondPad and allow notifications.
3. Optional, on the Desktop tab:
   - **Display over other apps** lets the desktop open by itself when the monitor becomes available.
   - **Usage access** marks taskbar apps that are active.

## 3. Known limitation on OxygenOS 16 (Pad 3)

Diagnostics from a Pad 3 on OxygenOS 16.0.10.600 showed that video does reach the monitor
(a Dell SE2722H at 1920 × 1080), but Android 16 creates the monitor as a **disabled** display and
waits for a confirmation that this OxygenOS version does not show. A disabled display is invisible
to apps, and switching it on needs system privileges that no normal app has.

So with this firmware, BeyondPad shows **"Monitor connected but switched off by Android"**. As soon
as OxygenOS switches the monitor on (a system prompt, a setting, or a future update), BeyondPad
detects it and opens the desktop on it automatically.

## 4. Offline, storage and memory

- No internet permission: the app cannot reach any network. No analytics, no update checks.
- arm64 native code only (the Pad 3's CPU) and English resources only.
- The background monitor only listens for system display, audio and USB events, and does no polling.

## 5. Testing guide (OnePlus Pad 3)

Note the OxygenOS build (Settings > About tablet) and the monitor model first.

| # | Test | Steps | Expected |
|---|---|---|---|
| 1 | Connected before launch | Plug in, then open BeyondPad | Overview shows the state at once. |
| 2 | Connected after launch | Open BeyondPad, then plug in | Overview updates within about a second; a notification appears. |
| 3 | Switched-off monitor | Plug in on OxygenOS 16.0.10.600 | "Monitor connected but switched off by Android". |
| 4 | Monitor available | When Android switches it on | Drawing turns blue; the desktop opens on the monitor if allowed. |
| 5 | Capability test | Advanced > Run capability test | Full screen or free windows. |
| 6 | Independent use | Use apps on the tablet while the desktop is open | Each screen keeps its own apps. |
| 7 | Open apps on either screen | Start menu; right-click > Open on the tablet | App appears on the chosen screen. |
| 8 | Several windows | Open three apps "in a window" | Windows if the OS supports them, otherwise one full-screen app with the rest in the taskbar. |
| 9 | Resolution | Display tab, turn off Automatic, pick a mode | "Verified" only if the monitor really switched. |
| 10 | Disconnect | Unplug while the desktop is open | Desktop closes; the tablet works normally. |

## 6. Build from source

JDK 17 and Android SDK platform 36.

```
./gradlew assembleRelease     # unsigned unless the signing environment variables are set
```

Release signing reads `BEYONDPAD_KEYSTORE`, `BEYONDPAD_KEYSTORE_PASSWORD`, `BEYONDPAD_KEY_ALIAS` and
`BEYONDPAD_KEY_PASSWORD`. No key is stored in this repo. GitHub Actions builds on every push and
publishes the APKs and the build log to the `ci-output` branch.
