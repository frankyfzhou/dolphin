// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.utils

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.dolphinemu.dolphinemu.DolphinApplication
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R

object PermissionsHandler {
    const val REQUEST_CODE_WRITE_PERMISSION = 500
    const val REQUEST_CODE_RECORD_AUDIO = 501

    private var writePermissionDenied = false

    @JvmStatic
    fun requestWritePermission(activity: FragmentActivity) {
        activity.requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_PERMISSION
        )
    }

    @JvmStatic
    fun hasWriteAccess(context: Context): Boolean {
        if (!isExternalStorageLegacy()) {
            return false
        }

        val hasWritePermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return hasWritePermission == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether the app holds All files access, which is what allows a user directory outside the
     * app-private folder on modern Android. Forks that target SDK 29 or lower get this implicitly
     * through legacy external storage; this app targets a modern SDK and cannot.
     */
    @JvmStatic
    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    /**
     * Sends the user to the system screen where All files access is granted. There is no runtime
     * prompt for this permission; it can only be toggled in Settings.
     */
    @JvmStatic
    fun requestAllFilesAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return

        val uri = Uri.fromParts("package", activity.packageName, null)
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Not every device exposes the per-app screen; fall back to the global list.
            activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    @JvmStatic
    fun isExternalStorageLegacy(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Environment.isExternalStorageLegacy()
    }

    @JvmStatic
    fun setWritePermissionDenied() {
        writePermissionDenied = true
    }

    @JvmStatic
    fun isWritePermissionDenied(): Boolean {
        return writePermissionDenied
    }

    @JvmStatic
    @Keep
    fun hasRecordAudioPermission(context: Context?): Boolean {
        val nonNullContext = context ?: DolphinApplication.getAppContext()
        val hasRecordPermission =
            ContextCompat.checkSelfPermission(nonNullContext, Manifest.permission.RECORD_AUDIO)
        return hasRecordPermission == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    @Keep
    fun requestRecordAudioPermission(activity: Activity?) {
        val targetActivity = activity ?: DolphinApplication.getAppActivity()!!
        if (activity == null) {
            // Calling from C++ code
            // Since the emulation (and cubeb) has already started, enabling the microphone permission
            // now might require restarting the game to be effective. Warn the user about it.
            NativeLibrary.displayAlertMsg(
                targetActivity.getString(R.string.wii_speak_permission_warning),
                targetActivity.getString(R.string.wii_speak_permission_warning_description),
                false,
                true,
                false
            )
        }

        targetActivity.requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO
        )
    }
}
