#!/bin/bash
set -euo pipefail
mkdir -p installation-proof
adb shell getprop ro.product.cpu.abilist > installation-proof/abis.txt
grep -q arm64-v8a installation-proof/abis.txt
if adb install rejected/verger-simplex-prototype.apk > installation-proof/old-install.txt 2>&1; then
  echo "Old testOnly APK unexpectedly installed"
  exit 1
fi
grep -q INSTALL_FAILED_TEST_ONLY installation-proof/old-install.txt
adb install corrected/verger-simplex-prototype.apk > installation-proof/corrected-install.txt 2>&1
grep -q Success installation-proof/corrected-install.txt
adb shell pm path chat.simplex.app.vergertest > installation-proof/package.txt
grep -q 'package:' installation-proof/package.txt
echo "Old APK refused as test-only; corrected APK installed without -t."
