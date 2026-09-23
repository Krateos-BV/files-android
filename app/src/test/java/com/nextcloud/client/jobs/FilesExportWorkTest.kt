/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 XeniaCloud
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.client.jobs

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nextcloud.client.account.User
import com.owncloud.android.utils.theme.ViewThemeUtils
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The export worker hands its counts back to the screen that started it through the output map,
 * which is what [com.owncloud.android.ui.helpers.FileOperationsHelper] reads to raise the
 * confirmation snackbar. Nothing covered that contract before XNT-120.
 *
 * These exercise the paths that do not need a real [com.owncloud.android.datamodel.FileDataStorageManager]
 * or an ownCloud client; exporting an actual file stays manual, emulator-verified territory.
 */
class FilesExportWorkTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val user: User = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val viewThemeUtils: ViewThemeUtils = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { params.inputData } returns Data.EMPTY
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun worker() = FilesExportWork(context, user, contentResolver, viewThemeUtils, params)

    private fun runWorker(): ListenableWorker.Result = runBlocking { worker().doWork() }

    private fun payloadFileContaining(text: String): File =
        temporaryFolder.newFile("worker_files_payload_test.tmp").apply { writeText(text) }

    private fun givenPayloadAt(path: String) {
        every { params.inputData } returns workDataOf(FilesExportWork.FILES_TO_DOWNLOAD to path)
    }

    @Test
    fun a_run_with_no_payload_succeeds_with_empty_counts() {
        val result = runWorker()

        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(0, output.getInt(FilesExportWork.EXPORTED_COUNT, -1))
        assertEquals(0, output.getInt(FilesExportWork.FAILED_COUNT, -1))
    }

    @Test
    fun the_output_map_always_carries_both_counts() {
        // FileOperationsHelper.showExportResult reads both keys off every successful run and
        // treats a missing one as zero, which would silently turn a failed export into "0 of 0".
        val output = (runWorker() as ListenableWorker.Result.Success).outputData

        assertTrue(output.keyValueMap.containsKey(FilesExportWork.EXPORTED_COUNT))
        assertTrue(output.keyValueMap.containsKey(FilesExportWork.FAILED_COUNT))
    }

    @Test
    fun a_payload_holding_no_usable_ids_succeeds_with_empty_counts() {
        givenPayloadAt(payloadFileContaining("not-a-file-id").absolutePath)

        val output = (runWorker() as ListenableWorker.Result.Success).outputData

        assertEquals(0, output.getInt(FilesExportWork.EXPORTED_COUNT, -1))
        assertEquals(0, output.getInt(FilesExportWork.FAILED_COUNT, -1))
    }

    @Test
    fun a_payload_file_is_cleaned_up_once_the_run_is_over() {
        // The payload is a temp file handed over by WorkerFilesPayload; leaving it behind leaks
        // into the app's temp directory on every export.
        val payload = payloadFileContaining("not-a-file-id")
        givenPayloadAt(payload.absolutePath)

        runWorker()

        assertFalse(payload.exists())
    }

    @Test
    fun a_payload_that_went_missing_is_treated_as_an_empty_run() {
        val payload = payloadFileContaining("not-a-file-id")
        val path = payload.absolutePath
        assertTrue(payload.delete())
        givenPayloadAt(path)

        val output = (runWorker() as ListenableWorker.Result.Success).outputData

        assertEquals(0, output.getInt(FilesExportWork.EXPORTED_COUNT, -1))
        assertEquals(0, output.getInt(FilesExportWork.FAILED_COUNT, -1))
    }
}
