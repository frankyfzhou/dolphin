// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.utils

import android.content.Context
import android.os.Environment
import androidx.preference.PreferenceManager
import org.dolphinemu.dolphinemu.DolphinApplication
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
 * Requires All files access (MANAGE_EXTERNAL_STORAGE). Without it, this is ignored and the
 * app-private directory is used, so a denied permission can never leave the app with an
 * unusable user directory.
 */
object SharedUserDirectory {
    const val DEFAULT_FOLDER_NAME = "mmjr2-vbi"

    private const val KEY_ENABLED = "SharedUserDirectoryEnabled"
    private const val KEY_FOLDER_NAME = "SharedUserDirectoryFolderName"

    private val preferences
        get() = PreferenceManager.getDefaultSharedPreferences(DolphinApplication.getAppContext())

    var isEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var folderName: String
        get() = preferences.getString(KEY_FOLDER_NAME, DEFAULT_FOLDER_NAME) ?: DEFAULT_FOLDER_NAME
        set(value) = preferences.edit().putString(KEY_FOLDER_NAME, value).apply()

    /**
     * The shared user directory to use, or null if it isn't configured or isn't usable right now.
     */
    fun getPath(): File? {
        // Nothing here may throw: this runs on the startup path that decides where the user
        // directory lives, and a failure to resolve it must degrade to the app-private folder
        // rather than take the app down.
        return try {
            if (!isEnabled || !PermissionsHandler.hasAllFilesAccess())
                return null

            val name = folderName
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
        isEnabled && !PermissionsHandler.hasAllFilesAccess()
    } catch (e: Exception) {
        Log.error("[SharedUserDirectory] Could not check permission state: $e")
        false
    }
}
