/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Xenia Cloud
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.owncloud.android.ui.adapter

import com.owncloud.android.ui.adapter.helper.OCFileListAdapterHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server capability reports the Notes folder without a leading slash ("Notes/"), while
 * [com.owncloud.android.datamodel.OCFile.getDecryptedRemotePath] always returns one ("/Notes/"),
 * so the two have to be normalized before they are compared.
 */
class OCFileListAdapterNotesFolderTest {

    @Test
    fun `capability path without leading slash matches the remote path`() {
        assertTrue(OCFileListAdapterHelper.isInNotesFolder("Notes/", "/Notes/"))
    }

    @Test
    fun `capability path without either slash matches the remote path`() {
        assertTrue(OCFileListAdapterHelper.isInNotesFolder("Notes", "/Notes/"))
    }

    @Test
    fun `capability path with both slashes matches the remote path`() {
        assertTrue(OCFileListAdapterHelper.isInNotesFolder("/Notes/", "/Notes/"))
    }

    @Test
    fun `nested notes folder is configured and matched`() {
        assertTrue(OCFileListAdapterHelper.isInNotesFolder("Documents/Notes", "/Documents/Notes/"))
    }

    @Test
    fun `subfolder of the notes folder matches`() {
        assertTrue(OCFileListAdapterHelper.isInNotesFolder("Notes", "/Notes/Recipes/"))
    }

    @Test
    fun `sibling folder with the notes folder as a name prefix does not match`() {
        assertFalse(OCFileListAdapterHelper.isInNotesFolder("Notes", "/Notesomething/"))
    }

    @Test
    fun `unrelated folder does not match`() {
        assertFalse(OCFileListAdapterHelper.isInNotesFolder("Notes", "/Documents/"))
    }

    @Test
    fun `root does not match`() {
        assertFalse(OCFileListAdapterHelper.isInNotesFolder("Notes", "/"))
    }

    @Test
    fun `blank capability path never matches`() {
        assertFalse(OCFileListAdapterHelper.isInNotesFolder("", "/Notes/"))
        assertFalse(OCFileListAdapterHelper.isInNotesFolder("  ", "/Notes/"))
    }

    @Test
    fun `null arguments never match`() {
        assertFalse(OCFileListAdapterHelper.isInNotesFolder(null, "/Notes/"))
        assertFalse(OCFileListAdapterHelper.isInNotesFolder("Notes", null))
    }
}
