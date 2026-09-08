import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { evaluateRule } from '../extension/background/rule-engine.js';

console.log('--- Testing Rule Engine Specifications (Parity Suite) ---');

const specDir = path.resolve('spec/rule-engine');
const fixtureFiles = [
  'allow.json',
  'grants.json',
  'schedules.json',
  'overnight.json',
  'hard-block.json',
  'backoff.json',
  'priority.json'
];

let totalPassed = 0;

for (const file of fixtureFiles) {
  const filePath = path.join(specDir, file);
  assert.ok(fs.existsSync(filePath), `Fixture file missing: ${filePath}`);

  const fixture = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  console.log(`\nEvaluating fixture: ${file} (${fixture.cases.length} cases)`);

  for (const testCase of fixture.cases) {
    const result = evaluateRule({
      url: testCase.url,
      target: testCase.target,
      now: testCase.now,
      zoneId: testCase.zoneId,
      recentAttemptsCount: testCase.recentAttemptsCount || 0,
      sessionId: testCase.sessionId !== undefined ? testCase.sessionId : null,
      state: testCase.state
    });

    assert.equal(
      result.action,
      testCase.expected.action,
      `[${file}::${testCase.id}] Expected action ${testCase.expected.action}, got ${result.action}`
    );

    if (testCase.expected.reason) {
      assert.equal(
        result.reason,
        testCase.expected.reason,
        `[${file}::${testCase.id}] Expected reason ${testCase.expected.reason}, got ${result.reason}`
      );
    }

    if (testCase.expected.until !== undefined) {
      assert.equal(
        result.until,
        testCase.expected.until,
        `[${file}::${testCase.id}] Expected until ${testCase.expected.until}, got ${result.until}`
      );
    }

    if (testCase.expected.durationSeconds !== undefined) {
      assert.equal(
        result.durationSeconds,
        testCase.expected.durationSeconds,
        `[${file}::${testCase.id}] Expected durationSeconds ${testCase.expected.durationSeconds}, got ${result.durationSeconds}`
      );
    }

    if (testCase.expected.durationMs !== undefined) {
      assert.equal(
        result.durationMs,
        testCase.expected.durationMs,
        `[${file}::${testCase.id}] Expected durationMs ${testCase.expected.durationMs}, got ${result.durationMs}`
      );
    }

    console.log(`  ✓ ${testCase.id}: ${testCase.description}`);
    totalPassed++;
  }
}

console.log(`\nAll ${totalPassed} rule engine spec test cases passed successfully!`);
