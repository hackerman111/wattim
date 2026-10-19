// wattim × One Sec × Go Fucking Work — Persistent Background Rule Engine (v2.0 Fixed)
const DEFAULT_SETTINGS = {
  "baseDelay": 10,
  "progressiveStep": 5,
  "backoffMode": "exponential",
  "growthPercent": 25,
  "windowMinutes": 60,
  "reinterventionMinutes": 5,
  "tabBlurResetSec": 30,
  "requireIntention": true,
  "minIntentionLength": 8,
  "requireChallengePhrase": true,
  "challengePhrase": "Я захожу на {domain} строго по делу",
  "canvasStyle": "PULSE",
  "themePalette": "gfw_crimson",
  "pauseMedia": true,
  "hapticFeedback": true,
  "touchHoldMode": false,
  "avgSessionMinutes": 10,
  "domains": [
    "youtube.com",
    "reddit.com",
    "x.com",
    "twitter.com",
    "vk.com",
    "t.me",
    "pikabu.ru",
    "instagram.com",
    "tiktok.com",
    "twitch.tv"
  ],
  "phrases": [
    "Серьёзно? Опять сюда? Твои задачи сами себя не закроют, иди работай.",
    "Ты открыл эту вкладку на чистом рефлексе. Закрой её и займись делом.",
    "Go Fucking Work. Мемы деградируют и без твоего участия.",
    "У тебя что, все дедлайны закрыты и на счету миллион долларов? Нет? Тогда за работу.",
    "Ещё одна минута прокрастинации — и ты снова будешь сидеть ночью в мыле.",
    "Сделай глубокий вдох, осознай масштаб своей лени и закрой эту вкладку."
  ],
  "schedules": [
    {
      "id": "work_focus",
      "name": "Рабочий фокус (Hard Block)",
      "enabled": false,
      "mode": "hard_block",
      "days": [
        1,
        2,
        3,
        4,
        5
      ],
      "start": "09:00",
      "end": "18:00"
    }
  ]
};

function normalizeHost(input) {
  if (!input || typeof input !== 'string') return '';
  return input.trim().toLowerCase()
    .replace(/^https?:\/\//, '')
    .replace(/^(www|m|mobile)\./, '')
    .split('/')[0]
    .split(':')[0];
}

function getTodayKey() {
  return new Date().toISOString().slice(0, 10);
}

function timeToMinutes(t) {
  if (!t || typeof t !== 'string') return 0;
  const [h, m] = t.split(':').map(n => parseInt(n, 10) || 0);
  return h * 60 + m;
}

function isScheduleActive(sched, nowDate = new Date()) {
  if (!sched || !sched.enabled || !Array.isArray(sched.days)) return false;
  const day = nowDate.getDay();
  const curMin = nowDate.getHours() * 60 + nowDate.getMinutes();
  const startMin = timeToMinutes(sched.start);
  const endMin = timeToMinutes(sched.end);

  if (startMin <= endMin) {
    return sched.days.includes(day) && curMin >= startMin && curMin < endMin;
  }
  if (curMin >= startMin && sched.days.includes(day)) return true;
  if (curMin < endMin && sched.days.includes((day + 6) % 7)) return true;
  return false;
}

function calculateDelay(settings, attemptCount) {
  const base = Math.max(3, Number(settings.baseDelay) || 10);
  const step = Math.max(0, Number(settings.progressiveStep) || 0);
  if (attemptCount <= 0 || step <= 0) return base;
  if (settings.backoffMode === 'exponential') {
    const growth = Number(settings.growthPercent) || (step * 5) || 20;
    return Math.min(180, Math.round(base * Math.pow(1 + growth / 100, attemptCount)));
  }
  return Math.min(180, base + attemptCount * step);
}

chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.get(['settings', 'stats', 'domainPasses', 'attemptHistory', 'globalPauseUntil'], (res) => {
    const updates = {};
    if (!res.settings) updates.settings = DEFAULT_SETTINGS;
    if (!res.stats) {
      updates.stats = {
        dayKey: getTodayKey(),
        savedCount: 0,
        enteredCount: 0,
        savedMinutes: 0,
        history: []
      };
    }
    if (!res.domainPasses) updates.domainPasses = {};
    if (!res.attemptHistory) updates.attemptHistory = [];
    if (res.globalPauseUntil === undefined) updates.globalPauseUntil = 0;
    if (Object.keys(updates).length > 0) {
      chrome.storage.local.set(updates);
    }
  });
});

function notifyPageNavigation(details) {
  if (details.frameId !== 0) return;
  chrome.tabs.sendMessage(details.tabId, { type: 'PAGE_URL_CHANGED' }, { frameId: 0 }, () => {
    // Restricted pages and tabs without a content script have no receiver.
    void chrome.runtime.lastError;
  });
}

