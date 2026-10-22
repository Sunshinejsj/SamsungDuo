package com.jsj.samsungduo;

import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * One coherent set of user settings, read from a {@link SharedPreferences}
 * file.
 *
 * <p>This type deliberately has no dependency on the Xposed API: the settings
 * app process reuses it to drive the live preview, and pulling in
 * {@code io.github.libxposed.api} there would mean shipping classes the app's
 * class loader cannot resolve.
 *
 * <p>Every field is public and mutable so the renderer can be handed a
 * snapshot cheaply; instances are published as a whole and never mutated after
 * publication.
 */
public final class TrioSettings {

    public boolean enabled;
    public boolean showWifi;
    public boolean showMobile;
    public boolean showValue;
    public boolean showBolt;
    /**
     * Where the mobile network type is drawn: {@link Prefs#MOBILE_TYPE_OFF},
     * {@link Prefs#MOBILE_TYPE_IN_RING} or {@link Prefs#MOBILE_TYPE_OUT_RING}.
     * Clamped to that range wherever it is read.
     */
    public int mobileTypeMode;
    /**
     * Draws the battery percentage enlarged in the middle of the ring, with the
     * Wi-Fi arcs shrunk into the 12 o'clock notch. While charging the bolt owns
     * the middle and the arcs stay in the notch.
     *
     * <p>Stored under the frozen key {@link Prefs#KEY_VALUE_CENTRED}.
     */
    public boolean valueCentred;
    /**
     * Which three-in-one layout is drawn: {@link Prefs#STYLE_RING},
     * {@link Prefs#STYLE_RECT} or {@link Prefs#STYLE_CAPSULE}. Clamped to that
     * range wherever it is read.
     *
     * <p>Both arrangements read the same keys, but they are not interchangeable:
     * the rectangular one ignores {@link #valueCentred} (its value has a fixed
     * place) and reads {@link #arcStroke} as the Wi-Fi arcs' stroke width, which
     * also decides their vertical position. {@link TrioAppearance} is where those
     * differences are resolved, so nothing else has to know them.
     */
    public int trioStyle;
    /**
     * Draws one row of level dots per SIM - SIM 1 on top, SIM 2 below - instead
     * of a single row for the current data SIM.
     *
     * <p>Only ever honoured while no Wi-Fi ink is on screen and no charger is
     * plugged in, because a plugged-in phone already says so with the ring's
     * colour, and one state should read as one reading - see
     * {@link TrioAppearance#dualSimRows(boolean, boolean, int)}.
     */
    public boolean dualSim;
    /**
     * Where the mobile signal is drawn: {@link Prefs#SIGNAL_IN_RING} or
     * {@link Prefs#SIGNAL_OUT_RING}. Clamped to that range wherever it is read.
     *
     * <p>Outside the glyph {@link #stackedSignal} decides what stands in the
     * signal's place - see {@link TrioAppearance#stackedOut}.
     */
    public int signalMode;
    /**
     * Draws the out-of-ring signal as four ascending capsule bars for one SIM
     * over four dots for the other, instead of letting MIUI keep its own icon.
     *
     * <p>Only meaningful while {@link #signalMode} is
     * {@link Prefs#SIGNAL_OUT_RING}.
     */
    public boolean stackedSignal;
    /**
     * Draws only the current data SIM, leaving the other out of the out-of-ring
     * reading entirely.
     *
     * <p>Only meaningful while {@link #stackedSignal} is on: with the native
     * icon in place the module draws no signal for this to narrow.
     */
    public boolean dataSimOnly;
    /**
     * Draws the glyph in a window of its own instead of in the status bar view.
     *
     * <p>Stored under the frozen key {@link Prefs#KEY_OVERLAY}. The status bar
     * window is about 43dp tall and clips what it cannot show; this flag is what
     * lets the glyph be drawn whole, at the price of hanging below the bar's own
     * background.
     */
    public boolean overlayGlyph;

    public boolean roleColors;
    public int criticalOnDark;
    public int criticalOnLight;
    public int chargingOnDark;
    public int chargingOnLight;
    public int lowOnDark;
    public int lowOnLight;
    public int lowThreshold;
    /** ★ v77：省电模式是否参与电量着色，默认开（MIUI 语义），见 Prefs#KEY_POWER_SAVE_COLOR。 */
    public boolean powerSaveColor;
    /**
     * ★ v104：充电同显三选项 —— {@link Prefs#CHARGE_PAIR_OFF} /
     * {@link Prefs#CHARGE_PAIR_GAP}（圈上）/ {@link Prefs#CHARGE_PAIR_CENTRE}（圈内）。
     *
     * <p>v82 的 {@code chargeWithValue} 布尔值已并入此项：老设置里开着的一次性
     * 迁移成"圈上"，见 {@link #readPairMode}。
     */
    public int chargePairMode;
    /** ★ v104：信号要不要也去缺口跟闪电并排（默认关）。 */
    public boolean chargeSignalAlso;
    /** ★ v82：「闪电+电量」这一对的字号（30–35，空间不足时自动缩小）。 */
    public int chargeValueSize;
    /** ★ v104：「闪电+信号」这一对的信号大小（30–35，与电量字号同一档位）。 */
    public int chargeSignalSize;
    /** ★ v104：组合图标里 5GA 的 "A" 相对主字号的百分比（50–100）。 */
    public int chargeSuffixAScale;

    /**
     * ★ 胶囊三按钮显式控制（2026-10-21 用户拍板）。仅 STYLE_CAPSULE 有意义，
     * 圆环/矩形读不到这三个键（键名不带 cap_ 前缀，只此一样式使用）。
     * 见 {@link Prefs#KEY_CAPSULE_RIGHT} 等处常量注释。
     */
    /** 按钮1 右列内容：{@link Prefs#CAP_RIGHT_VALUE} / {@link Prefs#CAP_RIGHT_SIGNAL}。 */
    public int capsuleRight;
    /** 按钮2 缺口内容：{@link Prefs#CAP_GAP_NONE} 等。 */
    public int capsuleGap;
    /** 按钮3 充电闪电位置：{@link Prefs#CAP_BOLT_HIDDEN} 等。 */
    public int capsuleBoltPos;

    public int ringStroke;
    public int arcStroke;
    public int valueSize;
    /** Numeric font weight of the value/type text, 100..900. */
    public int valueWeight;
    /**
     * Size and weight of the centred network type ("5G", "5GA"). Independent of
     * {@link #valueSize}/{@link #valueWeight}: the type is a label of its own,
     * tuned separately from the digits it shares the ring with.
     */
    public int typeSize;
    /**
     * Font size of the network type when it is drawn outside the ring. A field
     * of its own rather than a reuse of {@link #typeSize}: the two labels live
     * in different spaces (ring design space vs. real status-bar pixels) and are
     * tuned separately.
     */
    public int outTypeSize;
    /**
     * Height of the stacked out-of-ring signal, in dp, resolved against the
     * display's density. A field of its own rather than a reuse of
     * {@link #outTypeSize}: that one describes a glyph drawn on a canvas
     * this module owns, whereas this one is a view whose box has to be measured
     * and reserved in the status bar's layout, so it is tuned on its own scale.
     */
    public int outSignalSize;
    /**
     * The gap the out-of-ring label keeps on each side, in dp. Two fields rather
     * than one because the label's two neighbours differ - the native icon row
     * outside it, the out-of-ring reading (or the battery) inside it - and the
     * shipped spacing tunes the two independently.
     */
    public int outTypeMarginLeft;
    public int outTypeMarginRight;
    /**
     * The gap on the out-of-ring reading's outer side — which, the reading being
     * the group's leftmost part, is the gap between the whole group and the
     * native icons. Stored in <b>half-dp</b> (0.5dp steps); may be negative to
     * pull the group back over the native icons' own drawable padding.
     */
    public int outSignalMargin;
    /**
     * An extra nudge leftwards for the whole group — the ring plus whichever
     * out-of-ring parts are drawn — on top of the gap it keeps from the native
     * icon on its left. Stored in half-dp. Negative tightens the gap; see
     * {@link Prefs#KEY_GLYPH_LEFT_OFFSET}.
     */
    public int glyphLeftOffset;
    /**
     * ★ v82：整组左右平移（dp，1dp 一档）。0 = 现在的位置（贴状态栏右缘）。
     *
     * <p>与 {@link #glyphLeftOffset} 是两件事：那个只改"与左侧原生图标的间距"
     * （动的是 margin），这个动的是<b>整组的位置</b> —— 同时写 margin（把原生图标
     * 推走/放回）与 translationX（平移整组），两条路用同一个量，组内相对距离不变。
     * 用户原话："整组移动，往左移动把原生图标往左挤压，往右移动原生图标补位回来"。
     */
    public int glyphShift;

