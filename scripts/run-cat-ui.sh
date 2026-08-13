#!/usr/bin/env bash
set -u -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUITE_DIR="$ROOT_DIR/.maestro/flows"
RUN_ID="${CAT_UI_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
DEVICE="${MAESTRO_DEVICE_ID:-}"
APK=""

usage() {
    cat <<'EOF'
Usage: scripts/run-cat-ui.sh [--device DEVICE_ID] [--apk PATH] [--run-id ID]

Runs the checked-in CAT UI Maestro flows on one explicitly selected Android device.
If --apk is supplied, it is installed with adb before the run. The app must be
built separately; this runner never invokes Android Studio or changes product data.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            DEVICE="$2"
            shift 2
            ;;
        --apk)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            APK="$2"
            shift 2
            ;;
        --run-id)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            RUN_ID="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            usage >&2
            exit 64
            ;;
    esac
done

if [[ ! "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "run-id contains unsupported characters" >&2
    exit 64
fi

ARTIFACT_DIR="$ROOT_DIR/build/cat-ui/$RUN_ID"
MAESTRO_ARTIFACT_DIR="$ARTIFACT_DIR/maestro"
mkdir -p "$MAESTRO_ARTIFACT_DIR"
CONSOLE_LOG="$ARTIFACT_DIR/console.log"
COMMANDS_LOG="$ARTIFACT_DIR/commands.txt"
JUNIT_REPORT="$ARTIFACT_DIR/cat-ui-junit.xml"
HTML_REPORT="$ARTIFACT_DIR/cat-ui-report.html"
MAPPING_COPY="$ARTIFACT_DIR/CAT_UI_CHECKLIST.md"

printf 'CAT UI run %s\n' "$RUN_ID" > "$CONSOLE_LOG"
printf 'suite=%s\ndevice=%s\n' "$SUITE_DIR" "${DEVICE:-auto-select}" > "$COMMANDS_LOG"
cp "$ROOT_DIR/docs/CAT_UI_CHECKLIST.md" "$MAPPING_COPY" 2>/dev/null || true

html_report() {
    local result="$1"
    local exit_code="$2"
    {
        printf '%s\n' '<!doctype html>' '<html lang="en"><head><meta charset="utf-8">'
        printf '%s\n' '<title>DarkCat CAT UI Maestro report</title>'
        printf '%s\n' '<style>body{font:15px sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem}code,pre{background:#f3f4f6;padding:.2rem .4rem}li{margin:.35rem 0}</style></head><body>'
        printf '<h1>DarkCat CAT UI Maestro v0.1</h1>\n'
        printf '<p><strong>Result:</strong> %s<br><strong>Run ID:</strong> <code>%s</code><br><strong>Device:</strong> <code>%s</code><br><strong>Exit code:</strong> %s</p>\n' "$result" "$RUN_ID" "${DEVICE:-not selected}" "$exit_code"
        printf '%s\n' '<h2>Artifacts</h2><ul>'
        printf '<li><a href="cat-ui-junit.xml">JUnit XML</a></li>\n'
        printf '<li><a href="console.log">Maestro console log</a></li>\n'
        printf '<li><a href="commands.txt">Runner command metadata</a></li>\n'
        printf '<li><a href="CAT_UI_CHECKLIST.md">36-check mapping</a></li>\n'
        printf '%s\n' '<li><code>maestro/</code> contains screenshots, logs and command metadata when the CLI produced them.</li></ul>'
        printf '%s\n' '<h2>Interpretation</h2><p>Hardware-only checks remain PARTIAL or MANUAL unless the captured evidence proves the requested behavior on the named device. This report is a run summary, not a dashboard.</p>'
        printf '%s\n' '</body></html>'
    } > "$HTML_REPORT"
}

write_junit() {
    local result="$1"
    local message="$2"
    if [[ "$result" == "PASS" ]]; then
        cat > "$JUNIT_REPORT" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="darkcat-cat-ui" tests="7" failures="0" errors="0" skipped="0">
  <testcase classname="darkcat.cat.ui" name="maestro-suite" />
</testsuite>
EOF
    else
        local escaped
        escaped="${message//&/&amp;}"
        escaped="${escaped//</&lt;}"
        escaped="${escaped//>/&gt;}"
        cat > "$JUNIT_REPORT" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="darkcat-cat-ui" tests="1" failures="1" errors="0" skipped="0">
  <testcase classname="darkcat.cat.ui" name="runner-preflight"><failure message="${escaped}" /></testcase>
</testsuite>
EOF
    fi
}

blocked() {
    local message="$1"
    printf 'BLOCKED: %s\n' "$message" | tee -a "$CONSOLE_LOG" >&2
    write_junit BLOCKED "$message"
    html_report BLOCKED 2
    exit 2
}

if ! command -v adb >/dev/null 2>&1; then
    blocked "Android SDK platform-tools/adb is not installed or not on PATH"
fi

if [[ -z "$DEVICE" ]]; then
    mapfile -t connected_devices < <(adb devices | awk '$2 == "device" {print $1}')
    if [[ ${#connected_devices[@]} -eq 0 ]]; then
        blocked "no connected Android device; attach a device or start an emulator and pass --device"
    fi
    if [[ ${#connected_devices[@]} -gt 1 ]]; then
        blocked "multiple connected devices; rerun with --device DEVICE_ID"
    fi
    DEVICE="${connected_devices[0]}"
fi

if ! adb -s "$DEVICE" get-state 2>/dev/null | grep -qx 'device'; then
    blocked "adb device is not in the ready state: $DEVICE"
fi

if [[ -n "$APK" ]]; then
    [[ -f "$APK" ]] || blocked "APK path does not exist"
    printf 'install apk on selected device\n' >> "$COMMANDS_LOG"
    if ! adb -s "$DEVICE" install -r "$APK" >> "$CONSOLE_LOG" 2>&1; then
        blocked "adb install failed"
    fi
fi

if ! command -v maestro >/dev/null 2>&1; then
    blocked "Maestro CLI is not installed or not on PATH (Java 17+ is required by Maestro)"
fi

if [[ ! -d "$SUITE_DIR" ]]; then
    blocked "missing checked-in Maestro suite: $SUITE_DIR"
fi

MAESTRO_HELP="$(MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true maestro test --help 2>&1 || true)"
MAESTRO_CMD=(maestro test "$SUITE_DIR" --device "$DEVICE")
if grep -q -- '--format' <<< "$MAESTRO_HELP"; then
    MAESTRO_CMD+=(--format junit)
fi
if grep -q -- '--output' <<< "$MAESTRO_HELP"; then
    MAESTRO_CMD+=(--output "$JUNIT_REPORT")
fi
if grep -q -- '--test-output-dir' <<< "$MAESTRO_HELP"; then
    MAESTRO_CMD+=(--test-output-dir "$MAESTRO_ARTIFACT_DIR")
elif grep -q -- '--debug-output' <<< "$MAESTRO_HELP"; then
    MAESTRO_CMD+=(--debug-output "$MAESTRO_ARTIFACT_DIR")
fi
printf 'maestro test suite on selected device; output paths are under build/cat-ui/%s\n' "$RUN_ID" >> "$COMMANDS_LOG"

set +e
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true "${MAESTRO_CMD[@]}" 2>&1 | tee -a "$CONSOLE_LOG"
MAESTRO_EXIT=${PIPESTATUS[0]}
set -e

if [[ ! -s "$JUNIT_REPORT" ]]; then
    if [[ "$MAESTRO_EXIT" -eq 0 ]]; then
        write_junit PASS ""
    else
        write_junit FAILED "Maestro exited with code $MAESTRO_EXIT"
    fi
fi

if [[ "$MAESTRO_EXIT" -eq 0 ]]; then
    html_report PASS 0
    exit 0
fi

html_report FAILED "$MAESTRO_EXIT"
exit "$MAESTRO_EXIT"
