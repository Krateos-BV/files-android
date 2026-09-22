#!/bin/bash

# SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-FileCopyrightText: 2021-2023 Tobias Kaminsky <tobias@kaminsky.me>
# SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only

#1: PR
#2: BRANCH

PR=$1
BRANCH=$2

# This fork has no file hosting, so the logcat is kept where the workflow
# archives it as an artifact instead of being uploaded somewhere.
function save_logcat() {
    log_dir="app/build/logcat"
    mkdir -p "$log_dir"
    xz logcat.txt
    mv logcat.txt.xz "$log_dir/${PR}_logcat.txt.xz"
    echo >&2 "Saved logcat to $log_dir/${PR}_logcat.txt.xz, archived with this run's artifacts"
}

scripts/deleteOldComments.sh "$BRANCH" "IT" "$PR"

scripts/wait_for_emulator.sh || exit 1

./gradlew installGplayDebugAndroidTest

# clear logcat and start saving it to file
adb logcat -c
adb logcat > logcat.txt &
LOGCAT_PID=$!
# Screenshot tests only run when updating/testing screenshots, tests annotated with
# com.nextcloud.test.Flaky are known to be unstable and must not block a pull request.
EXCLUDED_ANNOTATIONS="com.owncloud.android.utils.ScreenshotTest,com.nextcloud.test.Flaky"

./gradlew createGplayDebugCoverageReport \
-Pcoverage -Pandroid.testInstrumentationRunnerArguments.notAnnotation="$EXCLUDED_ANNOTATIONS" \
-Dorg.gradle.jvmargs="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.nio.channels=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED"

stat=$?
# stop saving logcat
kill $LOGCAT_PID

if [ ! $stat -eq 0 ]; then
    save_logcat
fi

curl -Os https://uploader.codecov.io/latest/linux/codecov
chmod +x codecov
./codecov -t fc506ba4-33c3-43e4-a760-aada38c24fd5 -F integration

echo "Exit with: " $stat
exit $stat
