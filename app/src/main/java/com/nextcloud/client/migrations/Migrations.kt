/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.client.migrations

import android.content.Context
import androidx.work.WorkManager
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.jobs.BackgroundJobManager
import com.nextcloud.client.logger.Logger
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.R
import com.owncloud.android.datamodel.ArbitraryDataProvider
import com.owncloud.android.datamodel.FileDataStorageManager
import com.owncloud.android.ui.activity.ContactsPreferenceActivity
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * This class collects all migration steps and provides API to supply those
 * steps to [MigrationsManager] for execution.
 */
class Migrations @Inject constructor(
    private val logger: Logger,
    private val userAccountManager: UserAccountManager,
    private val workManager: WorkManager,
    private val arbitraryDataProvider: ArbitraryDataProvider,
    private val jobManager: BackgroundJobManager,
    private val context: Context,
    private val preferences: AppPreferences,
    private val fileDataStorageManager: FileDataStorageManager
) {

    companion object {
        val TAG = Migrations::class.java.simpleName

        /**
         * The data folder name this fork inherited from upstream. Only ever compared
         * against, never written, so it stays correct once the resource moves on.
         */
        private const val LEGACY_DATA_FOLDER = "nextcloud"
    }

    /**
     * This class wraps migration logic with some metadata with some
     * metadata required to register and log overall migration progress.
     *
     * @param id Step id; id must be unique; this is verified upon registration
     * @param description Human readable migration step descriptions
     * @param mandatory If true, failing migration will cause an exception; if false, it will be skipped and repeated
     *                  again on next startup
     * @throws Exception migration logic is permitted to throw any kind of exceptions; all exceptions will be wrapped
     * into [MigrationException]
     */
    class Step(val id: Int, val description: String, val mandatory: Boolean = true, val run: (s: Step) -> Unit) {
        override fun toString(): String = "Migration $id: $description"
    }

    /**
     * NOP migration used to replace applied migrations that should be applied again.
     */
    private fun nop(s: Step) {
        logger.i(TAG, "$s: skipped deprecated migration")
    }

    /**
     * Migrate legacy accounts by adding user IDs. This migration can be re-tried until all accounts are
     * successfully migrated.
     */
    private fun migrateUserId(s: Step) {
        val allAccountsHaveUserId = userAccountManager.migrateUserId()
        logger.i(TAG, "${s.description}: success = $allAccountsHaveUserId")
        if (!allAccountsHaveUserId) {
            throw IllegalStateException("Failed to set user id for all accounts")
        }
    }

    /**
     * Content observer job must be restarted to use new scheduler abstraction.
     */
    private fun migrateContentObserverJob(s: Step) {
        val legacyWork = workManager.getWorkInfosByTag("content_sync").get()
        legacyWork.forEach {
            logger.i(TAG, "${s.description}: cancelling legacy work ${it.id}")
            workManager.cancelWorkById(it.id)
        }
        jobManager.scheduleContentObserverJob()
        logger.i(TAG, "$s: enabled")
    }

    /**
     * Periodic contacts backup job has been changed and should be restarted.
     */
    private fun restartContactsBackupJobs(s: Step) {
        val users = userAccountManager.allUsers
        if (users.isEmpty()) {
            logger.i(TAG, "$s: no users to migrate")
        } else {
            users.forEach {
                val backupEnabled = arbitraryDataProvider.getBooleanValue(
                    it.accountName,
                    ContactsPreferenceActivity.PREFERENCE_CONTACTS_AUTOMATIC_BACKUP
                )
                if (backupEnabled) {
                    jobManager.schedulePeriodicContactsBackup(it)
                }
                logger.i(TAG, "$s: user = ${it.accountName}, backup enabled = $backupEnabled")
            }
        }
    }

    /**
     * Moves the on-device data folder off the name inherited from upstream.
     *
     * A file is located purely by the absolute path stored on its database row, so the rows
     * have to follow the tree; leaving them alone strands old downloads under the legacy
     * folder while new ones accumulate under the current one.
     *
     * Registered as non-mandatory: nothing here deletes user data, so an incomplete run is
     * safe to repeat on the next launch.
     */
    private fun migrateDataFolder(s: Step) {
        val currentDataFolder = context.getString(R.string.data_folder)
        if (currentDataFolder == LEGACY_DATA_FOLDER) {
            logger.i(TAG, "$s: data folder is still '$LEGACY_DATA_FOLDER', nothing to migrate")
            return
        }

        // MainApp.storagePath is assigned *after* migrations are started, so read the
        // preference exactly the way MainApp does rather than via MainApp.getStoragePath().
        val externalRoot = preferences.getStoragePath(context.filesDir.absolutePath)

        // getAppTempDirectoryPath() puts a second data folder under filesDir. The set
        // collapses both entries when the storage path already is filesDir.
        linkedSetOf(externalRoot, context.filesDir.absolutePath).forEach { root ->
            moveLegacyDataFolder(s, File(root, LEGACY_DATA_FOLDER), File(root, currentDataFolder))
        }

        // Unconditional because the move above may already have completed on a prior run
        // that did not reach this point, leaving no legacy folder but stale rows.
        fileDataStorageManager.migrateStoredFiles(
            File(externalRoot, LEGACY_DATA_FOLDER).absolutePath,
            File(externalRoot, currentDataFolder).absolutePath
        )
        logger.i(TAG, "$s: rewrote stored file paths under $externalRoot")
    }

    /**
     * Throws if any file cannot be moved, so the caller never rewrites database rows to a path
     * the file did not reach. Folders present on both sides are merged rather than skipped,
     * which keeps a half-finished earlier run from stranding its remainder.
     */
    private fun moveLegacyDataFolder(s: Step, legacy: File, target: File) {
        if (!legacy.isDirectory) {
            return
        }

        if (!target.exists() && legacy.renameTo(target)) {
            logger.i(TAG, "$s: renamed ${legacy.absolutePath} to ${target.absolutePath}")
            return
        }

        if (!target.isDirectory && !target.mkdirs()) {
            throw IOException("Could not create ${target.absolutePath}")
        }

        legacy.listFiles()?.forEach { child ->
            val destination = File(target, child.name)
            when {
                child.isDirectory -> moveLegacyDataFolder(s, child, destination)
                destination.exists() -> throw IOException("${destination.absolutePath} already exists")
                !child.renameTo(destination) ->
                    throw IOException("Could not move ${child.absolutePath} to ${destination.absolutePath}")
            }
        }

        // Only succeeds once empty; a leftover folder is harmless and retried next launch.
        if (legacy.delete()) {
            logger.i(TAG, "$s: removed empty ${legacy.absolutePath}")
        }
    }

    /**
     * List of migration steps. Those steps will be loaded and run by [MigrationsManager].
     *
     * If a migration should be run again (applicable to periodic job restarts), insert
     * the migration with new ID. To prevent accidental re-use of older IDs, replace old
     * migration with this::nop.
     */
    @Suppress("MagicNumber")
    val steps: List<Step> = listOf(
        Step(0, "Migrate user id", false, this::migrateUserId),
        Step(1, "Migrate content observer job", false, this::migrateContentObserverJob),
        Step(2, "Restart contacts backup job", true, this::nop),
        Step(3, "Restart contacts backup job", true, this::restartContactsBackupJobs),
        Step(4, "Migrate data folder off the upstream name", false, this::migrateDataFolder)
    ).sortedBy { it.id }.apply {
        val uniqueIds = associateBy { it.id }.size
        if (uniqueIds != size) {
            throw IllegalStateException("All migrations must have unique id")
        }
    }
}
