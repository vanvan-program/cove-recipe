"""Visual and platform checks on the dedicated Android emulator."""
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'docs/screenshots'
out.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output([str(root / '.tools/sdk/platform-tools/adb.exe'), '-s', 'emulator-5554', *args], stderr=subprocess.STDOUT)

def ui():
    for _ in range(5):
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/qa130.xml')
        if b'ERROR' not in result:
            try:
                return ET.fromstring(adb('shell', 'cat', '/sdcard/qa130.xml'))
            except ET.ParseError:
                pass
        time.sleep(.3)
    raise AssertionError('No UI tree')

def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))

def tap(label):
    for _ in range(4):
        tree = ui()
        nodes = [n for n in tree.iter('node') if n.get('text') == label or n.get('content-desc') == label]
        # Compose's extended FAB text is omitted by this SystemUI accessibility dump.
        if not nodes and label == '添加食材':
            nodes = [n for n in tree.iter('node') if n.get('NAF') == 'true' and n.get('clickable') == 'true' and n.get('class') == 'android.view.View']
        if len(nodes) == 1:
            break
        time.sleep(.3)
    assert len(nodes) == 1, (label, len(nodes))
    x1, y1, x2, y2 = bounds(nodes[0])
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def capture(name):
    tree = ui()
    ET.ElementTree(tree).write(root / '.tools' / f'{name}.xml', encoding='utf-8')
    adb('shell', 'screencap', '-p', '/sdcard/qa130.png')
    (out / f'{name}.png').write_bytes(adb('exec-out', 'cat', '/sdcard/qa130.png'))
    return tree

def seed(seconds=None):
    adb('shell', 'am', 'force-stop', 'cn.myrecipe.app')
    args = ['shell', 'am', 'instrument', '-w', '-e', 'class', 'cn.myrecipe.app.PreviewDeviceTest']
    if seconds is not None:
        args += ['-e', 'timerSeconds', str(seconds)]
    result = adb(*args, 'cn.myrecipe.app.test/androidx.test.runner.AndroidJUnitRunner')
    assert b'OK (1 test)' in result, result

def current_vibration(text):
    return text.split('CurrentVibration:', 1)[1].split('NextVibration:', 1)[0].strip()

def assert_repeating_alarm(text):
    current = current_vibration(text)
    assert all(value in current for value in ('status = running', 'durationMs = -1', 'repeat=0', 'opPkg=cn.myrecipe.app')), current

seed()
adb('shell', 'am', 'start', '-n', 'cn.myrecipe.app/.MainActivity')
tap('红烧肉 · 置顶')
capture('130-detail')
tap('开始做饭')
tap('查看食材')
capture('130-ingredients')
tap('返回步骤')
tap('修改')
tree = capture('130-time-wheels')
pickers = [n for n in tree.iter('node') if n.get('class') == 'android.widget.NumberPicker']
assert len(pickers) == 3, ET.tostring(tree, encoding='unicode')
x1, y1, x2, y2 = bounds(pickers[1])
x, cy = (x1+x2)//2, (y1+y2)//2
adb('shell', 'input', 'swipe', str(x), str(cy+70), str(x), str(cy-70), '400')
time.sleep(.5)
tap('确定')
changed = [n.get('text') for n in ui().iter('node') if n.get('text', '').startswith('参考计时')]
assert changed and changed != ['参考计时 01:00'], changed
tap('返回'); tap('返回'); tap('购物清单'); tap('添加食材')
tree = capture('130-add-focus')
assert any(n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true' for n in tree.iter('node'))
adb('shell', 'input', 'keyevent', '4')
tap('取消')
adb('shell', 'appops', 'set', 'cn.myrecipe.app', 'SCHEDULE_EXACT_ALARM', 'allow')
adb('shell', 'pm', 'grant', 'cn.myrecipe.app', 'android.permission.POST_NOTIFICATIONS')
seed(6)
adb('shell', 'am', 'kill', 'cn.myrecipe.app')
pid = subprocess.run([str(root / '.tools/sdk/platform-tools/adb.exe'), '-s', 'emulator-5554', 'shell', 'pidof', 'cn.myrecipe.app'], capture_output=True)
assert not pid.stdout.strip(), 'Background process was not reclaimed before the alarm'
time.sleep(8)
adb('shell', 'cmd', 'statusbar', 'expand-notifications')
tree = capture('130-finished-notification')
assert any(n.get('text') == '确认' for n in tree.iter('node'))
vibration = adb('shell', 'dumpsys', 'vibrator_manager').decode()
(root / 'docs/130-vibration-before.txt').write_text(vibration, encoding='utf-8')
assert_repeating_alarm(vibration)
tap('计时结束')
capture('130-finished-kitchen')
service = adb('shell', 'dumpsys', 'activity', 'services', 'cn.myrecipe.app').decode()
assert 'TimerAlertService' in service and 'isForeground=true' in service, service
later_vibration = adb('shell', 'dumpsys', 'vibrator_manager').decode()
(root / 'docs/130-vibration-later.txt').write_text(later_vibration, encoding='utf-8')
assert_repeating_alarm(later_vibration)
assert re.search(r'createTime = (.+)', vibration).group(1) == re.search(r'createTime = (.+)', later_vibration).group(1)
adb('shell', 'cmd', 'statusbar', 'expand-notifications')
tap('确认')
time.sleep(.5)
stopped = adb('shell', 'dumpsys', 'activity', 'services', 'cn.myrecipe.app').decode()
assert 'TimerAlertService' not in stopped, stopped
after_vibration = adb('shell', 'dumpsys', 'vibrator_manager').decode()
(root / 'docs/130-vibration-after.txt').write_text(after_vibration, encoding='utf-8')
assert current_vibration(after_vibration) == 'null'
adb('shell', 'cmd', 'statusbar', 'collapse')
(root / 'docs/130-qa.json').write_text(json.dumps({'wheelAfterSwipe': changed, 'shoppingNameFocused': True, 'processReclaimedBeforeAlarm': True, 'foregroundServicePersistsAfterOpeningNotification': True, 'acknowledgementStopsService': True}, ensure_ascii=False, indent=2), encoding='utf-8')
print('Visual checks, real wheel swipe, persistent reminder, notification opening and acknowledgement passed.')
