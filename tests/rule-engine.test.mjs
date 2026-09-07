import assert from 'node:assert/strict';
import { matchDomain, calculateBackoffDelay, isScheduleActive, extractHostname } from '../extension/background/rule-engine.js';

console.log('--- Testing Rule Engine ---');

// 1. extractHostname
{
  assert.equal(extractHostname('https://www.youtube.com/watch?v=123'), 'www.youtube.com');
  assert.equal(extractHostname('http://m.instagram.com/direct'), 'm.instagram.com');
  assert.equal(extractHostname('https://reddit.com/r/all/'), 'reddit.com');
  assert.equal(extractHostname('invalid-url'), '');
  console.log('✓ extractHostname passed');
}

// 2. matchDomain
{
  assert.equal(matchDomain('https://youtube.com', 'youtube.com'), true);
  assert.equal(matchDomain('https://www.youtube.com/feed/explore', 'youtube.com'), true);
  assert.equal(matchDomain('https://m.youtube.com', 'youtube.com'), true);
  assert.equal(matchDomain('https://music.youtube.com', 'youtube.com'), true);
  assert.equal(matchDomain('https://notyoutube.com', 'youtube.com'), false);
  assert.equal(matchDomain('https://google.com', 'youtube.com'), false);
  assert.equal(matchDomain('https://x.com/home', 'x.com'), true);
  assert.equal(matchDomain('https://sub.sub2.domain.org', 'domain.org'), true);
  console.log('✓ matchDomain passed');
}

// 3. calculateBackoffDelay
{
  // T = min(maxDelay, round(base * (1 + growth/100)^N))
  assert.equal(calculateBackoffDelay(10, 20, 0, 120), 10);
  assert.equal(calculateBackoffDelay(10, 20, 1, 120), 12);
  assert.equal(calculateBackoffDelay(10, 20, 2, 120), 14); // 10 * 1.44 = 14.4 -> 14
  assert.equal(calculateBackoffDelay(10, 20, 3, 120), 17); // 10 * 1.728 = 17.28 -> 17
  assert.equal(calculateBackoffDelay(10, 20, 4, 120), 21); // 10 * 2.0736 = 20.736 -> 21
  assert.equal(calculateBackoffDelay(10, 20, 20, 120), 120); // capped by maxDelay
  console.log('✓ calculateBackoffDelay passed');
}

// 4. isScheduleActive
{
  // Standard daytime schedule: Mon-Fri (1-5), 09:00 - 18:00
  const workSchedule = {
    enabled: true,
    days: [1, 2, 3, 4, 5],
    start: '09:00',
    end: '18:00'
  };

  // Tuesday 11:30 -> Active
  const tuesdayActive = new Date('2026-09-08T11:30:00'); // 2026-09-08 is Tuesday (day 2)
  assert.equal(isScheduleActive(workSchedule, tuesdayActive), true);

  // Tuesday 08:30 -> Inactive (before start)
  const tuesdayEarly = new Date('2026-09-08T08:30:00');
  assert.equal(isScheduleActive(workSchedule, tuesdayEarly), false);

  // Tuesday 18:05 -> Inactive (after end)
  const tuesdayLate = new Date('2026-09-08T18:05:00');
  assert.equal(isScheduleActive(workSchedule, tuesdayLate), false);

  // Sunday 12:00 -> Inactive (weekend)
  const sunday = new Date('2026-09-13T12:00:00'); // day 0
  assert.equal(isScheduleActive(workSchedule, sunday), false);

  // Disabled schedule -> Inactive
  assert.equal(isScheduleActive({ ...workSchedule, enabled: false }, tuesdayActive), false);

  // Overnight schedule crossing midnight: 22:00 to 06:00, all days
  const nightSchedule = {
    enabled: true,
    days: [0, 1, 2, 3, 4, 5, 6],
    start: '22:00',
    end: '06:00'
  };

  assert.equal(isScheduleActive(nightSchedule, new Date('2026-09-08T23:30:00')), true);
  assert.equal(isScheduleActive(nightSchedule, new Date('2026-09-08T02:15:00')), true);
  assert.equal(isScheduleActive(nightSchedule, new Date('2026-09-08T14:00:00')), false);

  console.log('✓ isScheduleActive passed');
}

console.log('All rule engine tests passed successfully!');
