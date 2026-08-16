#!/usr/bin/env python3
"""Write the required local CAT UI per-check results; this is reporting, not a test engine."""

from __future__ import annotations

import argparse
import html
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Tuple
from xml.etree import ElementTree as ET


def check(identifier: str, kind: str, flows: Iterable[str], action: str, expected: str,
          boundary: str, evidence: Iterable[str] = ()) -> dict:
    return {
        "id": identifier,
        "kind": kind,
        "flows": list(flows),
        "action": action,
        "expected": expected,
        "boundary": boundary,
        "evidence": list(evidence),
    }


CHECKS = [
    check("01", "fresh-auto", ["01-launch-main"], "Установить APK и запустить", "После clean install DarkCat UI открывается без старого стартового окна", "Без --fresh-install запуск сохраняет app data и остаётся PARTIAL.", ["01-launch-main"]),
    check("02", "partial", ["01-launch-main"], "Проверить main screen", "Верхняя и нижняя CAT-панели доступны", "Читаемость и отсутствие наложений требуют визуального review.", ["02-main"]),
    check("03", "manual", ["01-launch-main"], "Сделать Android/system screenshot main screen", "Системный screenshot работает", "Maestro takeScreenshot не доказывает поведение Android/system screenshot.", ["03-maestro-main"]),
    check("04", "partial", ["02-storage-gps"], "Переключить Vault/Gallery", "Видимая storage-кнопка переключает режим", "Семантика цвета и persistence требуют device evidence.", ["04-storage-toggle"]),
    check("05", "partial", ["02-storage-gps"], "Открыть GPS panel", "Видны owner, accuracy и state", "Реальная точность и работа Locker требуют location-enabled device.", ["05-gps-panel"]),
    check("06", "partial", ["02-storage-gps"], "Включить persistent GPS", "Панель показывает действие persistent Locker", "Старт без Settings и обновление точности требуют device evidence."),
    check("07", "manual", [], "Наблюдать GNSS near window/outside", "Accuracy меняется во времени", "Нужна реальная GNSS-сессия."),
    check("08", "partial", ["02-storage-gps"], "Выключить persistent GPS", "GPS panel закрывается без ухода из камеры", "Остановка Locker и ordinary-camera ownership требуют device evidence."),
    check("09", "partial", ["03-settings"], "Открыть Capture settings", "Настройки съёмки доступны", "Clipping/layout и readability требуют визуального review.", ["09-settings-capture"]),
    check("10", "partial", ["03-settings"], "Открыть photo resolution", "Reachable resolution control", "Размеры и persistence зависят от реальной камеры."),
    check("11", "partial", ["04-gallery-mode"], "Явно установить Gallery mode и снять кадр", "Новый CAT sequence увеличивается и viewer доступен", "Внешняя MediaStore Gallery/Pictures/DarkCat проверяется на устройстве.", ["11-gallery-mode", "12-gallery-viewer", "15-gallery-screen"]),
    check("12", "partial", ["04-gallery-mode"], "Открыть gallery-mode last shot", "Viewer показывает Edit, Share и Delete", "Swipe и destructive Delete не выполняются автоматически.", ["12-gallery-viewer"]),
    check("13", "partial", ["04-vault-mode"], "Явно установить Vault mode и снять кадр", "Новый CAT sequence увеличивается и Vault viewer доступен", "Отсутствие в system gallery проверяется на устройстве.", ["13-vault-mode", "13-vault-viewer", "15-vault-gallery-screen"]),
    check("14", "partial", ["04-vault-mode"], "Открыть Vault viewer", "Vault viewer показывает Share", "Android chooser и доступ receiving app требуют device evidence.", ["13-vault-viewer"]),
    check("15", "manual", ["03-settings", "04-gallery-mode", "04-vault-mode"], "Сделать Android/system screenshots Settings/Gallery/Viewer", "Системные screenshots работают на всех экранах", "Maestro screenshots доказывают навигацию, но не Android/system screenshot behavior.", ["09-settings-capture", "15-gallery-screen", "15-vault-gallery-screen"]),
    check("16", "partial", ["05-lens-rotate-stamp"], "Открыть Lens selector", "Нет raw Camera ID label", "Понятность реальных lens names требует review.", ["16-lenses"]),
    check("17", "partial", ["05-lens-rotate-stamp"], "Переключить доступные rear lenses", "Capability lens UI reachable", "Последовательное переключение без hang требует physical camera."),
    check("18", "partial", ["05-lens-rotate-stamp"], "Просмотреть zoom presets", "Zoom-capable UI reachable", "Полезность значений проверяется на устройстве."),
    check("19", "manual", [], "Проверить sub-1x", "Реальный угол шире, не digital crop", "Нужна физическая камера."),
    check("20", "partial", ["05-lens-rotate-stamp"], "Повернуть portrait-landscape-portrait", "CAT chrome остаётся visible", "Preview crop/black field требует review.", ["20-portrait", "20-landscape"]),
    check("21", "partial", ["05-lens-rotate-stamp"], "Снять technical stamp evidence", "Post-shutter stamp evidence captured", "Координаты, accuracy, sequence и frame placement требуют review.", ["21-technical-stamp"]),
    check("22", "hardware-auto", ["field-enable-gps"], "Включить Field Mode на Pixel 7", "Field active и Field-owned GPS Locker visible", "PASS возможен только в opt-in run на проверенном Pixel 7.", ["22-field-active"]),
    check("23", "hardware-auto", ["field-enable-gps"], "Проверить Field GPS owner и user toggle", "Owner остаётся Field Mode, пока user request включается/выключается", "PASS возможен только в opt-in run на проверенном Pixel 7.", ["23-field-gps-owner", "23-field-user-gps"]),
    check("24", "manual", [], "Ждать accuracy < 7m", "Field ready for capture", "Нужны реальные GNSS conditions."),
    check("25", "partial", ["field-volume"], "Нажать Volume+ в Field Mode", "CAT sequence увеличивается после Volume+", "Физический haptic остаётся manual.", ["25-volume-up"]),
    check("26", "manual", [], "Проверить strict GPS below 7m", "Capture blocked и fail haptic distinct", "GNSS quality и haptic требуют hardware observation."),
    check("27", "manual", ["pixel7-lock"], "Заблокировать телефон на 30-60s", "Устройство остаётся lockscreen", "Факт lockscreen и выдержка требуют operator observation."),
    check("28", "partial", ["pixel7-lock", "field-return"], "Нажать Volume+ while locked", "Событие отправлено и return flow доступен", "Locked capture и haptic не становятся PASS без physical evidence."),
    check("29", "partial", ["field-return"], "Разблокировать и вернуться в камеру", "CAT UI и Field state возвращаются", "GPS warm state/non-zero требует physical evidence.", ["29-field-return"]),
    check("30", "hardware-auto", ["field-off-ownership"], "Выключить Field Mode без user GPS", "Field off и GPS Locker выключен", "PASS возможен только в opt-in run на проверенном Pixel 7.", ["30-field-off"]),
    check("31", "hardware-auto", ["field-off-ownership"], "Field ON + user GPS ON + Field OFF", "User-owned persistent GPS остаётся visible, затем test restores it", "PASS возможен только в opt-in run на проверенном Pixel 7.", ["31-user-gps-survives"]),
    check("32", "hardware-auto", ["field-notification-start", "field-notification"], "Нажать notification Stop all", "Field и GPS Locker выключены", "PASS возможен только при доступном notification UI Pixel 7.", ["32-stop-all"]),
    check("33", "partial", ["field-notification-start", "field-notification"], "Проверить Field notification", "Visible notification не содержит ложного recovery текста", "Точность runtime camera/GPS state требует hardware evidence.", ["33-field-notification"]),
    check("34", "partial", ["06-field-mode"], "Открыть Field settings", "Haptic presets и test buttons visible", "Субъективная сила haptic manual.", ["34-haptics-settings"]),
    check("35", "partial", ["03-settings"], "Проверить OEM Night gate", "Capability-gated setting visible", "Реальная capability/unavailable semantics device-specific."),
    check("36", "partial", ["07-burst"], "Снять три кадра rapidly", "CAT sequence увеличивается минимум на три", "UI stability and real camera hang remain device evidence.", ["36-burst-stability"]),
]


