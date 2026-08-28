#!/usr/bin/env bash
set -euo pipefail
apk=${1:?unsigned or signed APK path required}
expected=${EXPECTED_SIGNER_SHA256:?EXPECTED_SIGNER_SHA256 is required}
apksigner=${APKSIGNER:-$ANDROID_HOME/build-tools/37.0.0/apksigner}
test -x "$apksigner"
"$apksigner" verify --min-sdk-version 23 "$apk"
actual=$("$apksigner" verify --print-certs "$apk" 2>&1 | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}')
normalize_fingerprint() {
  tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'
}
normalized_expected=$(printf '%s' "$expected" | normalize_fingerprint)
normalized_actual=$(printf '%s' "$actual" | normalize_fingerprint)
[[ "$normalized_expected" =~ ^[0-9A-F]{64}$ && "$normalized_actual" =~ ^[0-9A-F]{64}$ ]]
test "$normalized_actual" = "$normalized_expected"
echo "signer fingerprint verified: $normalized_actual"
