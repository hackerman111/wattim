/**
 * wattim Browser Extension - Storage Layer
 * Local schema management, persistence, defaults, and domain normalizers.
 */

import { matchDomain, extractHostname } from '../background/rule-engine.js';

export const DEFAULT_STORAGE = {
  settings: {
    theme: 'nord',
    animation: 'PULSE', // 'FILL' | 'PULSE' | 'ZEN_ORBIT'
    baseDelaySeconds: 10,
    maxDelaySeconds: 120,
    avgSessionMinutes: 10,
    reInterventionMinutes: 15,
    phrases: [
      'Сделай глубокий вдох...',
      'Ты действительно хочешь открыть этот сайт сейчас?',
      'Верни контроль над своим вниманием.',
      'Осознанность побеждает алгоритмы дофаминовых ловушек.',
      'Остановись на мгновение. Почувствуй настоящее.'
    ]
  },
  targets: [
    { id: 't_yt', domain: 'youtube.com', enabled: true },
    { id: 't_ig', domain: 'instagram.com', enabled: true },
    { id: 't_rd', domain: 'reddit.com', enabled: true },
    { id: 't_tw', domain: 'x.com', enabled: true },
    { id: 't_vk', domain: 'vk.com', enabled: true },
    { id: 't_tt', domain: 'tiktok.com', enabled: true }
  ],
  backoff: {
    enabled: true,
    growthPercent: 20,
    windowMinutes: 60
  },
  schedules: [
    {
      id: 'work_focus',
      name: 'Рабочий фокус',
      enabled: false,
      mode: 'hard_block', // 'hard_block' | 'custom_intervention'
      customDelaySeconds: 20,
      days: [1, 2, 3, 4, 5],
      start: '09:00',
      end: '18:00',
      targetDomain: 'all'
    }
  ],
  stats: {
    savedImpulses: 0,
    savedMinutes: 0,
    history: []
  },
  globalPause: {
    until: 0 // Epoch milliseconds
  }
};

/**
 * Normalizes user input or full URL into a clean domain.
 * Removes protocol, trailing slashes, www prefix, path, port.
 * 
 * @param {string} input - e.g. "https://www.youtube.com/watch?v=1" or "  vk.com/ "
 * @returns {string} - e.g. "youtube.com" or "vk.com"
 */
export function normalizeDomain(input) {
  if (!input || typeof input !== 'string') return '';
  let str = input.trim().toLowerCase();

  // If no protocol, prefix temporarily to parse via URL if it has paths
  if (!str.startsWith('http://') && !str.startsWith('https://')) {
    str = 'https://' + str;
  }

  let host = extractHostname(str);
  if (host.startsWith('www.')) {
    host = host.slice(4);
  }
  return host;
}

/**
 * Finds an enabled target domain matching the given URL.
 * 
 * @param {string} urlStr
 * @param {Array<{id: string, domain: string, enabled: boolean}>} targets
 * @returns {Object|null} Matching target object or null
 */
export function findTargetDomain(urlStr, targets) {
  if (!urlStr || !Array.isArray(targets)) return null;
  for (const target of targets) {
    if (target.enabled && matchDomain(urlStr, target.domain)) {
      return target;
    }
  }
  return null;
}

/**
 * Merges partial or existing storage object with default values,
 * ensuring all expected keys exist without overwriting user data.
 * 
 * @param {Object} existing
 * @returns {Object} Complete storage object
 */
export function initStorageWithDefaults(existing = {}) {
  const result = JSON.parse(JSON.stringify(DEFAULT_STORAGE));

  if (!existing || typeof existing !== 'object') {
    return result;
  }

  if (existing.settings && typeof existing.settings === 'object') {
    result.settings = { ...result.settings, ...existing.settings };
  }

  if (Array.isArray(existing.targets)) {
    result.targets = existing.targets;
  }

  if (existing.backoff && typeof existing.backoff === 'object') {
    result.backoff = { ...result.backoff, ...existing.backoff };
  }

  if (Array.isArray(existing.schedules)) {
    result.schedules = existing.schedules;
  }

  if (existing.stats && typeof existing.stats === 'object') {
    result.stats = {
      savedImpulses: Number(existing.stats.savedImpulses) || 0,
      savedMinutes: Number(existing.stats.savedMinutes) || 0,
      history: Array.isArray(existing.stats.history) ? existing.stats.history : []
    };
  }

  if (existing.globalPause && typeof existing.globalPause === 'object') {
    result.globalPause = {
      until: Number(existing.globalPause.until) || 0
    };
  }

  return result;
}

/**
 * Calculates updated statistics when an impulse is prevented.
 * 
 * @param {Object} currentStats
 * @param {number} minutesSaved
 * @param {string} domain
 * @returns {Object}
 */
export function calculateUpdatedStats(currentStats, minutesSaved, domain) {
  const stats = currentStats || { savedImpulses: 0, savedMinutes: 0, history: [] };
  const savedImpulses = (Number(stats.savedImpulses) || 0) + 1;
  const savedMinutes = (Number(stats.savedMinutes) || 0) + (Number(minutesSaved) || 10);

  const historyEntry = {
    timestamp: Date.now(),
    domain: domain || 'unknown',
    savedMinutes: Number(minutesSaved) || 10
  };

  // Keep last 100 entries to prevent unbounded storage growth
  const history = [historyEntry, ...(stats.history || [])].slice(0, 100);

  return {
    savedImpulses,
    savedMinutes,
    history
  };
}

/**
 * Retrieves the full storage state from chrome.storage.local
 * with automatic default fallback.
 * 
 * @returns {Promise<typeof DEFAULT_STORAGE>}
 */
export async function getStorage() {
  if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
    return new Promise((resolve) => {
      chrome.storage.local.get(null, (data) => {
        resolve(initStorageWithDefaults(data));
      });
    });
  }
  return DEFAULT_STORAGE;
}

/**
 * Saves given keys to chrome.storage.local.
 * 
 * @param {Object} items
 * @returns {Promise<void>}
 */
export async function setStorage(items) {
  if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
    return new Promise((resolve) => {
      chrome.storage.local.set(items, resolve);
    });
  }
}
