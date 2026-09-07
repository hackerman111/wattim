/**
 * wattim Browser Extension - Rule Engine
 * Deterministic domain matching, exponential backoff, and schedule evaluation.
 */

/**
 * Extracts normalized hostname from a URL string.
 * @param {string} urlStr
 * @returns {string} Hostname (lowercase, without port), or empty string if invalid
 */
export function extractHostname(urlStr) {
  if (!urlStr || typeof urlStr !== 'string') return '';
  try {
    const url = new URL(urlStr);
    return url.hostname.toLowerCase();
  } catch {
    return '';
  }
}

/**
 * Checks if a given URL matches a target domain specification.
 * Handles exact matches, subdomains (e.g., m.youtube.com matches youtube.com),
 * and wildcards (*.domain.com).
 * 
 * @param {string} urlStr - Full URL to test
 * @param {string} targetDomain - Target domain pattern (e.g. 'youtube.com', '*.site.org')
 * @returns {boolean} True if matching
 */
export function matchDomain(urlStr, targetDomain) {
  const host = extractHostname(urlStr);
  if (!host || !targetDomain) return false;

  let target = targetDomain.trim().toLowerCase();
  if (target.startsWith('http://') || target.startsWith('https://')) {
    target = extractHostname(target);
  }

  // Handle wildcard: *.domain.com
  if (target.startsWith('*.')) {
    const root = target.slice(2);
    return host === root || host.endsWith('.' + root);
  }

  // Exact match or subdomain match:
  // e.g. host === 'youtube.com' or host.endsWith('.youtube.com')
  return host === target || host.endsWith('.' + target);
}

/**
 * Computes the exponential backoff delay based on the number of attempts.
 * Equation: T = min(maxDelay, round(baseSeconds * (1 + growthPercent / 100)^attempts))
 * 
 * @param {number} baseSeconds - Base delay in seconds (e.g. 10)
 * @param {number} growthPercent - Growth percentage per attempt (e.g. 20)
 * @param {number} attempts - Number of attempts in the current window
 * @param {number} maxDelay - Maximum delay ceiling (e.g. 120)
 * @returns {number} Delay in seconds
 */
export function calculateBackoffDelay(baseSeconds, growthPercent, attempts, maxDelay = 120) {
  const base = Math.max(1, Number(baseSeconds) || 10);
  const growth = Math.max(0, Number(growthPercent) || 0);
  const count = Math.max(0, Number(attempts) || 0);
  const max = Math.max(base, Number(maxDelay) || 120);

  if (count === 0 || growth === 0) {
    return Math.min(max, base);
  }

  const multiplier = Math.pow(1 + growth / 100, count);
  const result = Math.round(base * multiplier);
  return Math.min(max, result);
}

/**
 * Converts 'HH:MM' string to minutes from start of day (0..1439).
 * @param {string} timeStr
 * @returns {number}
 */
export function timeToMinutes(timeStr) {
  if (!timeStr || typeof timeStr !== 'string') return 0;
  const parts = timeStr.split(':');
  if (parts.length < 2) return 0;
  const hours = parseInt(parts[0], 10) || 0;
  const minutes = parseInt(parts[1], 10) || 0;
  return hours * 60 + minutes;
}

/**
 * Checks whether a given schedule is currently active for a specific point in time.
 * Supports standard daytime schedules and overnight schedules crossing midnight.
 * 
 * @param {Object} schedule - Schedule configuration object
 * @param {boolean} schedule.enabled - Is schedule enabled
 * @param {number[]} schedule.days - Array of active days (0 = Sunday, 1 = Monday ... 6 = Saturday)
 * @param {string} schedule.start - Start time in 'HH:MM' (24h)
 * @param {string} schedule.end - End time in 'HH:MM' (24h)
 * @param {Date} [currentTime] - Current date/time object (defaults to now)
 * @returns {boolean} True if active
 */
export function isScheduleActive(schedule, currentTime = new Date()) {
  if (!schedule || !schedule.enabled) return false;
  if (!Array.isArray(schedule.days) || schedule.days.length === 0) return false;

  const day = currentTime.getDay(); // 0-6
  const currentMinutes = currentTime.getHours() * 60 + currentTime.getMinutes();
  const startMinutes = timeToMinutes(schedule.start);
  const endMinutes = timeToMinutes(schedule.end);

  // Check if today is an active day
  const isDayMatch = schedule.days.includes(day);

  // Standard interval: start <= end (e.g. 09:00 - 18:00)
  if (startMinutes <= endMinutes) {
    if (!isDayMatch) return false;
    return currentMinutes >= startMinutes && currentMinutes < endMinutes;
  }

  // Overnight interval crossing midnight: start > end (e.g. 22:00 - 06:00)
  // Two scenarios where it is active:
  // 1. After start time on scheduled day (e.g. 23:00 on scheduled day)
  // 2. Before end time, where previous day was scheduled (e.g. 02:00 morning)
  if (currentMinutes >= startMinutes && isDayMatch) {
    return true;
  }

  if (currentMinutes < endMinutes) {
    const prevDay = (day + 6) % 7;
    if (schedule.days.includes(prevDay)) {
      return true;
    }
  }

  return false;
}