def read_flow_results(path: Path) -> Dict[str, Tuple[str, str]]:
    values: Dict[str, Tuple[str, str]] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines()[1:]:
        parts = line.split("\t")
        if len(parts) >= 3:
            values[parts[0]] = (parts[1], parts[2])
    return values


def read_actuals(path: Path) -> Dict[str, Tuple[str, str]]:
    values: Dict[str, Tuple[str, str]] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines()[1:]:
        parts = line.split("\t", 2)
        if len(parts) == 3:
            values[parts[0]] = (parts[1], parts[2])
    return values


def evidence_paths(root: Path, stems: List[str]) -> List[str]:
    result: List[str] = []
    for stem in stems:
        matches = sorted(root.rglob(stem + ".png"))
        if matches:
            result.append(matches[0].relative_to(root).as_posix())
    return result


def evaluate(item: dict, flows: Dict[str, Tuple[str, str]], actuals: Dict[str, Tuple[str, str]], root: Path,
             fresh_install: bool) -> dict:
    flow_names = item["flows"]
    flow_states = [flows[name] for name in flow_names if name in flows]
    status = ""
    actual = ""
    reason = ""
    kind = item["kind"]
    if kind == "manual":
        status = "MANUAL"
        actual = "Relevant automation was not executed." if not flow_states else "Relevant UI navigation executed; the required Android/hardware observation was not automatic."
        reason = item["boundary"]
    elif not flow_states:
        status = "BLOCKED"
        reason = "Required flow was not executed" + (" (opt-in Pixel 7 flow)." if kind == "hardware-auto" else ".")
    elif any(state == "FAIL" for state, _ in flow_states):
        status = "FAIL"
        failed = [f"{name} (exit {code})" for name, (state, code) in flows.items() if name in flow_names and state == "FAIL"]
        reason = "Maestro flow failed: " + ", ".join(failed)
    else:
        status = "PASS" if kind in ("auto", "hardware-auto") else "PARTIAL"
        actual = "All associated Maestro flows completed."
        if kind == "fresh-auto" and fresh_install:
            status = "PASS"
        elif kind == "fresh-auto":
            reason = "Clean install was not selected; default install -r and clearState:false preserve app data."
        elif kind == "partial":
            reason = item["boundary"]

    observed = actuals.get(item["id"])
    if observed:
        observed_status, observed_actual = observed
        actual = observed_actual
        if observed_status == "FAIL":
            status = "FAIL"
            reason = observed_actual
        elif observed_status == "BLOCKED":
            status = "BLOCKED"
            reason = observed_actual
        elif observed_status == "PARTIAL" and status == "PASS":
            status = "PARTIAL"
            reason = item["boundary"]

    evidence = evidence_paths(root, item["evidence"])
    if status in ("PASS", "PARTIAL") and item["evidence"] and len(evidence) != len(item["evidence"]):
        status = "FAIL"
        missing = sorted(set(item["evidence"]) - {Path(path).stem for path in evidence})
        reason = "Required evidence missing: " + ", ".join(missing)
    return {
        "id": item["id"],
        "status": status,
        "action": item["action"],
        "expected": item["expected"],
        "actual": actual or None,
        "evidence_path": evidence,
        "reason": reason or None,
        "classification": kind,
    }


