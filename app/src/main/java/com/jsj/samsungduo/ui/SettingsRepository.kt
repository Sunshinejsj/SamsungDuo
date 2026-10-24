package com.jsj.samsungduo.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import com.jsj.samsungduo.SamsungDuoApp
import com.jsj.samsungduo.Prefs
import com.jsj.samsungduo.TrioSettings
import io.github.libxposed.service.XposedService

/**
 * Reads and writes every setting, and mirrors each write to the Xposed
 * framework so the hooked SystemUI process sees it immediately.
 *
 * <p>Two stores are involved and only one of them is authoritative for the
 * hooked process:
 *
 * <ul>
 *   <li>the app's own {@link SharedPreferences} file, which drives this UI and
 *       survives while the framework is absent;
 *   <li>the framework's remote-preference group of the same name, which is what
 *       the hooked process actually reads.
 * </ul>
 *
 * <p>The mirror is therefore not "sync on connect and forget": every single
 * write is pushed, otherwise a value the user changes while the service is
 * bound would never reach SystemUI.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val service: XposedService? get() = SamsungDuoApp.xposedService

    /** The display density, needed to migrate the retired raw-pixel size key. */
    private val density: Float
        get() = appContext.resources.displayMetrics.density

    /** True when the user is currently on the capsule style. */
    private val capsuleNow: Boolean
        get() = preferences().getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE) == Prefs.STYLE_CAPSULE

    /**
     * Returns the storage key for [base], namespaced to the capsule when the
     * capsule style is active. Geometry/position/charge fields live under a
     * separate {@code "cap_"} prefix so the capsule keeps its own independent
     * values instead of being polluted by the ring/rect settings.
     */
    private fun key(base: String): String = Prefs.capKeyAware(base, capsuleNow)

    private fun preferences() =
        appContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    /** The current values, as the preview should render them. */
    fun read(): TrioSettings = TrioSettings.from(preferences(), density)

    // ------------------------------------------------------------- appearance

    fun setEnabled(value: Boolean) = writeBoolean(Prefs.KEY_ENABLED, value)
    fun setShowWifi(value: Boolean) = writeBoolean(Prefs.KEY_SHOW_WIFI, value)
    fun setShowMobile(value: Boolean) = writeBoolean(Prefs.KEY_SHOW_MOBILE, value)
    fun setShowValue(value: Boolean) = writeBoolean(Prefs.KEY_SHOW_VALUE, value)
    fun setShowBolt(value: Boolean) = writeBoolean(Prefs.KEY_SHOW_BOLT, value)
    fun setDualSim(value: Boolean) = writeBoolean(Prefs.KEY_DUAL_SIM, value)
    fun setValueCentred(value: Boolean) = writeBoolean(key(Prefs.KEY_VALUE_CENTRED), value)
    /** 0 = hidden, 1 = inside the ring, 2 = outside it. */
    fun setMobileTypeMode(value: Int) = writeInt(key(Prefs.KEY_MOBILE_TYPE_MODE), value)
    /** 0 = the ring arrangement, 1 = the rectangular one. */
    fun setTrioStyle(value: Int) = writeInt(Prefs.KEY_TRIO_STYLE, value)
    /**
     * ★ 胶囊三按钮（2026-10-21 用户拍板）。仅胶囊有意义，键不带 cap_ 前缀
     * （只此一样式使用，不需要样式隔离），圆环/矩形读不到。
     * 按钮1 右列（恒显）：电量 / 信号；按钮2 缺口：隐藏 / 闪电 / 电量 / 信号；
     * 按钮3 充电闪电：隐藏 / 缺口独立 / 缺口+同伴 / 右侧+同伴。
     */
    fun setCapsuleRight(value: Int) = writeInt(Prefs.KEY_CAPSULE_RIGHT, value)
    fun setCapsuleGap(value: Int) = writeInt(Prefs.KEY_CAPSULE_GAP, value)
    fun setCapsuleBoltPos(value: Int) = writeInt(Prefs.KEY_CAPSULE_BOLT_POS, value)
    /** ★ 按钮4 胶囊形状：0 常规体育场 / 1 三星风格（S25U 超椭圆端帽）。 */
    fun setCapsuleShape(value: Int) = writeInt(Prefs.KEY_CAPSULE_SHAPE, value)
    /** ★ 按钮5 胶囊长度：加长两端圆角之间的直线段（-30~60 设计单位，0 = 参考宽度）。 */
    fun setCapsuleLength(value: Int) = writeInt(Prefs.KEY_CAPSULE_LENGTH, value)
    fun setCapsuleTimeSide(value: Int) = writeInt(Prefs.KEY_CAPSULE_TIME_SIDE, value)
    /** 0 = the signal readings stay inside the ring, 1 = they move outside it. */
    fun setSignalMode(value: Int) = writeInt(key(Prefs.KEY_SIGNAL_MODE), value)
    fun setStackedSignal(value: Boolean) = writeBoolean(Prefs.KEY_STACKED_SIGNAL, value)
    fun setDataSimOnly(value: Boolean) = writeBoolean(Prefs.KEY_DATA_SIM_ONLY, value)

    /**
     * Draws the glyph in a window of its own - see [Prefs.KEY_OVERLAY].
     *
     * ★ v80：这个开关同时是「整体尺寸」>100% 的钥匙（用户定的规则）—— 关着独立窗口
     * 时三合一挂在状态栏那一行里参与排布，放大只能放开 clipChildren，而 clipChildren
     * 对 Android 窗口边界无效，所以只能到 100%。因此关掉它时，如果当前整体尺寸已经
     * 超过 100%，一并写回 100，否则会出现"滑块还拖着 130、实际只画 100%"。
     */
    fun setOverlay(value: Boolean) {
        writeBoolean(Prefs.KEY_OVERLAY, value)
        if (!value) {
            val scaleKey = key(Prefs.KEY_GLYPH_SCALE)
            val current = preferences().getInt(scaleKey, Prefs.DEF_GLYPH_SCALE)
            if (current > Prefs.MAX_GLYPH_SCALE_TREE) {
                writeInt(scaleKey, Prefs.MAX_GLYPH_SCALE_TREE)
            }
        }
    }

    // ----------------------------------------------------------------- colours

    fun setRoleColors(value: Boolean) = writeBoolean(Prefs.KEY_ROLE_COLORS, value)
    fun setColorCriticalOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, value)
    fun setColorCriticalOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, value)
    fun setColorChargingOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, value)
    fun setColorChargingOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, value)
    fun setColorLowOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_LOW_ON_DARK, value)
    fun setColorLowOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, value)

    /** Restores all six role colours to their defaults in one write. */
    fun resetRoleColors() {
        preferences().edit(commit = true) {
            putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK)
            putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK)
            putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
        }
        push { prefs ->
            prefs.putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT)
            prefs.putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT)
            prefs.putInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            prefs.putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
        }
        notifyModule()
    }

    // ------------------------------------------------------------------ sizing

    fun setLowThreshold(value: Int) = writeInt(Prefs.KEY_LOW_THRESHOLD, value)
    fun setPowerSaveColor(value: Boolean) = writeBoolean(Prefs.KEY_POWER_SAVE_COLOR, value)
    /** ★ v104：充电同显三选项（0 隐藏 / 1 圈上 / 2 圈内），只管电量那一对。 */
    fun setChargePairMode(value: Int) = writeInt(key(Prefs.KEY_CHARGE_PAIR_MODE), value)
    /** ★ v104：信号要不要也去缺口跟闪电并排（默认关）。 */
    fun setChargeSignalAlso(value: Boolean) =
        writeBoolean(key(Prefs.KEY_CHARGE_SIGNAL_ALSO), value)
    /** ★ v82：「闪电+电量」这一对的字号（30–35，空间不足时自动缩小）。 */
    fun setChargeValueSize(value: Int) = writeInt(key(Prefs.KEY_CHARGE_VALUE_SIZE), value)
    /** ★ v104：「闪电+信号」这一对的信号大小（30–35，空间不足时自动缩小）。 */
    fun setChargeSignalSize(value: Int) = writeInt(key(Prefs.KEY_CHARGE_SIGNAL_SIZE), value)
    /** ★ v104：组合图标里 5GA 的 "A" 相对主字号的百分比（50–100）。 */
    fun setChargeSuffixAScale(value: Int) = writeInt(key(Prefs.KEY_CHARGE_SUFFIX_A_SCALE), value)
    fun setRingStroke(value: Int) = writeInt(key(Prefs.KEY_RING_STROKE), value)
    fun setArcStroke(value: Int) = writeInt(key(Prefs.KEY_ARC_STROKE), value)
    fun setValueSize(value: Int) = writeInt(key(Prefs.KEY_VALUE_SIZE), value)
    fun setValueWeight(value: Int) = writeInt(key(Prefs.KEY_VALUE_WEIGHT), value)
    fun setTypeSize(value: Int) = writeInt(key(Prefs.KEY_TYPE_SIZE), value)
    fun setOutTypeSize(value: Int) = writeInt(key(Prefs.KEY_OUT_TYPE_SIZE_DP), value)
    fun setOutSignalSize(value: Int) = writeInt(key(Prefs.KEY_OUT_SIGNAL_SIZE), value)
    fun setOutTypeMarginLeft(value: Int) = writeInt(key(Prefs.KEY_OUT_TYPE_MARGIN_LEFT), value)
    fun setOutTypeMarginRight(value: Int) = writeInt(key(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT), value)
    fun setOutSignalMargin(value: Int) = writeInt(key(Prefs.KEY_OUT_SIGNAL_MARGIN), value)
    fun setGlyphLeftOffset(value: Int) = writeInt(key(Prefs.KEY_GLYPH_LEFT_OFFSET), value)
    /**
     * ★ v82：整组左右平移（dp）。0 = 现在的位置；负值往左、正值往右。
     * 走 margin（推/放原生图标）+ translationX（平移整组）两条路，同一个量。
     */
    fun setGlyphShift(value: Int) = writeInt(key(Prefs.KEY_GLYPH_SHIFT), value)
    fun setPerfTemp(value: Boolean) = writeBoolean(key(Prefs.KEY_PERF_TEMP), value)
    fun setPerfOuterMargin(value: Int) = writeInt(key(Prefs.KEY_PERF_OUTER_MARGIN), value)
    fun setPerfOuterMarginRight(value: Int) = writeInt(key(Prefs.KEY_PERF_OUTER_MARGIN_RIGHT), value)
    fun setPerfScale(value: Int) = writeInt(key(Prefs.KEY_PERF_SCALE), value)
    fun setPerfWeight(value: Int) = writeInt(key(Prefs.KEY_PERF_WEIGHT), value)
    fun setGlyphScale(value: Int) = writeInt(key(Prefs.KEY_GLYPH_SCALE), value)
    fun setBurnInProtect(value: Boolean) = writeBoolean(Prefs.KEY_BURN_IN_PROTECT, value)
    fun setBurnInAmplitude(value: Int) = writeInt(Prefs.KEY_BURN_IN_AMPLITUDE, value)
    fun setBurnInPeriod(value: Int) = writeInt(Prefs.KEY_BURN_IN_PERIOD, value)
    fun setTypeSuffixScale(value: Int) = writeInt(key(Prefs.KEY_TYPE_SUFFIX_SCALE), value)
    fun setTypeWeight(value: Int) = writeInt(key(Prefs.KEY_TYPE_WEIGHT), value)
    fun setTrackAlpha(value: Int) = writeInt(key(Prefs.KEY_TRACK_ALPHA), value)

    // ---------------------------------------------------------------- advanced

    fun setDebugLog(value: Boolean) = writeBoolean(Prefs.KEY_DEBUG_LOG, value)

    // ----------------------------------------------------------- remote mirror

    /**
     * Pushes every value currently in the app's own file to the framework.
     *
     * <p>Called once when the service binds: while the framework is not
     * connected writes only land in the app's file, so a plain "push on write"
     * would leave SystemUI running with stale values from a previous session.
     */
    fun syncAllToFramework() {
        val snapshot = TrioSettings.from(preferences(), density)
        val bundle = snapshot.toBundle()
        push { putBundle(it, bundle) }
        notifyModule()
    }

    // -------------------------------------------------------- import / export

    /**
     * Replaces every setting with [snapshot], in one write per store.
     *
     * <p>Used by "import settings". Deliberately not a loop over the single-key
     * setters: each of those commits, pushes and broadcasts on its own, so
     * importing ~50 keys that way would broadcast 50 times and make SystemUI
     * rebuild the glyph 50 times in a row.
     */
    fun replaceAll(snapshot: TrioSettings) {
        val bundle = snapshot.toBundle()
        preferences().edit(commit = true) { putBundle(this, bundle) }
        push { putBundle(it, bundle) }
        notifyModule()
    }

    /** Copies every entry of [bundle] onto [editor], dispatching on value type. */
    private fun putBundle(editor: SharedPreferences.Editor, bundle: Bundle) {
        for (key in bundle.keySet()) {
            when (val value = bundle[key]) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
    }

    private fun writeBoolean(key: String, value: Boolean) {
        preferences().edit(commit = true) { putBoolean(key, value) }
        push { it.putBoolean(key, value) }
        notifyModule()
    }

    private fun writeInt(key: String, value: Int) {
        preferences().edit(commit = true) { putInt(key, value) }
        push { it.putInt(key, value) }
        notifyModule()
    }

    /**
     * Tells the hooked process to re-read the settings, carrying the whole
     * snapshot in the intent.
     *
     * <p>This is a second, callback-independent path: the framework's
     * remote-preference change callback is the intended mechanism, but it has
     * been observed to stop reaching SystemUI on device - the daemon database
     * held the new value while the status bar kept drawing the old one. The
     * broadcast is sent even when the service is not bound, because the hooked
     * process is up regardless and only needs the extras.
     *
     * <p>Delivery is asynchronous, so no ordering against [push] is needed; both
     * carry the same values.
     */
    private fun notifyModule() {
        runCatching {
            val intent = Intent(Prefs.ACTION_RELOAD)
                .setPackage(Prefs.SYSTEMUI_PACKAGE)
                .putExtras(TrioSettings.from(preferences(), density).toBundle())
            appContext.sendBroadcast(intent)
        }.onFailure { Log.w(TAG, "cannot notify the hooked process", it) }
    }

    /** Applies [block] to the framework's remote preferences, if connected. */
    private fun push(block: (SharedPreferences.Editor) -> Unit) {
        val bound = service
        if (bound == null) {
            // Not fatal: onServiceBind replays every value through
            // syncAllToFramework(), so the write is not lost. Logged because a
            // silent return here looks exactly like "live updates are broken".
            Log.w(TAG, "framework not bound, settings will sync on connect")
            return
        }
        runCatching {
            val editor = bound.getRemotePreferences(Prefs.NAME).edit()
            block(editor)
            // commit() rather than apply(): the value has to be visible in the
            // hooked process before the next status-bar frame is drawn.
            editor.commit()
        }.onFailure { Log.e(TAG, "cannot push settings to framework", it) }
    }

    private companion object {
        const val TAG = "SamsungDuo"
    }
}
