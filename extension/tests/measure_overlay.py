"""Measure renderer main-thread work on the local browser fixture.

Run: python extension/tests/measure_overlay.py /tmp/wattim-before.json
This excludes the browser process, extension service worker and real website work.
"""
import json
from pathlib import Path
import statistics
import sys
import time

from test_overlay import OverlayTests


OverlayTests.setUpClass()
try:
    result = {"browser": OverlayTests.browser.version, "viewport": "1440x900, DPR=1",
              "boundary": "fixture renderer main-thread TaskDuration / wall time", "states": {}}
    for state in ("unprotected", "waiting_for_input", "hard_block"):
        case = OverlayTests()
        case.setUp()
        try:
            case.load(**({"shouldIntervene": False} if state == "unprotected" else
                         {"mode": "hard_block"} if state == "hard_block" else {}))
            if state == "waiting_for_input":
                case.unlock_inputs()
            session = case.page.context.new_cdp_session(case.page)
            session.send("Performance.enable")

            def snapshot():
                metrics = session.send("Performance.getMetrics")["metrics"]
                return next(item["value"] for item in metrics if item["name"] == "TaskDuration")

            samples = []
            counters_before = case.page.evaluate("({...metrics})")
            for _ in range(3):
                before = snapshot()
                started = time.monotonic()
                case.page.wait_for_timeout(2000)
                elapsed = time.monotonic() - started
                samples.append(round((snapshot() - before) / elapsed * 100, 3))
            counters_after = case.page.evaluate("({...metrics})")
            result["states"][state] = {
                "main_thread_busy_percent_samples": samples,
                "median_percent": statistics.median(samples),
                "canvas_draws_in_6s": counters_after["draws"] - counters_before["draws"],
                "media_scans_in_6s": counters_after["scans"] - counters_before["scans"],
            }
        finally:
            case.tearDown()
    Path(sys.argv[1]).write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
finally:
    OverlayTests.tearDownClass()
