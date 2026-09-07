/**
 * wattim Browser Extension - Background Service Worker
 * Intercepts navigation to distracting sites, computes exponential delay,
 * handles mindful pauses, session grants, and alarms.
 */

import {
  extractHostname,
  matchDomain,
  calculateBackoffDelay,
  isScheduleActive
} from './rule-engine.js';

import {
  DEFAULT_STORAGE,
  initStorageWithDefaults,
  calculateUpdatedStats,
  getStorage,
  setStorage
} from '../common/storage.js';

// In-memory state (backed by service worker lifecycle)
// Maps domain -> expiration timestamp (ms)
const sessionPasses = new Map();

// Attempt history: Array of { domain: string, timestamp: number }
let attemptHistory = [];

/**
 * Clean up old attempts outside the maximum backoff window (default 60 mins).
 */
function pruneAttemptHistory(windowMinutes = 60) {
  const cutoff = Date.now() - windowMinutes * 60 * 1000;
  attemptHistory = attemptHistory.filter(a => a.timestamp >= cutoff);
}

/**
 * Counts attempts for a specific domain within the sliding window.
 */
function getRecentAttemptCount(domain, windowMinutes = 60) {
  pruneAttemptHistory(windowMinutes);
  return attemptHistory.filter(a => a.domain === domain).length;
}

/**
 * Initializes default settings upon installation or update.
 */
chrome.runtime.onInstalled.addListener(async () => {
  const current = await getStorage();
  await setStorage(current);
  console.log('[wattim] Extension initialized with settings:', current.settings);
});

/**
 * Intercept navigation before web request starts.
 */
chrome.webNavigation.onBeforeNavigate.addListener(async (details) => {
  // Only intercept top-level main frame navigations
  if (details.frameId !== 0) return;

  const url = details.url;
  if (!url || typeof url !== 'string') return;

  // Ignore internal/browser pages
  if (
    url.startsWith('chrome://') ||
    url.startsWith('chrome-extension://') ||
    url.startsWith('moz-extension://') ||
    url.startsWith('about:') ||
    url.startsWith('edge://') ||
    url.startsWith('view-source:')
  ) {
    return;
  }

  const hostname = extractHostname(url);
  if (!hostname) return;

  const storage = await getStorage();

  // 1. Check Global Pause
  const now = Date.now();
  if (storage.globalPause && storage.globalPause.until > now) {
    return; // Protection is paused globally
  }

  // 2. Check if host matches any enabled target
  const matchedTarget = storage.targets.find(t => t.enabled && matchDomain(url, t.domain));
  if (!matchedTarget) {
    return; // Not a distracting/target site
  }

  const targetDomain = matchedTarget.domain;

  // 3. Check if active session pass exists for this domain
  const passExpiry = sessionPasses.get(targetDomain);
  if (passExpiry && passExpiry > now) {
    return; // Allowed by temporary session pass
  }

  // 4. Check active schedules
  let isHardBlocked = false;
  let customDelay = null;

  if (Array.isArray(storage.schedules)) {
    for (const schedule of storage.schedules) {
      if (isScheduleActive(schedule, new Date())) {
        // Check if schedule applies to all or this specific domain
        if (schedule.targetDomain === 'all' || schedule.targetDomain === targetDomain) {
          if (schedule.mode === 'hard_block') {
            isHardBlocked = true;
            break;
          } else if (schedule.mode === 'custom_intervention') {
            customDelay = Number(schedule.customDelaySeconds) || 20;
          }
        }
      }
    }
  }

  // If hard blocked, user cannot enter at all
  if (isHardBlocked) {
    const interventionUrl = chrome.runtime.getURL(
      `intervention/intervention.html?mode=hard_block&target=${encodeURIComponent(url)}&domain=${encodeURIComponent(targetDomain)}`
    );
    chrome.tabs.update(details.tabId, { url: interventionUrl });
    return;
  }

  // 5. Calculate Delay with Exponential Backoff
  const windowMinutes = (storage.backoff && storage.backoff.windowMinutes) || 60;
  const recentAttempts = getRecentAttemptCount(targetDomain, windowMinutes);

  // Record this attempt
  attemptHistory.push({ domain: targetDomain, timestamp: now });

  let delay = customDelay;
  if (delay === null) {
    const baseDelay = (storage.settings && storage.settings.baseDelaySeconds) || 10;
    const maxDelay = (storage.settings && storage.settings.maxDelaySeconds) || 120;
    const growthPercent = (storage.backoff && storage.backoff.enabled)
      ? (storage.backoff.growthPercent || 20)
      : 0;

    delay = calculateBackoffDelay(baseDelay, growthPercent, recentAttempts, maxDelay);
  }

  // 6. Redirect to Mindful Intervention Screen
  const interventionUrl = chrome.runtime.getURL(
    `intervention/intervention.html?target=${encodeURIComponent(url)}&domain=${encodeURIComponent(targetDomain)}&delay=${delay}&attempts=${recentAttempts}`
  );

  chrome.tabs.update(details.tabId, { url: interventionUrl });
});

