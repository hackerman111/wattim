"""Browser regressions: python -m unittest discover -s extension/tests -v.

Requires Python Playwright and Chromium (or WATTIM_BROWSER pointing to Chrome).
Only the extension runtime transport is faked; DOM, typing and media events are real.
"""
import os
import json
import re
from pathlib import Path
import unittest

from playwright.sync_api import sync_playwright


EXTENSION = Path(__file__).resolve().parents[1]
CHALLENGE = "I visit example.com on purpose"


class OverlayHarness:
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        executable = os.environ.get("WATTIM_BROWSER")
        if not executable and Path("/opt/google/chrome/chrome").exists():
            executable = "/opt/google/chrome/chrome"
        cls.browser = cls.playwright.chromium.launch(
            headless=True, executable_path=executable, args=["--no-sandbox"]
        )

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.page = self.browser.new_page(**getattr(self, "browser_options", {
            "viewport": {"width": 1440, "height": 900}
        }))
        self.page.route("https://example.com/**", lambda route: route.fulfill(
            content_type="text/html", body='<html><head><meta name="viewport" '
            'content="width=device-width,initial-scale=1"></head><body><video></video></body></html>'
        ))
        self.page.goto("https://example.com/")
        self.overlay = self.page.frame_locator("#onesec-gfw-root")
        self.page.evaluate("""() => {
          window.metrics = { scans: 0, draws: 0, intervals: 0, messages: [] };
          const query = document.querySelectorAll.bind(document);
          document.querySelectorAll = selector => {
            if (selector === 'video, audio') metrics.scans++;
            return query(selector);
          };
          const clear = CanvasRenderingContext2D.prototype.clearRect;
          CanvasRenderingContext2D.prototype.clearRect = function(...args) {
            metrics.draws++;
            return clear.apply(this, args);
          };
          const interval = window.setInterval;
          window.setInterval = (...args) => { metrics.intervals++; return interval(...args); };
          window.chrome = { runtime: {
            onMessage: { addListener(listener) { window.onExtensionMessage = listener; } },
            sendMessage(message, callback) {
              metrics.messages.push(message);
              if (message.type === 'EVALUATE_SITE') callback(window.evaluation);
              if (message.type === 'GRANT_SITE_PASS') {
                callback(window.grantResponse || { ok: true, until: Date.now() + 60000 });
              }
            }
          }};
        }""")

    def tearDown(self):
        self.page.close()

    def load(self, **overrides):
        evaluation = {
            "shouldIntervene": True, "domainKey": "example.com", "waitSec": 1,
            "settings": {
                "requireChallengePhrase": True, "requireIntention": True,
                "challengePhrase": "I visit {domain} on purpose", "pauseMedia": True,
                "hapticFeedback": False, "tabBlurResetSec": 30,
            },
        }
        evaluation.update(overrides)
        self.page.evaluate("value => window.evaluation = value", evaluation)
        self.page.add_script_tag(path=str(EXTENSION / "overlay/overlay.js"))

    def unlock_inputs(self):
        self.overlay.locator("#challenge-input").wait_for(state="visible")


