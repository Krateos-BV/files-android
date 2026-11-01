/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.owncloud.android.lib.common.utils.Log_OC

object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimizationHelper"

    fun isBatteryOptimizationEnabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @Suppress("TooGenericExceptionCaught")
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            context.startActivity(batteryOptimizationIntent(context))
        } catch (e: Exception) {
            Log_OC.d(TAG, "open battery optimization settings: ", e)
        }
    }

    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent {
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri()
        )

        if (canRequestIgnoreBatteryOptimizations(context) &&
            requestIntent.resolveActivity(context.packageManager) != null
        ) {
            return requestIntent
        }

        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    private fun canRequestIgnoreBatteryOptimizations(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
