#!/bin/bash
#
# SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-FileCopyrightText: 2021 Tobias Kaminsky <tobias@kaminsky.me>
# SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only

# Builds the local screenshot comparison overview.
#
# This fork holds no file-hosting credentials, so the summary is not uploaded
# anywhere: it stays in app/build/screenshotSummary and is opened from there.

mkdir -p app/build/screenshotSummary/images

scripts/generateScreenshotOverview.sh > app/build/screenshotSummary/summary.html
error=$?

echo "Screenshot summary written to app/build/screenshotSummary/summary.html"

exit $error
