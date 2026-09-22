#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
# SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only

# Reports a failed test job on its pull request.
#
# This fork holds no file-hosting credentials, so the reports are not uploaded
# anywhere: they stay attached to the workflow run as artifacts and the comment
# links to that run.
#
#1: BRANCH (or the screenshot color-scheme combination)
#2: TYPE (IT, Unit or Screenshot)
#3: PR

BRANCH=$1
TYPE=$2
PR=$3

source scripts/lib.sh

BRANCH_TYPE="$BRANCH-$TYPE"

if [ -z "${GITHUB_REPOSITORY:-}" ]; then
    err "GITHUB_REPOSITORY is not set, skipping the failure comment"
    exit 0
fi

RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/$GITHUB_REPOSITORY/actions/runs/${GITHUB_RUN_ID:-}"
message="$BRANCH_TYPE test failed: $RUN_URL - the reports are attached to that run as artifacts."

echo "$message"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "$message" >> "$GITHUB_STEP_SUMMARY"
fi

scripts/deleteOldComments.sh "$BRANCH" "$TYPE" "$PR"

if [ -z "$PR" ] || [ "$PR" = "null" ]; then
    err "No pull request number given, not commenting"
    exit 0
fi

payload=$(jq -n --arg body "$message" '{body: $body}')
curl_gh -X POST "https://api.github.com/repos/$GITHUB_REPOSITORY/issues/$PR/comments" -d "$payload" > /dev/null
