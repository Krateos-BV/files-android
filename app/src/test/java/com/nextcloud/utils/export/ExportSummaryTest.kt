/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 XeniaCloud
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.utils.export

import com.owncloud.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summary notification and the on-screen confirmation used to pick their wording with two
 * separate copies of this branch, kept in step by nothing but a comment. See XNT-120.
 */
class ExportSummaryTest {

    @Test
    fun a_run_with_no_failures_reports_success() {
        val summary = ExportSummary.of(exported = 3, failed = 0)

        assertEquals(R.plurals.export_successful, summary.messageRes)
        assertEquals(3, summary.quantity)
    }

    @Test
    fun a_run_with_no_successes_reports_failure() {
        val summary = ExportSummary.of(exported = 0, failed = 2)

        assertEquals(R.plurals.export_failed, summary.messageRes)
        assertEquals(2, summary.quantity)
    }

    @Test
    fun a_mixed_run_reports_partial_failure() {
        val summary = ExportSummary.of(exported = 2, failed = 1)

        assertEquals(R.plurals.export_partially_failed, summary.messageRes)
    }

    @Test
    fun a_partial_run_counts_the_files_that_made_it() {
        // Not the failures, and not the total: "2 of 3 exported" is phrased around the 2.
        val summary = ExportSummary.of(exported = 2, failed = 1)

        assertEquals(2, summary.quantity)
    }

    @Test
    fun an_empty_run_reports_success_of_zero() {
        // Callers that would rather say nothing at all have to check the counts themselves;
        // FileOperationsHelper.showExportResult does exactly that before asking for wording.
        val summary = ExportSummary.of(exported = 0, failed = 0)

        assertEquals(R.plurals.export_successful, summary.messageRes)
        assertEquals(0, summary.quantity)
    }

    @Test
    fun a_detached_view_is_not_confirmed_on_screen() {
        // The job outlives the screen that started it, so the observer can fire after the
        // fragment's view is gone. The notification still reports the outcome.
        assertFalse(ExportSummary.shouldConfirmOnScreen(viewAttached = false, exported = 3, failed = 0))
    }

    @Test
    fun a_run_that_did_nothing_is_not_confirmed_on_screen() {
        assertFalse(ExportSummary.shouldConfirmOnScreen(viewAttached = true, exported = 0, failed = 0))
    }

    @Test
    fun a_run_that_exported_something_is_confirmed_on_screen() {
        assertTrue(ExportSummary.shouldConfirmOnScreen(viewAttached = true, exported = 1, failed = 0))
    }

    @Test
    fun a_run_that_only_failed_is_still_confirmed_on_screen() {
        assertTrue(ExportSummary.shouldConfirmOnScreen(viewAttached = true, exported = 0, failed = 1))
    }

    @Test
    fun locating_the_folder_is_offered_only_when_a_file_arrived_there() {
        assertTrue(ExportSummary.shouldOfferLocateFolder(exported = 1))
        assertFalse(ExportSummary.shouldOfferLocateFolder(exported = 0))
    }
}