    /** v73：电池温度读数开关。★ v75：功率读数已删，只剩这一项。 */
    public boolean perfTemp;
    /** v73：读数整体缩放（%），乘在自适应字号之上。 */
    public int perfScale;
    /** v73：读数字重。 */
    public int perfWeight;
    /** ★ v75：温度读数与左侧邻居之间的空隙（dp），见 Prefs#KEY_PERF_OUTER_MARGIN。 */
    public int perfOuterMargin;
    /** ★ v76：温度读数与右侧三合一之间的空隙（dp），见 Prefs#KEY_PERF_OUTER_MARGIN_RIGHT。 */
    public int perfOuterMarginRight;
    /** v73：三合一整体尺寸（%）。 */
    public int glyphScale;

    /**
     * ★ v72 防烧屏总开关。状态栏不在三星原生防烧屏的适用范围里（反编译实证），
     * 图标位置又固定不动，所以自己加。
     */
    public boolean burnInProtect;
    /** ★ v72 防烧屏幅度，单位 1/4 dp（滑块一格 0.25dp）。 */
    public int burnInAmplitude;
    /** ★ v72 防烧屏往复周期，单位秒。 */
    public int burnInPeriodSec;
    /**
     * Scale of a trailing "A" in the network type, as a percentage of the main
     * size. 100 means no shrink; the reference "5GA" is 65.
     */
    public int typeSuffixScale;
    public int typeWeight;
    public int trackAlpha;

    /** Only used by the settings app: the hooked side reads this via {@code TrioConfig}. */
    public boolean debugLog;

    public static TrioSettings defaults() {
        TrioSettings s = new TrioSettings();
        s.enabled = Prefs.DEF_ENABLED;
        s.showWifi = Prefs.DEF_SHOW_WIFI;
        s.showMobile = Prefs.DEF_SHOW_MOBILE;
        s.showValue = Prefs.DEF_SHOW_VALUE;
        s.showBolt = Prefs.DEF_SHOW_BOLT;
        s.mobileTypeMode = Prefs.DEF_MOBILE_TYPE_MODE;
        s.valueCentred = Prefs.DEF_VALUE_CENTRED;
        s.trioStyle = Prefs.DEF_TRIO_STYLE;
        s.dualSim = Prefs.DEF_DUAL_SIM;
        s.signalMode = Prefs.DEF_SIGNAL_MODE;
        s.stackedSignal = Prefs.DEF_STACKED_SIGNAL;
        s.dataSimOnly = Prefs.DEF_DATA_SIM_ONLY;
        s.overlayGlyph = Prefs.DEF_OVERLAY;

        s.roleColors = Prefs.DEF_ROLE_COLORS;
        s.criticalOnDark = Prefs.DEF_COLOR_CRITICAL_ON_DARK;
        s.criticalOnLight = Prefs.DEF_COLOR_CRITICAL_ON_LIGHT;
        s.chargingOnDark = Prefs.DEF_COLOR_CHARGING_ON_DARK;
        s.chargingOnLight = Prefs.DEF_COLOR_CHARGING_ON_LIGHT;
        s.lowOnDark = Prefs.DEF_COLOR_LOW_ON_DARK;
        s.lowOnLight = Prefs.DEF_COLOR_LOW_ON_LIGHT;
        s.lowThreshold = Prefs.DEF_LOW_THRESHOLD;
        s.powerSaveColor = Prefs.DEF_POWER_SAVE_COLOR;
        s.chargePairMode = Prefs.DEF_CHARGE_PAIR_MODE;
        s.chargeSignalAlso = Prefs.DEF_CHARGE_SIGNAL_ALSO;
        s.chargeValueSize = Prefs.DEF_CHARGE_VALUE_SIZE;
        s.chargeSignalSize = Prefs.DEF_CHARGE_SIGNAL_SIZE;
        s.chargeSuffixAScale = Prefs.DEF_CHARGE_SUFFIX_A_SCALE;

        s.capsuleRight = Prefs.DEF_CAPSULE_RIGHT;
        s.capsuleGap = Prefs.DEF_CAPSULE_GAP;
        s.capsuleBoltPos = Prefs.DEF_CAPSULE_BOLT_POS;

        s.ringStroke = Prefs.DEF_RING_STROKE;
        s.arcStroke = Prefs.DEF_ARC_STROKE;
        s.valueSize = Prefs.DEF_VALUE_SIZE;
        s.valueWeight = Prefs.DEF_VALUE_WEIGHT;
        s.typeSize = Prefs.DEF_TYPE_SIZE;
        s.outTypeSize = Prefs.DEF_OUT_TYPE_SIZE_DP;
        s.outSignalSize = Prefs.DEF_OUT_SIGNAL_SIZE;
        s.outTypeMarginLeft = Prefs.DEF_OUT_TYPE_MARGIN_LEFT;
        s.outTypeMarginRight = Prefs.DEF_OUT_TYPE_MARGIN_RIGHT;
        s.outSignalMargin = Prefs.DEF_OUT_SIGNAL_MARGIN;
        s.glyphLeftOffset = Prefs.DEF_GLYPH_LEFT_OFFSET;
        s.glyphShift = Prefs.DEF_GLYPH_SHIFT;
        s.perfTemp = Prefs.DEF_PERF_TEMP;
        s.perfScale = Prefs.DEF_PERF_SCALE;
        s.perfWeight = Prefs.DEF_PERF_WEIGHT;
        s.perfOuterMargin = Prefs.DEF_PERF_OUTER_MARGIN;
        s.perfOuterMarginRight = Prefs.DEF_PERF_OUTER_MARGIN_RIGHT;
        s.glyphScale = Prefs.DEF_GLYPH_SCALE;
        s.burnInProtect = Prefs.DEF_BURN_IN_PROTECT;
        s.burnInAmplitude = Prefs.DEF_BURN_IN_AMPLITUDE;
        s.burnInPeriodSec = Prefs.DEF_BURN_IN_PERIOD;
        s.typeSuffixScale = Prefs.DEF_TYPE_SUFFIX_SCALE;
        s.typeWeight = Prefs.DEF_TYPE_WEIGHT;
        s.trackAlpha = Prefs.DEF_TRACK_ALPHA;
        s.debugLog = Prefs.DEF_DEBUG_LOG;
        return s;
    }

    /**
     * Reads every key, falling back to the shared defaults. Numeric keys are
     * clamped here rather than at draw time so the renderer can trust them.
     *
     * <p>Same as {@link #from(SharedPreferences, float)} at the density the
     * retired raw-pixel keys were authored on. Kept for callers that have no
     * display to ask - the workbench stubs - which never hold a migrated value
     * anyway.
     */
    public static TrioSettings from(SharedPreferences p) {
        return from(p, Prefs.AUTHORED_DENSITY);
    }

