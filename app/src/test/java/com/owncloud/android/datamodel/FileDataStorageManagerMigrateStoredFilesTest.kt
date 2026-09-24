/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 XeniaCloud
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */

package com.owncloud.android.datamodel

import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import com.nextcloud.client.account.User
import com.owncloud.android.MainApp
import com.owncloud.android.db.ProviderMeta.ProviderTableMeta
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * The selection a query carries is only honoured when it is paired with an argument array.
 * `FileContentProvider.query` rebinds a selection that arrives with a null array as a single
 * `(?)` parameter, which SQLite evaluates as 0, so the query matches no rows and the caller is
 * told nothing went wrong. A data folder rename relies on this method to move the stored paths
 * with the files, so a silent no-op here strands every downloaded file's row on the old path.
 */
class FileDataStorageManagerMigrateStoredFilesTest {

    private val emptyCursor: Cursor = mockk(relaxed = true) {
        every { moveToFirst() } returns false
    }

    @Before
    fun setUp() {
        mockkStatic(MainApp::class)
        every { MainApp.getAppContext() } returns mockk<Context>(relaxed = true)
        every { MainApp.getAuthority() } returns "eu.xeniacloud.files"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `content resolver query pairs the selection with an argument array`() {
        val contentResolver: ContentResolver = mockk(relaxed = true)
        val selection = slot<String>()
        val selectionArgs = slot<Array<String>?>()
        every {
            contentResolver.query(any(), any(), capture(selection), captureNullable(selectionArgs), any())
        } returns emptyCursor

        FileDataStorageManager(mockk<User>(relaxed = true), contentResolver)
            .migrateStoredFiles(SOURCE_PATH, DESTINATION_PATH)

        assertEquals(ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL", selection.captured)
        assertNotNull("a literal selection with null arguments is silently discarded", selectionArgs.captured)
    }

    @Test
    fun `content provider client query pairs the selection with an argument array`() {
        val contentProviderClient: ContentProviderClient = mockk(relaxed = true)
        val selection = slot<String>()
        val selectionArgs = slot<Array<String>?>()
        every {
            contentProviderClient.query(any(), any(), capture(selection), captureNullable(selectionArgs), any())
        } returns emptyCursor

        FileDataStorageManager(mockk<User>(relaxed = true), contentProviderClient)
            .migrateStoredFiles(SOURCE_PATH, DESTINATION_PATH)

        assertEquals(ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL", selection.captured)
        assertNotNull("a literal selection with null arguments is silently discarded", selectionArgs.captured)
    }

    @Test
    fun `the cursor is closed once the rows have been read`() {
        val contentResolver: ContentResolver = mockk(relaxed = true)
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns emptyCursor

        FileDataStorageManager(mockk<User>(relaxed = true), contentResolver)
            .migrateStoredFiles(SOURCE_PATH, DESTINATION_PATH)

        verify(exactly = 1) { emptyCursor.close() }
    }

    companion object {
        private const val SOURCE_PATH = "/storage/emulated/0/Android/media/eu.xeniacloud.files/nextcloud"
        private const val DESTINATION_PATH = "/storage/emulated/0/Android/media/eu.xeniacloud.files/xeniacloud"
    }
}
