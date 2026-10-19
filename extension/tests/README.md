# Extension verification

Run from the repository root. Python browser tests need `playwright` and Chromium
(`python -m pip install playwright` and `python -m playwright install chromium`).
Set `WATTIM_BROWSER` to use another Chrome executable; the harness also detects
`/opt/google/chrome/chrome`. Runtime extension code has no new dependencies.

```sh
python -m unittest discover -s extension/tests -v
node --test extension/tests/background.test.cjs
python extension/tests/firefox_smoke.py
python extension/tests/measure_overlay.py /tmp/wattim-performance.json
python extension/tests/build_firefox.py
```

The Firefox smoke test needs `firefox` and `web-ext` on PATH. It uses fresh
temporary profiles and a runtime copy with only localhost/default challenge/delay
changed for the fixture. It exercises actual content-script/background messages,
iframe styles under normal and restrictive site CSP, pass granting and HUD creation.
The Chromium tests fake only extension messaging and exercise real DOM, keyboard,
touch and CSS behavior. Navigation notification routing also has a Node test.

The Firefox build writes `ext.zip` at the repository root with 11 runtime files.
Tests, evidence, the standalone userscript and legacy aliases are excluded.
Unpack it and run `web-ext lint --source-dir <unpacked-directory>` before upload.
For an existing AMO listing, submit the unpacked directory with
`web-ext sign --source-dir <unpacked-directory> --channel listed`; provide
`WEB_EXT_API_KEY` and `WEB_EXT_API_SECRET` through the environment. Never commit
these credentials. See [Mozilla's publishing instructions](https://extensionworkshop.com/documentation/develop/getting-started-with-web-ext/#sign-and-submit-your-updated-extension-for-publication).

## Results, 2026-10-20

- 14 Chromium browser tests and one Node test passed.
- Actual Firefox 157.0.1 passed normal and restrictive-CSP smoke cases. The original
  iframe inline stylesheet failed the restrictive case; a stylesheet constructed
  in the frame document fixed it. Raw final reports: [firefox-smoke.json](evidence/firefox-smoke.json).
- Mobile touch emulation passed at 320×480, 360×640, 390×844 and 844×390, including
  scrolling to the form's top and bottom, 48 px buttons, popup/options width,
  long Cyrillic input, 150% text enlargement and a simulated keyboard viewport resize.
- No Android device was connected. Real Firefox Android keyboard, safe-area and
  browser-toolbar behavior still require a device check.
- Firefox runtime package lint: zero errors, two existing dynamic `innerHTML`
  warnings in the overlay template/HUD. No warnings were suppressed.
- Independent review found no remaining introduced correctness/lifecycle blocker.
  The long content script was reviewed for decomposition; this change keeps one
  overlay/session owner and removes the canvas loop without a broader rewrite.

## Performance boundary

Baseline is commit `13e8db8`. Same local fixture, headless Chrome 143.0.7499.192,
1440×900, DPR 1, three consecutive 2-second samples per state. The metric is CDP
renderer **main-thread busy time / wall time**, not whole-browser or OS CPU usage.
GPU/compositor, service-worker and real website costs are outside this measurement.
The fixture is short; samples are not a broad hardware benchmark.

| State | Before median | After median |
| --- | ---: | ---: |
| Unprotected page | 0.024% | 0.012% |
| Waiting for input | 13.795% | 0.868% |
| Hard block | 15.131% | 0.017% |

During the 6-second input window, canvas draws fell from 360 to zero and repeated
media scans from 15 to zero. The remaining early input-window work includes the
finite breathing-ring transition. A breathing animation runs only during the
countdown, pauses when hidden and respects reduced motion. Media replay is stopped
by `play` events; same-document navigation uses browser events instead of polling.

Raw samples: [before](evidence/performance-before.json),
[after](evidence/performance-after.json). These results do not predict the exact
CPU percentage on a user's phone or desktop.
