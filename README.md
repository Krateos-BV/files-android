<!--
 ~ SPDX-FileCopyrightText: 2016-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-FileCopyrightText: 2026 XeniaCloud
 ~ SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
-->
# Xenia Files :iphone:

[![REUSE status](https://api.reuse.software/badge/github.com/Krateos-BV/files-android)](https://api.reuse.software/info/github.com/Krateos-BV/files-android)

> Independently maintained Android client for Xenia Files, based on the [Nextcloud Android app](https://github.com/nextcloud/android) (AGPLv3). Not affiliated with or endorsed by Nextcloud GmbH.

**The Android client for [XeniaCloud](https://xeniacloud.eu). Easily work with your data on your XeniaCloud account.**

## Getting help :rescue_worker_helmet:

This is an independently maintained fork — the upstream Nextcloud Help Forum and issue tracker are for the Nextcloud app, not Xenia Files, and won't be able to help with anything specific to this fork or to a XeniaCloud account. For support with Xenia Files or your XeniaCloud account, contact XeniaCloud support directly.

Keep in mind that this repository only manages the Android app. Server/backend issues should go through XeniaCloud support, not the Nextcloud project.

## Upstream & license :scroll:

Xenia Files is a rebrand of [nextcloud/android](https://github.com/nextcloud/android), forked under its [AGPLv3](https://github.com/nextcloud/android/blob/master/LICENSE.txt) license, which permits forking provided Nextcloud's own trademarks and branding are not carried over — the app name, icon, splash screen and accent colors here have been changed accordingly; the underlying code and functionality are otherwise unchanged from upstream unless noted in this fork's own commit history.

All contributions to the upstream repository from 16 June 2016 on are licensed under the AGPLv3 or any later version. See [CONTRIBUTING.md](CONTRIBUTING.md) and [SETUP.md](SETUP.md) for the general (upstream) development workflow, which this fork continues to follow.

## Development version :hammer:

This fork is not currently distributed via Google Play, F-Droid, or GitHub Releases. Builds are produced by this repository's own CI (`xenia-ci.yml`) as unsigned debug artifacts.

## Logs

### Getting debug info via logcat :mag:

#### With a linux computer:

*   enable USB-Debugging in your smartphones developer settings and connect it via USB
*   open command prompt/terminal
*   enter `adb logcat --pid=$(adb shell pidof -s 'eu.xeniacloud.files') > logcatOutput.txt` to save the output to this file

**Note:** You must have [adb](https://developer.android.com/studio/releases/platform-tools.html) installed first!

#### On Windows:

*   download and install [Minimal ADB and fastboot](https://forum.xda-developers.com/t/tool-minimal-adb-and-fastboot-2-9-18.2317790/#post-42407269)
*   enable USB-Debugging in your smartphones developer settings and connect it via USB
*   launch Minimal ADB and fastboot
*   enter `adb shell pidof -s 'eu.xeniacloud.files'` and use the output as `<processID>` in the following command:
*   `adb logcat --pid=<processID> > "%USERPROFILE%\Downloads\logcatOutput.txt"` (This will produce a `logcatOutput.txt` file in your downloads)
*   if the processID is `18841`, an example command is: `adb logcat --pid=18841 > "%USERPROFILE%\Downloads\logcatOutput.txt"` (You might cancel the process after a while manually: it will not be exited automatically.)
*   For a PowerShell terminal, replace `%USERPROFILE%` with `$env:USERPROFILE` in the commands above.

#### On a device (with root) :wrench:

*   open terminal app *(can be enabled in developer options)*
*   get root access via "su"
*   enter `logcat -d --pid $(pidof -s eu.xeniacloud.files) -f /sdcard/logcatOutput.txt`

or

*   use [CatLog](https://play.google.com/store/apps/details?id=com.nolanlawson.logcat) or [aLogcat](https://play.google.com/store/apps/details?id=org.jtb.alogcat)

**Note:** Your device needs to be rooted for this approach!

## Known Problems and FAQs

### Push notifications do not work on F-Droid editions

Push Notifications are not currently supported in F-Droid-style builds due to dependencies on Google Play services.

## Remarks :scroll:

Google Play and the Google Play logo are trademarks of Google Inc.
