# In-Page Shadow DOM Overlay Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Replace the full URL redirect mechanism with an in-page Shadow DOM overlay so that when the user completes their mindful breathing pause and clicks «ПРОДОЛЖИТЬ», the overlay fades out smoothly and the site is immediately accessible without any page reload or state loss.

**Architecture:** 
1. `sentinel/overlay.js` is injected at `run_at: "document_start"` via Manifest `content_scripts`.
2. On initial page load, `overlay.js` queries `background.js` via `CHECK_NAVIGATION`.
3. If intervention is required, an isolated `ShadowRoot` modal is mounted directly into the DOM before page contents render, freezing video/audio playback and keyboard/click events.
4. If «ВЫЙТИ» is clicked: signals background to log impulse and close tab immediately.
5. If «ПРОДОЛЖИТЬ» is clicked: grants session pass, fades out and unmounts the overlay with zero page reload.

**Tech Stack:** Vanilla JavaScript (ES2022), Web Components / Shadow DOM (closed mode), HTML5 Canvas 60 FPS, WebExtension Messaging.

---

### Task 1: Background Service Worker Interception Update

**Files:**
- Modify: `extension/background/background.js`
- Test: `tests/storage.test.mjs`

**Step 1: Write tests / verification criteria**
Ensure background worker handles:
- `CHECK_NAVIGATION` message: returns `{ shouldIntervene, delay, attempts, mode, targetDomain, settings }` without forcing a `chrome.tabs.update(tabId, { url })` redirect.
- `CLOSE_TAB` message: calls `chrome.tabs.remove(sender.tab.id)` and logs impulse.
- Retain session pass and global pause checks.

**Step 2: Update `background.js`**
- Adjust `webNavigation.onBeforeNavigate`: do not navigate away to `intervention.html` if content script overlay is handling it. Keep `intervention.html` for manual direct opening/previews.
- Handle `CHECK_NAVIGATION` and `CLOSE_TAB` runtime messages.

**Step 3: Test and syntax check**
Run: `node -c extension/background/background.js`
Expected: Clean exit 0.

**Step 4: Commit**
Run: `git add extension/background/background.js && git commit -m "feat(extension): add CHECK_NAVIGATION and in-page overlay support in background worker"`

---

### Task 2: Shadow DOM In-Page Overlay Component

**Files:**
- Create: `extension/overlay/overlay.js`
- Create: `extension/overlay/overlay.css`
- Modify: `extension/manifest.json`

**Step 1: Create `extension/overlay/overlay.css`**
- Encapsulated Shadow DOM styles (host container `position: fixed; inset: 0; z-index: 2147483647`).
- Includes theme variables and smooth fade-in / fade-out animations.

**Step 2: Create `extension/overlay/overlay.js`**
- Injected at `document_start`.
- Sends `CHECK_NAVIGATION` on execution.
- If intervention needed:
  - Mounts `<div id="wattim-overlay-host">` with closed Shadow DOM.
  - Injects Canvas and terminal UI cards.
  - Runs 60 FPS Canvas breathing animation (`PULSE`, `FILL`, `ZEN_ORBIT`).
  - Connects «ВЫЙТИ» -> sends `CLOSE_TAB`.
  - Connects «ПРОДОЛЖИТЬ» -> sends `GRANT_PASS`, plays smooth fadeOut animation, removes host element from DOM without page reload.
  - Pauses background media (`video.pause()`, `audio.pause()`).
  - Supports Re-intervention on the fly.

**Step 3: Update `extension/manifest.json`**
- Register `overlay/overlay.js` in `content_scripts` at `run_at: "document_start"`.
- Expose `overlay/*` in `web_accessible_resources`.

**Step 4: Commit**
Run: `git add extension/overlay/ extension/manifest.json && git commit -m "feat(extension): implement in-page Shadow DOM overlay without page reload"`

---

### Task 3: Full Test Suite Verification

**Files:**
- Modify: `tests/run-all.mjs`
- Modify: `extension/README.md`

**Step 1: Update test runner**
- Add `extension/overlay/overlay.js` to syntax checks.
- Verify manifest references for overlay files.

**Step 2: Run all tests**
Run: `node tests/run-all.mjs`
Expected: 100% tests passing.

**Step 3: Commit**
Run: `git add tests/run-all.mjs extension/README.md && git commit -m "test(extension): verify in-page overlay and update docs"`
