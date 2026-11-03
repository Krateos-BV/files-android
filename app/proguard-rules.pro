# SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# This app is the single sign-on provider for Xenia Notes and Xenia Talk: the request, response and
# exception classes are deserialized by class name in the other apps.
-keep class com.nextcloud.android.sso.** { *; }
-keep interface com.nextcloud.android.sso.** { *; }

# Gson-mapped models are stored as JSON in the database, preferences and end-to-end encryption metadata,
# so field names must survive an upgrade from a non-minified build.
-keepclassmembers class com.owncloud.android.**, com.nextcloud.** { <fields>; <init>(...); }

# WorkManager stores worker class names, and BackgroundJobFactory loads them with Class.forName.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

-keep class com.nextcloud.appscan.ScanPageContract { *; }

-keepclassmembers class * { @org.greenrobot.eventbus.Subscribe <methods>; }
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
