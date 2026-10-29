package com.vwillcox.mytv.system

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.vwillcox.mytv.app

/**
 * Tells AllMedia who is watching, chosen here from AllMedia's own people, so AllMedia plays as them. Only the
 * person's id and name: AllMedia holds everyone's accounts. Readable only with this app's PROFILE permission, which
 * is signature-level: only apps signed with the same key (AllMedia) get it.
 *
 * `content://com.vwillcox.mytv.profile/active` gives one row (`person`, `name`), or none when nobody is chosen and
 * AllMedia uses its own sign-in.
 */
class ProfileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("person", "name"))
        val who = context!!.app.store.value.watching
        if (uri.lastPathSegment == "active" && who != null && who.id.isNotBlank()) cursor.addRow(arrayOf(who.id, who.name))
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
