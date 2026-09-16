#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2016-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-FileCopyrightText: 2016 Tobias Kaminsky <tobias@kaminsky.me>
# SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only

BRANCH=$1
PR_NUMBER=$2

# Findings are compared against a baseline committed to this repository, not a live upstream report.
# After reducing the count, regenerate it with scripts/analysis/spotbugsBaseline.py and commit it.
baselineXml="scripts/analysis/spotbugs-baseline.xml"

ruby scripts/analysis/spotbugs-up.rb "$baselineXml"
spotbugsValue=$?

# exit codes:
# 0: count was reduced or stayed the same
# 1: count was increased
# 3: Spotbugs report or baseline missing

source scripts/lib.sh

echo "Branch: $BRANCH"

if [ $spotbugsValue -eq 3 ]; then
    exit 1
fi

spotbugsResult="<h1>SpotBugs</h1>$(scripts/analysis/spotbugsComparison.py "$baselineXml" app/build/reports/spotbugs/gplayDebug.xml)"

if [ $spotbugsValue -eq 1 ]; then
    spotbugsMessage="<h1>SpotBugs increased!</h1>"
fi

# check gplay limitation: all changelog files must only have 500 chars
gplayLimitation=$(scripts/checkGplayLimitation.sh)

if [ -n "$gplayLimitation" ]; then
    gplayLimitation="<h1>Following files are beyond 500 char limit:</h1><br><br>"$gplayLimitation
fi

# check for NotNull
if [[ $(grep org.jetbrains.annotations app/src/main/* -irl | wc -l) -gt 0 ]] ; then
    notNull="org.jetbrains.annotations.* is used. Please use androidx.annotation.* instead.<br><br>"
fi

bodyContent="$spotbugsResult $spotbugsMessage $gplayLimitation $notNull"
echo "$bodyContent" >> "$GITHUB_STEP_SUMMARY"

if [ "$GITHUB_EVENT_NAME" = "pull_request" ]; then
    # replace the previous results comment
    oldComments=$(curl_gh -X GET "https://api.github.com/repos/$GITHUB_REPOSITORY/issues/${PR_NUMBER}/comments" | jq '.[] | select((.user.login | contains("github-actions")) and (.body | test("^<h1>SpotBugs"))) | .id')

    echo "$oldComments" | while read -r comment ; do
        [ -n "$comment" ] && curl_gh -X DELETE "https://api.github.com/repos/$GITHUB_REPOSITORY/issues/comments/$comment"
    done

    payload=$(jq -n --arg body "$bodyContent" '{body: $body}')
    curl_gh -X POST "https://api.github.com/repos/$GITHUB_REPOSITORY/issues/${PR_NUMBER}/comments" -d "$payload"
fi

if [ -n "$gplayLimitation" ]; then
    exit 1
fi

if [ -n "$notNull" ]; then
    exit 1
fi

exit $spotbugsValue