class OverlayTests(OverlayHarness, unittest.TestCase):
    def test_disabled_gates_allow_entry_without_inputs(self):
        self.load(settings={'requireChallengePhrase': False, 'requireIntention': False})
        proceed = self.overlay.locator('#btn-proceed')
        proceed.wait_for(state='visible')
        self.assertEqual(self.overlay.locator('input').count(), 0)
        self.assertTrue(proceed.is_enabled())
        proceed.click()
        self.assertEqual(self.page.locator('#onesec-gfw-root').count(), 0)

    def test_intention_only_gate_works_without_challenge(self):
        self.load(settings={'requireChallengePhrase': False, 'requireIntention': True})
        self.overlay.locator('#intention-input').wait_for(state='visible')
        self.assertEqual(self.overlay.locator('#challenge-input').count(), 0)
        self.assertTrue(self.overlay.locator('#btn-proceed').is_disabled())
        self.overlay.locator('#intention-input').fill('Research for work')
        self.assertTrue(self.overlay.locator('#btn-proceed').is_enabled())

    def test_settings_markup_is_displayed_as_literal_text(self):
        payload = '<img src=x onerror="alert(1)"> & <b>work</b>'
        self.load(settings={
            'phrases': [payload], 'challengePhrase': payload,
            'requireChallengePhrase': True, 'pauseMedia': False,
        })
        self.assertEqual(self.overlay.locator('h1').text_content(), payload)
        self.assertEqual(self.overlay.locator('.phrase-target').text_content(), payload)
        self.assertEqual(self.overlay.locator('img, b').count(), 0)

    def test_schedule_name_is_displayed_as_literal_text(self):
        payload = '<img src=x> & <b>Focus</b>'
        self.load(mode='hard_block', scheduleName=payload)
        self.assertIn(payload, self.overlay.locator('h1').text_content())
        self.assertEqual(self.overlay.locator('img, b').count(), 0)

    def test_hud_treats_intention_as_text_and_keeps_its_nodes(self):
        payload = '<img src=x onerror="alert(1)"> & <b>work</b>'
        self.page.clock.install()
        self.load(shouldIntervene=False, hasActivePass=True,
                  passUntil=self.page.evaluate('Date.now() + 60000'), intention=payload)
        hud = self.page.locator('#onesec-gfw-hud')
        self.assertEqual(hud.locator('span').text_content(), payload)
        self.assertEqual(hud.locator('img, b').count(), 0)
        hud.locator('strong').evaluate('el => window.originalTimeNode = el')
        before = hud.locator('strong').text_content()
        self.page.clock.fast_forward(1000)
        self.assertTrue(hud.locator('strong').evaluate('el => el === window.originalTimeNode'))
        self.assertNotEqual(hud.locator('strong').text_content(), before)

    def test_userscript_phrase_is_displayed_as_literal_text(self):
        payload = '<img src=x onerror="alert(1)"> & <b>work</b>'
        source = (EXTENSION / 'onesec-gfw.user.js').read_text()
        source = source.replace('"domains": [', '"domains": ["example.com",', 1)
        source = re.sub(r'"phrases": \[.*?\]',
                        lambda _: '"phrases": ' + json.dumps([payload]), source, count=1, flags=re.S)
        self.page.add_script_tag(content=source)
        root = self.page.locator('#onesec-userscript-root')
        self.assertEqual(root.locator('h1').text_content(), payload)
        self.assertEqual(root.locator('img, b').count(), 0)

    def test_site_shortcuts_cannot_truncate_phrase_or_intention(self):
        # A page sees the shadow host as event.target, so its input exemption fails.
        self.page.evaluate("""() => document.addEventListener('keydown', event => {
          if (event.target.tagName !== 'INPUT' && event.key.length === 1) {
            event.preventDefault();
          }
        })""")
        self.load()
        self.unlock_inputs()
        self.overlay.locator("#challenge-input").press_sequentially(CHALLENGE)
        self.overlay.locator("#intention-input").press_sequentially("Research for work")
        self.assertEqual(self.overlay.locator("#challenge-input").input_value(), CHALLENGE)
        self.assertEqual(self.overlay.locator("#intention-input").input_value(), "Research for work")
        self.assertTrue(self.overlay.locator("#btn-proceed").is_enabled())

    def test_capture_handlers_cannot_steal_input_after_two_characters(self):
        self.page.evaluate("""() => {
          window.pageKeys = 0;
          window.addEventListener('keydown', event => {
            if (event.key.length === 1 && ++pageKeys > 2) event.preventDefault();
          }, true);
        }""")
        self.load()
        self.unlock_inputs()
        self.overlay.locator("#challenge-input").press_sequentially(CHALLENGE)
        self.assertEqual(self.overlay.locator("#challenge-input").input_value(), CHALLENGE)
        self.assertEqual(self.page.evaluate("pageKeys"), 0)

    def test_media_pause_is_event_driven_and_stops_after_unlock(self):
        self.load()
        self.unlock_inputs()
        self.page.wait_for_timeout(1200)
        self.assertEqual(self.page.evaluate("metrics.scans"), 1)
        self.page.evaluate("""() => {
          window.pauses = 0;
          const media = document.querySelector('video');
          Object.defineProperty(media, 'paused', { get: () => false });
          media.pause = () => window.pauses++;
          media.dispatchEvent(new Event('play'));
        }""")
        self.assertEqual(self.page.evaluate("window.pauses"), 1)
        self.overlay.locator("#challenge-input").fill(CHALLENGE)
        self.overlay.locator("#intention-input").fill("Research for work")
        self.overlay.locator("#btn-proceed").click()
        self.page.evaluate("document.querySelector('video').dispatchEvent(new Event('play'))")
        self.assertEqual(self.page.evaluate("window.pauses"), 1)

    def test_waiting_for_input_does_not_keep_rendering_canvas(self):
        self.load()
        self.unlock_inputs()
        self.assertEqual(self.overlay.locator('#ambient').evaluate("el => el.getAnimations().length"), 0)
        before = self.page.evaluate("metrics.draws")
        self.page.wait_for_timeout(500)
        self.assertEqual(self.page.evaluate("metrics.draws"), before)

    def test_unprotected_page_has_no_periodic_polling(self):
        self.load(shouldIntervene=False)
        self.assertEqual(self.page.evaluate("metrics.intervals"), 0)

    def test_same_document_navigation_is_checked_once_per_new_url(self):
        self.load(shouldIntervene=False)
        self.page.evaluate("""() => {
          history.pushState({}, '', '/new-page');
          window.onExtensionMessage?.({ type: 'PAGE_URL_CHANGED' });
          window.onExtensionMessage?.({ type: 'PAGE_URL_CHANGED' });
        }""")
        self.assertEqual(self.page.evaluate("metrics.messages.length"), 2)

    def test_outdated_evaluation_cannot_replace_newer_page_decision(self):
        self.page.evaluate("""() => {
          window.replies = [];
          chrome.runtime.sendMessage = (message, callback) => replies.push(callback);
        }""")
        self.load()
        self.page.evaluate("""() => {
          history.pushState({}, '', '/new-page');
          window.onExtensionMessage?.({ type: 'PAGE_URL_CHANGED' });
          replies.at(-1)({ shouldIntervene: false });
          replies[0](evaluation);
        }""")
        self.assertEqual(self.page.locator("#onesec-gfw-root").count(), 0)

    def test_hard_block_has_no_animation_or_polling(self):
        self.load(mode="hard_block")
        self.page.wait_for_timeout(300)
        self.assertEqual(self.page.evaluate("metrics.draws"), 0)
        self.assertEqual(self.page.evaluate("metrics.intervals"), 0)
        self.assertEqual(self.overlay.locator('#ambient').evaluate("el => el.getAnimations().length"), 0)

    def test_animation_pauses_while_hidden_and_respects_reduced_motion(self):
        self.load(waitSec=20)
        self.assertEqual(self.overlay.locator('#ambient').evaluate(
            "el => el.getAnimations()[0].playState"), "running")
        self.page.evaluate("""() => {
          Object.defineProperty(document, 'hidden', { configurable: true, value: true });
          document.dispatchEvent(new Event('visibilitychange'));
        }""")
        self.assertEqual(self.overlay.locator('#ambient').evaluate(
            "el => el.getAnimations()[0].playState"), "paused")
        self.page.emulate_media(reduced_motion="reduce")
        self.assertEqual(self.overlay.locator('#ambient').evaluate("el => el.getAnimations().length"), 0)

    def test_paste_is_still_blocked(self):
        self.load()
        self.unlock_inputs()
        prevented = self.overlay.locator("#challenge-input").evaluate("""input => {
          const event = new ClipboardEvent('paste', { bubbles: true, cancelable: true });
          input.dispatchEvent(event);
          return event.defaultPrevented;
        }""")
        self.assertTrue(prevented)


if __name__ == "__main__":
    unittest.main()
