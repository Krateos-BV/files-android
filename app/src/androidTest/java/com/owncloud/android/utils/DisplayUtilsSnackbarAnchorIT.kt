/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 XeniaCloud
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.owncloud.android.utils

import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.owncloud.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Snackbars must sit above the bottom chrome rather than on top of it — see XNT-119, where the
 * export confirmation covered the bottom navigation bar.
 */
class DisplayUtilsSnackbarAnchorIT {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hierarchy(fabVisibility: Int, bottomNavVisibility: Int): Triple<View, View, View> {
        val root = FrameLayout(context)

        val content = View(context)
        root.addView(content)

        val fab = View(context).apply {
            id = R.id.fab_main
            visibility = fabVisibility
        }
        root.addView(fab)

        val bottomNav = View(context).apply {
            id = R.id.bottom_navigation
            visibility = bottomNavVisibility
        }
        root.addView(bottomNav)

        return Triple(content, fab, bottomNav)
    }

    @Test
    fun anchorsToFabWhenItIsVisible() {
        val (content, fab, _) = hierarchy(View.VISIBLE, View.VISIBLE)

        assertEquals(fab, DisplayUtils.findBottomChromeAnchor(content))
    }

    @Test
    fun anchorsToBottomNavigationWhenFabIsHidden() {
        val (content, _, bottomNav) = hierarchy(View.GONE, View.VISIBLE)

        assertEquals(bottomNav, DisplayUtils.findBottomChromeAnchor(content))
    }

    @Test
    fun anchorsToNothingWhenNoBottomChromeIsShown() {
        val (content, _, _) = hierarchy(View.GONE, View.GONE)

        assertNull(DisplayUtils.findBottomChromeAnchor(content))
    }
}
