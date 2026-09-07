# wattim Browser Extension Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Build a zero-dependency, cross-browser (Chromium + Firefox Manifest V3) extension providing mindful breathing pauses, exponential backoff, schedules, terminal aesthetics, and statistics before opening distracting websites.

**Architecture:** Manifest V3 WebExtension utilizing `chrome.webNavigation.onBeforeNavigate` to intercept distracting domains before page load, redirecting to an internal interstitial breathing page (`intervention.html`) with 60 FPS Canvas animations, issuing temporary session passes upon completion, and providing an options dashboard, toolbar popup, and re-intervention sentinel.

**Tech Stack:** Vanilla JavaScript (ES Modules), HTML5 Canvas, CSS3 Variables, WebExtension APIs (`webNavigation`, `tabs`, `storage`, `alarms`), Node.js (test runner).

---

### Task 1: Rule Engine & Domain Matching Logic with Unit Tests

**Files:**
- Create: `extension/background/rule-engine.js`
- Test: `tests/rule-engine.test.mjs`

**Step 1: Write the failing test**
Create `tests/rule-engine.test.mjs` testing:
1. `matchDomain(url, targetDomain)` (handling subdomains e.g. `m.youtube.com`, `https://www.instagram.com/p/123` matching `youtube.com` and `instagram.com`).
2. `calculateBackoffDelay(baseSeconds, growthPercent, recentAttemptsCount, maxDelay)`
3. `isScheduleActive(schedule, currentTime)` (checking days of week and time ranges, including overnight crossing midnight).

**Step 2: Run test to verify it fails**
Run: `node tests/rule-engine.test.mjs`
Expected: FAIL (module not found)

**Step 3: Write implementation**
Write pure functions in `extension/background/rule-engine.js` implementing domain extraction, wildcard/subdomain matching, exponential delay equation:
$$T = \min\left(T_{\text{max}}, \; T_{\text{base}} \times (1 + \text{growth}/100)^N\right)$$
and schedule time evaluation.

**Step 4: Run test to verify it passes**
Run: `node tests/rule-engine.test.mjs`
Expected: PASS (all tests pass)

**Step 5: Commit**
Run: `git add tests/rule-engine.test.mjs extension/background/rule-engine.js && git commit -m "feat(extension): implement rule engine with backoff and schedule logic"`

---

### Task 2: Manifest V3 & Storage Manager

**Files:**
- Create: `extension/manifest.json`
- Create: `extension/common/storage.js`
- Test: `tests/storage.test.mjs`

**Step 1: Write the failing test**
Create `tests/storage.test.mjs` verifying default data structure, migration/initialization, and validation helper functions.

**Step 2: Run test to verify it fails**
Run: `node tests/storage.test.mjs`
Expected: FAIL

**Step 3: Write implementation**
- Create `extension/manifest.json` with permissions (`webNavigation`, `storage`, `tabs`, `alarms`), cross-browser gecko id, background service worker, icons, popup action, and options page.
- Create `extension/common/storage.js` with defaults (`nord` theme, `PULSE` animation, 10s base delay, preset targets for youtube/instagram/reddit/x/vk/tiktok).

**Step 4: Run test to verify it passes**
Run: `node tests/storage.test.mjs`
Expected: PASS

**Step 5: Commit**
Run: `git add extension/manifest.json extension/common/storage.js tests/storage.test.mjs && git commit -m "feat(extension): add Manifest V3 and storage layer"`

---

### Task 3: Background Service Worker & Interception Flow

**Files:**
- Create: `extension/background/background.js`

**Step 1: Write background service worker implementation**
- Listen to `chrome.webNavigation.onBeforeNavigate` (top-level frame `frameId === 0`).
- Check global pause, session passes, targeted domains, schedules (hard block vs custom intervention).
- Calculate exponential backoff based on recent attempts log.
- Redirect intercepted tabs to `intervention/intervention.html?target=...&delay=...&mode=...`.
- Handle runtime messages:
  - `GRANT_PASS`: adds temporary pass token for `(domain, tabId)` and redirects tab to original destination.
  - `LOG_IMPULSE`: increments `savedImpulses` and `savedMinutes`.
  - `SET_GLOBAL_PAUSE`: sets pause timestamp.
  - `GET_STATUS`: returns current active/paused status and countdown.

**Step 2: Verify syntax and module loading**
Run: `node -c extension/background/background.js`
Expected: Clean exit code 0

**Step 3: Commit**
Run: `git add extension/background/background.js && git commit -m "feat(extension): implement background navigation interceptor"`

---

### Task 4: Themes & 60 FPS Canvas Animations

**Files:**
- Create: `extension/common/theme.css`
- Create: `extension/common/themes.js`
- Create: `extension/intervention/animations.js`

**Step 1: Implement CSS themes**
Define 6 color palettes matching wattim:
- Nord, Catppuccin Mocha, Dracula, Gruvbox Dark, Tokyo Night, Cyber Terminal.
- Export color objects for Canvas rendering in `themes.js`.

**Step 2: Implement 60 FPS Canvas Animations**
Create `animations.js` supporting:
- `FILL`: smooth oscillating wave filling screen according to breath phases.
- `PULSE`: expanding/contracting awareness sphere with glow and concentric ripples.
- `ZEN_ORBIT`: harmonic orbiting particle with particle trail.
- Breath cycle state machine: Inhale (Вдох) $\to$ Hold (Задержка) $\to$ Exhale (Выдох) $\to$ Rest (Покой).

