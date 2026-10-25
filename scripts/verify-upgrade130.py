"""Compare all rows on a dedicated emulator before/after an overwrite install."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'docs/upgrade130'
out.mkdir(parents=True, exist_ok=True)
stage = sys.argv[1]
assert stage in ('before', 'after')

def adb(*args):
    return subprocess.check_output([str(root / '.tools/sdk/platform-tools/adb.exe'), '-s', 'emulator-5554', *args], stderr=subprocess.STDOUT)

adb('shell', 'am', 'force-stop', 'cn.myrecipe.app')
db = out / f'{stage}.db'
db.write_bytes(adb('exec-out', 'run-as', 'cn.myrecipe.app', 'cat', 'databases/my-recipe.db'))
for suffix in ('-wal', '-shm'):
    try:
        db.with_name(db.name + suffix).write_bytes(adb('exec-out', 'run-as', 'cn.myrecipe.app', 'cat', 'databases/my-recipe.db' + suffix))
    except subprocess.CalledProcessError:
        pass
with sqlite3.connect(db) as con:
    data = {name: [list(row) for row in sorted(con.execute(f'SELECT * FROM {name}').fetchall(), key=repr)]
            for name in ('recipes', 'ingredients', 'steps', 'categories', 'tags', 'recipe_tags', 'shopping', 'active_timer')}
(out / f'{stage}.json').write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
if stage == 'after':
    assert data == json.loads((out / 'before.json').read_text(encoding='utf-8')), 'Upgrade changed data'
    print('All database rows preserved during 1.2.0 -> 1.3.0 overwrite')
print({name: len(rows) for name, rows in data.items()})
