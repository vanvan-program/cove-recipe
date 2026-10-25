"""Verify a real 1.0.0 -> 1.1.0 overwrite on the project's dedicated emulator.
This clears TEST data on the emulator. Never runs against a physical device.
Run after scripts/build.ps1 -Offline -DeviceTests.
"""
import subprocess, sqlite3, time, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ADB=ROOT/'.tools/sdk/platform-tools/adb.exe'
SERIAL='emulator-5554'
PACKAGE='cn.myrecipe.app'
OUT=ROOT/'docs/upgrade110'
OUT.mkdir(parents=True,exist_ok=True)
def adb(*args):
    return subprocess.check_output([str(ADB),'-s',SERIAL,*args],stderr=subprocess.STDOUT)
def text(*args):
    return adb(*args).decode('utf-8',errors='replace')
def capture(stage):
    text('shell','am','force-stop',PACKAGE)
    db=OUT/f'{stage}.db'
    db.write_bytes(adb('exec-out','run-as',PACKAGE,'cat','databases/my-recipe.db'))
    for suffix in ['-wal','-shm']:
        try: db.with_name(db.name+suffix).write_bytes(adb('exec-out','run-as',PACKAGE,'cat','databases/my-recipe.db'+suffix))
        except subprocess.CalledProcessError: pass
    con=sqlite3.connect(db)
    con.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    data={}
    for table in ['recipes','ingredients','steps','categories','tags','recipe_tags','shopping','active_timer']:
        columns=[row[1] for row in con.execute(f'PRAGMA table_info({table})') if row[1]!='preparation']
        data[table]=sorted(con.execute(f'SELECT {",".join(columns)} FROM {table}').fetchall(),key=repr)
    version=con.execute('PRAGMA user_version').fetchone()[0]
    if stage=='after': assert all(row[0]=='' for row in con.execute('SELECT preparation FROM recipes'))
    con.close()
    return version,data
assert SERIAL.startswith('emulator-')
assert '\tdevice' in text('get-state') or text('get-state').strip()=='device'
print(text('shell','pm','clear',PACKAGE).strip())
print(text('install','-r','-d',str(ROOT/'dist/MyRecipe-1.0.0.apk')).strip())
print(text('install','-r',str(ROOT/'.tools/test100.apk')).strip())
seed=text('shell','am','instrument','-w','-e','class','cn.myrecipe.app.PreviewDeviceTest',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner')
assert 'OK (1 test)' in seed,seed
text('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(2)
v1,before=capture('before');assert v1==1 and len(before['recipes'])==2
print(text('install','-r',str(ROOT/'dist/CoveRecipe-1.1.0.apk')).strip())
text('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(3)
v2,after=capture('after');assert v2==2
assert before==after,'Old fields changed during overwrite upgrade'
# Leave the current test APK installed for subsequent visual evidence preparation.
print(text('install','-r',str(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).strip())
result={'from':'1.0.0 / database 1','to':'1.1.0 / database 2','overwrite':'adb install -r','oldFieldsUnchanged':True,'newPreparationEmpty':True,'rows':{k:len(v) for k,v in before.items()}}
(OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
text('shell','am','start','-n',PACKAGE+'/.MainActivity')
