import assert from 'node:assert/strict';
import {
  DEFAULT_STORAGE,
  initStorageWithDefaults,
  normalizeDomain,
  findTargetDomain,
  calculateUpdatedStats,
  getReinterventionSeconds
} from '../extension/common/storage.js';

console.log('--- Testing Storage Module ---');

// 1. DEFAULT_STORAGE structure
{
  assert.equal(typeof DEFAULT_STORAGE, 'object');
  assert.equal(DEFAULT_STORAGE.settings.theme, 'nord');
  assert.equal(DEFAULT_STORAGE.settings.animation, 'PULSE');
  assert.equal(DEFAULT_STORAGE.settings.baseDelaySeconds, 10);
  assert.equal(DEFAULT_STORAGE.backoff.enabled, true);
  assert.equal(DEFAULT_STORAGE.backoff.growthPercent, 20);
  assert.ok(Array.isArray(DEFAULT_STORAGE.targets));
  assert.ok(DEFAULT_STORAGE.targets.some(t => t.domain === 'youtube.com'));
  console.log('✓ DEFAULT_STORAGE structure verified');
}

// 2. normalizeDomain
{
  assert.equal(normalizeDomain('https://www.youtube.com/watch?v=1'), 'youtube.com');
  assert.equal(normalizeDomain('HTTP://VK.COM/feed'), 'vk.com');
  assert.equal(normalizeDomain('reddit.com/'), 'reddit.com');
  assert.equal(normalizeDomain('  instagram.com  '), 'instagram.com');
  console.log('✓ normalizeDomain passed');
}

// 3. findTargetDomain
{
  const targets = [
    { id: '1', domain: 'youtube.com', enabled: true },
    { id: '2', domain: 'instagram.com', enabled: false },
    { id: '3', domain: 'reddit.com', enabled: true }
  ];

  const matched = findTargetDomain('https://m.youtube.com/feed', targets);
  assert.ok(matched);
  assert.equal(matched.domain, 'youtube.com');

  // Disabled target should not match
  const disabledMatch = findTargetDomain('https://instagram.com/p/1', targets);
  assert.equal(disabledMatch, null);

  // Non-target domain
  const noMatch = findTargetDomain('https://wikipedia.org', targets);
  assert.equal(noMatch, null);
  console.log('✓ findTargetDomain passed');
}

// 4. initStorageWithDefaults deep merges missing fields
{
  const partial = {
    settings: {
      theme: 'dracula'
    },
    stats: {
      savedImpulses: 5,
      savedMinutes: 50
    }
  };

  const initialized = initStorageWithDefaults(partial);
  assert.equal(initialized.settings.theme, 'dracula'); // preserves user setting
  assert.equal(initialized.settings.animation, 'PULSE'); // fills default
  assert.equal(initialized.stats.savedImpulses, 5); // preserves stats
  assert.ok(initialized.targets.length > 0); // fills default targets
  console.log('✓ initStorageWithDefaults passed');
}

// 5. calculateUpdatedStats
{
  const initialStats = {
    savedImpulses: 2,
    savedMinutes: 20,
    history: []
  };

  const updated = calculateUpdatedStats(initialStats, 10, 'youtube.com');
  assert.equal(updated.savedImpulses, 3);
  assert.equal(updated.savedMinutes, 30);
  assert.equal(updated.history.length, 1);
  assert.equal(updated.history[0].domain, 'youtube.com');
  console.log('✓ calculateUpdatedStats passed');
}

// 6. getReinterventionSeconds
{
  assert.equal(getReinterventionSeconds({ reInterventionSeconds: 45 }), 45);
  assert.equal(getReinterventionSeconds({ reInterventionMinutes: 10 }), 600);
  assert.equal(getReinterventionSeconds({ reInterventionSeconds: 30, reInterventionMinutes: 5 }), 30); // prioritizes seconds
  assert.equal(getReinterventionSeconds({}), 900); // default 15 min (900s)
  console.log('✓ getReinterventionSeconds passed');
}

console.log('All storage tests passed successfully!');
