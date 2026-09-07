/**
 * wattim Browser Extension - Background Service Worker
 * Evaluates navigation rules, computes exponential delay,
 * handles mindful pauses, session grants, and tab closures.
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
  getReinterventionSeconds,
  getStorage,
  setStorage
} from '../common/storage.js';

// In-memory state
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
 * Core rule evaluation: determines if a URL requires intervention.
 * 
 * @param {string} url - Target URL to evaluate
 * @returns {Promise<Object>} Evaluation result
 */
export async function evaluateNavigation(url) {
  if (!url || typeof url !== 'string') {
    return { shouldIntervene: false };
  }

  if (
    url.startsWith('chrome://') ||
    url.startsWith('chrome-extension://') ||
    url.startsWith('moz-extension://') ||
    url.startsWith('about:') ||
    url.startsWith('edge://') ||
    url.startsWith('view-source:')
  ) {
    return { shouldIntervene: false };
  }

  const hostname = extractHostname(url);
  if (!hostname) return { shouldIntervene: false };

  const storage = await getStorage();
  const now = Date.now();

  // 1. Global Pause check
  if (storage.globalPause && storage.globalPause.until > now) {
    return { shouldIntervene: false, isGloballyPaused: true };
  }

  // 2. Target domain check
  const matchedTarget = storage.targets.find(t => t.enabled && matchDomain(url, t.domain));
  if (!matchedTarget) {
    return { shouldIntervene: false };
  }

  const targetDomain = matchedTarget.domain;

  // 3. Active Session Pass check
  const passExpiry = sessionPasses.get(targetDomain);
  if (passExpiry && passExpiry > now) {
    return { shouldIntervene: false, hasActivePass: true, remainingMs: passExpiry - now };
  }

  // 4. Schedules check
  let isHardBlocked = false;
  let customDelay = null;

  if (Array.isArray(storage.schedules)) {
    for (const schedule of storage.schedules) {
      if (isScheduleActive(schedule, new Date())) {
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

  if (isHardBlocked) {
    return {
      shouldIntervene: true,
      mode: 'hard_block',
      targetDomain,
      url,
      settings: storage.settings,
      stats: storage.stats
    };
  }

  // 5. Exponential Backoff Calculation
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

  return {
    shouldIntervene: true,
    mode: 'normal',
    targetDomain,
    url,
    delay,
    attempts: recentAttempts,
    growthPercent: (storage.backoff && storage.backoff.growthPercent) || 20,
    reInterventionSeconds: getReinterventionSeconds(storage.settings),
    settings: storage.settings,
    stats: storage.stats
  };
}

/**
 * Message Dispatcher
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || typeof message !== 'object') return;

  const now = Date.now();

  switch (message.type) {
    case 'CHECK_NAVIGATION': {
      (async () => {
        const result = await evaluateNavigation(message.url);
        sendResponse(result);
      })();
      return true;
    }

    case 'CLOSE_TAB': {
      (async () => {
        const { domain, minutesSaved } = message;
        const storage = await getStorage();
        const updatedStats = calculateUpdatedStats(
          storage.stats,
          minutesSaved || storage.settings.avgSessionMinutes || 10,
          domain
        );
        await setStorage({ stats: updatedStats });

        if (sender.tab && sender.tab.id) {
          chrome.tabs.remove(sender.tab.id);
        }
        sendResponse({ success: true, stats: updatedStats });
      })();
      return true;
    }

    case 'GRANT_PASS': {
      const { domain, durationSeconds, durationMinutes } = message;
      const sec = (durationSeconds !== undefined && durationSeconds !== null)
        ? Number(durationSeconds)
        : (Number(durationMinutes) || 15) * 60;
      if (domain) {
        const expiry = now + Math.max(5, sec) * 1000;
        sessionPasses.set(domain, expiry);
      }
      sendResponse({ success: true });
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
      return true;
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

    default:
      sendResponse({ error: 'Unknown message type' });
  }
});