    /**
     * Reads every key, falling back to the shared defaults. Numeric keys are
     * clamped here rather than at draw time so the renderer can trust them.
     *
     * @param density the display's density, needed only to convert a value left
     *        in the retired raw-pixel {@link Prefs#KEY_OUT_TYPE_SIZE} by an
     *        install that predates {@link Prefs#KEY_OUT_TYPE_SIZE_DP}; ignored
     *        once the new key exists
     */
    public static TrioSettings from(SharedPreferences p, float density) {
        TrioSettings s = new TrioSettings();
        s.enabled = p.getBoolean(Prefs.KEY_ENABLED, Prefs.DEF_ENABLED);
        s.showWifi = p.getBoolean(Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI);
        s.showMobile = p.getBoolean(Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE);
        s.showValue = p.getBoolean(Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE);
        s.showBolt = p.getBoolean(Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT);
        // 先确定样式，下面所有几何/位置字段按当前命名空间（cap_ 或原键）读取。
        final int style = Prefs.clamp(
                p.getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE),
                Prefs.MIN_TRIO_STYLE, Prefs.MAX_TRIO_STYLE);
        final boolean capsule = style == Prefs.STYLE_CAPSULE;
        s.trioStyle = style;
        s.mobileTypeMode = readMobileTypeMode(p, Prefs.capKeyAware(Prefs.KEY_MOBILE_TYPE_MODE, capsule));
        s.valueCentred = p.getBoolean(Prefs.capKeyAware(Prefs.KEY_VALUE_CENTRED, capsule), Prefs.DEF_VALUE_CENTRED);
        s.dualSim = p.getBoolean(Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM);
        s.signalMode = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_SIGNAL_MODE, capsule), Prefs.DEF_SIGNAL_MODE),
                Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE);
        s.stackedSignal = p.getBoolean(Prefs.KEY_STACKED_SIGNAL, Prefs.DEF_STACKED_SIGNAL);
        s.dataSimOnly = p.getBoolean(Prefs.KEY_DATA_SIM_ONLY, Prefs.DEF_DATA_SIM_ONLY);
        s.overlayGlyph = p.getBoolean(Prefs.KEY_OVERLAY, Prefs.DEF_OVERLAY);

        s.roleColors = p.getBoolean(Prefs.KEY_ROLE_COLORS, Prefs.DEF_ROLE_COLORS);
        s.criticalOnDark = p.getInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK);
        s.criticalOnLight = p.getInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT);
        s.chargingOnDark = p.getInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK);
        s.chargingOnLight = p.getInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT);
        s.lowOnDark = p.getInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK);
        s.lowOnLight = p.getInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT);
        s.lowThreshold = Prefs.clamp(
                p.getInt(Prefs.KEY_LOW_THRESHOLD, Prefs.DEF_LOW_THRESHOLD),
                Prefs.MIN_LOW_THRESHOLD, Prefs.MAX_LOW_THRESHOLD);
        s.powerSaveColor = p.getBoolean(Prefs.KEY_POWER_SAVE_COLOR, Prefs.DEF_POWER_SAVE_COLOR);
        s.chargePairMode = readPairMode(p, Prefs.capKeyAware(Prefs.KEY_CHARGE_PAIR_MODE, capsule),
                Prefs.KEY_CHARGE_WITH_VALUE, true);
        s.chargeSignalAlso = p.getBoolean(
                Prefs.capKeyAware(Prefs.KEY_CHARGE_SIGNAL_ALSO, capsule), Prefs.DEF_CHARGE_SIGNAL_ALSO);
        s.chargeValueSize = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_VALUE_SIZE, capsule), Prefs.DEF_CHARGE_VALUE_SIZE),
                Prefs.MIN_CHARGE_VALUE_SIZE, Prefs.MAX_CHARGE_VALUE_SIZE);
        s.chargeSignalSize = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_SIGNAL_SIZE, capsule), Prefs.DEF_CHARGE_SIGNAL_SIZE),
                Prefs.MIN_CHARGE_SIGNAL_SIZE, Prefs.MAX_CHARGE_SIGNAL_SIZE);
        s.chargeSuffixAScale = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_SUFFIX_A_SCALE, capsule), Prefs.DEF_CHARGE_SUFFIX_A_SCALE),
                Prefs.MIN_CHARGE_SUFFIX_A_SCALE, Prefs.MAX_CHARGE_SUFFIX_A_SCALE);

        // ★ 胶囊三按钮：仅胶囊读（键不带 cap_ 前缀，圆环/矩形不读）。
        s.capsuleRight = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_RIGHT, Prefs.DEF_CAPSULE_RIGHT),
                Prefs.MIN_CAPSULE_RIGHT, Prefs.MAX_CAPSULE_RIGHT);
        s.capsuleGap = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_GAP, Prefs.DEF_CAPSULE_GAP),
                Prefs.MIN_CAPSULE_GAP, Prefs.MAX_CAPSULE_GAP);
        s.capsuleBoltPos = Prefs.clamp(
                p.getInt(Prefs.KEY_CAPSULE_BOLT_POS, Prefs.DEF_CAPSULE_BOLT_POS),
                Prefs.MIN_CAPSULE_BOLT_POS, Prefs.MAX_CAPSULE_BOLT_POS);

        s.ringStroke = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_RING_STROKE, capsule), Prefs.DEF_RING_STROKE),
                Prefs.MIN_RING_STROKE, Prefs.MAX_RING_STROKE);
        s.arcStroke = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_ARC_STROKE, capsule), Prefs.DEF_ARC_STROKE),
                Prefs.MIN_ARC_STROKE, Prefs.MAX_ARC_STROKE);
        s.valueSize = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_VALUE_SIZE, capsule), Prefs.DEF_VALUE_SIZE),
                Prefs.MIN_VALUE_SIZE, Prefs.MAX_VALUE_SIZE);
        s.valueWeight = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_VALUE_WEIGHT, capsule), Prefs.DEF_VALUE_WEIGHT),
                Prefs.MIN_VALUE_WEIGHT, Prefs.MAX_VALUE_WEIGHT);
        s.typeSize = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_TYPE_SIZE, capsule), Prefs.DEF_TYPE_SIZE),
                Prefs.MIN_TYPE_SIZE, Prefs.MAX_TYPE_SIZE);
        s.outTypeSize = readOutTypeSize(p, Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_SIZE_DP, capsule), density);
        s.outSignalSize = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_SIGNAL_SIZE, capsule), Prefs.DEF_OUT_SIGNAL_SIZE),
                Prefs.MIN_OUT_SIGNAL_SIZE, Prefs.MAX_OUT_SIGNAL_SIZE);
        s.outTypeMarginLeft = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, capsule), Prefs.DEF_OUT_TYPE_MARGIN_LEFT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outTypeMarginRight = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, capsule), Prefs.DEF_OUT_TYPE_MARGIN_RIGHT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outSignalMargin = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_SIGNAL_MARGIN, capsule), Prefs.DEF_OUT_SIGNAL_MARGIN),
                Prefs.MIN_OUT_SIGNAL_MARGIN, Prefs.MAX_OUT_SIGNAL_MARGIN);
        s.glyphLeftOffset = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_LEFT_OFFSET, capsule), Prefs.DEF_GLYPH_LEFT_OFFSET),
                Prefs.MIN_GLYPH_LEFT_OFFSET, Prefs.MAX_GLYPH_LEFT_OFFSET);
        s.glyphShift = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_SHIFT, capsule), Prefs.DEF_GLYPH_SHIFT),
                Prefs.MIN_GLYPH_SHIFT, Prefs.MAX_GLYPH_SHIFT);
        s.perfTemp = p.getBoolean(Prefs.capKeyAware(Prefs.KEY_PERF_TEMP, capsule), Prefs.DEF_PERF_TEMP);
        s.perfOuterMargin = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_OUTER_MARGIN, capsule), Prefs.DEF_PERF_OUTER_MARGIN),
                Prefs.MIN_PERF_OUTER_MARGIN, Prefs.MAX_PERF_OUTER_MARGIN);
        s.perfOuterMarginRight = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_OUTER_MARGIN_RIGHT, capsule), Prefs.DEF_PERF_OUTER_MARGIN_RIGHT),
                Prefs.MIN_PERF_OUTER_MARGIN_RIGHT, Prefs.MAX_PERF_OUTER_MARGIN_RIGHT);
        s.perfScale = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_SCALE, capsule), Prefs.DEF_PERF_SCALE),
                Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE);
        s.perfWeight = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_WEIGHT, capsule), Prefs.DEF_PERF_WEIGHT),
                Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT);
        // ★ v80：>100% 那一档由「独立窗口绘制」看着（见 Prefs.MAX_GLYPH_SCALE_TREE）。
        //   关着独立窗口 = 三合一挂在状态栏那一行里参与排布，放大只能放开
        //   clipChildren，而 clipChildren 对 Android 窗口边界无效 → 只能到 100%。
        //   开着 = 独立小窗口，可以把窗口自身加高 → 解锁 130%。
        //   在读取时就压掉，而不是只在设置页限制：否则用户关掉独立窗口后，
        //   prefs 里仍存着 130，三处里表现为"滑块拖着 130、实际只画 100%"。
        final int glyphScaleCap = s.overlayGlyph
                ? Prefs.MAX_GLYPH_SCALE
                : Prefs.MAX_GLYPH_SCALE_TREE;
        s.glyphScale = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_SCALE, capsule), Prefs.DEF_GLYPH_SCALE),
                Prefs.MIN_GLYPH_SCALE, glyphScaleCap);
        s.burnInProtect = p.getBoolean(Prefs.KEY_BURN_IN_PROTECT, Prefs.DEF_BURN_IN_PROTECT);
        s.burnInAmplitude = Prefs.clamp(
                p.getInt(Prefs.KEY_BURN_IN_AMPLITUDE, Prefs.DEF_BURN_IN_AMPLITUDE),
                Prefs.MIN_BURN_IN_AMPLITUDE, Prefs.MAX_BURN_IN_AMPLITUDE);
        s.burnInPeriodSec = Prefs.clamp(
                p.getInt(Prefs.KEY_BURN_IN_PERIOD, Prefs.DEF_BURN_IN_PERIOD),
                Prefs.MIN_BURN_IN_PERIOD, Prefs.MAX_BURN_IN_PERIOD);
        s.typeSuffixScale = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_TYPE_SUFFIX_SCALE, capsule), Prefs.DEF_TYPE_SUFFIX_SCALE),
                Prefs.MIN_TYPE_SUFFIX_SCALE, Prefs.MAX_TYPE_SUFFIX_SCALE);
        s.typeWeight = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_TYPE_WEIGHT, capsule), Prefs.DEF_TYPE_WEIGHT),
                Prefs.MIN_TYPE_WEIGHT, Prefs.MAX_TYPE_WEIGHT);
        s.trackAlpha = Prefs.clamp(
                p.getInt(Prefs.capKeyAware(Prefs.KEY_TRACK_ALPHA, capsule), Prefs.DEF_TRACK_ALPHA),
                Prefs.MIN_TRACK_ALPHA, Prefs.MAX_TRACK_ALPHA);
        s.debugLog = p.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG);
        return s;
    }

    /**
     * Reads the three-way network-type placement, migrating the legacy boolean.
     *
     * <p>The new int key wins whenever it is present. Only while it is absent -
     * i.e. on an install that predates it - is the old
     * {@link Prefs#KEY_SHOW_MOBILE_TYPE} boolean consulted, and an existing
     * "on" maps to {@link Prefs#MOBILE_TYPE_IN_RING} so that user keeps the
     * placement they had. The legacy key is never written again, so this runs at
     * most until the user's first write of the new key.
     */
    private static int readMobileTypeMode(SharedPreferences p, String key) {
        if (p.contains(key)) {
            return Prefs.clamp(
                    p.getInt(key, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        return p.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)
                ? Prefs.MOBILE_TYPE_IN_RING
                : Prefs.MOBILE_TYPE_OFF;
    }

    /**
     * Reads the out-of-ring type size, migrating the retired raw-pixel key.
     *
     * <p>The dp key wins whenever it is present. Only an install that predates
     * it - one that may hold a raw-pixel value in {@link Prefs#KEY_OUT_TYPE_SIZE}
     * - has that value converted once, by dividing by the display density that
     * gives the pixels their physical size. An install that never touched the
     * old slider has no old key at all and simply gets the dp default. The old
     * key is never written again, so like
     * {@link #readMobileTypeMode(SharedPreferences)} this runs at most until the
     * user's first write of the new key.
     *
     * <p>The conversion rounds before clamping so a user's tuned value lands as
     * close as the new range allows: 32px at density 3 reads as 11dp, which the
     * 8..26dp range holds unchanged.
     */
    private static int readOutTypeSize(SharedPreferences p, String dpKey, float density) {
        if (p.contains(dpKey)) {
            return Prefs.clamp(
                    p.getInt(dpKey, Prefs.DEF_OUT_TYPE_SIZE_DP),
                    Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
        }
        if (p.contains(Prefs.KEY_OUT_TYPE_SIZE)) {
            final float safeDensity = density > 0f ? density : Prefs.AUTHORED_DENSITY;
            return Prefs.clamp(
                    Math.round(p.getInt(Prefs.KEY_OUT_TYPE_SIZE, Prefs.DEF_OUT_TYPE_SIZE)
                            / safeDensity),
                    Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
        }
        return Prefs.clamp(Prefs.DEF_OUT_TYPE_SIZE_DP,
                Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
    }

    /**
     * The broadcast-extras mirror of {@link #readMobileTypeMode(SharedPreferences)}.
     *
     * <p>The legacy fallback cannot actually fire here - the settings app only
     * ever puts the new int key into the bundle - but it is kept for symmetry
     * with the stored read, so a bundle built by an older writer still migrates
     * the same way instead of silently resetting to off.
     */
    private static int readMobileTypeMode(Bundle bundle, String key) {
        if (bundle.containsKey(key)) {
            return Prefs.clamp(
                    bundle.getInt(key, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        return bundle.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)
                ? Prefs.MOBILE_TYPE_IN_RING
                : Prefs.MOBILE_TYPE_OFF;
    }

    /**
     * An independent copy. A snapshot is published as a whole and never mutated
     * afterwards, so updating a single field means copying and republishing.
     */
    public TrioSettings copy() {
        TrioSettings s = new TrioSettings();
        s.enabled = enabled;
        s.showWifi = showWifi;
        s.showMobile = showMobile;
        s.showValue = showValue;
        s.showBolt = showBolt;
        s.mobileTypeMode = mobileTypeMode;
        s.valueCentred = valueCentred;
        s.trioStyle = trioStyle;
        s.dualSim = dualSim;
        s.signalMode = signalMode;
        s.stackedSignal = stackedSignal;
        s.dataSimOnly = dataSimOnly;
        s.overlayGlyph = overlayGlyph;

        s.roleColors = roleColors;
        s.criticalOnDark = criticalOnDark;
        s.criticalOnLight = criticalOnLight;
        s.chargingOnDark = chargingOnDark;
        s.chargingOnLight = chargingOnLight;
        s.lowOnDark = lowOnDark;
        s.lowOnLight = lowOnLight;
        s.lowThreshold = lowThreshold;
        s.powerSaveColor = powerSaveColor;
        s.chargePairMode = chargePairMode;
        s.chargeSignalAlso = chargeSignalAlso;
        s.chargeValueSize = chargeValueSize;
        s.chargeSignalSize = chargeSignalSize;
        s.chargeSuffixAScale = chargeSuffixAScale;

        s.capsuleRight = capsuleRight;
        s.capsuleGap = capsuleGap;
        s.capsuleBoltPos = capsuleBoltPos;

        s.ringStroke = ringStroke;
        s.arcStroke = arcStroke;
        s.valueSize = valueSize;
        s.valueWeight = valueWeight;
        s.typeSize = typeSize;
        s.outTypeSize = outTypeSize;
        s.outSignalSize = outSignalSize;
        s.outTypeMarginLeft = outTypeMarginLeft;
        s.outTypeMarginRight = outTypeMarginRight;
        s.outSignalMargin = outSignalMargin;
        s.glyphLeftOffset = glyphLeftOffset;
        s.glyphShift = glyphShift;
        s.perfTemp = perfTemp;
        s.perfScale = perfScale;
        s.perfWeight = perfWeight;
        s.perfOuterMargin = perfOuterMargin;
        s.perfOuterMarginRight = perfOuterMarginRight;
        s.glyphScale = glyphScale;
        s.burnInProtect = burnInProtect;
        s.burnInAmplitude = burnInAmplitude;
        s.burnInPeriodSec = burnInPeriodSec;
        s.typeSuffixScale = typeSuffixScale;
        s.typeWeight = typeWeight;
        s.trackAlpha = trackAlpha;

        s.debugLog = debugLog;
        return s;
    }

    /**
     * Copies the one field named by {@code key} out of {@code src}.
     *
     * <p>The framework's remote map is only guaranteed to be fresh for the key it
     * just reported: the daemon merges a diff into the map and then fires one
     * change callback per key in that diff, so a full re-read here would take
     * stale values for every key whose diff never arrived. That is exactly how a
     * value published by another path can be silently reverted by the next
     * unrelated setting change, which is why only the reported key is applied and
     * the rest of an already-correct snapshot is left alone.
     *
     * @return true when the key maps to a field of this type
     */
    public boolean applyKeyFrom(TrioSettings src, String key) {
        if (src == null || key == null) {
            return false;
        }
        key = Prefs.baseKey(key);
        switch (key) {
            case Prefs.KEY_ENABLED: enabled = src.enabled; return true;
            case Prefs.KEY_SHOW_WIFI: showWifi = src.showWifi; return true;
            case Prefs.KEY_SHOW_MOBILE: showMobile = src.showMobile; return true;
            case Prefs.KEY_SHOW_VALUE: showValue = src.showValue; return true;
            case Prefs.KEY_SHOW_BOLT: showBolt = src.showBolt; return true;
            case Prefs.KEY_MOBILE_TYPE_MODE: mobileTypeMode = src.mobileTypeMode; return true;
            case Prefs.KEY_VALUE_CENTRED: valueCentred = src.valueCentred; return true;
            case Prefs.KEY_TRIO_STYLE: trioStyle = src.trioStyle; return true;
            case Prefs.KEY_DUAL_SIM: dualSim = src.dualSim; return true;
            case Prefs.KEY_SIGNAL_MODE: signalMode = src.signalMode; return true;
            case Prefs.KEY_STACKED_SIGNAL: stackedSignal = src.stackedSignal; return true;
            case Prefs.KEY_DATA_SIM_ONLY: dataSimOnly = src.dataSimOnly; return true;
            case Prefs.KEY_OVERLAY: overlayGlyph = src.overlayGlyph; return true;
            case Prefs.KEY_ROLE_COLORS: roleColors = src.roleColors; return true;
            case Prefs.KEY_COLOR_CRITICAL_ON_DARK: criticalOnDark = src.criticalOnDark; return true;
            case Prefs.KEY_COLOR_CRITICAL_ON_LIGHT: criticalOnLight = src.criticalOnLight; return true;
            case Prefs.KEY_COLOR_CHARGING_ON_DARK: chargingOnDark = src.chargingOnDark; return true;
            case Prefs.KEY_COLOR_CHARGING_ON_LIGHT: chargingOnLight = src.chargingOnLight; return true;
            case Prefs.KEY_COLOR_LOW_ON_DARK: lowOnDark = src.lowOnDark; return true;
            case Prefs.KEY_COLOR_LOW_ON_LIGHT: lowOnLight = src.lowOnLight; return true;
            case Prefs.KEY_LOW_THRESHOLD: lowThreshold = src.lowThreshold; return true;
            case Prefs.KEY_POWER_SAVE_COLOR: powerSaveColor = src.powerSaveColor; return true;
            case Prefs.KEY_CHARGE_PAIR_MODE: chargePairMode = src.chargePairMode; return true;
            case Prefs.KEY_CHARGE_SIGNAL_ALSO: chargeSignalAlso = src.chargeSignalAlso; return true;
            case Prefs.KEY_CHARGE_VALUE_SIZE: chargeValueSize = src.chargeValueSize; return true;
            case Prefs.KEY_CHARGE_SIGNAL_SIZE: chargeSignalSize = src.chargeSignalSize; return true;
            case Prefs.KEY_CHARGE_SUFFIX_A_SCALE: chargeSuffixAScale = src.chargeSuffixAScale; return true;
            case Prefs.KEY_CAPSULE_RIGHT: capsuleRight = src.capsuleRight; return true;
            case Prefs.KEY_CAPSULE_GAP: capsuleGap = src.capsuleGap; return true;
            case Prefs.KEY_CAPSULE_BOLT_POS: capsuleBoltPos = src.capsuleBoltPos; return true;
            case Prefs.KEY_RING_STROKE: ringStroke = src.ringStroke; return true;
            case Prefs.KEY_ARC_STROKE: arcStroke = src.arcStroke; return true;
            case Prefs.KEY_VALUE_SIZE: valueSize = src.valueSize; return true;
            case Prefs.KEY_VALUE_WEIGHT: valueWeight = src.valueWeight; return true;
            case Prefs.KEY_TYPE_SIZE: typeSize = src.typeSize; return true;
            case Prefs.KEY_OUT_TYPE_SIZE_DP: outTypeSize = src.outTypeSize; return true;
            case Prefs.KEY_OUT_SIGNAL_SIZE: outSignalSize = src.outSignalSize; return true;
            case Prefs.KEY_OUT_TYPE_MARGIN_LEFT: outTypeMarginLeft = src.outTypeMarginLeft; return true;
            case Prefs.KEY_OUT_TYPE_MARGIN_RIGHT: outTypeMarginRight = src.outTypeMarginRight; return true;
            case Prefs.KEY_OUT_SIGNAL_MARGIN: outSignalMargin = src.outSignalMargin; return true;
            case Prefs.KEY_GLYPH_LEFT_OFFSET: glyphLeftOffset = src.glyphLeftOffset; return true;
            case Prefs.KEY_GLYPH_SHIFT: glyphShift = src.glyphShift; return true;
            case Prefs.KEY_PERF_TEMP: perfTemp = src.perfTemp; return true;
            case Prefs.KEY_PERF_OUTER_MARGIN: perfOuterMargin = src.perfOuterMargin; return true;
            case Prefs.KEY_PERF_OUTER_MARGIN_RIGHT: perfOuterMarginRight = src.perfOuterMarginRight; return true;
            case Prefs.KEY_PERF_SCALE: perfScale = src.perfScale; return true;
            case Prefs.KEY_PERF_WEIGHT: perfWeight = src.perfWeight; return true;
            case Prefs.KEY_GLYPH_SCALE: glyphScale = src.glyphScale; return true;
            case Prefs.KEY_BURN_IN_PROTECT: burnInProtect = src.burnInProtect; return true;
            case Prefs.KEY_BURN_IN_AMPLITUDE: burnInAmplitude = src.burnInAmplitude; return true;
            case Prefs.KEY_BURN_IN_PERIOD: burnInPeriodSec = src.burnInPeriodSec; return true;
            case Prefs.KEY_TYPE_SUFFIX_SCALE: typeSuffixScale = src.typeSuffixScale; return true;
            case Prefs.KEY_TYPE_WEIGHT: typeWeight = src.typeWeight; return true;
            case Prefs.KEY_TRACK_ALPHA: trackAlpha = src.trackAlpha; return true;
            case Prefs.KEY_DEBUG_LOG: debugLog = src.debugLog; return true;
            default: return false;
        }
    }

    /**
     * Reads a snapshot delivered as broadcast extras, falling back to the same
     * defaults as {@link #from(SharedPreferences)}. Numeric values are clamped
     * identically, because the extras come from another process and are just as
     * untrusted as the stored file.
     *
     * <p>{@code bundle} may be null or partial; every key falls back on its own.
     */
    public static TrioSettings fromBundle(Bundle bundle) {
        TrioSettings s = new TrioSettings();
        if (bundle == null) {
            return defaults();
        }
        s.enabled = bundle.getBoolean(Prefs.KEY_ENABLED, Prefs.DEF_ENABLED);
        s.showWifi = bundle.getBoolean(Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI);
        s.showMobile = bundle.getBoolean(Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE);
        s.showValue = bundle.getBoolean(Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE);
        s.showBolt = bundle.getBoolean(Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT);
        // 先确定样式，下面所有几何/位置字段按当前命名空间（cap_ 或原键）读取。
        final int style = Prefs.clamp(
                bundle.getInt(Prefs.KEY_TRIO_STYLE, Prefs.DEF_TRIO_STYLE),
                Prefs.MIN_TRIO_STYLE, Prefs.MAX_TRIO_STYLE);
        final boolean capsule = style == Prefs.STYLE_CAPSULE;
        s.trioStyle = style;
        s.mobileTypeMode = readMobileTypeMode(bundle, Prefs.capKeyAware(Prefs.KEY_MOBILE_TYPE_MODE, capsule));
        s.valueCentred = bundle.getBoolean(Prefs.capKeyAware(Prefs.KEY_VALUE_CENTRED, capsule), Prefs.DEF_VALUE_CENTRED);
        s.dualSim = bundle.getBoolean(Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM);
        s.signalMode = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_SIGNAL_MODE, capsule), Prefs.DEF_SIGNAL_MODE),
                Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE);
        s.stackedSignal = bundle.getBoolean(Prefs.KEY_STACKED_SIGNAL, Prefs.DEF_STACKED_SIGNAL);
        s.dataSimOnly = bundle.getBoolean(Prefs.KEY_DATA_SIM_ONLY, Prefs.DEF_DATA_SIM_ONLY);
        s.overlayGlyph = bundle.getBoolean(Prefs.KEY_OVERLAY, Prefs.DEF_OVERLAY);

        s.roleColors = bundle.getBoolean(Prefs.KEY_ROLE_COLORS, Prefs.DEF_ROLE_COLORS);
        s.criticalOnDark = bundle.getInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK);
        s.criticalOnLight = bundle.getInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT);
        s.chargingOnDark = bundle.getInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK);
        s.chargingOnLight = bundle.getInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT);
        s.lowOnDark = bundle.getInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK);
        s.lowOnLight = bundle.getInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT);
        s.lowThreshold = Prefs.clamp(
                bundle.getInt(Prefs.KEY_LOW_THRESHOLD, Prefs.DEF_LOW_THRESHOLD),
                Prefs.MIN_LOW_THRESHOLD, Prefs.MAX_LOW_THRESHOLD);
        s.powerSaveColor = bundle.getBoolean(Prefs.KEY_POWER_SAVE_COLOR, Prefs.DEF_POWER_SAVE_COLOR);
        s.chargePairMode = readPairMode(bundle, Prefs.capKeyAware(Prefs.KEY_CHARGE_PAIR_MODE, capsule),
                Prefs.KEY_CHARGE_WITH_VALUE, true);
        s.chargeSignalAlso = bundle.getBoolean(
                Prefs.capKeyAware(Prefs.KEY_CHARGE_SIGNAL_ALSO, capsule), Prefs.DEF_CHARGE_SIGNAL_ALSO);
        s.chargeValueSize = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_VALUE_SIZE, capsule), Prefs.DEF_CHARGE_VALUE_SIZE),
                Prefs.MIN_CHARGE_VALUE_SIZE, Prefs.MAX_CHARGE_VALUE_SIZE);
        s.chargeSignalSize = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_SIGNAL_SIZE, capsule), Prefs.DEF_CHARGE_SIGNAL_SIZE),
                Prefs.MIN_CHARGE_SIGNAL_SIZE, Prefs.MAX_CHARGE_SIGNAL_SIZE);
        s.chargeSuffixAScale = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_SUFFIX_A_SCALE, capsule), Prefs.DEF_CHARGE_SUFFIX_A_SCALE),
                Prefs.MIN_CHARGE_SUFFIX_A_SCALE, Prefs.MAX_CHARGE_SUFFIX_A_SCALE);

        s.capsuleRight = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_RIGHT, Prefs.DEF_CAPSULE_RIGHT),
                Prefs.MIN_CAPSULE_RIGHT, Prefs.MAX_CAPSULE_RIGHT);
        s.capsuleGap = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_GAP, Prefs.DEF_CAPSULE_GAP),
                Prefs.MIN_CAPSULE_GAP, Prefs.MAX_CAPSULE_GAP);
        s.capsuleBoltPos = Prefs.clamp(
                bundle.getInt(Prefs.KEY_CAPSULE_BOLT_POS, Prefs.DEF_CAPSULE_BOLT_POS),
                Prefs.MIN_CAPSULE_BOLT_POS, Prefs.MAX_CAPSULE_BOLT_POS);

        s.ringStroke = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_RING_STROKE, capsule), Prefs.DEF_RING_STROKE),
                Prefs.MIN_RING_STROKE, Prefs.MAX_RING_STROKE);
        s.arcStroke = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_ARC_STROKE, capsule), Prefs.DEF_ARC_STROKE),
                Prefs.MIN_ARC_STROKE, Prefs.MAX_ARC_STROKE);
        s.valueSize = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_VALUE_SIZE, capsule), Prefs.DEF_VALUE_SIZE),
                Prefs.MIN_VALUE_SIZE, Prefs.MAX_VALUE_SIZE);
        s.valueWeight = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_VALUE_WEIGHT, capsule), Prefs.DEF_VALUE_WEIGHT),
                Prefs.MIN_VALUE_WEIGHT, Prefs.MAX_VALUE_WEIGHT);
        s.typeSize = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_TYPE_SIZE, capsule), Prefs.DEF_TYPE_SIZE),
                Prefs.MIN_TYPE_SIZE, Prefs.MAX_TYPE_SIZE);
        s.outTypeSize = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_SIZE_DP, capsule), Prefs.DEF_OUT_TYPE_SIZE_DP),
                Prefs.MIN_OUT_TYPE_SIZE_DP, Prefs.MAX_OUT_TYPE_SIZE_DP);
        s.outSignalSize = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_SIGNAL_SIZE, capsule), Prefs.DEF_OUT_SIGNAL_SIZE),
                Prefs.MIN_OUT_SIGNAL_SIZE, Prefs.MAX_OUT_SIGNAL_SIZE);
        s.outTypeMarginLeft = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, capsule), Prefs.DEF_OUT_TYPE_MARGIN_LEFT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outTypeMarginRight = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, capsule), Prefs.DEF_OUT_TYPE_MARGIN_RIGHT),
                Prefs.MIN_OUT_TYPE_MARGIN, Prefs.MAX_OUT_TYPE_MARGIN);
        s.outSignalMargin = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_OUT_SIGNAL_MARGIN, capsule), Prefs.DEF_OUT_SIGNAL_MARGIN),
                Prefs.MIN_OUT_SIGNAL_MARGIN, Prefs.MAX_OUT_SIGNAL_MARGIN);
        s.glyphLeftOffset = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_LEFT_OFFSET, capsule), Prefs.DEF_GLYPH_LEFT_OFFSET),
                Prefs.MIN_GLYPH_LEFT_OFFSET, Prefs.MAX_GLYPH_LEFT_OFFSET);
        s.glyphShift = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_SHIFT, capsule), Prefs.DEF_GLYPH_SHIFT),
                Prefs.MIN_GLYPH_SHIFT, Prefs.MAX_GLYPH_SHIFT);
        s.perfTemp = bundle.getBoolean(Prefs.capKeyAware(Prefs.KEY_PERF_TEMP, capsule), Prefs.DEF_PERF_TEMP);
        s.perfOuterMargin = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_OUTER_MARGIN, capsule), Prefs.DEF_PERF_OUTER_MARGIN),
                Prefs.MIN_PERF_OUTER_MARGIN, Prefs.MAX_PERF_OUTER_MARGIN);
        s.perfOuterMarginRight = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_OUTER_MARGIN_RIGHT, capsule), Prefs.DEF_PERF_OUTER_MARGIN_RIGHT),
                Prefs.MIN_PERF_OUTER_MARGIN_RIGHT, Prefs.MAX_PERF_OUTER_MARGIN_RIGHT);
        s.perfScale = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_SCALE, capsule), Prefs.DEF_PERF_SCALE),
                Prefs.MIN_PERF_SCALE, Prefs.MAX_PERF_SCALE);
        s.perfWeight = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_PERF_WEIGHT, capsule), Prefs.DEF_PERF_WEIGHT),
                Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT);
        // ★ v80：同 from(SharedPreferences) —— 广播这条路也要把「独立窗口绘制」
        //   关着时的 130% 压回 100%，否则 SystemUI 侧与设置页看到的值会不一致。
        final int bundleScaleCap = s.overlayGlyph
                ? Prefs.MAX_GLYPH_SCALE
                : Prefs.MAX_GLYPH_SCALE_TREE;
        s.glyphScale = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_SCALE, capsule), Prefs.DEF_GLYPH_SCALE),
                Prefs.MIN_GLYPH_SCALE, bundleScaleCap);
        s.burnInProtect = bundle.getBoolean(Prefs.KEY_BURN_IN_PROTECT, Prefs.DEF_BURN_IN_PROTECT);
        s.burnInAmplitude = Prefs.clamp(
                bundle.getInt(Prefs.KEY_BURN_IN_AMPLITUDE, Prefs.DEF_BURN_IN_AMPLITUDE),
                Prefs.MIN_BURN_IN_AMPLITUDE, Prefs.MAX_BURN_IN_AMPLITUDE);
        s.burnInPeriodSec = Prefs.clamp(
                bundle.getInt(Prefs.KEY_BURN_IN_PERIOD, Prefs.DEF_BURN_IN_PERIOD),
                Prefs.MIN_BURN_IN_PERIOD, Prefs.MAX_BURN_IN_PERIOD);
        s.typeSuffixScale = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_TYPE_SUFFIX_SCALE, capsule), Prefs.DEF_TYPE_SUFFIX_SCALE),
                Prefs.MIN_TYPE_SUFFIX_SCALE, Prefs.MAX_TYPE_SUFFIX_SCALE);
        s.typeWeight = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_TYPE_WEIGHT, capsule), Prefs.DEF_TYPE_WEIGHT),
                Prefs.MIN_TYPE_WEIGHT, Prefs.MAX_TYPE_WEIGHT);
        s.trackAlpha = Prefs.clamp(
                bundle.getInt(Prefs.capKeyAware(Prefs.KEY_TRACK_ALPHA, capsule), Prefs.DEF_TRACK_ALPHA),
                Prefs.MIN_TRACK_ALPHA, Prefs.MAX_TRACK_ALPHA);
        s.debugLog = bundle.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG);
        return s;
    }

    /**
     * The same read on top of an existing snapshot: keys the bundle carries are
     * taken from it, keys it leaves out keep the value {@code base} already had.
     *
     * <p>Seeding matters because the bundle is untrusted input. If an absent key
     * fell back to the shared default instead, a single missing extra would wipe
     * the user's configuration - including {@code enabled}, which would flip back
     * to true and draw the trio glyph over an otherwise native status bar.
     */
    public static TrioSettings fromBundle(Bundle bundle, TrioSettings base) {
        TrioSettings s = (base == null) ? defaults() : base.copy();
        if (bundle == null) {
            return s;
        }
        TrioSettings delivered = fromBundle(bundle);
        for (String key : bundle.keySet()) {
            s.applyKeyFrom(delivered, key);
        }
        return s;
    }

    /**
     * ★ v104：读一个「充电同显」三选项，并顺手把 v82 的布尔键迁移过来。
     *
     * <p>新键缺失（老用户第一次升上来）时看旧布尔键：开着 = "圈上"，关着 = 关闭。
     * 旧键也不存在就走默认值。两个容器各一份重载，因为它们没有共同的取值接口。
     *
     * @param legacyKey 要迁移的旧布尔键；{@code null} 表示这一项没有旧键
     */
    private static int readPairMode(SharedPreferences p, String key, String legacyKey,
                                    boolean hasLegacy) {
        final int raw = p.getInt(key, -1);
        if (raw >= 0) {
            return Prefs.clamp(raw, Prefs.CHARGE_PAIR_OFF, Prefs.CHARGE_PAIR_CENTRE);
        }
        if (hasLegacy && legacyKey != null
                && p.getBoolean(legacyKey, Prefs.DEF_CHARGE_WITH_VALUE)) {
            return Prefs.CHARGE_PAIR_GAP;
        }
        return Prefs.CHARGE_PAIR_OFF;
    }

    /** @see #readPairMode(SharedPreferences, String, String, boolean) */
    private static int readPairMode(Bundle b, String key, String legacyKey, boolean hasLegacy) {
        final int raw = b.getInt(key, -1);
        if (raw >= 0) {
            return Prefs.clamp(raw, Prefs.CHARGE_PAIR_OFF, Prefs.CHARGE_PAIR_CENTRE);
        }
        if (hasLegacy && legacyKey != null
                && b.getBoolean(legacyKey, Prefs.DEF_CHARGE_WITH_VALUE)) {
            return Prefs.CHARGE_PAIR_GAP;
        }
        return Prefs.CHARGE_PAIR_OFF;
    }

    /**
     * The inverse of {@link #fromBundle(Bundle)}, using the same pref key names
     * so the two sides cannot drift apart. The settings app sends this to the
     * hooked process after every write.
     */
    public Bundle toBundle() {
        Bundle b = new Bundle();
        final boolean capsule = trioStyle == Prefs.STYLE_CAPSULE;
        b.putBoolean(Prefs.KEY_ENABLED, enabled);
        b.putBoolean(Prefs.KEY_SHOW_WIFI, showWifi);
        b.putBoolean(Prefs.KEY_SHOW_MOBILE, showMobile);
        b.putBoolean(Prefs.KEY_SHOW_VALUE, showValue);
        b.putBoolean(Prefs.KEY_SHOW_BOLT, showBolt);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_MOBILE_TYPE_MODE, capsule), mobileTypeMode);
        b.putBoolean(Prefs.capKeyAware(Prefs.KEY_VALUE_CENTRED, capsule), valueCentred);
        b.putInt(Prefs.KEY_TRIO_STYLE, trioStyle);
        b.putBoolean(Prefs.KEY_DUAL_SIM, dualSim);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_SIGNAL_MODE, capsule), signalMode);
        b.putBoolean(Prefs.KEY_STACKED_SIGNAL, stackedSignal);
        b.putBoolean(Prefs.KEY_DATA_SIM_ONLY, dataSimOnly);
        b.putBoolean(Prefs.KEY_OVERLAY, overlayGlyph);

        b.putBoolean(Prefs.KEY_ROLE_COLORS, roleColors);
        b.putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, criticalOnDark);
        b.putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, criticalOnLight);
        b.putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, chargingOnDark);
        b.putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, chargingOnLight);
        b.putInt(Prefs.KEY_COLOR_LOW_ON_DARK, lowOnDark);
        b.putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, lowOnLight);
        b.putInt(Prefs.KEY_LOW_THRESHOLD, lowThreshold);
        b.putBoolean(Prefs.KEY_POWER_SAVE_COLOR, powerSaveColor);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_PAIR_MODE, capsule), chargePairMode);
        b.putBoolean(Prefs.capKeyAware(Prefs.KEY_CHARGE_SIGNAL_ALSO, capsule), chargeSignalAlso);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_VALUE_SIZE, capsule), chargeValueSize);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_SIGNAL_SIZE, capsule), chargeSignalSize);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_CHARGE_SUFFIX_A_SCALE, capsule), chargeSuffixAScale);

        b.putInt(Prefs.KEY_CAPSULE_RIGHT, capsuleRight);
        b.putInt(Prefs.KEY_CAPSULE_GAP, capsuleGap);
        b.putInt(Prefs.KEY_CAPSULE_BOLT_POS, capsuleBoltPos);

        b.putInt(Prefs.capKeyAware(Prefs.KEY_RING_STROKE, capsule), ringStroke);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_ARC_STROKE, capsule), arcStroke);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_VALUE_SIZE, capsule), valueSize);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_VALUE_WEIGHT, capsule), valueWeight);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_TYPE_SIZE, capsule), typeSize);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_SIZE_DP, capsule), outTypeSize);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_OUT_SIGNAL_SIZE, capsule), outSignalSize);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, capsule), outTypeMarginLeft);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, capsule), outTypeMarginRight);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_OUT_SIGNAL_MARGIN, capsule), outSignalMargin);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_LEFT_OFFSET, capsule), glyphLeftOffset);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_SHIFT, capsule), glyphShift);
        b.putBoolean(Prefs.capKeyAware(Prefs.KEY_PERF_TEMP, capsule), perfTemp);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_PERF_OUTER_MARGIN, capsule), perfOuterMargin);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_PERF_OUTER_MARGIN_RIGHT, capsule), perfOuterMarginRight);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_PERF_SCALE, capsule), perfScale);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_PERF_WEIGHT, capsule), perfWeight);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_GLYPH_SCALE, capsule), glyphScale);
        b.putBoolean(Prefs.KEY_BURN_IN_PROTECT, burnInProtect);
        b.putInt(Prefs.KEY_BURN_IN_AMPLITUDE, burnInAmplitude);
        b.putInt(Prefs.KEY_BURN_IN_PERIOD, burnInPeriodSec);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_TYPE_SUFFIX_SCALE, capsule), typeSuffixScale);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_TYPE_WEIGHT, capsule), typeWeight);
        b.putInt(Prefs.capKeyAware(Prefs.KEY_TRACK_ALPHA, capsule), trackAlpha);

        b.putBoolean(Prefs.KEY_DEBUG_LOG, debugLog);
        return b;
    }

    /**
     * Field-wise equality. Exists for one caller - the boot-time re-read in
     * {@code TrioConfig}, which must not notify the listeners (and re-fold every
     * container) when the delayed read saw the same values the first one did -
     * but a full comparison is also what a snapshot type ought to answer.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrioSettings)) {
            return false;
        }
        final TrioSettings s = (TrioSettings) o;
        return enabled == s.enabled
                && showWifi == s.showWifi
                && showMobile == s.showMobile
                && showValue == s.showValue
                && showBolt == s.showBolt
                && mobileTypeMode == s.mobileTypeMode
                && valueCentred == s.valueCentred
                && trioStyle == s.trioStyle
                && dualSim == s.dualSim
                && signalMode == s.signalMode
                && stackedSignal == s.stackedSignal
                && dataSimOnly == s.dataSimOnly
                // Overlay decides where the glyph is drawn rather than what it
                // draws, which is why it was easy to leave out - but it is a
                // stored setting like any other, and leaving it out of equals
                // is not harmless: the settings screen holds the snapshot in a
                // mutableStateOf, whose default policy is equality, so a write
                // that changed only this field compared equal to the previous
                // snapshot, no recomposition was scheduled, and the switch went
                // on showing its old state until some other write happened to
                // recompose the row. issue 25.
                && overlayGlyph == s.overlayGlyph
                && roleColors == s.roleColors
                && criticalOnDark == s.criticalOnDark
                && criticalOnLight == s.criticalOnLight
                && chargingOnDark == s.chargingOnDark
                && chargingOnLight == s.chargingOnLight
                && lowOnDark == s.lowOnDark
                && lowOnLight == s.lowOnLight
                && lowThreshold == s.lowThreshold
                && powerSaveColor == s.powerSaveColor
                && chargePairMode == s.chargePairMode
                && chargeSignalAlso == s.chargeSignalAlso
                && chargeValueSize == s.chargeValueSize
                && chargeSignalSize == s.chargeSignalSize
                && chargeSuffixAScale == s.chargeSuffixAScale
                && capsuleRight == s.capsuleRight
                && capsuleGap == s.capsuleGap
                && capsuleBoltPos == s.capsuleBoltPos
                && ringStroke == s.ringStroke
                && arcStroke == s.arcStroke
                && valueSize == s.valueSize
                && valueWeight == s.valueWeight
                && typeSize == s.typeSize
                && outTypeSize == s.outTypeSize
                && outSignalSize == s.outSignalSize
                && outTypeMarginLeft == s.outTypeMarginLeft
                && outTypeMarginRight == s.outTypeMarginRight
                && outSignalMargin == s.outSignalMargin
                && glyphLeftOffset == s.glyphLeftOffset
                && glyphShift == s.glyphShift
                && perfTemp == s.perfTemp
                && perfOuterMargin == s.perfOuterMargin
                && perfOuterMarginRight == s.perfOuterMarginRight
                && perfScale == s.perfScale
                && perfWeight == s.perfWeight
                && glyphScale == s.glyphScale
                && burnInProtect == s.burnInProtect
                && burnInAmplitude == s.burnInAmplitude
                && burnInPeriodSec == s.burnInPeriodSec
                && typeSuffixScale == s.typeSuffixScale
                && typeWeight == s.typeWeight
                && trackAlpha == s.trackAlpha
                && debugLog == s.debugLog;
    }

    @Override
    public int hashCode() {
        // Fields are small ints and booleans; a cheap fold is enough. The class
        // is used as a value only through equals() - nothing hashes it.
        int h = (enabled ? 1 : 0) ^ (mobileTypeMode << 1) ^ signalMode ^ trioStyle;
        return h != 0 ? h : 1;
    }
}
