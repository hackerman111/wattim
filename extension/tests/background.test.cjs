const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

function loadBackground() {
  const navigation = {};
  const messages = [];
  const context = {
    chrome: {
      runtime: { onInstalled: { addListener() {} }, onMessage: { addListener() {} } },
      webNavigation: Object.fromEntries(['onHistoryStateUpdated', 'onReferenceFragmentUpdated']
        .map(name => [name, { addListener(listener) { navigation[name] = listener; } }])),
      tabs: { sendMessage(...args) { messages.push(args); args.at(-1)(); } },
    },
  };
  vm.runInNewContext(readFileSync(join(__dirname, '../background/background.js'), 'utf8'), context);
  return { navigation, messages };
}

test('SPA and fragment navigation notify only the top-level content script', () => {
  const { navigation, messages } = loadBackground();
  for (const name of ['onHistoryStateUpdated', 'onReferenceFragmentUpdated']) {
    assert.equal(typeof navigation[name], 'function');
    navigation[name]({ tabId: 7, frameId: 2, url: 'https://example.com/frame' });
    assert.equal(messages.length, name === 'onHistoryStateUpdated' ? 0 : 1);
    navigation[name]({ tabId: 7, frameId: 0, url: 'https://example.com/new' });
  }
  assert.deepEqual(JSON.parse(JSON.stringify(messages.map(args => args.slice(0, 3)))), [
    [7, { type: 'PAGE_URL_CHANGED' }, { frameId: 0 }],
    [7, { type: 'PAGE_URL_CHANGED' }, { frameId: 0 }],
  ]);
});
