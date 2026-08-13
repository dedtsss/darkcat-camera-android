#!/usr/bin/env bash
set -u -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUITE_DIR="$ROOT_DIR/.maestro/flows"
HARDWARE_DIR="$ROOT_DIR/.maestro/hardware"
APP_ID="ru.darkcat.camera"
RUN_ID="${CAT_UI_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
DEVICE="${MAESTRO_DEVICE_ID:-}"
DEVICE_WAS_EXPLICIT=false
APK=""
FRESH_INSTALL=false
PIXEL7_FIELD=false
PIXEL7_FIELD_LOCK=false

usage() {
    cat <<'EOF'
Usage: scripts/run-cat-ui.sh [--device DEVICE_ID] [--apk PATH] [--run-id ID]
                             [--fresh-install] [--pixel7-field]
                             [--pixel7-field-lock]

Runs CAT UI Maestro flows on one selected Android device.

--apk PATH            install this APK before the run with adb install -r.
--fresh-install       opt in to adb uninstall + clean install of ru.darkcat.camera.
                      This deletes all DarkCat app data on the selected device and requires --apk.
--pixel7-field        opt in to the real-hardware Pixel 7 Field Mode flows. Requires an
                      explicit --device whose reported model is exactly "Pixel 7".
--pixel7-field-lock   additionally sends power/Volume+ ADB key events while locked, then
                      waits for the operator to unlock normally. Requires --pixel7-field.

The default run never clears DarkCat app data and never runs Field Mode hardware checks.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            DEVICE="$2"
            DEVICE_WAS_EXPLICIT=true
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
        --fresh-install)
            FRESH_INSTALL=true
            shift
            ;;
        --pixel7-field)
            PIXEL7_FIELD=true
            shift
            ;;
        --pixel7-field-lock)
            PIXEL7_FIELD_LOCK=true
            shift
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
if [[ "$FRESH_INSTALL" == true && -z "$APK" ]]; then
    echo "--fresh-install requires --apk" >&2
    exit 64
fi
if [[ "$PIXEL7_FIELD_LOCK" == true && "$PIXEL7_FIELD" != true ]]; then
    echo "--pixel7-field-lock requires --pixel7-field" >&2
    exit 64
fi
if [[ "$PIXEL7_FIELD" == true && "$DEVICE_WAS_EXPLICIT" != true ]]; then
    echo "--pixel7-field requires an explicit --device PIXEL_7_ADB_SERIAL" >&2
    exit 64
fi

ARTIFACT_DIR="$ROOT_DIR/build/cat-ui/$RUN_ID"
MAESTRO_ARTIFACT_DIR="$ARTIFACT_DIR/maestro"
CONSOLE_LOG="$ARTIFACT_DIR/console.log"
COMMANDS_LOG="$ARTIFACT_DIR/commands.txt"
FLOW_RESULTS="$ARTIFACT_DIR/flow-results.tsv"
ACTUALS="$ARTIFACT_DIR/actuals.tsv"
mkdir -p "$MAESTRO_ARTIFACT_DIR"
printf 'CAT UI run %s\n' "$RUN_ID" > "$CONSOLE_LOG"
printf 'suite=%s\ndevice=%s\nfresh_install=%s\npixel7_field=%s\npixel7_field_lock=%s\n' \
    "$SUITE_DIR" "${DEVICE:-auto-select}" "$FRESH_INSTALL" "$PIXEL7_FIELD" "$PIXEL7_FIELD_LOCK" > "$COMMANDS_LOG"
printf 'flow\tstatus\texit_code\n' > "$FLOW_RESULTS"
printf 'check\tverdict\tactual\n' > "$ACTUALS"
cp "$ROOT_DIR/docs/CAT_UI_CHECKLIST.md" "$ARTIFACT_DIR/CAT_UI_CHECKLIST.md" 2>/dev/null || true

write_results() {
    local overall="$1"
    if command -v python3 >/dev/null 2>&1; then
        local -a result_command=(
            python3 "$ROOT_DIR/scripts/cat_ui_results.py"
            --artifact-dir "$ARTIFACT_DIR"
            --run-id "$RUN_ID"
            --device "${DEVICE:-not-selected}"
            --overall "$overall"
            --flow-results "$FLOW_RESULTS"
            --actuals "$ACTUALS"
        )
        if [[ "$PIXEL7_FIELD" == true ]]; then result_command+=(--pixel7-field); fi
        if [[ "$FRESH_INSTALL" == true ]]; then result_command+=(--fresh-install); fi
        "${result_command[@]}" >> "$CONSOLE_LOG" 2>&1
    else
        printf 'Unable to write per-check reports: python3 is not available\n' >> "$CONSOLE_LOG"
    fi
}

blocked() {
    local message="$1"
    printf 'BLOCKED: %s\n' "$message" | tee -a "$CONSOLE_LOG" >&2
    write_results BLOCKED
    exit 2
}

