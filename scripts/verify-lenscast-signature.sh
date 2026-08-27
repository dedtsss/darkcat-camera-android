#!/usr/bin/env bash
set -euo pipefail
apk=${1:?unsigned or signed APK path required}
expected=${EXPECTED_SIGNER_SHA256:?EXPECTED_SIGNER_SHA256 is required}
apksigner=${APKSIGNER:-$ANDROID_HOME/build-tools/37.0.0/apksigner}
test -x "$apksigner"
"$apksigner" verify --min-sdk-version 23 "$apk"
actual=$("$apksigner" verify --print-certs "$apk" 2>&1 | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}')
test -n "$actual" && test "$actual" = "$expected"
echo "signer fingerprint verified: $actual"