chrome.webNavigation.onHistoryStateUpdated.addListener(notifyPageNavigation);
chrome.webNavigation.onReferenceFragmentUpdated.addListener(notifyPageNavigation);

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (!msg || typeof msg !== 'object') return;
  const now = Date.now();

  if (msg.action === 'CLOSE_CURRENT_TAB' || msg.type === 'CLOSE_TAB') {
    chrome.storage.local.get(['settings', 'stats'], (data) => {
      const settings = data.settings || DEFAULT_SETTINGS;
      let stats = data.stats || { dayKey: getTodayKey(), savedCount: 0, enteredCount: 0, savedMinutes: 0, history: [] };
      if (stats.dayKey !== getTodayKey()) {
        stats.dayKey = getTodayKey();
        stats.enteredCount = 0;
      }
      const addMins = Number(msg.minutesSaved) || Number(settings.avgSessionMinutes) || 10;
      stats.savedCount = (stats.savedCount || 0) + 1;
      stats.savedMinutes = (stats.savedMinutes || 0) + addMins;
      stats.history = [{ timestamp: now, domain: msg.domain || '', savedMinutes: addMins }, ...(stats.history || [])].slice(0, 100);

      chrome.storage.local.set({ stats }, () => {
        if (sender.tab && sender.tab.id) {
          chrome.tabs.remove(sender.tab.id, () => sendResponse({ ok: true, stats }));
        } else {
          chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
            if (tabs && tabs[0] && tabs[0].id) chrome.tabs.remove(tabs[0].id);
            sendResponse({ ok: true, stats });
          });
        }
      });
    });
    return true;
  }

  if (msg.type === 'EVALUATE_SITE') {
    chrome.storage.local.get(['settings', 'stats', 'domainPasses', 'attemptHistory', 'globalPauseUntil'], (data) => {
      const settings = data.settings || DEFAULT_SETTINGS;
      const globalPauseUntil = Number(data.globalPauseUntil) || 0;
      if (globalPauseUntil > now) {
        sendResponse({ shouldIntervene: false, isGloballyPaused: true, remainingPauseMs: globalPauseUntil - now });
        return;
      }

      const cleanHost = normalizeHost(msg.hostname || msg.url);
      const matchedRoot = (settings.domains || []).find(d => {
        const cleanD = normalizeHost(d);
        return cleanD && (cleanHost === cleanD || cleanHost.endsWith('.' + cleanD));
      });

      if (!matchedRoot) {
        sendResponse({ shouldIntervene: false });
        return;
      }

      const domainKey = normalizeHost(matchedRoot);
      const passes = data.domainPasses || {};
      const activePass = passes[domainKey] || passes[cleanHost];

      if (activePass && activePass.until > now && !msg.forceReintervene) {
        sendResponse({
          shouldIntervene: false,
          hasActivePass: true,
          domainKey,
          passUntil: activePass.until,
          intention: activePass.intention || 'Осознанный визит'
        });
        return;
      }

      // Check schedules (Hard Block)
      const activeHardBlock = (settings.schedules || []).find(s => isScheduleActive(s) && s.mode === 'hard_block');
      const windowMs = ((settings.windowMinutes || 60) * 60 * 1000);
      const history = (data.attemptHistory || []).filter(a => (now - a.timestamp) < windowMs);
      const domainAttempts = history.filter(a => a.domain === domainKey).length;

      const waitSec = calculateDelay(settings, domainAttempts);
      const stats = data.stats || { dayKey: getTodayKey(), savedCount: 0, enteredCount: 0, savedMinutes: 0 };

      sendResponse({
        shouldIntervene: true,
        mode: activeHardBlock ? 'hard_block' : 'normal',
        scheduleName: activeHardBlock ? activeHardBlock.name : null,
        domainKey,
        cleanHost,
        attemptNumber: domainAttempts + 1,
        waitSec,
        settings,
        stats
      });
    });
    return true;
  }

  if (msg.type === 'GRANT_SITE_PASS') {
    chrome.storage.local.get(['settings', 'stats', 'domainPasses', 'attemptHistory'], (data) => {
      const settings = data.settings || DEFAULT_SETTINGS;
      const domainKey = normalizeHost(msg.domainKey || msg.hostname);
      const minutes = Math.max(1, Number(msg.minutes) || Number(settings.reinterventionMinutes) || 5);
      const until = now + minutes * 60 * 1000;

      const domainPasses = data.domainPasses || {};
      domainPasses[domainKey] = {
        until,
        intention: msg.intention || 'Осознанный визит'
      };

      const windowMs = ((settings.windowMinutes || 60) * 60 * 1000);
      const attemptHistory = [
        ...(data.attemptHistory || []).filter(a => (now - a.timestamp) < windowMs),
        { domain: domainKey, timestamp: now }
      ];

      let stats = data.stats || { dayKey: getTodayKey(), savedCount: 0, enteredCount: 0, savedMinutes: 0, history: [] };
      if (stats.dayKey !== getTodayKey()) {
        stats.dayKey = getTodayKey();
        stats.enteredCount = 0;
      }
      stats.enteredCount = (stats.enteredCount || 0) + 1;

      chrome.storage.local.set({ domainPasses, attemptHistory, stats }, () => {
        sendResponse({ ok: true, until, stats });
      });
    });
    return true;
  }

  if (msg.type === 'SET_GLOBAL_PAUSE') {
    const mins = Number(msg.durationMinutes) || 0;
    const globalPauseUntil = mins > 0 ? (now + mins * 60 * 1000) : 0;
    chrome.storage.local.set({ globalPauseUntil }, () => {
      sendResponse({ ok: true, globalPauseUntil });
    });
    return true;
  }
});
