import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

console.log('========================================');
console.log('🌿 Running Full wattim Extension Test Suite');
console.log('========================================\n');

// 1. Run Unit Tests
console.log('[1/4] Running Rule Engine tests...');
execSync('node tests/rule-engine.test.mjs', { stdio: 'inherit' });

console.log('\n[2/4] Running Storage tests...');
execSync('node tests/storage.test.mjs', { stdio: 'inherit' });

// 3. Syntax check all JS files
console.log('\n[3/4] Verifying JavaScript syntax...');
const jsFiles = [
  'extension/background/rule-engine.js',
  'extension/background/background.js',
  'extension/common/storage.js',
  'extension/common/themes.js',
  'extension/intervention/animations.js',
  'extension/intervention/intervention.js',
  'extension/popup/popup.js',
  'extension/options/options.js',
  'extension/sentinel/sentinel.js',
  'extension/overlay/overlay.js'
];

for (const file of jsFiles) {
  assert.ok(fs.existsSync(file), `File ${file} must exist`);
  execSync(`node -c "${file}"`);
  console.log(`✓ Syntax OK: ${file}`);
}

// 4. Validate Manifest references
console.log('\n[4/4] Validating Manifest V3 schema and assets...');
const manifestPath = 'extension/manifest.json';
assert.ok(fs.existsSync(manifestPath), 'manifest.json must exist');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

assert.equal(manifest.manifest_version, 3, 'Manifest version must be 3');
assert.ok(manifest.name, 'Manifest name must be present');
assert.ok(manifest.version, 'Manifest version must be present');

// Verify Chromium + Firefox dual compatibility
assert.ok(manifest.background?.service_worker, 'background.service_worker must be present for Chromium');
assert.ok(Array.isArray(manifest.background?.scripts) && manifest.background.scripts.length > 0, 'background.scripts must be present for Firefox');

// Verify referenced files
const filesToCheck = [
  manifest.background?.service_worker,
  ...(manifest.background?.scripts || []),
  manifest.action?.default_popup,
  manifest.options_ui?.page,
  manifest.action?.default_icon?.['16'],
  manifest.action?.default_icon?.['32'],
  manifest.action?.default_icon?.['48'],
  manifest.action?.default_icon?.['128'],
  manifest.icons?.['16'],
  manifest.icons?.['32'],
  manifest.icons?.['48'],
  manifest.icons?.['128'],
  ...(manifest.content_scripts?.[0]?.js || [])
];

for (const relPath of filesToCheck) {
  if (relPath) {
    const full = path.join('extension', relPath);
    assert.ok(fs.existsSync(full), `Referenced asset not found: ${full}`);
  }
}
console.log('✓ All manifest file references verified successfully');

console.log('\n========================================');
console.log('🎉 100% Tests Passed Successfully!');
console.log('========================================');
