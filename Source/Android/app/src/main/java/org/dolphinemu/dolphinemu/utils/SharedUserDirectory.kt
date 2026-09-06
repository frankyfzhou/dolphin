// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.utils

import android.content.Context
import android.os.Environment
import androidx.preference.PreferenceManager
import java.io.File

/**
 * Puts Dolphin's user directory in a folder on the main internal storage volume instead of the
 * app-private Android/data directory, so it is reachable from a file manager and can be shared
 * with another Dolphin fork. The default matches Dolphin MMJR2-VBI, which uses
 * /storage/emulated/0/mmjr2-vbi.
 *
 * These settings deliberately live in SharedPreferences rather than Dolphin.ini: the user
 * directory is what decides where Dolphin.ini itself lives, so storing them in the config would
 * be circular.
 *
 * Every entry point takes an explicit Context rather than reaching for DolphinApplication.
 * The user directory is resolved by DocumentProvider.onCreate, and Android creates content
 * providers before Application.onCreate runs, so the application singleton isn't set yet.
 *
 * Requires All files access (MANAGE_EXTERNAL_STORAGE). Without it, this is ignored and the
 * app-private directory is used, so a denied permission can never leave the app with an
 * unusable user directory.
 */
object SharedUserDirectory {
    const val DEFAULT_FOLDER_NAME = "mmjr2-vbi"

    private const val KEY_ENABLED = "SharedUserDirectoryEnabled"
    private const val KEY_FOLDER_NAME = "SharedUserDirectoryFolderName"

    private fun preferences(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun getFolderName(context: Context): String =
        preferences(context).getString(KEY_FOLDER_NAME, DEFAULT_FOLDER_NAME) ?: DEFAULT_FOLDER_NAME

    fun setFolderName(context: Context, name: String) =
        preferences(context).edit().putString(KEY_FOLDER_NAME, name).apply()

    /**
     * The shared user directory to use, or null if it isn't configured or isn't usable right now.
     */
    fun getPath(context: Context): File? {
        // Nothing here may throw: this runs on the startup path that decides where the user
        // directory lives, and a failure to resolve it must degrade to the app-private folder
        // rather than take the app down.
        return try {
            // Legacy external storage (targetSdk below 30) reaches the whole volume through
            // WRITE_EXTERNAL_STORAGE and does so without the scoped-storage indirection, which
            // is how MMJR2-VBI keeps full speed. All files access is the fallback for when this
            // is ever built against a modern target again.
            if (!isEnabled(context))
                return null
            if (!PermissionsHandler.hasWriteAccess(context) && !PermissionsHandler.hasAllFilesAccess())
                return null

            val name = getFolderName(context)
            if (name.isEmpty() || name.contains('/'))
                return null

            val externalPath = Environment.getExternalStorageDirectory() ?: return null
            File(externalPath, name)
        } catch (e: Exception) {
            Log.error("[SharedUserDirectory] Could not resolve shared user directory: $e")
            null
        }
    }

    /**
     * True when the user wants the shared directory but hasn't granted the permission that makes
     * it possible, which is the case worth prompting about.
     */
    fun isWaitingForPermission(context: Context): Boolean = try {
        isEnabled(context) &&
                !PermissionsHandler.hasWriteAccess(context) &&
                !PermissionsHandler.hasAllFilesAccess()
    } catch (e: Exception) {
        Log.error("[SharedUserDirectory] Could not check permission state: $e")
        false
    }
}
