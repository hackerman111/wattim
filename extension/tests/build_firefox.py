"""Build the Firefox runtime archive: python extension/tests/build_firefox.py."""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


extension = Path(__file__).resolve().parents[1]
archive = extension.parent / 'ext.zip'
files = [extension / 'manifest.json']
for directory in ('background', 'icons', 'options', 'overlay', 'popup'):
    files.extend(path for path in (extension / directory).rglob('*') if path.is_file())
with ZipFile(archive, 'w', compression=ZIP_DEFLATED) as output:
    for path in sorted(files):
        info = ZipInfo(path.relative_to(extension).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        output.writestr(info, path.read_bytes())
with ZipFile(archive) as result:
    assert result.testzip() is None
    for path in files:
        assert result.read(path.relative_to(extension).as_posix()) == path.read_bytes()
print(f'{archive}: {len(files)} runtime files, {archive.stat().st_size} bytes')
