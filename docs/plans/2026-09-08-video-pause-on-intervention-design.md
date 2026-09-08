# Design Document: Video Pause on Intervention (Staggered Media Pause + Audio Focus Watchdog)

## Context & Problem
When opening targeted media-heavy applications on Android (such as YouTube, TikTok, Instagram Reels, VK, Telegram, or Chrome), Wattim's accessibility service intercepts the launch and displays the intervention overlay within 10–30 ms.

At this initial moment ($t=0$), the underlying application's activity is still initializing and has not yet started video or audio playback. A single immediate `dispatchMediaPause()` call is therefore dropped because the target player hasn't registered a media button receiver or requested audio focus yet. When the player finally begins playback 200–800 ms later, it plays audio and video behind Wattim's intervention screen.

## Proposed Architecture

### 1. Exclusive Audio Focus
- In `SystemAudioGuard`, request `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` instead of `AUDIOFOCUS_GAIN_TRANSIENT`.
- Sets audio attributes to:
  - `usage = AudioAttributes.USAGE_MEDIA`
  - `contentType = AudioAttributes.CONTENT_TYPE_MUSIC`
  - `acceptsDelayedFocusGain = false`
- `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` signals to the Android media router and media players (such as ExoPlayer) that background audio ducking is forbidden and playback must pause completely.

### 2. Staggered Media Pause Pulses
- At $t = 0$ ms: request exclusive audio focus and send `KEYCODE_MEDIA_PAUSE`.
- At $t = 150$ ms: send `KEYCODE_MEDIA_PAUSE`.
- At $t = 400$ ms: send `KEYCODE_MEDIA_PAUSE` and verify audio focus.
- At $t = 800$ ms: send `KEYCODE_MEDIA_PAUSE`.
- At $t = 1200$ ms: send final check `KEYCODE_MEDIA_PAUSE`.
- All key events are sent as a symmetric pair (`ACTION_DOWN` + `ACTION_UP`) via `AudioManager.dispatchMediaKeyEvent`.

### 3. Audio Focus Watchdog & Reclaim
- In `OnAudioFocusChangeListener`:
  - If a focus change event (`AUDIOFOCUS_LOSS_TRANSIENT` or `AUDIOFOCUS_LOSS`) occurs while the session is still active (`activeSessionId == sessionId`):
  - Immediately re-request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` and dispatch `KEYCODE_MEDIA_PAUSE` to reclaim audio focus from any late-starting players.

### 4. Lifecycle & Symmetric Cleanup
- `release(sessionId)` immediately cancels the staggered pause coroutine job, abandons audio focus via `AudioManager.abandonAudioFocusRequest()`, and resets `activeSessionId = null`.
- Cleaned up symmetrically on user exit, continue, screen off, or service destruction.