record_flow() {
    local flow="$1"
    local status="$2"
    local exit_code="$3"
    printf '%s\t%s\t%s\n' "$flow" "$status" "$exit_code" >> "$FLOW_RESULTS"
}

record_actual() {
    local check="$1"
    local verdict="$2"
    local actual="$3"
    actual="${actual//$'\t'/ }"
    actual="${actual//$'\n'/ }"
    printf '%s\t%s\t%s\n' "$check" "$verdict" "$actual" >> "$ACTUALS"
}

if ! command -v adb >/dev/null 2>&1; then
    blocked "Android SDK platform-tools/adb is not installed or not on PATH"
fi
if ! command -v maestro >/dev/null 2>&1; then
    blocked "Maestro CLI is not installed or not on PATH (Java 17+ is required by Maestro)"
fi
if [[ ! -d "$SUITE_DIR" || ! -d "$HARDWARE_DIR" ]]; then
    blocked "missing checked-in Maestro suite"
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

ADB=(adb -s "$DEVICE")
if ! "${ADB[@]}" get-state 2>/dev/null | grep -qx 'device'; then
    blocked "adb device is not in the ready state: $DEVICE"
fi

DEVICE_MODEL="$("${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r' | head -n 1)"
DEVICE_ANDROID="$("${ADB[@]}" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' | head -n 1)"
printf 'serial=%s\nmodel=%s\nandroid=%s\n' "$DEVICE" "$DEVICE_MODEL" "$DEVICE_ANDROID" > "$ARTIFACT_DIR/device-info.txt"
printf 'selected_device=%s\nmodel=%s\nandroid=%s\n' "$DEVICE" "$DEVICE_MODEL" "$DEVICE_ANDROID" >> "$COMMANDS_LOG"

if [[ "$PIXEL7_FIELD" == true && "$DEVICE_MODEL" != "Pixel 7" ]]; then
    blocked "--pixel7-field requires ro.product.model exactly 'Pixel 7'; selected device reports '$DEVICE_MODEL'"
fi

if [[ -n "$APK" ]]; then
    [[ -f "$APK" ]] || blocked "APK path does not exist"
    if [[ "$FRESH_INSTALL" == true ]]; then
        printf 'fresh install requested: uninstalling %s (deletes its app data)\n' "$APP_ID" >> "$COMMANDS_LOG"
        if "${ADB[@]}" shell pm path "$APP_ID" 2>/dev/null | grep -q '^package:'; then
            if ! "${ADB[@]}" uninstall "$APP_ID" >> "$CONSOLE_LOG" 2>&1; then
                blocked "adb uninstall failed; refusing a false fresh-install result"
            fi
        fi
        if ! "${ADB[@]}" install "$APK" >> "$CONSOLE_LOG" 2>&1; then
            blocked "adb clean install failed"
        fi
    else
        printf 'preserving app data: adb install -r\n' >> "$COMMANDS_LOG"
        if ! "${ADB[@]}" install -r "$APK" >> "$CONSOLE_LOG" 2>&1; then
            blocked "adb install -r failed"
        fi
    fi
fi

MAESTRO_HELP="$(MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true maestro test --help 2>&1 || true)"

run_flow() {
    local flow_path="$1"
    local flow_name
    flow_name="$(basename "${flow_path%.yaml}")"
    local flow_artifact_dir="$MAESTRO_ARTIFACT_DIR/$flow_name"
    mkdir -p "$flow_artifact_dir"
    local -a command=(maestro test "$flow_path" --device "$DEVICE")
    if grep -q -- '--format' <<< "$MAESTRO_HELP"; then command+=(--format junit); fi
    if grep -q -- '--output' <<< "$MAESTRO_HELP"; then command+=(--output "$flow_artifact_dir/junit.xml"); fi
    if grep -q -- '--test-output-dir' <<< "$MAESTRO_HELP"; then
        command+=(--test-output-dir "$flow_artifact_dir")
    elif grep -q -- '--debug-output' <<< "$MAESTRO_HELP"; then
        command+=(--debug-output "$flow_artifact_dir")
    fi
    printf 'maestro flow=%s device=%s\n' "$flow_name" "$DEVICE" >> "$COMMANDS_LOG"
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
        "${command[@]}" 2>&1 | tee -a "$CONSOLE_LOG"
    local exit_code=${PIPESTATUS[0]}
    if [[ "$exit_code" -eq 0 ]]; then
        record_flow "$flow_name" PASS "$exit_code"
    else
        record_flow "$flow_name" FAIL "$exit_code"
    fi
    return "$exit_code"
}

sequence_number() {
    "${ADB[@]}" exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import html, re, sys
for node in re.findall(r"<node\\b[^>]*>", sys.stdin.read()):
    if "ru.darkcat.camera:id/cat_ui_sequence" not in node:
        continue
    match = re.search(r"\\btext=\\\"([^\\\"]*)\\\"", node)
    if match:
        text = html.unescape(match.group(1))
        number = re.search(r"№(\\d+)", text)
        if number:
            print(number.group(1))
    break
' 2>/dev/null || true
}

run_measured_flow() {
    local flow_path="$1"
    local check="$2"
    local minimum_increment="$3"
    local before after exit_code
    before="$(sequence_number)"
    run_flow "$flow_path"
    exit_code=$?
    after="$(sequence_number)"
    if [[ "$before" =~ ^[0-9]+$ && "$after" =~ ^[0-9]+$ ]]; then
        if (( after - before >= minimum_increment )); then
            record_actual "$check" PASS "CAT sequence changed from $before to $after"
        else
            record_actual "$check" FAIL "expected sequence increase of at least $minimum_increment; actual $before to $after"
        fi
    else
        record_actual "$check" PARTIAL "CAT sequence was not machine-readable before and after the flow"
    fi
    return "$exit_code"
}

run_lock_sequence() {
    local before after
    before="$(sequence_number)"
    printf 'Pixel 7 lock sequence: sending KEYCODE_POWER, waiting 30 seconds, then KEYCODE_VOLUME_UP while locked\n' >> "$COMMANDS_LOG"
    if ! "${ADB[@]}" shell input keyevent 26 >> "$CONSOLE_LOG" 2>&1; then
        record_flow pixel7-lock FAIL 1
        record_actual 28 PARTIAL "ADB could not send the lock key event"
        return 1
    fi
    sleep 30
    if ! "${ADB[@]}" shell input keyevent 24 >> "$CONSOLE_LOG" 2>&1; then
        record_flow pixel7-lock FAIL 1
        record_actual 28 PARTIAL "ADB could not send Volume+ while the phone was locked"
        return 1
    fi
    sleep 2
    after="$(sequence_number)"
    "${ADB[@]}" shell input keyevent 26 >> "$CONSOLE_LOG" 2>&1 || true
    record_flow pixel7-lock PASS 0
    record_actual 27 PARTIAL "ADB sent KEYCODE_POWER and kept the device locked for 30 seconds; normal unlock remains operator-observed"
    if [[ "$before" =~ ^[0-9]+$ && "$after" =~ ^[0-9]+$ && $after -gt $before ]]; then
        record_actual 28 PARTIAL "CAT sequence changed from $before to $after after locked Volume+; haptic remains unverified"
    else
        record_actual 28 PARTIAL "Volume+ was sent while locked; capture cannot be confirmed until the post-unlock flow or a test-created artifact is observed"
    fi
    printf 'Unlock the Pixel 7 normally; no credential is requested or automated. Maestro will wait for the CAT UI to return.\n' | tee -a "$CONSOLE_LOG"
    return 0
}

DEFAULT_FLOWS=(
    "$SUITE_DIR/01-launch-main.yaml"
    "$SUITE_DIR/02-storage-gps.yaml"
    "$SUITE_DIR/03-settings.yaml"
    "$SUITE_DIR/04-gallery-mode.yaml"
    "$SUITE_DIR/04-vault-mode.yaml"
    "$SUITE_DIR/05-lens-rotate-stamp.yaml"
    "$SUITE_DIR/06-field-mode.yaml"
    "$SUITE_DIR/07-burst.yaml"
)

suite_failed=false
for flow_path in "${DEFAULT_FLOWS[@]}"; do
    case "$(basename "$flow_path")" in
        04-gallery-mode.yaml) run_measured_flow "$flow_path" 11 1 || suite_failed=true ;;
        04-vault-mode.yaml) run_measured_flow "$flow_path" 13 1 || suite_failed=true ;;
        07-burst.yaml) run_measured_flow "$flow_path" 36 3 || suite_failed=true ;;
        *) run_flow "$flow_path" || suite_failed=true ;;
    esac
done

if [[ "$PIXEL7_FIELD" == true ]]; then
    run_flow "$HARDWARE_DIR/field-enable-gps.yaml" || suite_failed=true
    if [[ "$PIXEL7_FIELD_LOCK" == true ]]; then
        run_lock_sequence || suite_failed=true
        run_flow "$HARDWARE_DIR/field-return.yaml" || suite_failed=true
    fi
    run_measured_flow "$HARDWARE_DIR/field-volume.yaml" 25 1 || suite_failed=true
    run_flow "$HARDWARE_DIR/field-off-ownership.yaml" || suite_failed=true
    run_flow "$HARDWARE_DIR/field-notification-start.yaml" || suite_failed=true
    if "${ADB[@]}" shell cmd statusbar expand-notifications >> "$CONSOLE_LOG" 2>&1; then
        run_flow "$HARDWARE_DIR/field-notification.yaml" || suite_failed=true
    else
        record_flow field-notification FAIL 1
        suite_failed=true
    fi
fi

if [[ "$suite_failed" == true ]]; then
    write_results FAILED
    exit 1
fi
write_results PASS
