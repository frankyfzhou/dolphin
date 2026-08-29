// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.utils

import org.dolphinemu.dolphinemu.features.settings.model.FloatSetting
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig

/**
 * Engages and disengages fast-forward by overriding the emulation speed limit on the
 * CurrentRun config layer.
 *
 * The throttler picks the new value up live: [NativeConfig] calls Config::OnConfigChanged, which
 * makes CoreTimingManager::RefreshConfig re-read Config::MAIN_EMULATION_SPEED on the CPU thread.
 * Nothing is written to any INI file, and disengaging simply drops the override, so the speed
 * always returns to whatever Speed Limit the user has configured, even if they changed it while
 * fast-forward was engaged.
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
            FloatSetting.MAIN_EMULATION_SPEED.setFloat(
                NativeConfig.LAYER_CURRENT,
                FloatSetting.MAIN_FAST_FORWARD_SPEED.float
            )
        } else {
            // If the user edited the Speed Limit setting while fast-forward was engaged, their
            // value may have landed on the CurrentRun layer. Only drop the override if it's still
            // ours.
            val currentValue = FloatSetting.MAIN_EMULATION_SPEED.getFloat(NativeConfig.LAYER_CURRENT)
            if (currentValue == FloatSetting.MAIN_FAST_FORWARD_SPEED.float)
                FloatSetting.MAIN_EMULATION_SPEED.delete(NativeConfig.LAYER_CURRENT)
        }
    }

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