/**
 * Handle messages from intervention page, popup, or options.
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || typeof message !== 'object') return;

  const now = Date.now();

  switch (message.type) {
    case 'GRANT_PASS': {
      const { domain, durationMinutes = 15 } = message;
      if (domain) {
        const expiry = now + (Number(durationMinutes) || 15) * 60 * 1000;
        sessionPasses.set(domain, expiry);
      }
      sendResponse({ success: true, passes: Object.fromEntries(sessionPasses) });
      break;
    }

    case 'LOG_IMPULSE': {
      (async () => {
        const { domain, minutesSaved } = message;
        const storage = await getStorage();
        const updatedStats = calculateUpdatedStats(
          storage.stats,
          minutesSaved || storage.settings.avgSessionMinutes || 10,
          domain
        );
        await setStorage({ stats: updatedStats });
        sendResponse({ success: true, stats: updatedStats });
      })();
      return true; // Keep channel open for async response
    }

    case 'SET_GLOBAL_PAUSE': {
      (async () => {
        const { durationMinutes = 15 } = message;
        const until = durationMinutes > 0 ? now + durationMinutes * 60 * 1000 : 0;
        await setStorage({ globalPause: { until } });
        sendResponse({ success: true, until });
      })();
      return true;
    }

    case 'GET_STATUS': {
      (async () => {
        const storage = await getStorage();
        const until = (storage.globalPause && storage.globalPause.until) || 0;
        const isPaused = until > now;
        const remainingMs = isPaused ? until - now : 0;

        sendResponse({
          isPaused,
          remainingMs,
          until,
          activeTargetsCount: storage.targets.filter(t => t.enabled).length,
          savedImpulses: (storage.stats && storage.stats.savedImpulses) || 0,
          savedMinutes: (storage.stats && storage.stats.savedMinutes) || 0
        });
      })();
      return true;
    }

    case 'CHECK_SESSION': {
      const { domain } = message;
      const expiry = sessionPasses.get(domain) || 0;
      sendResponse({ valid: expiry > now, remainingMs: Math.max(0, expiry - now) });
      break;
    }

    case 'RE_INTERVENE': {
      // Re-trigger intervention when dwell time sentinel expires
      const { targetUrl, domain } = message;
      if (domain) {
        sessionPasses.delete(domain); // Invalidate pass
      }
      if (sender.tab && sender.tab.id) {
        const interventionUrl = chrome.runtime.getURL(
          `intervention/intervention.html?target=${encodeURIComponent(targetUrl)}&domain=${encodeURIComponent(domain)}&reintervention=true`
        );
        chrome.tabs.update(sender.tab.id, { url: interventionUrl });
      }
      sendResponse({ success: true });
      break;
    }

    default:
      sendResponse({ error: 'Unknown message type' });
  }
});
