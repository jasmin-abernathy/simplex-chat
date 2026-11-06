"""Reject APKs requiring adb -t, even when compilation/signature succeed."""
import re
import sys
from pathlib import Path

manifest = Path(sys.argv[1]).read_text()
for line in manifest.splitlines():
    if re.search(r'\b(?:testOnly|isSplitRequired)\(', line):
        if not re.search(r'=\(type 0x12\)0x0\s*$', line):
            raise SystemExit(f'APK cannot be distributed for normal installation: {line.strip()}')
print('Installability flags: no test-only or required-split restriction')
