#!/usr/bin/env bash
set -euo pipefail
usage() { echo "usage: $0 --unsigned-apk FILE --version-name NAME --version-code CODE --git-sha SHA --build-run-id ID [--publish-root DIR]" >&2; exit 2; }
unsigned_apk= version_name= version_code= git_sha= build_run_id= publish_root=/srv/darkcat-downloads/lenscast-test
while (($#)); do
  case "$1" in
    --unsigned-apk) unsigned_apk=${2:?}; shift 2;; --version-name) version_name=${2:?}; shift 2;;
    --version-code) version_code=${2:?}; shift 2;; --git-sha) git_sha=${2:?}; shift 2;;
    --build-run-id) build_run_id=${2:?}; shift 2;; --publish-root) publish_root=${2:?}; shift 2;; *) usage;;
  esac
done
[[ -n "$unsigned_apk" && -n "$version_name" && "$version_code" =~ ^[0-9]+$ && -n "$git_sha" && -n "$build_run_id" ]]
[[ "$publish_root" == */lenscast-test ]] || { echo 'publish root must end in /lenscast-test' >&2; exit 1; }
[[ -f "$unsigned_apk" ]] || { echo 'unsigned APK not found' >&2; exit 1; }
expected=5A002D0F1D84849DD02A7ECD24B940BC8412E0CD762182EE0958C5B17D63CE5D
build_tools=${ANDROID_BUILD_TOOLS:-${ANDROID_HOME:?}/build-tools/37.0.0}; apksigner=$build_tools/apksigner; aapt=$build_tools/aapt
[[ -x "$apksigner" && -x "$aapt" ]]
! "$apksigner" verify "$unsigned_apk" >/dev/null 2>&1 || { echo 'input APK is signed' >&2; exit 1; }
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT; signed=$tmp/signed.apk
EXPECTED_SIGNER_SHA256=$expected ANDROID_BUILD_TOOLS=$build_tools \
  LENSCAST_KEYSTORE=${LENSCAST_KEYSTORE:-/etc/bruce/codex/darkcat-lenscast-signing/lenscast-test.jks} \
  LENSCAST_PASSWORD_FILE=${LENSCAST_PASSWORD_FILE:-/etc/bruce/codex/darkcat-lenscast-signing/lenscast-test.password} \
  scripts/sign-lenscast-test.sh "$unsigned_apk" "$signed" >/dev/null
EXPECTED_SIGNER_SHA256=$expected APKSIGNER=$apksigner scripts/verify-lenscast-signature.sh "$signed" >/dev/null
badging=$($aapt dump badging "$signed")
grep -Fq "package: name='com.dedtsss.darkcat.lenscast.test'" <<<"$badging"
grep -Fq "versionName='$version_name'" <<<"$badging"
grep -Fq "versionCode='$version_code'" <<<"$badging"
name="darkcat-camera-${version_name}.apk"; release_dir=$publish_root/releases; mkdir -p "$release_dir"; destination=$release_dir/$name
[[ ! -e "$destination" ]] || { echo 'immutable release already exists' >&2; exit 1; }
sha=$(sha256sum "$signed" | awk '{print $1}'); manifest=$tmp/latest.json
jq -n --arg versionName "$version_name" --argjson versionCode "$version_code" --arg gitSha "$git_sha" --arg buildRunId "$build_run_id" --arg channel lenscast-test --arg apk "https://darkcat.bruce-group.net/lenscast-test/releases/$name" --arg sha256 "$sha" '{versionName:$versionName,versionCode:$versionCode,gitSha:$gitSha,buildRunId:$buildRunId,channel:$channel,apk:$apk,sha256:$sha256}' > "$manifest"
install -m 0644 "$signed" "$destination"
printf '%s  %s\n' "$sha" "$name" > "$tmp/SHA256SUMS"; install -m 0644 "$tmp/SHA256SUMS" "$publish_root/SHA256SUMS"
install -m 0644 "$manifest" "$publish_root/latest.json.tmp"; mv -f "$publish_root/latest.json.tmp" "$publish_root/latest.json"
echo "published $destination sha256=$sha"
