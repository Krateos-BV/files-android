/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 XeniaCloud
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.utils.export

import androidx.annotation.PluralsRes
import com.owncloud.android.R

/**
 * How a finished export describes itself.
 *
 * Two surfaces report the outcome: the summary notification raised by
 * [com.nextcloud.client.jobs.FilesExportWork], and the snackbar
 * [com.owncloud.android.ui.helpers.FileOperationsHelper] shows on the screen that started the
 * export. They must never disagree, so the choice of wording lives here instead of being spelled
 * out at each of them.
 *
 * @param messageRes the plural to read the wording from
 * @param quantity the count that plural is phrased around
 */
data class ExportSummary(@PluralsRes val messageRes: Int, val quantity: Int) {

    companion object {
        /**
         * Pick the wording for a run that exported [exported] files and failed on [failed] of them.
         *
         * A run that exported nothing and failed at nothing is described as an empty success; it is
         * for the caller to decide whether that is worth reporting at all.
         */
        @JvmStatic
        fun of(exported: Int, failed: Int): ExportSummary = when {
            failed == 0 -> ExportSummary(R.plurals.export_successful, exported)
            exported == 0 -> ExportSummary(R.plurals.export_failed, failed)
            else -> ExportSummary(R.plurals.export_partially_failed, exported)
        }

        /**
         * Whether the screen that started the export should confirm it.
         *
         * The export job outlives that screen: its observer is scoped to the host activity, so it
         * can fire once the initiating fragment's view is already detached and there is no parent
         * left to attach a snackbar to. The summary notification still reports the outcome.
         */
        @JvmStatic
        fun shouldConfirmOnScreen(viewAttached: Boolean, exported: Int, failed: Int): Boolean =
            viewAttached && (exported > 0 || failed > 0)

        /**
         * Whether to offer the shortcut to the folder the files were written to. Nothing arrived
         * there when every file failed.
         */
        @JvmStatic
        fun shouldOfferLocateFolder(exported: Int): Boolean = exported > 0
    }
}
