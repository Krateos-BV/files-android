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
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * The move and the database rewrite have to agree: a file is found only through the absolute path
 * on its row, so a tree that moves without its rows is a file the app can no longer open.
 */
class DataFolderMigrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val logger: Logger = mockk(relaxed = true)
    private val preferences: AppPreferences = mockk(relaxed = true)
    private val fileDataStorageManager: FileDataStorageManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private lateinit var storageRoot: File
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        storageRoot = temporaryFolder.newFolder("storage")
        filesDir = temporaryFolder.newFolder("filesDir")
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun runStep(dataFolder: String = CURRENT_DATA_FOLDER) {
        every { context.getString(R.string.data_folder) } returns dataFolder
        every { context.filesDir } returns filesDir
        every { preferences.getStoragePath(any()) } returns storageRoot.absolutePath

        val migrations = Migrations(
            logger,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            context,
            preferences,
            fileDataStorageManager
        )

        val step = migrations.steps.first { it.id == DATA_FOLDER_STEP_ID }
        step.run(step)
    }

    private fun writeFile(parent: File, relativePath: String) {
        val file = File(parent, relativePath)
        file.parentFile?.mkdirs()
        file.writeText("content")
    }

    @Test
    fun `legacy tree is moved and stored paths are rewritten`() {
        writeFile(storageRoot, "$LEGACY_DATA_FOLDER/account/document.txt")

        runStep()

        assertTrue(File(storageRoot, "$CURRENT_DATA_FOLDER/account/document.txt").exists())
        assertFalse(File(storageRoot, LEGACY_DATA_FOLDER).exists())
        verify {
            fileDataStorageManager.migrateStoredFiles(
                File(storageRoot, LEGACY_DATA_FOLDER).absolutePath,
                File(storageRoot, CURRENT_DATA_FOLDER).absolutePath
            )
        }
    }

    @Test
    fun `existing target folder is merged into rather than replaced`() {
        writeFile(storageRoot, "$LEGACY_DATA_FOLDER/account/old.txt")
        writeFile(storageRoot, "$CURRENT_DATA_FOLDER/account-new/new.txt")

        runStep()

        assertTrue(File(storageRoot, "$CURRENT_DATA_FOLDER/account/old.txt").exists())
        assertTrue(File(storageRoot, "$CURRENT_DATA_FOLDER/account-new/new.txt").exists())
    }

    @Test
    fun `folders present on both sides are merged`() {
        writeFile(storageRoot, "$LEGACY_DATA_FOLDER/account/old.txt")
        writeFile(storageRoot, "$CURRENT_DATA_FOLDER/account/already-there.txt")

        runStep()

        assertTrue(File(storageRoot, "$CURRENT_DATA_FOLDER/account/old.txt").exists())
        assertTrue(File(storageRoot, "$CURRENT_DATA_FOLDER/account/already-there.txt").exists())
        assertFalse(File(storageRoot, LEGACY_DATA_FOLDER).exists())
    }

    @Test
    fun `a colliding file aborts before any stored path is rewritten`() {
        writeFile(storageRoot, "$LEGACY_DATA_FOLDER/account/document.txt")
        writeFile(storageRoot, "$CURRENT_DATA_FOLDER/account/document.txt")

        assertThrows(IOException::class.java) { runStep() }

        verify(exactly = 0) { fileDataStorageManager.migrateStoredFiles(any(), any()) }
    }

    @Test
    fun `stored paths are rewritten even when the legacy folder is already gone`() {
        runStep()

        verify {
            fileDataStorageManager.migrateStoredFiles(
                File(storageRoot, LEGACY_DATA_FOLDER).absolutePath,
                File(storageRoot, CURRENT_DATA_FOLDER).absolutePath
            )
        }
    }

    @Test
    fun `nothing is touched while the data folder still carries the legacy name`() {
        writeFile(storageRoot, "$LEGACY_DATA_FOLDER/account/document.txt")

        runStep(dataFolder = LEGACY_DATA_FOLDER)

        assertTrue(File(storageRoot, "$LEGACY_DATA_FOLDER/account/document.txt").exists())
        verify(exactly = 0) { fileDataStorageManager.migrateStoredFiles(any(), any()) }
    }

    @Test
    fun `the internal temp tree is migrated alongside the external one`() {
        writeFile(filesDir, "$LEGACY_DATA_FOLDER/tmp/account/part.tmp")

        runStep()

        assertTrue(File(filesDir, "$CURRENT_DATA_FOLDER/tmp/account/part.tmp").exists())
        assertFalse(File(filesDir, LEGACY_DATA_FOLDER).exists())
    }

    companion object {
        private const val DATA_FOLDER_STEP_ID = 4
        private const val LEGACY_DATA_FOLDER = "nextcloud"
        private const val CURRENT_DATA_FOLDER = "xeniacloud"
    }
}
