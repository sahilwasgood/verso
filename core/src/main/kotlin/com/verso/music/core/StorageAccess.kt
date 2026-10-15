package com.verso.music.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * "All files access" gate for tag/lyrics/artwork writes (API 30+).
 * Plain File writes into shared Music folders fail without it; nothing
 * is requested silently - the user taps once and approves in Settings.
 */
object StorageAccess {

    fun canWrite(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Opens this app's "All files access" Settings page (no-op when granted). */
    fun request(context: Context) {
        if (canWrite()) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                // No Settings entry on this build; user can grant manually.
            }
        }
    }
}
