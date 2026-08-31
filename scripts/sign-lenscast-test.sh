#!/usr/bin/env bash
set -euo pipefail
apk=${1:?unsigned APK path required}; out=${2:?signed APK output path required}
expected=${EXPECTED_SIGNER_SHA256:?EXPECTED_SIGNER_SHA256 is required}
keystore=${LENSCAST_KEYSTORE:?LENSCAST_KEYSTORE is required}
password_file=${LENSCAST_PASSWORD_FILE:?LENSCAST_PASSWORD_FILE is required}
alias=${LENSCAST_KEY_ALIAS:-lenscast-test}; build_tools=${ANDROID_BUILD_TOOLS:?ANDROID_BUILD_TOOLS is required}
zipalign="$build_tools/zipalign"; apksigner="$build_tools/apksigner"
test -f "$apk" -a -f "$keystore" -a -f "$password_file" -a ! -e "$out"
test -x "$zipalign" -a -x "$apksigner"; mkdir -p "$(dirname "$out")"
aligned=$(mktemp --suffix=.apk); trap 'rm -f "$aligned"' EXIT
"$zipalign" -f -p 4 "$apk" "$aligned" >/dev/null
password=$(<"$password_file")
"$apksigner" sign --ks "$keystore" --ks-key-alias "$alias" --ks-pass "pass:$password" --out "$out" "$aligned" >/dev/null
EXPECTED_SIGNER_SHA256="$expected" APKSIGNER="$apksigner" scripts/verify-lenscast-signature.sh "$out"
sha256sum "$out"
