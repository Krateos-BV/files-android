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

# commons-httpclient 3.x resolves its own classes by name in static initializers (javac class$ pattern).
-keep class org.apache.commons.httpclient.** { *; }
# Jackrabbit DavException loads statuscode.properties relative to its own package.
-keepnames class org.apache.jackrabbit.webdav.DavException

# Guava TypeToken subclasses read their generic signature at runtime; without it the upload workers fail.
-keep,allowobfuscation,allowshrinking class com.google.common.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.common.reflect.TypeToken

# SSO responses to Xenia Notes and Xenia Talk are Java-serialized, so serializable classes are resolved by name.
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# WorkManager stores worker class names, and BackgroundJobFactory loads them with Class.forName.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

-keep class com.nextcloud.appscan.ScanPageContract { *; }

-keepclassmembers class * { @org.greenrobot.eventbus.Subscribe <methods>; }
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Compile-time references that are absent from the desugared runtime library or are optional annotations.
-dontwarn java.time.zone.ZoneRulesProvider
-dontwarn org.joda.convert.**
