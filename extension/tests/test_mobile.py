"""Touch/viewport emulation; this does not replace a Firefox Android device check."""
import unittest

from test_overlay import CHALLENGE, EXTENSION, OverlayHarness


class MobileTests(OverlayHarness, unittest.TestCase):
    browser_options = {
        "viewport": {"width": 360, "height": 640},
        "device_scale_factor": 2, "is_mobile": True, "has_touch": True,
    }

    def test_overlay_scroll_and_touch_targets_in_portrait_and_landscape(self):
        self.load()
        self.unlock_inputs()
        for width, height in ((320, 480), (360, 640), (390, 844), (844, 390)):
            with self.subTest(width=width, height=height):
                self.page.set_viewport_size({"width": width, "height": height})
                geometry = self.overlay.locator('#ov-wrap').evaluate("""overlay => {
                  overlay.scrollTop = 0;
                  const card = overlay.querySelector('.card-wrap').getBoundingClientRect();
                  return { top: card.top, width: overlay.clientWidth, scrollWidth: overlay.scrollWidth };
                }""")
                self.assertGreaterEqual(geometry['top'], 0, "Top of the form must be reachable")
                self.assertLessEqual(geometry['scrollWidth'], geometry['width'])
                for button in self.overlay.locator('button:visible').all():
                    box = button.bounding_box()
                    self.assertGreaterEqual(box['height'], 48)
                    self.assertGreaterEqual(box['width'], 48)
        self.overlay.locator('.dur-btn[data-m="2"]').tap()
        self.overlay.locator('#challenge-input').fill(CHALLENGE)
        self.overlay.locator('#intention-input').fill('Research for work')
        self.overlay.locator('#btn-proceed').tap()
        self.assertEqual(self.page.locator('#onesec-gfw-root').count(), 0)
        self.assertEqual(self.page.evaluate("metrics.messages.at(-1).minutes"), 2)

    def test_popup_and_options_fit_narrow_phone(self):
        self.page.route('**/*.js', lambda route: route.fulfill(
            content_type='application/javascript', body=''))
        for path in ('popup/popup.html', 'options/options.html'):
            with self.subTest(page=path):
                self.page.set_viewport_size({"width": 320, "height": 568})
                self.page.set_content((EXTENSION / path).read_text())
                geometry = self.page.evaluate("""() => ({
                  width: document.documentElement.clientWidth,
                  scrollWidth: document.documentElement.scrollWidth
                })""")
                self.assertLessEqual(geometry['scrollWidth'], geometry['width'])
                for button in self.page.locator('button:visible').all():
                    self.assertGreaterEqual(button.bounding_box()['height'], 48)

    def test_keyboard_viewport_resize_keeps_overlay_inside_visible_area(self):
        self.load()
        self.unlock_inputs()
        self.page.evaluate("""() => {
          Object.defineProperty(visualViewport, 'height', { configurable: true, value: 280 });
          visualViewport.dispatchEvent(new Event('resize'));
        }""")
        self.assertAlmostEqual(self.page.locator('#onesec-gfw-root').bounding_box()['height'], 280, delta=1)

    def test_large_text_and_long_phrase_keep_every_control_reachable(self):
        self.page.set_viewport_size({"width": 320, "height": 480})
        self.load(settings={
            'requireChallengePhrase': True, 'requireIntention': True,
            'challengePhrase': 'Я захожу на very-long-subdomain.example.com строго по делу',
            'pauseMedia': False, 'hapticFeedback': False,
        })
        self.unlock_inputs()
        # Simulate 150% text enlargement independently of the desktop OS font scale.
        self.overlay.locator('#ov-wrap').evaluate("""overlay => {
          for (const el of overlay.querySelectorAll('h1,p,span,input,button,.phrase-target')) {
            el.style.fontSize = `${parseFloat(getComputedStyle(el).fontSize) * 1.5}px`;
          }
          overlay.scrollTop = 0;
        }""")
        geometry = self.overlay.locator('#ov-wrap').evaluate("""overlay => ({
          width: overlay.clientWidth, scrollWidth: overlay.scrollWidth,
          top: overlay.querySelector('.card-wrap').getBoundingClientRect().top
        })""")
        self.assertGreaterEqual(geometry['top'], 0)
        self.assertLessEqual(geometry['scrollWidth'], geometry['width'])
        self.overlay.locator('#challenge-input').fill('Я захожу на very-long-subdomain.example.com строго по делу')
        self.overlay.locator('#intention-input').fill('Проверить рабочие сообщения')
        self.overlay.locator('#btn-proceed').tap()
        self.assertEqual(self.page.locator('#onesec-gfw-root').count(), 0)


if __name__ == '__main__':
    unittest.main()
