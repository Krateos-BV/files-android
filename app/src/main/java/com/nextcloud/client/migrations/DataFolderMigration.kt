/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 XeniaCloud
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.client.migrations

import android.content.Context
import com.nextcloud.client.logger.Logger
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.R
import com.owncloud.android.datamodel.FileDataStorageManager
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Moves the on-device data folder off the name inherited from upstream.
 *
 * A file is located purely by the absolute path stored on its database row, so the rows have to
 * follow the tree; leaving them alone strands old downloads under the legacy folder while new ones
 * accumulate under the current one.
 */
class DataFolderMigration @Inject constructor(
    private val context: Context,
    private val preferences: AppPreferences,
    private val fileDataStorageManager: FileDataStorageManager,
    private val logger: Logger
) {

    fun migrate(step: String) {
        val currentDataFolder = context.getString(R.string.data_folder)
        if (currentDataFolder == LEGACY_DATA_FOLDER) {
            logger.i(TAG, "$step: data folder is still '$LEGACY_DATA_FOLDER', nothing to migrate")
            return
        }

        // MainApp.storagePath is assigned after migrations are started, so read the preference
        // exactly the way MainApp does rather than via MainApp.getStoragePath().
        val externalRoot = preferences.getStoragePath(context.filesDir.absolutePath)

        // getAppTempDirectoryPath() puts a second data folder under filesDir. The set collapses
        // both entries when the storage path already is filesDir.
        linkedSetOf(externalRoot, context.filesDir.absolutePath).forEach { root ->
            move(step, File(root, LEGACY_DATA_FOLDER), File(root, currentDataFolder))
        }

        // Unconditional because the move above may already have completed on a prior run that did
        // not reach this point, leaving no legacy folder but stale rows.
        fileDataStorageManager.migrateStoredFiles(
            File(externalRoot, LEGACY_DATA_FOLDER).absolutePath,
            File(externalRoot, currentDataFolder).absolutePath
        )
        logger.i(TAG, "$step: rewrote stored file paths under $externalRoot")
    }

    /**
     * Folders present on both sides are merged rather than skipped, which keeps a half-finished
     * earlier run from stranding its remainder.
     */
    private fun move(step: String, legacy: File, target: File) {
        if (!legacy.isDirectory) {
            return
        }

        if (!target.exists() && legacy.renameTo(target)) {
            logger.i(TAG, "$step: renamed ${legacy.absolutePath} to ${target.absolutePath}")
            return
        }

        if (!target.isDirectory && !target.mkdirs()) {
            throw IOException("Could not create ${target.absolutePath}")
        }

        legacy.listFiles()?.forEach { child ->
            val destination = File(target, child.name)
            if (child.isDirectory) {
                move(step, child, destination)
            } else {
                moveFile(child, destination)
            }
        }

        // Only succeeds once empty; a leftover folder is harmless and retried on the next launch.
        if (legacy.delete()) {
            logger.i(TAG, "$step: removed empty ${legacy.absolutePath}")
        }
    }

    /**
     * Throws rather than skipping, so the caller never rewrites database rows to a path the file
     * did not reach.
     */
    private fun moveFile(child: File, destination: File) {
        val failure = when {
            destination.exists() -> "${destination.absolutePath} already exists"
            !child.renameTo(destination) -> "could not move ${child.absolutePath} to $destination"
            else -> return
        }
        throw IOException(failure)
    }

    companion object {
        private val TAG = DataFolderMigration::class.java.simpleName

        /**
         * The data folder name this fork inherited from upstream. Only ever compared against,
         * never written, so it stays correct once the resource moves on.
         */
        private const val LEGACY_DATA_FOLDER = "nextcloud"
    }
}