**Step 3: Verify syntax**
Run: `node -c extension/common/themes.js extension/intervention/animations.js`
Expected: Clean exit code 0

**Step 4: Commit**
Run: `git add extension/common/theme.css extension/common/themes.js extension/intervention/animations.js && git commit -m "feat(extension): implement 6 themes and 60fps canvas animations"`

---

### Task 5: Interstitial Intervention Page (Screen of Mindful Pause)

**Files:**
- Create: `extension/intervention/intervention.html`
- Create: `extension/intervention/intervention.css`
- Create: `extension/intervention/intervention.js`

**Step 1: Write HTML and CSS**
- Canvas background covering 100% viewport.
- Centered terminal card with `$ wattim --breathe`, current phase indicator, countdown timer, mindful phrase.
- Immediate button **«ВЫЙТИ»** (accent color, prominent).
- Completion state button **«ПРОДОЛЖИТЬ НА САЙТ»** (revealed after cycle finishes).
- Hard block variant: shows schedule message, only exit button available.
- Emergency bypass button with 2-step confirmation modal («Войти разово», «Приостановить для сайта»).
- Bottom stats badge: «🌱 Сбережено: X минут · Y импульсов».

**Step 2: Write `intervention.js`**
- Connect to canvas animations, cycle management, sound/haptic ticks (optional audio chime), message exchange with background worker.
- Handle tab close on "ВЫЙТИ" and message `LOG_IMPULSE`.
- Handle navigation to target URL on "ПРОДОЛЖИТЬ".

**Step 3: Commit**
Run: `git add extension/intervention/ && git commit -m "feat(extension): implement interstitial intervention UI and logic"`

---

### Task 6: Re-intervention Sentinel (Dwell Time Monitor)

**Files:**
- Create: `extension/sentinel/sentinel.js`

**Step 1: Implement Sentinel Script**
- Runs on unlocked sites.
- Tracks active user interaction and dwell time.
- Sends heartbeat/check to background worker.
- If session pass expires while user is browsing, notifies background to re-trigger intervention.

**Step 2: Commit**
Run: `git add extension/sentinel/sentinel.js && git commit -m "feat(extension): implement re-intervention dwell time sentinel"`

---

### Task 7: Toolbar Popup Interface

**Files:**
- Create: `extension/popup/popup.html`
- Create: `extension/popup/popup.css`
- Create: `extension/popup/popup.js`

**Step 1: Implement Toolbar Popup**
- Status header: `STATUS: ACTIVE` (green) / `STATUS: PAUSED` (yellow with countdown).
- Quick Pause buttons: `+15 мин`, `+30 мин`, `+1 час`, `Возобновить`.
- Quick action: "Заблокировать этот сайт" (detects current tab domain and adds to targets in 1 click).
- Mini stats: сохраненные импульсы и минуты за сегодня.
- Link to open full settings dashboard.

**Step 2: Commit**
Run: `git add extension/popup/ && git commit -m "feat(extension): implement toolbar popup menu"`

---

### Task 8: Full Options Dashboard

**Files:**
- Create: `extension/options/options.html`
- Create: `extension/options/options.css`
- Create: `extension/options/options.js`

**Step 1: Implement Options Dashboard**
- Tab navigation:
  1. **Сайты (Targets)**: list of active sites, toggle switch, add new site input, quick presets chips.
  2. **Дыхание (Intervention)**: duration slider (1–120s), animation style picker (`FILL`, `PULSE`, `ZEN_ORBIT`) with live preview canvas, customizable quotes list.
  3. **Бэкофф (Backoff)**: toggle, growth % slider, reset window slider, interactive delay table for attempts 1–10.
  4. **Расписания (Schedules)**: add/edit schedule, select days of week, time start/end, mode (Hard Block / Custom Intervention).
  5. **Темы (Appearance)**: interactive theme selector with instant theme switching.
  6. **Статистика (Stats)**: total saved impulses, saved time, reset stats button.

**Step 2: Commit**
Run: `git add extension/options/ && git commit -m "feat(extension): implement full options dashboard"`

---

### Task 9: Application Icons & Manifest Validation

**Files:**
- Create: `extension/icons/icon-16.png`
- Create: `extension/icons/icon-32.png`
- Create: `extension/icons/icon-48.png`
- Create: `extension/icons/icon-128.png`
- Create: `scripts/generate-icons.js`

**Step 1: Create crisp vector icons**
- Render icons based on wattim's minimal leaf + terminal pixel aesthetics into required PNG sizes.

**Step 2: Verify manifest validity**
- Validate `manifest.json` schema and file references.

**Step 3: Commit**
Run: `git add extension/icons/ scripts/generate-icons.js && git commit -m "feat(extension): add extension icons and assets"`

---

### Task 10: Complete Verification & Test Suite

**Files:**
- Create: `tests/run-all.mjs`
- Test: Full unit test runner for all modules.

**Step 1: Run complete test suite**
Run: `node tests/run-all.mjs`
Expected: 100% tests passing.

**Step 2: Add README documentation for extension loading**
Document how to load unpacked extension in:
- Chrome / Brave / Edge (`chrome://extensions` $\to$ Developer mode $\to$ Load unpacked)
- Firefox (`about:debugging#/runtime/this-firefox` $\to$ Load Temporary Add-on)

**Step 3: Commit**
Run: `git add tests/ docs/ extension/README.md && git commit -m "docs(extension): add loading instructions and test runner"`
