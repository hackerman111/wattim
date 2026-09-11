# wattim

<div align="center">

<img src="wattim.png" alt="wattim logo" width="120" />

**Mindful breathing pauses before opening distracting apps and websites.**  
*Native Android (Jetpack Compose) + Browser Extension (Manifest V3)*

**English** · [Русский](README_RU.md)

[![Android](https://img.shields.io/badge/Android-10.0%2B%20(API%2029%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Extension](https://img.shields.io/badge/Extension-Manifest%20V3-brightgreen?style=flat-square&logo=googlechrome&logoColor=white)](https://developer.chrome.com/docs/extensions/mv3/)
[![Offline](https://img.shields.io/badge/Network-100%25%20Offline-blue?style=flat-square)](#privacy-and-security)
[![License](https://img.shields.io/badge/License-MIT-gray?style=flat-square)](LICENSE)

[Download APK (2.4 MB)](./wattim.apk) · [How it works](#how-it-works) · [Features](#features) · [Android](#android-client) · [Extension](#browser-extension) · [Build](#build-and-test)

</div>

---

## Overview

**wattim** is a lightweight tool that introduces intentional friction when launching habit-forming apps and websites (an open-source alternative to *one sec*).

Instead of permanently blocking apps, wattim interrupts the automatic impulse: whenever you launch a targeted app or visit a domain, a full-screen overlay presents a brief breathing cycle ("Inhale → Hold → Exhale → Rest"). This short delay gives your prefrontal cortex time to kick in so you can decide whether you actually want to use the app right now.

### Monorepo Structure

The project is structured as a monorepo with two standalone clients:
- **`app/`** — Native Android client (Kotlin, Jetpack Compose, Room, Accessibility Service, `WindowManager` overlay).
- **`extension/`** — Browser extension for Chromium-based browsers and Firefox (Manifest V3, closed Shadow DOM overlay without page reload, zero dependencies).

---

## How It Works

```
[User opens targeted app / site]
               │
               ▼
     Detection & Interception
               │
               ▼
   ┌───────────────────────┐
   │  Intervention Screen  │
   │  (60 FPS breath cycle)│
   └───────────┬───────────┘
               │
        ┌──────┴──────┐
        ▼             ▼
    [ EXIT ]     [ CONTINUE ]
  (Immediate    (Access granted
   return to     only after full
  home screen)    breath cycle)
```

1. **Interception**: A background monitor detects foreground window changes on Android or tab navigation in the browser.
2. **Intervention**: A breathing animation renders over the screen. Underlying page interactions are blocked, and media audio/video is muted.
3. **Choice**:
   - **"Exit" button**: Available at any second — immediately returns to the home screen or closes the tab, recording a saved impulse to local stats.
   - **"Continue" button**: Becomes clickable only after the breathing timer finishes.
4. **Session Watchdog (Re-intervention)**: If you stay inside the app longer than your configured threshold (e.g. 15 minutes), the intervention triggers again.

---

## Features

### Exponential Backoff
If you frequently re-open a protected app within a short window (1 hour by default), the pause duration progressively increases:

$$T = T_{\text{base}} \times \left(1 + \frac{r}{100}\right)^N$$

Where:
- $T_{\text{base}}$ — Base duration (default: 10s);
- $r$ — Growth percentage per attempt (default: 20%);
- $N$ — Number of re-open attempts within the current time window.

| Attempt ($N$) | Pause Duration (Base 10s, +20%) |
| :---: | :--- |
| **1st** | 10.0s |
| **2nd** | 12.0s |
| **3rd** | 14.4s |
| **4th** | 17.3s |
| **5th** | 20.7s |
| **10th** | 51.6s |

An interactive calculation table is embedded directly in the settings for the first 10 steps.

### Schedules & Block Modes
- **Hard Block**: Completely prevents opening targeted apps during configured hours (e.g. work hours or bedtime) without an option to bypass through breathing.
- **Custom Intervention**: Configure custom pause durations and re-intervention frequencies per specific app or domain.
- **Days & Minute Precision**: Select specific days of the week (Mon–Sun) with minute-level precision (`09:30` → `18:15`).

### Emergency Access & Global Pause
- **One-time Pass**: Quick confirmation to skip the pause in urgent situations.
- **Temporary Suspension**: Pause protection for an individual app or globally across the entire device for **15 min**, **30 min**, **1 hour**, or **until manually resumed**.
- **Optional Two-stage Unlock**: Generate a per-session code, view it manually in the **Codes** tab, and enter it on the intervention screen before breathing starts. Code length is configurable per app.
- **Optional Emergency Code**: Timed emergency access can require the separate 10-digit code displayed on the intervention screen.
- **Status Widget**: Home screen card with a live countdown timer and instant resumption button.

### 6 Terminal Themes
Both Android and the browser extension share identical aesthetic themes:
- `Nord` — Arctic cool palette.
- `Catppuccin Mocha` — High-contrast pastel dark palette.
- `Dracula` — Classic purple and cyan contrast.
- `Gruvbox Dark` — Retro warm terminal tones.
- `Tokyo Night` — Neon cyberpunk accents.
- `Cyber Terminal` — Monochrome matrix green.

### 5 Canvas Animation Styles (60 FPS)
- **Pulse**: Breathing pulsating sphere with inhale/exhale rhythm.
- **Fill**: Smooth vertical ambient wave fill.
- **Zen Orbit**: Particle orbiting with dynamic expansion and contraction.
- **Wave**: A continuous ambient wave that does not reveal intervention progress or remaining time.
- **Fill 2**: Smooth untimed vertical fill wandering between random heights without revealing remaining time.

---

## Privacy and Security

- **Zero Network Permissions**: `android.permission.INTERNET` is completely omitted from `AndroidManifest.xml`. The Android app cannot physically communicate with the network.
- **Zero Telemetry or Trackers**: No Firebase, Google Play Services, Crashlytics, AppMetrica, or ad SDKs.
- **Local Storage Only**:
  - Android: Local SQLite database via Room (`app/data/local/`).
  - Browser: `chrome.storage.local`, never synced to remote servers.
- **Lightweight Binary**: Release APK is R8-optimized and measures just **2.4 MB**.

---

## Android Client

### Requirements
- Android 10.0+ (API 29+)
- ABIs: ARM64, ARMv7, x86_64

### Required Permissions
wattim requires the following permissions for reliable, privacy-preserving operation:
1. **Accessibility Service (`AppMonitorService`)** — Uses adaptive event filtering (`TYPE_WINDOW_STATE_CHANGED`) to identify foreground app launches. It does not inspect screen text, passwords, or capture keystrokes.
2. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)** — Displays the full-screen Compose intervention view over target apps (`TYPE_APPLICATION_OVERLAY`).
3. **Foreground Service (`FOREGROUND_SERVICE_SPECIAL_USE`)** — Keeps background protection active and manages transient audio focus without volume mutation (Android 14+).
4. **Notifications (`POST_NOTIFICATIONS`)** — Displays a low-priority ongoing notification showing current active protection status (Android 13+).
5. **Package Visibility (`QUERY_ALL_PACKAGES`)** — Allows selecting any installed user application to be protected.

### Installing the APK
1. Download [wattim.apk](./wattim.apk) onto your device.
2. Allow installation from unknown sources if prompted.
3. Grant the required permissions during the initial setup wizard.

> **Note (Google Play Protect & Android 13+ Restricted Settings):**  
> Google Play Protect or Android may flag the app as potentially unwanted because it requests overlay and accessibility permissions (mechanics commonly monitored by security scanners). wattim is completely offline and requests zero network access.  
> If the Accessibility toggle is greyed out (*"Restricted setting. For your security, this setting is currently unavailable"*):  
> 1. Go to **Android Settings** → **Apps** → **wattim**.  
> 2. Tap the three dots **(⋮)** in the top-right corner.  
> 3. Select **"Allow restricted settings"** and confirm with your PIN/biometrics.  
> 4. Return to wattim to turn on the service.

---

## Browser Extension

Compatible with Chromium-based browsers (Google Chrome, Brave, Microsoft Edge, Vivaldi) and Mozilla Firefox.

### Overlay Architecture
- **Closed Shadow DOM (`mode: "closed"`)**: The intervention overlay is injected into the active tab within an isolated shadow root, preventing styling conflicts with web pages.
- **No Page Reload**: Clicking "Continue" removes the overlay element directly, restoring tab interaction and media playback without discarding state or reloading.
- **Media Muting**: Video and audio elements are automatically paused during the breathing phase.

### Installation in Chromium (Chrome, Brave, Edge)
1. Navigate to `chrome://extensions` (or `brave://extensions` / `edge://extensions`).
2. Toggle on **"Developer mode"** in the top-right corner.
3. Click **"Load unpacked"**.
4. Select the `extension/` directory from this repository.

### Installation in Firefox
1. Navigate to `about:debugging#/runtime/this-firefox`.
2. Click **"Load Temporary Add-on..."**.
3. Select the `extension/manifest.json` file.

---

## Repository Structure

```
.
├── app/                              # Native Android app (Kotlin + Jetpack Compose)
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # Manifest (strictly offline, no INTERNET)
│   │   └── java/io/ronesec/android/
│   │       ├── data/                 # Room DAO, Entities, Repository
│   │       ├── domain/               # RuleEngine, animations, intervention logic
│   │       ├── overlay/              # WindowManager overlay + Edge-to-Edge insets
│   │       ├── service/              # AppMonitorService (Accessibility Service)
│   │       └── ui/                   # Jetpack Compose M3 screens, themes, components
│   └── src/test/                     # Unit & acceptance tests
│
├── extension/                        # Browser extension (Manifest V3, Vanilla JS)
│   ├── manifest.json                 # Extension declaration (Chromium + Firefox)
│   ├── background/                   # Service worker & RuleEngine
│   ├── overlay/                      # Content script & Shadow DOM injector
│   ├── intervention/                 # Breathing screen & 60 FPS Canvas animations
│   ├── sentinel/                     # In-page idle and duration watchdog
│   ├── popup/                        # Quick action popup (status, pause, add domain)
│   └── options/                      # Settings page, domain lists, schedules
│
├── tests/                            # Extension automated test suite (Node.js)
│   ├── test-rule-engine.mjs          # Rule engine & backoff calculations
│   ├── test-storage.mjs              # Schema migrations & storage integrity
│   └── run-all.mjs                   # Unified test runner
│
├── wattim.apk                        # Pre-built release APK (2.4 MB)
└── build.gradle.kts                  # Root Gradle build script
```

---

## Build and Test

### Android

Prerequisites: JDK 17, Android SDK (API 34).

```bash
# Run unit tests
./gradlew test

# Build optimized release APK
./gradlew assembleRelease

# The compiled APK is generated at:
# app/build/outputs/apk/release/app-release.apk
```

### Browser Extension

Prerequisites: Node.js 18+.

```bash
# Run complete test suite (syntax, RuleEngine, storage, manifest integrity)
npm test
```

---

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
