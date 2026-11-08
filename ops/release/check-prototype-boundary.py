"""Static tripwire for the disposable prototype, not a substitute for device tests.

Run from any directory with Python 3. Update only when the approved scope changes.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "android"
NS = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ANDROID / "app/src/main/AndroidManifest.xml").getroot()
permissions = {node.attrib[NS + "name"] for node in manifest.findall("uses-permission")}
assert permissions == {"android.permission.READ_SMS", "android.permission.RECEIVE_SMS"}
application = manifest.find("application")
assert application is not None
assert application.attrib[NS + "allowBackup"] == "false"
assert application.attrib[NS + "usesCleartextTraffic"] == "false"
assert not application.findall("receiver"), "Permission probe must not receive SMS"
assert not application.findall("service"), "Permission probe must not process notifications"

# Deliberately conservative source tripwire: no alert reads, networking, or raw-data logging.
for path in ANDROID.glob("*/src/main/**/*.kt"):
    text = path.read_text()
    for forbidden in (
        r"contentResolver", r"ContentResolver", r"Telephony\.Sms", r"content://sms",
        r"BroadcastReceiver", r"NotificationListenerService", r"WorkManager",
        r"android\.util\.Log", r"\bprintln\s*\(", r"java\.net", r"okhttp",
    ):
        assert not re.search(forbidden, text), f"Forbidden prototype capability in {path}: {forbidden}"
print("Prototype static boundary checks passed (device validation still required).")
