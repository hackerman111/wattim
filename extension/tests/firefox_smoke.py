"""Actual Firefox add-on smoke: python extension/tests/firefox_smoke.py.

Needs Firefox and web-ext on PATH. Uses isolated temporary profiles and a copy of
the extension, modifying default settings in that copy to protect localhost.
Reports and browser logs remain under /tmp/wattim-firefox-*.
"""
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


EXTENSION = Path(__file__).resolve().parents[1]
SCRIPT = r"""
(async () => {
  const result = { restrictive: location.pathname === '/restrictive' };
  const wait = async predicate => {
    const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
      const value = predicate();
      if (value) return value;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw Error('Timed out waiting for overlay state');
  };
  try {
    const root = await wait(() => document.querySelector('#onesec-gfw-root')
      ?.contentDocument?.body?.shadowRoot);
    result.iframe = true;
    const overlay = root.querySelector('.overlay');
    const style = overlay.ownerDocument.defaultView.getComputedStyle(overlay);
    result.position = style.position;
    result.display = style.display;
    const input = await wait(() => {
      const el = root.querySelector('#challenge-input');
      return el && el.getBoundingClientRect().height > 0 && el;
    });
    input.value = 'I visit localhost on purpose';
    input.dispatchEvent(new input.ownerDocument.defaultView.Event('input', { bubbles: true }));
    const intention = root.querySelector('#intention-input');
    intention.value = 'Research for work';
    intention.dispatchEvent(new intention.ownerDocument.defaultView.Event('input', { bubbles: true }));
    result.proceedEnabled = !root.querySelector('#btn-proceed').disabled;
    root.querySelector('#btn-proceed').click();
    await wait(() => document.querySelector('#onesec-gfw-hud'));
    result.overlayRemoved = !document.querySelector('#onesec-gfw-root');
    result.hud = true;
  } catch (error) {
    result.error = String(error);
  }
  await fetch('/report', { method: 'POST', body: JSON.stringify(result) });
})();
"""


def main():
    artifacts = Path(tempfile.mkdtemp(prefix='wattim-firefox-'))
    source = artifacts / 'extension'
    shutil.copytree(EXTENSION, source, ignore=shutil.ignore_patterns('tests', '*.zip', '*.xpi'))
    background = source / 'background/background.js'
    text = background.read_text()
    text = text.replace('"domains": [', '"domains": ["localhost",', 1)
    text = text.replace('"baseDelay": 10', '"baseDelay": 3', 1)
    text = text.replace('"challengePhrase": "Я захожу на {domain} строго по делу"',
                        '"challengePhrase": "I visit {domain} on purpose"', 1)
    background.write_text(text)
    reports = []
    received = threading.Event()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            script = self.path == '/fixture.js'
            self.send_response(200)
            self.send_header('Content-Type', 'text/javascript' if script else 'text/html')
            if self.path == '/restrictive':
                self.send_header('Content-Security-Policy', "default-src 'self'; style-src 'self'; script-src 'self'")
            self.end_headers()
            body = SCRIPT if script else '<!doctype html><html><body><script src="/fixture.js"></script></body></html>'
            self.wfile.write(body.encode())

        def do_POST(self):
            reports.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
            self.send_response(204)
            self.end_headers()
            received.set()

        def log_message(self, *_args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print(subprocess.check_output(['firefox', '--version'], text=True).strip(), flush=True)
    print(f'Artifacts: {artifacts}', flush=True)
    try:
        for mode in ['normal', 'restrictive']:
            received.clear()
            command = ['web-ext', 'run', '--no-config-discovery', '--no-reload', '--no-input',
                       '--source-dir', str(source), '--firefox', '/usr/bin/firefox',
                       '--arg=-headless', '--start-url', f'http://localhost:{server.server_port}/{mode}']
            with (artifacts / f'{mode}.log').open('w') as log:
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    if not received.wait(45):
                        raise RuntimeError(f'{mode}: no report; see {artifacts / (mode + ".log")}')
                    print(json.dumps(reports[-1]), flush=True)
                finally:
                    os.killpg(process.pid, signal.SIGTERM)
                    process.wait(timeout=10)
        (artifacts / 'reports.json').write_text(json.dumps(reports, indent=2))
        for report in reports:
            assert not report.get('error'), report
            assert report['position'] == 'fixed' and report['display'] == 'flex', report
            assert report['proceedEnabled'] and report['overlayRemoved'] and report['hud'], report
        print('PASS: real Firefox overlay styles and grant/HUD flow, normal and restrictive CSP', flush=True)
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
