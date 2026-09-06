// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.utils

import org.dolphinemu.dolphinemu.DolphinApplication
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.FloatSetting
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig

/**
 * Engages and disengages fast-forward.
 *
 * There are two ways to make emulation run faster, and which one applies depends on the configured
 * speed:
 *
 *  * Unlimited (a speed of 0) uses the throttler's temp-disable flag. CoreTimingManager reads that
 *    flag live in IsSpeedUnlimited, so it engages immediately.
 *  * Any other multiplier has to override Config::MAIN_EMULATION_SPEED on the CurrentRun config
 *    layer. The throttler only picks that up when CoreTimingManager::RefreshConfig runs, which is
 *    a CPU-thread config callback - so it does not take effect while the CPU thread is stalled,
 *    for example while a blocking panic alert is waiting to be dismissed.
 *
 * Disengaging clears both, so speed always returns to the user's configured Speed Limit even if
 * they changed it while fast-forward was engaged. Nothing is written to any INI file.
 */
object FastForward {
    @Volatile
    private var enabled = false

    val isEnabled: Boolean
        get() = enabled

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled == this.enabled)
            return

        this.enabled = enabled

        if (enabled) {
            val speed = FloatSetting.MAIN_FAST_FORWARD_SPEED.float
            if (speed <= 0.0f) {
                NativeLibrary.SetThrottlerTempDisabled(true)
            } else {
                FloatSetting.MAIN_EMULATION_SPEED.setFloat(NativeConfig.LAYER_CURRENT, speed)
            }
        } else {
            // Always clear both, regardless of which one engaged it: the speed setting can be
            // changed while fast-forward is running, and leaving either in place would strand
            // emulation at the wrong speed.
            NativeLibrary.SetThrottlerTempDisabled(false)

            // If the user edited the Speed Limit setting while fast-forward was engaged, their
            // value may have landed on the CurrentRun layer. Only drop the override if it's still
            // ours.
            val currentValue = FloatSetting.MAIN_EMULATION_SPEED.getFloat(NativeConfig.LAYER_CURRENT)
            if (currentValue == FloatSetting.MAIN_FAST_FORWARD_SPEED.float)
                FloatSetting.MAIN_EMULATION_SPEED.delete(NativeConfig.LAYER_CURRENT)
        }

        showOsdMessage(enabled)
    }

    /**
     * Announces the new state on the emulation OSD. Uses Dolphin's own on-screen display rather
     * than a Toast so it renders over the game, doesn't outlive the state change, and can't queue
     * up behind other Toasts.
     */
    private fun showOsdMessage(enabled: Boolean) {
        val context = DolphinApplication.getAppContext()
        val text = context.getString(
            if (enabled) R.string.fast_forward_osd_on else R.string.fast_forward_osd_off
        )
        NativeLibrary.DisplayOSDMessage(text, OSD_DURATION_MS)
    }

    private const val OSD_DURATION_MS = 1000

    @Synchronized
    fun toggle(): Boolean {
        setEnabled(!enabled)
        return isEnabled
    }

    /**
     * Disengages fast-forward if it is engaged. Safe to call at any time, and called whenever
     * emulation leaves the foreground so that fast-forward can't get stuck on.
     */
    @Synchronized
    fun reset() = setEnabled(false)
}
