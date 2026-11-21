#!/usr/bin/env bash
set -euo pipefail
adb -e shell wm size 720x1600
adb -e shell wm density 300
adb -e shell input keyevent KEYCODE_WAKEUP
adb -e shell wm dismiss-keyguard
adb -e install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -e install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
suite=cn.myrecipe.app
cases="$suite.MigrationDeviceTest,$suite.RepositoryDeviceTest#recipeSaveEditDeleteCascades,$suite.RepositoryDeviceTest#trashBackupRetainsIngredientsAndStepsAfterRestore,$suite.UiDeviceTest#settingsShowsInstalledVersionAndUpdateControls"
output=$(mktemp)
trap 'rm -f "$output"' EXIT
adb -e shell am instrument -w -e class "$cases" "$suite.test/androidx.test.runner.AndroidJUnitRunner" | tee "$output"
grep -Eq '^OK \([1-9][0-9]* tests?\)' "$output"

