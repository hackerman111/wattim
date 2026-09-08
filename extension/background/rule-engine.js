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
 * Computes exponential backoff delay based on attempt count.
 * Equation: T = min(maxDelay, round(baseSeconds * (1 + growthPercent / 100)^attempts))
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
export function isScheduleActive(schedule, currentTime = new Date(), useUtc = false) {
  if (!schedule || !schedule.enabled) return false;
  if (!Array.isArray(schedule.days) || schedule.days.length === 0) return false;

  const day = useUtc ? currentTime.getUTCDay() : currentTime.getDay(); // 0-6
  const currentMinutes = useUtc
    ? currentTime.getUTCHours() * 60 + currentTime.getUTCMinutes()
    : currentTime.getHours() * 60 + currentTime.getMinutes();
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

/**
 * Evaluates access rules according to the unified Wattim Rule Engine specification.
 * 
 * @param {Object} params
 * @returns {Object} Evaluation decision: { action: 'ALLOW'|'BLOCK'|'INTERVENTION', ... }
 */
export function evaluateRule({
  url,
  target,
  now = Date.now(),
  zoneId,
  recentAttemptsCount = 0,
  sessionId = null,
  state = {}
}) {
  const currentMs = typeof now === 'number' ? now : (now instanceof Date ? now.getTime() : Date.now());
  const currentDate = new Date(currentMs);
  const useUtc = zoneId === 'UTC';

  // 1. Global Pause check
  const globalPauseUntil = state.globalPauseUntil || (state.globalPause && state.globalPause.until) || 0;
  if (globalPauseUntil && (globalPauseUntil === -1 || currentMs < globalPauseUntil)) {
    return { action: 'ALLOW', reason: 'GLOBAL_PAUSE' };
  }

  // Determine target identifier
  const targetId = target || (url ? extractHostname(url) : '');
  if (!targetId) {
    return { action: 'ALLOW', reason: 'NOT_TARGET' };
  }

  // 2. Target check
  const targets = state.targets || [];
  const matchedTarget = targets.find(t => {
    const domain = t.domain || t.packageName || '';
    if (target) {
      return domain === target;
    }
    return url ? matchDomain(url, domain) : false;
  });

  if (!matchedTarget) {
    return { action: 'ALLOW', reason: 'NOT_TARGET' };
  }
  if (matchedTarget.enabled === false) {
    return { action: 'ALLOW', reason: 'TARGET_DISABLED' };
  }

  const effectiveDomain = matchedTarget.domain || matchedTarget.packageName || targetId;

  // 3. Priority 1: Active manual block sessions
  const activeBlockSessions = (state.activeBlockSessions || []).filter(session => {
    if (!session.active) return false;
    const pkgs = session.packages || (session.targetDomain ? [session.targetDomain] : []);
    const isTarget = pkgs.some(p => p === effectiveDomain || (url && matchDomain(url, p)));
    return isTarget && currentMs >= session.startTime && currentMs < session.endTime;
  });

  if (activeBlockSessions.length > 0) {
    const maxUntil = Math.max(...activeBlockSessions.map(s => s.endTime));
    return { action: 'BLOCK', until: maxUntil };
  }

  // 4. Evaluate Schedules
  const schedules = state.schedules || [];
  let hardBlockEnd = null;
  let activeInterventionSchedule = null;

  for (const schedule of schedules) {
    if (!schedule.enabled) continue;
    const pkgs = schedule.packages || (schedule.targetDomain ? [schedule.targetDomain] : []);
    const isTarget = pkgs.length === 0 || pkgs.includes('all') || pkgs.some(p => p === effectiveDomain || (url && matchDomain(url, p)));
    if (!isTarget) continue;

    const DAY_MAP = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 };
    const normalizedDays = (schedule.daysInt || schedule.days || []).map(d =>
      typeof d === 'number' ? d : (DAY_MAP[String(d).toUpperCase()] ?? parseInt(d, 10))
    );

    const schedObj = { ...schedule, days: normalizedDays };
    if (isScheduleActive(schedObj, currentDate, useUtc)) {
      const type = schedule.scheduleType || (schedule.mode === 'hard_block' ? 'HARD_BLOCK' : 'INTERVENTION');
      if (type === 'HARD_BLOCK') {
        const startMinutes = timeToMinutes(schedule.start);
        const endMinutes = timeToMinutes(schedule.end);
        const curMinutes = useUtc
          ? currentDate.getUTCHours() * 60 + currentDate.getUTCMinutes()
          : currentDate.getHours() * 60 + currentDate.getMinutes();

        const scheduleEnd = new Date(currentMs);
        if (useUtc) {
          scheduleEnd.setUTCHours(Math.floor(endMinutes / 60), endMinutes % 60, 0, 0);
          if (startMinutes > endMinutes && curMinutes >= startMinutes) scheduleEnd.setUTCDate(scheduleEnd.getUTCDate() + 1);
        } else {
          scheduleEnd.setHours(Math.floor(endMinutes / 60), endMinutes % 60, 0, 0);
          if (startMinutes > endMinutes && curMinutes >= startMinutes) scheduleEnd.setDate(scheduleEnd.getDate() + 1);
        }
        const until = scheduleEnd.getTime();
        if (!hardBlockEnd || until > hardBlockEnd) {
          hardBlockEnd = until;
        }
      } else if (type === 'INTERVENTION') {
        if (!activeInterventionSchedule) {
          activeInterventionSchedule = schedule;
        }
      }
    }
  }

  // Priority 2: Hard Block schedule overrides intervention and grants
  if (hardBlockEnd !== null) {
    return { action: 'BLOCK', until: hardBlockEnd };
  }

  function checkGrant() {
    const permits = state.sessionPermits || {};
    if (sessionId !== null && permits[effectiveDomain] !== undefined) {
      if (permits[effectiveDomain] === sessionId) {
        return { action: 'ALLOW', reason: 'ACTIVE_SESSION_PERMIT' };
      }
    }
    const grants = state.grants || [];
    const grant = grants.find(g => (g.target || g.packageName || g.domain) === effectiveDomain);
    if (grant && grant.expiresAt && currentMs < grant.expiresAt) {
      return { action: 'ALLOW', reason: 'ACTIVE_TIMED_PERMIT' };
    }
    return null;
  }

  // Priority 3: Intervention schedules
  if (activeInterventionSchedule) {
    const grantResult = checkGrant();
    if (grantResult) return grantResult;

    const baseMs = activeInterventionSchedule.customDurationMs ||
      (activeInterventionSchedule.customDelaySeconds ? activeInterventionSchedule.customDelaySeconds * 1000 : null) ||
      matchedTarget.durationMs ||
      ((matchedTarget.durationSeconds || 10) * 1000);

    return {
      action: 'INTERVENTION',
      durationMs: baseMs,
      durationSeconds: Math.round(baseMs / 1000)
    };
  }

  // Priority 4: Access grants (session or timed permit)
  const grantResult = checkGrant();
  if (grantResult) return grantResult;

  // Priority 5: Standard Intervention with backoff
  const baseMs = matchedTarget.durationMs || ((matchedTarget.durationSeconds || 10) * 1000);
  const baseSec = Math.round(baseMs / 1000);
  const growthPercent = matchedTarget.exponentialGrowthEnabled !== false ? (matchedTarget.growthPercent ?? 20) : 0;
  const maxDelaySec = (state.settings && state.settings.maxDelaySeconds) || 120;
  const delaySec = calculateBackoffDelay(baseSec, growthPercent, recentAttemptsCount, maxDelaySec);
  const delayMs = (growthPercent > 0 && recentAttemptsCount > 0)
    ? Math.round(baseMs * Math.pow(1 + growthPercent / 100, recentAttemptsCount))
    : baseMs;

  return {
    action: 'INTERVENTION',
    durationMs: delayMs,
    durationSeconds: delaySec
  };
}
