#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
LAYOUT = RES / "layout/activity_main.xml"
DIMENS = RES / "values/dimens.xml"
MAIN = ROOT / "app/src/main/java/com/darkcat/camera/MainActivity.java"

for xml in [LAYOUT, DIMENS, RES / "values/strings.xml", RES / "values/styles.xml", *sorted((RES / "drawable").glob("*.xml"))]:
    ET.parse(xml)

layout = LAYOUT.read_text()
dimens = DIMENS.read_text()
main = MAIN.read_text()

required_ids = ["preview", "status_overlay", "camera_state", "camera_controls", "capture_button", "info_button"]
for view_id in required_ids:
    assert f'@+id/{view_id}' in layout, f"missing view id {view_id}"

assert '@dimen/camera_shutter_size' in layout
assert '@dimen/camera_info_touch_size' in layout
assert re.search(r'<dimen name="camera_shutter_size">(?:[7-9][0-9]|1[0-9]{2})dp</dimen>', dimens), "shutter must be camera-sized dp"
for name in ["camera_info_touch_size", "camera_controls_height"]:
    m = re.search(rf'<dimen name="{name}">(\d+)dp</dimen>', dimens)
    assert m and int(m.group(1)) >= 48, f"{name} must be >=48dp"

assert "new FrameLayout" not in main and "new Button" not in main and "LinearLayout.LayoutParams" not in main, "programmatic geometry returned"
assert "setOnApplyWindowInsetsListener" in main
assert "WindowInsetsCompat.Type.systemBars()" in main
assert "WindowInsetsCompat.Type.displayCutout()" in main
assert "setDecorFitsSystemWindows(getWindow(),false)" in main
assert "R.layout.activity_main" in main
assert "CameraCaptureService.ACTION_CAPTURE" in main
assert "KEYCODE_VOLUME_UP" in main
assert "service.attachPreview(preview)" in main
assert "CameraCaptureService.java" not in "\n".join([])

for raw in re.findall(r'(?:setPadding|setTextSize|LayoutParams)\([^;\n]*\)', main):
    raise AssertionError(f"raw geometry call in MainActivity: {raw}")

print("camera UI static checks PASS")