def write_junit(root: Path, results: List[dict]) -> None:
    failures = sum(result["status"] == "FAIL" for result in results)
    errors = sum(result["status"] == "BLOCKED" for result in results)
    skipped = sum(result["status"] in ("PARTIAL", "MANUAL") for result in results)
    suite = ET.Element("testsuite", name="darkcat-cat-ui", tests=str(len(results)), failures=str(failures), errors=str(errors), skipped=str(skipped))
    for result in results:
        case = ET.SubElement(suite, "testcase", classname="darkcat.cat.ui", name="CAT-" + result["id"])
        if result["status"] == "FAIL":
            ET.SubElement(case, "failure", message=result["reason"] or "check failed").text = result["actual"] or ""
        elif result["status"] == "BLOCKED":
            ET.SubElement(case, "error", message=result["reason"] or "check blocked")
        elif result["status"] in ("PARTIAL", "MANUAL"):
            ET.SubElement(case, "skipped", message=result["reason"] or result["status"])
    ET.ElementTree(suite).write(root / "cat-ui-junit.xml", encoding="utf-8", xml_declaration=True)


def write_markdown(root: Path, run_id: str, device: str, overall: str, results: List[dict]) -> None:
    lines = ["# DarkCat CAT UI per-check results", "", f"Run: `{run_id}`", f"Device: `{device}`", f"Suite: **{overall}**", "", "## Ready list", ""]
    for result in results:
        suffix = result["evidence_path"][0] if result["evidence_path"] else result["reason"]
        lines.append(f"{result['id']} {result['status']}" + (f" — {suffix}" if suffix else ""))
    lines.extend(["", "## Details", "", "| ID | Status | Action | Expected | Actual | Evidence | Reason |", "|---:|:---:|---|---|---|---|---|"])
    for result in results:
        evidence = "<br>".join(result["evidence_path"]) or "—"
        lines.append("| {id} | {status} | {action} | {expected} | {actual} | {evidence} | {reason} |".format(
            id=result["id"], status=result["status"], action=result["action"], expected=result["expected"],
            actual=result["actual"] or "—", evidence=evidence, reason=result["reason"] or "—"))
    (root / "cat-ui-results.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_html(root: Path, run_id: str, device: str, overall: str, results: List[dict]) -> None:
    rows = []
    for result in results:
        evidence = "<br>".join(f'<a href="{html.escape(path)}">{html.escape(path)}</a>' for path in result["evidence_path"]) or "—"
        cells = [result["id"], result["status"], result["action"], result["expected"], result["actual"] or "—", evidence, result["reason"] or "—"]
        rows.append("<tr>" + "".join(f"<td>{cell if index == 5 else html.escape(cell)}</td>" for index, cell in enumerate(cells)) + "</tr>")
    document = (
        '<!doctype html><html lang="en"><head><meta charset="utf-8"><title>DarkCat CAT UI results</title>'
        '<style>body{font:15px system-ui,sans-serif;margin:2rem;line-height:1.4}'
        'table{border-collapse:collapse;width:100%}td,th{border:1px solid #cbd5e1;padding:.45rem;vertical-align:top}'
        'th{background:#f1f5f9}code{background:#f1f5f9;padding:.1rem .25rem}</style></head><body>'
        '<h1>DarkCat CAT UI per-check results</h1><p>Run <code>' + html.escape(run_id)
        + '</code><br>Device <code>' + html.escape(device) + '</code><br>Suite <strong>'
        + html.escape(overall) + '</strong></p>'
        '<p><a href="cat-ui-results.md">Markdown ready list</a> · <a href="cat-ui-results.json">JSON</a> · '
        '<a href="cat-ui-junit.xml">JUnit</a></p>'
        '<table><thead><tr><th>ID</th><th>Status</th><th>Action</th><th>Expected</th><th>Actual</th><th>Evidence</th><th>Reason</th></tr></thead><tbody>'
        + "\n".join(rows) + '</tbody></table></body></html>'
    )
    (root / "cat-ui-report.html").write_text(document, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--device", required=True)
    parser.add_argument("--overall", required=True, choices=["PASS", "FAILED", "BLOCKED"])
    parser.add_argument("--flow-results", required=True)
    parser.add_argument("--actuals", required=True)
    parser.add_argument("--pixel7-field", action="store_true")
    parser.add_argument("--fresh-install", action="store_true")
    args = parser.parse_args()
    root = Path(args.artifact_dir)
    flows = read_flow_results(Path(args.flow_results))
    actuals = read_actuals(Path(args.actuals))
    results = [evaluate(item, flows, actuals, root, args.fresh_install) for item in CHECKS]
    payload = {
        "run_id": args.run_id,
        "device": args.device,
        "suite_status": args.overall,
        "pixel7_field_opt_in": args.pixel7_field,
        "fresh_install_opt_in": args.fresh_install,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "checks": results,
    }
    (root / "cat-ui-results.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_markdown(root, args.run_id, args.device, args.overall, results)
    write_html(root, args.run_id, args.device, args.overall, results)
    write_junit(root, results)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
