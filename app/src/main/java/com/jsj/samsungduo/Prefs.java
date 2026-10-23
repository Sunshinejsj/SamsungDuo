package com.jsj.samsungduo;

/**
 * The single source of truth for every setting shared between the settings app
 * and the hooked SystemUI process.
 *
 * <p>Both sides talk over the Xposed framework's remote preferences: the
 * settings app writes, the hooked process only reads. Because the framework
 * casts stored values to the requested type without conversion, every key is
 * read and written with exactly one type for the whole lifetime of the module.
 * Keep the {@code DEF_*} defaults here as well so both sides agree when the
 * framework cannot provide remote preferences (embedded mode, or a first run
 * before the user ever opened the settings screen).
 */
public final class Prefs {

    private Prefs() {
    }

    /**
     * The display density the retired raw-pixel sizes were authored against -
     * the test device's density 3. Only the one-time migration consults this,
     * when it has no real display to ask (the desktop workbenches).
     */
    public static final float AUTHORED_DENSITY = 3f;

    /** Remote-preference group name. Also the settings app's file name. */
    public static final String NAME = "samsungduo_settings";

    /**
     * Explicit broadcast the settings app sends to SystemUI after every write,
     * carrying the whole snapshot as extras.
     *
     * <p>Remote preferences remain the store of record, but the framework's
     * "a value changed" callback has been observed to never reach the hooked
     * process on this device: the daemon database held the new {@code enabled}
     * value while SystemUI kept drawing from the old one. The broadcast is a
     * second, callback-independent path for exactly that case.
     *
     * <p>Lives here rather than in {@code TrioConfig} because the settings app
     * must be able to name it: {@code TrioConfig} references
     * {@code io.github.libxposed.api}, which is {@code compileOnly} and so is
     * not on the app's runtime class path.
     */
    public static final String ACTION_RELOAD = "com.jsj.samsungduo.action.RELOAD";

    /**
     * Package the reload broadcast is addressed to. SystemUI registers the
     * receiver, so an explicit package keeps the intent away from every other
     * process on the device.
     */
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    // ------------------------------------------------------------------ keys
    public static final String KEY_ENABLED = "enabled";
    /**
     * Draws the glyph in a window of its own instead of inside the status bar
     * view - see {@code TrioOverlay}.
     *
     * <p>The status bar window is only {@code status_bar_height} tall (121px,
     * about 43dp, on this device) and clips everything past that rectangle, so a
     * glyph that wants more height than the bar has can never be drawn whole in
     * the bar, whatever the view tree does.
     *
     * <p>On by default, and the only route that draws the glyph whole: growing
     * the status bar's own row to make room was tried and abandoned - MIUI's
     * measure chain re-derives the size of every box on the way up, so each level
     * fixed exposed the next one, and the window's own height was taken back by
     * MIUI's controller on its own schedule. The cost of this route is placement:
     * the screen's top edge is hard, so a glyph taller than the bar sits a few dp
     * below the icons beside it instead of on their centre line.
     */
    /**
     * Stored under a name that did not exist while this route was the fallback.
     *
     * <p>The earlier default (off) was written into the framework's remote
     * preferences by the settings app on first open, and a stored value wins over
     * any new default - an install that has it would keep the window route off no
     * matter what {@link #DEF_OVERLAY} says. The framework does no type conversion
     * and never forgets a key, so the new route gets a key with no history.
     */
    public static final String KEY_OVERLAY = "glyph_window";
    public static final String KEY_SHOW_WIFI = "show_wifi";
    public static final String KEY_SHOW_MOBILE = "show_mobile";
    public static final String KEY_SHOW_VALUE = "show_value";
    public static final String KEY_SHOW_BOLT = "show_bolt";
    /**
     * Where the mobile network type ("5G", "4G"...) is drawn: hidden, inside the
     * ring, or outside it.
     *
     * <p>An {@code int} rather than the boolean it replaced. The framework does
     * no type conversion, so {@link #KEY_SHOW_MOBILE_TYPE} could not simply be
     * re-read as an int: on an existing install the stored boolean would make
     * {@code getInt} throw {@code ClassCastException}. A new key keeps every
     * installed value readable, and the old one is consulted once during
     * migration - see {@code TrioSettings.readMobileTypeMode}.
     */
    public static final String KEY_MOBILE_TYPE_MODE = "mobile_type_mode";
    /**
     * Legacy, read-only for migration.
     *
     * <p>Superseded by {@link #KEY_MOBILE_TYPE_MODE}. Never write it again; the
     * only remaining reader is the one-time migration in
     * {@code TrioSettings.from(SharedPreferences)}, which maps an existing
     * "on" to {@link #MOBILE_TYPE_IN_RING} while the new key is absent.
     */
    public static final String KEY_SHOW_MOBILE_TYPE = "show_mobile_type";
    /**
     * Draws the battery percentage enlarged in the middle of the ring, with the
     * Wi-Fi arcs shrunk into the 12 o'clock notch.
     *
     * <p>The stored name is frozen at {@code "swap_wifi_value"} and must never
     * change: it is a boolean the user may already have set, and rewriting the
     * key would silently reset that choice on every existing install. Only the
     * <em>label</em> was renamed - the setting used to be presented as a swap
     * between the two slots, which described the mechanism rather than what the
     * user gets.
     */
    public static final String KEY_VALUE_CENTRED = "swap_wifi_value";
    /**
     * Which of the three-in-one layouts is drawn: the ring arrangement or the
     * rectangular one.
     *
     * <p>An {@code int} holding one of the {@code STYLE_*} constants below. Like
     * every other key it keeps exactly one type for the whole lifetime of the
     * module, so the value is only ever read back with {@code getInt}.
     */
    public static final String KEY_TRIO_STYLE = "trio_style";
    /**
     * Draws the mobile level as two rows of four dots - one SIM each - instead of
     * the single row of the current data SIM.
     *
     * <p>Only honoured while there is no Wi-Fi ink and no charging bolt: with the
     * arcs or the bolt on screen the glyph's top and bottom slots are taken, and
     * the user asked for the two-SIM reading precisely as the alternative to
     * those. The top row is SIM 1 (slot 0) and the bottom row SIM 2 (slot 1).
     * With a single SIM, or while the per-SIM levels cannot be read, the single
     * row stays and keeps showing the current data SIM.
     *
     * <p>A brand-new key with a brand-new name. The framework does no type
     * conversion, so a key may only ever be read back with the type it was
     * written with - see the note on {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_DUAL_SIM = "dual_sim_signal";
    /**
     * Where the mobile signal is drawn: inside the glyph, or as a status-bar view
     * outside it.
     *
     * <p>An {@code int} holding one of the {@code SIGNAL_*} constants below.
     * Outside the glyph the module stops drawing the level dots entirely and -
     * depending on {@link #KEY_STACKED_SIGNAL} - either hands the native signal
     * icon back or draws one of its own next to the battery.
     *
     * <p>A brand-new key with a brand-new name: the framework does no type
     * conversion, so a key may only ever be read back with the type it was
     * written with - see the note on {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_SIGNAL_MODE = "signal_mode";
    /**
     * Draws the out-of-ring signal as four ascending capsule bars for one SIM
     * over four dots for the other, instead of leaving the native icon in place.
     *
     * <p>Only ever honoured while {@link #KEY_SIGNAL_MODE} is
     * {@link #SIGNAL_OUT_RING}: inside the glyph the rings' own level dots are the
     * signal, and there is nothing here for this switch to change. Turning it off
     * outside the glyph is not "draw the default dots" but "let MIUI draw its own
     * signal icon", which is what makes it a meaningful choice rather than a
     * second way of saying the same thing.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_STACKED_SIGNAL = "stacked_signal";
    /**
     * Draws only the current data SIM's out-of-ring signal row, leaving the other
     * SIM out entirely.
     *
     * <p>Gated on {@link #KEY_STACKED_SIGNAL} rather than on anything of its own:
     * while the native icon is in place the module draws no signal at all, so
     * there would be nothing for this to narrow. Being switched off by
     * <em>another</em> row is fine; a row that switches itself off is not.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_DATA_SIM_ONLY = "data_sim_only";

    public static final String KEY_ROLE_COLORS = "role_colors";
    public static final String KEY_COLOR_CRITICAL_ON_DARK = "color_critical_on_dark";
    public static final String KEY_COLOR_CRITICAL_ON_LIGHT = "color_critical_on_light";
    public static final String KEY_COLOR_CHARGING_ON_DARK = "color_charging_on_dark";
    public static final String KEY_COLOR_CHARGING_ON_LIGHT = "color_charging_on_light";
    public static final String KEY_COLOR_LOW_ON_DARK = "color_low_on_dark";
    public static final String KEY_COLOR_LOW_ON_LIGHT = "color_low_on_light";
    public static final String KEY_LOW_THRESHOLD = "low_threshold";

    public static final String KEY_RING_STROKE = "ring_stroke";
    public static final String KEY_ARC_STROKE = "arc_stroke";
    public static final String KEY_VALUE_SIZE = "value_size";
    public static final String KEY_VALUE_WEIGHT = "value_weight";
    /** Font size of the network type drawn inside the ring. */
    public static final String KEY_TYPE_SIZE = "type_size";
    /**
     * Font size of the network type when it is drawn <em>outside</em> the ring.
     *
     * <p>Deliberately a separate key from {@link #KEY_TYPE_SIZE}: the in-ring
     * label lives in the ring's 120x120 design space and its size is tuned
     * against the ring geometry, whereas the out-of-ring label is a plain
     * status-bar TextView laid out in real pixels next to MIUI's own icons, so
     * it is tuned on its own scale - and its range reaches higher accordingly.
     * Sharing one key between the two spaces is what this key fixes.
     *
     * <p>An {@code int}, like {@link #KEY_TYPE_SIZE}. A brand-new key name and
     * never a reuse of the old one: the framework does no type conversion, so
     * every key has exactly one type for the whole lifetime of the module.
     *
     * @deprecated Superseded by {@link #KEY_OUT_TYPE_SIZE_DP}. This key held raw
     * pixels, which made the same slider value a different physical size on
     * every density - the exact defect that moved {@link #KEY_OUT_SIGNAL_SIZE}
     * to a dp a release earlier. Kept readable only for the one-time migration;
     * never written again.
     */
    @Deprecated
    public static final String KEY_OUT_TYPE_SIZE = "out_type_size";
    /**
     * Font size of the network type outside the ring, in density pixels.
     *
     * <p>Replaces the retired raw-pixel key above. The label is a real view in
     * the status bar's pixel space, but the <em>setting</em> is a length the
     * user reasons about, and only a dp means the same slider value lands on the
     * same physical size on every screen - same reasoning as
     * {@link #KEY_OUT_SIGNAL_SIZE}, which moved to a dp for exactly this. The
     * conversion to pixels happens at the point of use, where a {@code Resources}
     * is at hand; this class stays Android-free.
     *
     * <p>Migration follows the {@link #KEY_MOBILE_TYPE_MODE} pattern: the new
     * key wins whenever present, and only an install predating it - which may
     * hold a raw-pixel value in the old key - has that value converted once
     * (px &divide; density) into the new key's terms. Users who never touched the
     * old slider get the new default, which equals the old default at the
     * density it was authored on.
     *
     * <p>An {@code int}, a brand-new name, never a reuse - see
     * {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_OUT_TYPE_SIZE_DP = "out_type_size_dp";
    /**
     * The stacked out-of-ring signal's height, in density pixels.
     *
     * <p>A dp rather than a percentage of anything, because the row this reading
     * stands in does not keep one height. At rest the battery container measures
     * one way, and the moment the control centre is pulled down MIUI lays the
     * same container out through the whole {@code statusBars} inset instead - on
     * the test device 88px becomes 134px, so a reading sized as a share of its
     * row grew by half just because a shade was opened. A dp is resolved against
     * the display's density and never against whoever is laying the row out.
     *
     * <p>Named {@code _dp} rather than reusing the shorter key a percentage once
     * used: the unit changed, and an install that still holds a percentage in the
     * old key would have it read as a dp. Nothing shipped with that key, so no
     * migration is owed and the old name is simply retired.
     *
     * <p>An {@code int}, like {@link #KEY_OUT_TYPE_SIZE}: the framework does no
     * type conversion, so every key has exactly one type for the whole lifetime
     * of the module.
     */
    public static final String KEY_OUT_SIGNAL_SIZE = "out_signal_size_dp";
    /**
     * The gap the out-of-ring network type keeps on its two sides, in density
     * pixels.
     *
     * <p>Two keys rather than one: in the status bar the label is flanked by
     * different things on each side - the native icon row on the outside, the
     * out-of-ring signal reading (or the battery) on the inside - and the two
     * gaps are tuned against those different neighbours.
     *
     * <p>Both are dplike rather than pixels for the same reason as
     * {@link #KEY_OUT_SIGNAL_SIZE}: the label lives in the status bar's real
     * pixel space, whose row height MIUI changes under the control centre, so a
     * length measured in pixels would drift with the shade.
     *
     * <p>Each defaults to the single gap the label used to have hard-coded, so
     * an install that never touches these keeps exactly the spacing it had.
     *
     * <p>New names, never a reuse: the framework does no type conversion, so
     * every key has exactly one type for the whole lifetime of the module - see
     * the note on {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_OUT_TYPE_MARGIN_LEFT = "out_type_margin_left_dp";
    public static final String KEY_OUT_TYPE_MARGIN_RIGHT = "out_type_margin_right_dp";
    /**
     * The gap between the out-of-ring signal reading and the battery meter, in
     * density pixels.
     *
     * <p>A margin rather than a position. This length is reserved out of the
     * native icon row exactly like the label margins above, so widening it moves
     * the reading away from the battery <em>and</em> pushes the icons over by the
     * same amount. An earlier version of this setting was a free position offset
     * instead: it moved the view without reserving anything, so the reading slid
     * out of the room set aside for it and painted over the neighbouring icons.
     *
     * <p>dp for the same reason as the label margins: the status bar's real pixel
     * space, whose row height MIUI changes under the control centre.
     *
     * <p>Defaults to the gap that was hard-coded before it was settable, so an
     * install that never touches it keeps exactly the spacing it had.
     *
     * <p>A brand-new name, never a reuse of the retired offset keys - see
     * {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_OUT_SIGNAL_MARGIN = "out_signal_margin_dp";
    /**
     * An extra nudge leftwards for the ring, added on top of the gap it already
     * keeps from whichever native icon sits on its left.
     *
     * <p>Stored in <b>half-dp</b> units so the control can move in 0.5dp steps;
     * see {@link #MIN_GLYPH_LEFT_OFFSET} for the range.
     *
     * <p>Why it has to be able to go negative: once the automatic gap is 0 there
     * is nothing left for a layout margin to remove — the distance that remains
     * is transparent padding inside the native icons' own drawables, which no
     * margin can see. The only way to tighten it is to pull the ring back over
     * that padding, and how much of the padding is safe to eat depends on the
     * icon set on the device, so it is a control rather than a constant.
     *
     * <p>It moves the <b>whole group</b>, not just the ring: the out-of-ring
     * reading and the type label sit to the ring's left, so one of them is the
     * group's left edge and the nudge lands on whichever of the three is
     * leftmost. One control therefore spaces the lot, which is what the user
     * asked for — and is why a version that wrote it only on the ring went dead
     * the moment the out-of-ring parts were switched on.
     *
     * <p>Read every frame, so moving it takes effect without a re-mount.
     */
    public static final String KEY_GLYPH_LEFT_OFFSET = "glyph_left_offset_dp";
    /**
     * ★ v82：整组左右平移（dp）。与 {@link #KEY_GLYPH_LEFT_OFFSET} 是两件事：
     * 那个只改与左侧原生图标的间距，这个动的是整组位置 —— margin（推/放原生图标）
     * 与 translationX（平移整组）两条路共用一个量。0 = 现在的位置。
     */
    public static final String KEY_GLYPH_SHIFT = "glyph_shift_dp";

    /**
     * ★ v73：<b>电池温度</b>读数开关（默认关）。整摄氏度，
     * 温度是电池自身属性，不接充电器同样显示。
     *
     * <p>★ v75：充电功率读数已删除 —— 用户实测三星上不显示，且打开开关
     * 还会让状态栏闪一下，明确说"三星不需要这个"。
     */
    public static final String KEY_PERF_TEMP = "perf_temp";
    /**
     * ★ v73：两项读数在自适应字号之上的<b>整体</b>缩放，百分比 60–150。
     *
     * <p>它乘在每一种答案上（单项读数、两项都开的自适应结果、量不到宿主的
     * 兜底值都算），所以只开一个开关时拖动它也看得见效果。
     */
    public static final String KEY_PERF_SCALE = "perf_scale_pct";
    /**
     * ★ v73：两项读数文字的笔画粗细，100 最细、900 最粗。
     *
     * <p>独立于「网络类型字重」：那一个同时管 5G 标签，共用会把旁边的标签
     * 一起加粗。字重还决定行高，两项都开时的自适应字号会跟着重算。
     */
    public static final String KEY_PERF_WEIGHT = "perf_weight";
    /**
     * ★ v75：温度读数与左侧邻居（电池 / 环外信号 / 环外类型）之间的空隙，dp。
     *
     * <p>与 {@link #KEY_OUT_SIGNAL_MARGIN} 同语义：margin 而不是位置偏移，
     * 从原生图标行里真实占位；它是整组最左端时，这份空隙就是"读数与电池"的距离。
     */
    public static final String KEY_PERF_OUTER_MARGIN = "perf_outer_margin_dp";
    /** ★ v76：温度读数<b>右侧</b>边距（与三合一的空隙）的键。 */
    public static final String KEY_PERF_OUTER_MARGIN_RIGHT = "perf_outer_margin_right_dp";
    /**
     * ★ v75：三合一<b>整体</b>尺寸，百分比 60–120。
     *
     * <p>≤100%：只缩<b>绘制</b>、不动视图 —— 视图那一块是占位，动了就会挤压
     * 原生图标（v73 的既定行为）。
     *
     * <p>&gt;100%：视图随之一起放大（否则墨迹超出视图画布，被 View 自身的
     * canvas 裁掉——{@code clipChildren=false} 管不了那一层），同时跳过
     * "行高上限"并对所在容器关掉 {@code clipChildren}，让溢出部分画到
     * 状态栏行外。这是用户明确要的"可以出状态栏，只要图像显示完全"。
     */
    public static final String KEY_GLYPH_SCALE = "glyph_scale_pct";

    /**
     * ★ v72：<b>防烧屏</b>总开关。
     *
     * <p>三星有整套防烧屏（AOD / 锁屏 / 息屏 / 电源菜单），但反编译确认
     * <b>状态栏不在其适用范围</b>（{@code grep statusbar*Burn} 返回空）。
     * 而状态栏图标是"位置固定、高对比度、常年不动"的典型残影来源，所以自己加，
     * 做法沿用三星自己的 {@code setTranslationX/Y}（不触发重新布局）。
     * 默认开：屏幕残影不可逆，默认保护比默认裸奔安全。
     */
    public static final String KEY_BURN_IN_PROTECT = "burn_in_protect";
    /**
     * ★ v72：防烧屏幅度。存储单位是 <b>1/4 dp</b>（滑块一格 0.25dp），
     * 因为这里要的是"比 1dp 更细一档"的微调；见 {@link #MAX_BURN_IN_AMPLITUDE}。
     */
    public static final String KEY_BURN_IN_AMPLITUDE = "burn_in_amplitude_qdp";
    /** ★ v72：防烧屏一个完整往复的周期，单位秒。 */
    public static final String KEY_BURN_IN_PERIOD = "burn_in_period_sec";
    /**
     * How large a trailing "A" is drawn against the rest of the network type, as
     * a percentage.
     *
     * <p>The reference draws "5GA" with the "A" noticeably smaller than the
     * "5G", sitting on the same baseline; 65 reproduces the reference's
     * {@code A height / main height} of about 56/86. 100 turns the shrink off
     * and restores the single-size label.
     *
     * <p>One key for both places the type is drawn - the ring canvas and the
     * out-of-ring status-bar label - because the reference's proportion is a
     * property of the label, not of where it happens to sit.
     *
     * <p>A new name, never a reuse - see {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_TYPE_SUFFIX_SCALE = "type_suffix_scale";
    public static final String KEY_TYPE_WEIGHT = "type_weight";
    public static final String KEY_TRACK_ALPHA = "track_alpha";

    public static final String KEY_DEBUG_LOG = "debug_log";

    // ------------------------------------------------------------------ capsule namespace
    /**
     * ★ 胶囊（STYLE_CAPSULE）独立命名空间。
     *
     * <p>胶囊复用 {@link TrioAppearance.Ring} 逻辑，原本与圆环共用同一份
     * {@link TrioSettings}，导致圆环的尺寸/位置/布局类设置会污染胶囊（用户实测
     * 5G 等位置被带偏）。现在把这些字段按样式隔离：胶囊用 {@code "cap_"} 前缀键，
     * 圆环/矩形用原键。规则 = 原键前拼 {@code "cap_"}，可逆。
     *
     * <p>仅 {@link #STYLE_KEYS} 里的字段受隔离；其余（总开关、显示哪些组件、
     * 颜色、防烧屏、调试日志）三种样式共享，不走前缀。
     */
    public static final java.util.Set<String> STYLE_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "glyph_scale_pct", "glyph_left_offset_dp", "glyph_shift_dp",
                    "ring_stroke", "arc_stroke",
                    "value_size", "value_weight",
                    "type_size", "out_type_size_dp", "type_suffix_scale",
                    "out_type_margin_left_dp", "out_type_margin_right_dp", "type_weight",
                    "out_signal_size_dp", "out_signal_margin_dp",
                    "perf_temp", "perf_outer_margin_dp", "perf_outer_margin_right_dp",
                    "perf_scale_pct", "perf_weight",
                    "track_alpha",
                    "charge_value_size", "charge_signal_size", "charge_suffix_a_scale",
                    "mobile_type_mode", "signal_mode", "swap_wifi_value",
                    "charge_pair_mode", "charge_signal_also"
            ));

    /** 返回某键的胶囊命名空间键：{@code "cap_" + base}。可逆，见 {@link #baseKey}。 */
    public static String capKey(String base) {
        return "cap_" + base;
    }

    /** 剥掉 {@code "cap_"} 前缀，找回原键；非前缀键原样返回。 */
    public static String baseKey(String key) {
        return key.startsWith("cap_") ? key.substring(4) : key;
    }

    /**
     * 按当前样式返回实际要用到的键：胶囊且属于 {@link #STYLE_KEYS} 时走
     * {@code "cap_"} 前缀，否则原键。这是读/写/广播四处统一的分支点。
     */
    public static String capKeyAware(String base, boolean capsule) {
        return (capsule && STYLE_KEYS.contains(base)) ? ("cap_" + base) : base;
    }

    // -------------------------------------------------------------- defaults
    public static final boolean DEF_ENABLED = true;
    public static final boolean DEF_OVERLAY = true;
    public static final boolean DEF_SHOW_WIFI = true;
    public static final boolean DEF_SHOW_MOBILE = true;
    public static final boolean DEF_SHOW_VALUE = true;
    public static final boolean DEF_SHOW_BOLT = true;
    /**
     * Legacy, read-only for migration.
     *
     * <p>Superseded by {@link #DEF_MOBILE_TYPE_MODE}; kept only so the migration
     * can tell what an installed user had before the int key existed.
     */
    public static final boolean DEF_SHOW_MOBILE_TYPE = false;

    /** Off: the network type is not drawn at all. */
    public static final int MOBILE_TYPE_OFF = 0;
    /** Draw the network type inside the ring, in the middle when Wi-Fi is absent. */
    public static final int MOBILE_TYPE_IN_RING = 1;
    /** Draw the network type outside the ring. */
    public static final int MOBILE_TYPE_OUT_RING = 2;
    /**
     * Off by default: the shipment look keeps the battery number in the centre
     * when there is no Wi-Fi, and draws no network type at all.
     */
    public static final int DEF_MOBILE_TYPE_MODE = MOBILE_TYPE_OFF;
    /**
     * Off by default: the shipped look keeps the Wi-Fi arcs in the middle of the
     * ring and the battery reading small in the 12 o'clock notch.
     *
     * <p>Turning this on moves the percentage into the middle, drawn at
     * {@link TrioGeometry#CENTRE_SIZE_RATIO} scale, and shrinks the arcs into the
     * notch.
     */
    public static final boolean DEF_VALUE_CENTRED = false;

    /** The original arrangement: a battery ring with the content inside it. */
    public static final int STYLE_RING = 0;
    /**
     * The rectangular arrangement: a column of four level dots down each side,
     * the network state in the top slot between them, an enlarged percentage
     * below, and a full-width battery bar along the bottom. There is no ring.
     */
    public static final int STYLE_RECT = 1;
    /**
     * The capsule arrangement (ported from upstream v1.7): a stadium-shaped
     * outline that IS the battery bar, the clock in a fixed left column, the
     * bolt / Wi-Fi arcs / percentage / network type in the right-hand slot by
     * that priority, and rows of signal dots riding the outline at 12 and 6
     * o'clock. Fixed default layout; the editor is not ported.
     */
    public static final int STYLE_CAPSULE = 2;
    /**
     * The ring, for every install that has never chosen.
     *
     * <p>Only the styles themselves are honoured: an unrecognised stored value
     * degrades to the ring rather than to a blank glyph.
     */
    public static final int DEF_TRIO_STYLE = STYLE_RING;

    /** The signal is the glyph's own level dots, exactly as shipped. */
    public static final int SIGNAL_IN_RING = 0;
    /**
     * The signal leaves the glyph and is drawn beside the battery instead.
     *
     * <p>What stands there depends on {@link #KEY_STACKED_SIGNAL}: off means
     * MIUI's own signal icon keeps its place, on means the module draws the
     * stacked bars-and-dots reading of its own.
     */
    public static final int SIGNAL_OUT_RING = 1;
    /**
     * Inside the glyph, for every install that has never chosen: the shipped look
     * keeps the level dots in the ring's lower opening.
     */
    public static final int DEF_SIGNAL_MODE = SIGNAL_IN_RING;

    /**
     * Off by default: outside the glyph the shipped behaviour is to hand the
     * signal back to MIUI rather than draw a second version of it.
     */
    public static final boolean DEF_STACKED_SIGNAL = false;
    /** Off by default: the stacked reading shows both SIMs unless told otherwise. */
    public static final boolean DEF_DATA_SIM_ONLY = false;

    /**
     * Off by default: the shipped look draws one row of dots for the current data
     * SIM, and the second row is an opt-in reading for a dual-SIM device.
     */
    public static final boolean DEF_DUAL_SIM = false;

    public static final boolean DEF_ROLE_COLORS = true;
    public static final int DEF_COLOR_CRITICAL_ON_DARK = 0xFFFF3B30;
    public static final int DEF_COLOR_CRITICAL_ON_LIGHT = 0xFFFF3B30;
    public static final int DEF_COLOR_CHARGING_ON_DARK = 0xFF34C759;
    public static final int DEF_COLOR_CHARGING_ON_LIGHT = 0xFF1F8F3D;
    public static final int DEF_COLOR_LOW_ON_DARK = 0xFFF2B900;
    public static final int DEF_COLOR_LOW_ON_LIGHT = 0xFFC99700;
    public static final int DEF_LOW_THRESHOLD = 20;

    /**
     * ★ v77：是否让<b>省电模式</b>把电量染成低电量色。
     *
     * <p>默认 {@code true} = 保持 MIUI 原版语义（省电模式 → 电量黄）。三星原生
     * 并不因省电模式改电池图标颜色，他人实测"高电量也显示黄色"，所以给出开关，
     * 不想被染色的可以关掉。
     */
    public static final String KEY_POWER_SAVE_COLOR = "power_save_color";
    /**
     * @deprecated v82 的布尔键，只被一次性迁移读取，见 {@link #KEY_CHARGE_PAIR_MODE}。
     */
    @Deprecated
    public static final String KEY_CHARGE_WITH_VALUE = "charge_with_value";
    /**
     * ★ v104：充电同显三选项 —— 0 隐藏 / 1 圈上 / 2 圈内（默认 0）。
     *
     * <p>取代 v82 的 {@link #KEY_CHARGE_WITH_VALUE} 布尔开关：那个开关只有"缺口里
     * 闪电+电量"一种画法，没有"圈内"这一档。老设置开着的一次性迁移成"圈上"
     * （见 {@code TrioSettings#readPairMode}）。
     *
     * <p><b>只管电量那一对</b>：选哪个槽，闪电就去哪个槽，电量跟着并排（上下边对齐）。
     */
    public static final String KEY_CHARGE_PAIR_MODE = "charge_pair_mode";
    /**
     * ★ v104：充电时<b>信号</b>要不要也去缺口跟闪电并排（＝ L29 的样子）。
     *
     * <p>默认<b>关</b>：关着时缺口归电量，三选项说了算（v82 的行为）。
     * 开着时缺口归信号 —— 「电量数字居中」打开的用户最需要它（否则充电时信号被
     * 闪电挤掉，报告的正是这个 BUG）。
     *
     * <p>只在 {@link #KEY_CHARGE_PAIR_MODE} 不是"隐藏"、且「电量数字居中」开着时
     * 才可用：居中关着时信号本来就在环内正常显示，这一排也不存在。
     */
    public static final String KEY_CHARGE_SIGNAL_ALSO = "charge_signal_also";
    /** ★ v82：「闪电+电量」这一对的字号（30–35）。 */
    public static final String KEY_CHARGE_VALUE_SIZE = "charge_value_size";
    /** ★ v104：「闪电+信号」这一对的信号大小（30–35，与电量字号同一档位）。 */
    public static final String KEY_CHARGE_SIGNAL_SIZE = "charge_signal_size";
    /** ★ v104：组合图标里 5GA 的 "A" 相对主字号的百分比（50–100）。 */
    public static final String KEY_CHARGE_SUFFIX_A_SCALE = "charge_suffix_a_scale";
    public static final boolean DEF_POWER_SAVE_COLOR = true;
    /** 充电同显：隐藏（保持正式版的样子 —— 充电时只有闪电，信号不显示）。 */
    public static final int CHARGE_PAIR_OFF = 0;
    /** 充电同显：闪电去 12 点缺口，与缺口里那个并排。 */
    public static final int CHARGE_PAIR_GAP = 1;
    /** 充电同显：闪电去环内（环心那一行），与环内那个并排。 */
    public static final int CHARGE_PAIR_CENTRE = 2;
    /** @deprecated 只被迁移读取。 */
    @Deprecated
    public static final boolean DEF_CHARGE_WITH_VALUE = false;
    /** ★ v104：默认隐藏，与 v82 的默认一致（老用户开着会自动迁移成"圈上"）。 */
    public static final int DEF_CHARGE_PAIR_MODE = CHARGE_PAIR_OFF;
    /** ★ v104：默认关 —— 缺口留给电量那一对（v82 的行为）。 */
    public static final boolean DEF_CHARGE_SIGNAL_ALSO = false;
    /** ★ v82：那对"闪电+电量"的字号，默认 30（缺口装得下且两侧有余量）。 */
    public static final int DEF_CHARGE_VALUE_SIZE = 30;
    /** ★ v104：那对"闪电+信号"的信号大小，默认 30，与电量字号对齐。 */
    public static final int DEF_CHARGE_SIGNAL_SIZE = 30;
    /** ★ v104：组合图标里 "A" 的大小，默认 65%，与环内 5GA 的 A 一致。 */
    public static final int DEF_CHARGE_SUFFIX_A_SCALE = 65;

    // ---------------------------------------------------------------------
    // ★ 胶囊（STYLE_CAPSULE）独立命名空间（2026-10-21 新加）。
    //
    // 胶囊复用 {@link TrioAppearance.Ring} 逻辑（缺口＝顶部开口、环心＝中部、
    // 底部信号点＝下边），但尺寸/位置/布局类字段不再与圆环共用：
    // 它们在 {@link #STYLE_KEYS} 里，读取/写入/广播时按当前样式走 {@code "cap_"} 前缀键，
    // 与圆环（及矩形）的那份互不干扰。总开关、显示哪些组件、颜色、防烧屏、调试日志
    // 仍三种样式共享。详见 TrioSettings 的 from / fromBundle / toBundle / applyKeyFrom
    // 与 SettingsRepository 的几何类 setter。
    // ---------------------------------------------------------------------


    public static final int DEF_RING_STROKE = 14;
    /** WiFi 弧线粗细默认值：v76 由 12 改 7，胶囊移植（上游 v1.7）按用户要求定为 8。 */
    public static final int DEF_ARC_STROKE = 8;

    // ---------------------------------------------------------------------
    //  胶囊三车道零件 id（与上游 HyperDuo v1.7 对齐；SamsungDuo 仅用其中几件）
    // ---------------------------------------------------------------------
    public static final int PART_NONE = 0, PART_WIFI = 1, PART_MOBILE = 2,
            PART_VALUE = 3, PART_BOLT = 4, PART_TYPE = 5, PART_CLOCK = 6;
    public static final int PART_MAX = PART_CLOCK;
    public static final int PART_COUNT = PART_MAX + 1;
    /** 胶囊三车道：TOP=缺口 / CENTRE=环心右列 / BOTTOM=底部信号点。 */
    public static final int ZONE_COUNT = 3;

    // ---------------------------------------------------------------------
    // ★ 胶囊三按钮显式控制（2026-10-21 用户拍板，替代圆环 chargePair 派生）。
    //
    // 基础模板 = 整圈 + 左列时钟 + 底部信号点（双卡完全照圆环 dualSimRows）。
    // 按钮1（右列内容，恒显、无隐藏）：电量 / 信号。
    // 按钮2（缺口内容）：隐藏 / 闪电 / 电量 / 信号。
    // 按钮3（充电闪电位置）：隐藏 / 缺口独立 / 缺口+同伴 / 右侧+同伴。
    // ★ 同伴 = 按钮1没选的那件；缺口与右列绝不出现同一组件。
    // ★ 右侧永远要有东西（右侧是前提）：按钮3 的两种同伴档右列照常显示按钮1。
    // ---------------------------------------------------------------------
    /** 按钮1 右列：电量数字。 */
    public static final int CAP_RIGHT_VALUE = 0;
    /** 按钮1 右列：信号（5G/5GA 网络类型；有 Wi-Fi 时画 Wi-Fi 弧，同圆环 wifi 优先）。 */
    public static final int CAP_RIGHT_SIGNAL = 1;
    public static final String KEY_CAPSULE_RIGHT = "capsule_right";
    public static final int DEF_CAPSULE_RIGHT = CAP_RIGHT_VALUE;
    public static final int MIN_CAPSULE_RIGHT = CAP_RIGHT_VALUE;
    public static final int MAX_CAPSULE_RIGHT = CAP_RIGHT_SIGNAL;

    /** 按钮2 缺口：隐藏。 */
    public static final int CAP_GAP_NONE = 0;
    /** 按钮2 缺口：闪电（仅充电时有意义）。 */
    public static final int CAP_GAP_BOLT = 1;
    /** 按钮2 缺口：电量数字（右列选了电量时此选项禁用，避免重复）。 */
    public static final int CAP_GAP_VALUE = 2;
    /** 按钮2 缺口：信号（右列选了信号时此选项禁用，避免重复）。 */
    public static final int CAP_GAP_SIGNAL = 3;
    public static final String KEY_CAPSULE_GAP = "capsule_gap";
    public static final int DEF_CAPSULE_GAP = CAP_GAP_NONE;
    public static final int MIN_CAPSULE_GAP = CAP_GAP_NONE;
    public static final int MAX_CAPSULE_GAP = CAP_GAP_SIGNAL;

    /** 按钮3 充电闪电：不显示。 */
    public static final int CAP_BOLT_HIDDEN = 0;
    /** 按钮3 充电闪电：缺口独立显示。 */
    public static final int CAP_BOLT_GAP = 1;
    /** 按钮3 充电闪电：缺口与同伴（按钮1没选的那件）同时显示。 */
    public static final int CAP_BOLT_GAP_PAIR = 2;
    /** 按钮3 充电闪电：右侧与按钮1那件同时显示。 */
    public static final int CAP_BOLT_CENTRE_PAIR = 3;
    public static final String KEY_CAPSULE_BOLT_POS = "capsule_bolt_pos";
    public static final int DEF_CAPSULE_BOLT_POS = CAP_BOLT_GAP;
    public static final int MIN_CAPSULE_BOLT_POS = CAP_BOLT_HIDDEN;
    public static final int MAX_CAPSULE_BOLT_POS = CAP_BOLT_CENTRE_PAIR;

    // 按钮4 胶囊形状（2026-10-22）：只改基础模板的轮廓几何，其余逻辑（三车道/
    // 开口退让/取色）全部不动。
    /** 按钮4 胶囊形状：常规胶囊 —— 体育场形，圆角 = 半高（上游 HyperDuo 同款）。 */
    public static final int CAP_SHAPE_NORMAL = 0;
    /**
     * 按钮4 胶囊形状：三星风格 —— 圆角匹配 Galaxy S25 Ultra 屏幕圆角比例。
     * 依据官方规格反解：6.9"(174.2mm) 完整矩形 vs 6.8"(172.2mm) 含圆角、3120x1440
     * ⇒ r ≈ 3.1mm ≈ 61px(@501dpi) ≈ 19.5dp，r/屏高 ≈ 4.25%（用户 10-22 选定）。
     */
    public static final int CAP_SHAPE_SAMSUNG = 1;
    public static final String KEY_CAPSULE_SHAPE = "capsule_shape";
    public static final int DEF_CAPSULE_SHAPE = CAP_SHAPE_NORMAL;
    public static final int MIN_CAPSULE_SHAPE = CAP_SHAPE_NORMAL;
    public static final int MAX_CAPSULE_SHAPE = CAP_SHAPE_SAMSUNG;

    /**
     * ★ 胶囊长度（2026-10-23，移植上游 HyperDuo v1.7.2 的 {@code capsule_length}）：
     * 只加长两端圆角之间的<b>直线段</b>，设计单位。
     *
     * <p>胶囊是体育场形：两个半圆端帽的半径由高度钉死（{@code CAP_RADIUS}），
     * 直线段是唯一能变长而不改变形状的部分。加长它就是加宽胶囊：端帽保持圆、
     * 描边保持粗细、所有按高度表述的比例（点距、字号、端帽半径）都不动。
     * 0 = 参考宽度（与历史版本完全一致）；上限是再长就要读成"一根条"而不是胶囊。
     * 下限 -30 时直线段仍剩 49 单位（参考 79），形状不塌。
     */
    public static final String KEY_CAPSULE_LENGTH = "capsule_length";
    public static final int DEF_CAPSULE_LENGTH = 0;
    public static final int MIN_CAPSULE_LENGTH = -30;
    public static final int MAX_CAPSULE_LENGTH = 60;

    public static final int DEF_VALUE_SIZE = 36;
    /**
     * 700 is {@code Typeface.BOLD}, the weight the reference glyph uses. The
     * whole 100..900 range is exposed so thin skinning and heavy digits are both
     * reachable.
     */
    public static final int DEF_VALUE_WEIGHT = 700;
    /**
     * Font size of the centred network type. Deliberately an absolute size rather
     * than a ratio of {@link #DEF_VALUE_SIZE}: the type is a standalone label the
     * user tunes on its own, and 32 is what the old 0.9 x 36 ratio produced, so
     * the default look is unchanged.
     */
    public static final int DEF_TYPE_SIZE = 32;
    /**
     * Font size of the network type when it is drawn outside the ring, at the
     * status bar's own scale. 32 matches {@link #DEF_TYPE_SIZE}, so a user who
     * never touches the new slider keeps exactly the look they had.
     *
     * @deprecated Raw pixels, superseded by {@link #DEF_OUT_TYPE_SIZE_DP}; read
     * only by the one-time migration. 32px was authored on the density-3 test
     * device, where it is 10.67dp - the new default rounds to the same look.
     */
    @Deprecated
    public static final int DEF_OUT_TYPE_SIZE = 32;
    /**
     * Font size of the network type outside the ring, in density pixels.
     *
     * <p>14dp is the default (about 42px on density 3), clearly visible and
     * proportionate to the battery icon and status bar text.
     */
    public static final int DEF_OUT_TYPE_SIZE_DP = 14;
    /**
     * The stacked out-of-ring signal's height, in density pixels.
     *
     * <p>15dp: MIUI's own four signal bars measure 44px on the test device at
     * density 3, and 15dp is 45px there - the reading ships at the size of the
     * native icon it replaces. Being a dp, it lands on the same physical size on
     * any density instead of only matching on this one.
     */
    public static final int DEF_OUT_SIGNAL_SIZE = 15;
    /**
     * The gap the out-of-ring label keeps on each side, in dp.
     *
     * <p>2dp is the value the single hard-coded {@code OUT_LABEL_GAP_DP} used to
     * hold, so the shipped spacing is unchanged and only a user who moves the
     * sliders sees anything different.
     */
    public static final int DEF_OUT_TYPE_MARGIN_LEFT = 2;
    public static final int DEF_OUT_TYPE_MARGIN_RIGHT = 2;
    /**
     * 2dp: the gap the reading kept from the battery before it was settable
     * (the same value the label margins default to, since one constant served
     * both), so the shipped spacing is unchanged.
     */
    public static final int DEF_OUT_SIGNAL_MARGIN = 2;
    /** v75：温度读数外边距默认与其它间距项一致（2dp）。 */
    public static final int DEF_PERF_OUTER_MARGIN = 2;
    /** v76：温度读数右侧边距默认同左侧（2dp）。 */
    public static final int DEF_PERF_OUTER_MARGIN_RIGHT = 2;
    /**
     * 0 for the ring's extra left offset: the automatic gap is left exactly as
     * it is until the user decides it needs tightening.
     */
    public static final int DEF_GLYPH_LEFT_OFFSET = 0;
    /**
     * v82：整组左右平移默认 0 = 保持现在的位置（三合一贴在状态栏右缘）。
     * 负值往左（远离右缘），正值往右（往右缘贴）。
     */
    public static final int DEF_GLYPH_SHIFT = 0;
    /** v73：温度读数默认关 —— 什么都不开时状态栏与之前完全一致。 */
    public static final boolean DEF_PERF_TEMP = false;
    /** v73：读数整体尺寸默认不缩放（自适应字号就是设计值）。 */
    public static final int DEF_PERF_SCALE = 100;
    /** v73：读数字重默认与数字/网络类型同一档，观感一致。 */
    public static final int DEF_PERF_WEIGHT = 700;
    /** v73：三合一默认吃满系统给的那块区域（= 现状）。 */
    public static final int DEF_GLYPH_SCALE = 100;
    /**
     * v72：默认<b>开</b>防烧屏 —— 屏幕残影不可逆，默认保护比默认裸奔安全，
     * 且默认幅度小到看不出来；不喜欢可在设置里关掉。
     */
    public static final boolean DEF_BURN_IN_PROTECT = true;
    /**
     * v72：2 个 1/4-dp 单位 = 0.5dp，本机（density 3.5）约 1.7px。
     * 足够打散像素老化，肉眼基本看不出图标在动。
     */
    public static final int DEF_BURN_IN_AMPLITUDE = 2;
    /** v72：60 秒走完一个完整往复。 */
    public static final int DEF_BURN_IN_PERIOD = 60;
    /**
     * 65% for the trailing "A": the reference's {@code 56/86} ratio, measured
     * from {@code docs/ref-5ga.png}. 100 turns the shrink off.
     */
    public static final int DEF_TYPE_SUFFIX_SCALE = 65;
    public static final int DEF_TYPE_WEIGHT = 700;
    public static final int DEF_TRACK_ALPHA = 56;

    public static final boolean DEF_DEBUG_LOG = false;

    // --------------------------------------------------------------- bounds
    public static final int MIN_RING_STROKE = 4;
    public static final int MAX_RING_STROKE = 16;
    public static final int MIN_ARC_STROKE = 3;
    public static final int MAX_ARC_STROKE = 16;
    public static final int MIN_VALUE_SIZE = 16;
    public static final int MAX_VALUE_SIZE = 44;
    public static final int MIN_VALUE_WEIGHT = 100;
    public static final int MAX_VALUE_WEIGHT = 900;
    public static final int MIN_TYPE_SIZE = 16;
    public static final int MAX_TYPE_SIZE = 44;
    public static final int MIN_OUT_TYPE_SIZE = 16;
    /**
     * Higher than {@link #MAX_TYPE_SIZE} on purpose: the out-of-ring label sits
     * in the status bar's real pixel space rather than the ring's design space,
     * so it needs headroom the in-ring label does not.
     *
     * @deprecated Raw-pixel bounds of the retired {@link #KEY_OUT_TYPE_SIZE};
     * read only by the one-time migration, which clamps the converted value
     * into the new dp range.
     */
    @Deprecated
    public static final int MAX_OUT_TYPE_SIZE = 64;
    /**
     * The span the out-of-ring type size offers, in density pixels.
     *
     * <p>8..26dp, cleanly covering everything from the stock ~7.2dp label up to
     * a prominent label without overflowing the status bar row. Default is 14dp
     * (~42px at density 3). The 40..80dp range this briefly held was a
     * misreading of the size report - what the user was seeing was the label
     * drawn through the bars, not a label that was too small - so it is back to
     * a span that keeps the label inside the row.
     */
    public static final int MIN_OUT_TYPE_SIZE_DP = 8;
    public static final int MAX_OUT_TYPE_SIZE_DP = 26;
    /**
     * The physical span the slider offers, in density pixels: 6dp (18px at
     * density 3) is where the four bars stop being distinguishable, and 20dp
     * (60px) is the tallest the status bar row shows before the reading starts
     * pushing the icons around it.
     *
     * <p>Narrow on purpose. The point of the setting is to match or gently
     * nudge MIUI's own signal bars, which measure 44px - about 14.7dp - so the
     * shipped 15dp sits in the middle of the range and a quarter of the slider
     * either way is already a visible change.
     */
    public static final int MIN_OUT_SIGNAL_SIZE = 6;
    public static final int MAX_OUT_SIGNAL_SIZE = 20;
    /**
     * The span the two label margins offer, in dp.
     *
     * <p>0 lets the label butt right up against its neighbour and 16 is already
     * wider than the gap between the native icons, so the whole useful range is
     * inside it. The default 2 sits near the low end, where the shipped look is.
     */
    public static final int MIN_OUT_TYPE_MARGIN = 0;
    public static final int MAX_OUT_TYPE_MARGIN = 16;
    /**
     * The span the signal margin offers, in dp.
     *
     * <p>Non-negative on purpose: unlike the retired position offset, this is a
     * reserved gap. A negative value would mean "overlap the battery", which is
     * the very thing the reservation exists to prevent. 0 lets the reading butt
     * up against the battery; 16 is a comfortable separation without eating the
     * status bar.
     */
    public static final int MIN_OUT_SIGNAL_MARGIN = 0;
    public static final int MAX_OUT_SIGNAL_MARGIN = 16;
    /** v75 温度读数外边距范围：与其它间距项同一套 0–16dp。 */
    public static final int MIN_PERF_OUTER_MARGIN = 0;
    public static final int MAX_PERF_OUTER_MARGIN = 16;
    /** v76 温度读数右侧边距范围：与其它间距项同一套 0–16dp。 */
    public static final int MIN_PERF_OUTER_MARGIN_RIGHT = 0;
    public static final int MAX_PERF_OUTER_MARGIN_RIGHT = 16;
    /**
     * How far the group may be nudged left. The unit is <b>half a dp</b>, so the
     * stored -16..+16 is the -8.0..+8.0 dp the control shows: the finer step is
     * what makes the difference between "a little tight" and "too tight" at
     * status bar size, where 1dp is a visible jump.
     *
     * <p>Negative pulls the group back over the native icons' own padding; -8dp
     * is a little over two icon-widths' worth of tightening, past which the
     * group would be crowding the last icon rather than spacing itself from it.
     * The positive half exists only so the value can be walked back the other
     * way without leaving the slider's range.
     */
    public static final int MIN_GLYPH_LEFT_OFFSET = -16;
    public static final int MAX_GLYPH_LEFT_OFFSET = 16;
    /**
     * v82 整组左右平移范围（dp）：-16..+16，1dp 一档。
     *
     * <p>语义（用户 2026-10-15 定）：**整组平移**，且"往左移动把原生图标往左挤压，
     * 往右移动原生图标补位回来"。实现 = 平移量同时走两条路 ——
     * ① 写进最左那块 View 的 leftMargin（把原生图标推走/放回）；
     * ② 以 translationX 平移整组（三合一 + 三块环外陪衬）。
     * 两条路用同一个量，于是组内各块之间的相对距离不变，看起来就是"整组在动"。
     *
     * <p>为什么必须两条路一起：本组是状态栏那一行里**最后**一组子 View，而那一行
     * 右缘是钉死在屏幕右缘的（实机几何：{@code geometry} dump 里我们的 View 的
     * {@code r} 恒等于行的宽）—— 只改 margin 动的会是**原生图标**，我们自己的
     * 图标纹丝不动；只改 translation 则会直接压到原生图标上面去。
     *
     * <p>0 = 现在的位置（贴状态栏右缘）。负值往左（远离右缘，原生图标被推着左移）；
     * 正值往右（往右缘贴，原生图标回位让出地方）。
     */
    public static final int MIN_GLYPH_SHIFT = -16;
    public static final int MAX_GLYPH_SHIFT = 16;
    /**
     * ★ v82/v104 两个组合图标的大小范围：30–35（用户指定，10.2 起两个滑块统一）。
     *
     * <p>数值就是"配对字号"：配对高度 = 字号 × 0.71（cap 高度），闪电、wifi 图标、
     * 电量/信号文字都按同一个高度铺，于是上下边天然对齐（用户定的规则）。
     *
     * <p>上界 35 是用户要的上限；但缺口净宽只有约 63 设计单位，"闪电 + 两位数字"
     * 或"闪电 + 5GA"在这档位以上就装不满了 —— 所以渲染层仍会按剩余净宽自动缩小，
     * 而不是把内容压到弧线的圆头上去。
     */
    public static final int MIN_CHARGE_VALUE_SIZE = 30;
    public static final int MAX_CHARGE_VALUE_SIZE = 35;
    /** ★ v104：「闪电+信号」那一对的范围，与电量字号同一档位。 */
    public static final int MIN_CHARGE_SIGNAL_SIZE = 30;
    public static final int MAX_CHARGE_SIGNAL_SIZE = 35;
    /**
     * ★ v104：组合图标里 5GA 的 "A" 相对主字号的百分比，50–100。
     *
     * <p>与环内 {@link #MIN_TYPE_SUFFIX_SCALE} 同一套区间，只是<b>独立成一项</b>：
     * 组合图标里的 A 和环内 5GA 的 A 是两个场景，用户要能分别调。
     */
    public static final int MIN_CHARGE_SUFFIX_A_SCALE = 50;
    public static final int MAX_CHARGE_SUFFIX_A_SCALE = 100;
    /**
     * v73 读数整体尺寸范围（%）：60–150。
     * 150 之上单项读数会高过旁边的图标；60 之下在状态栏尺寸上难以卒读。
     */
    public static final int MIN_PERF_SCALE = 60;
    public static final int MAX_PERF_SCALE = 150;
    /** v73 读数字重范围：与其它字重滑块同一套 100–900。 */
    public static final int MIN_PERF_WEIGHT = 100;
    public static final int MAX_PERF_WEIGHT = 900;
    /**
     * v76 三合一整体尺寸范围（%）：60–130。
     * 上限 130 是**实机实测**定的，不是随手取的：130% 对应视图 124px，在 107px 的
     * 行里上下各溢约 8.5px，正好能被状态栏窗口完整容纳；131% 起视图 125px 就被
     * 窗口边界裁掉（用户实测"131% 到 150% 显示又不全"）——窗口能容纳的视图高度
     * 上限就在 124~125px 之间，这是物理边界，放宽祖先 clipChildren 也越不过去。
     * 下限 60 再小信号点与弧线就开始糊。
     */
    public static final int MIN_GLYPH_SCALE = 60;
    /**
     * ★ 「解除尺寸限制」**开启**时的整体尺寸上限 = 130。
     * 130% 是**实机实测**的物理上限：视图 124px，在状态栏行里上下各溢约 8.5px，
     * 靠逐层放开 clipChildren 能完整显示；131% 起被窗口边界裁掉（实测"131% 到 150% 显示又不全"）。
     * 曾试独立悬浮窗想突破（v102），但脱离状态栏树就不抢位子、会遮挡/被遮挡，已彻底放弃，回到树模式封顶 130%。
     */
    public static final int MAX_GLYPH_SCALE = 130;
    /**
     * ★ 「解除尺寸限制」**关闭**时的整体尺寸上限 = 100。
     * 用户定的语义：一个开关两种档位、不加新按钮 —— 关(锁)=60–100%，开(解锁)=60–130%。
     * 这不是 UI 假限制：SystemUI 侧读到的值也被同样压到 100（见 TrioSettings.from），
     * 避免"设置里偷偷存着 130、实际只显示 100%"。
     */
    public static final int MAX_GLYPH_SCALE_TREE = 100;
    /**
     * v72 幅度范围（1/4 dp 单位）：0..8 = 0..2.0dp。
     * 0 直接关掉位移（等于不动）；2.0dp 本机约 7px，再大就能明显看见图标在飘了。
     */
    public static final int MIN_BURN_IN_AMPLITUDE = 0;
    public static final int MAX_BURN_IN_AMPLITUDE = 8;
    /**
     * v72 周期范围（秒）：10..300。
     * 短于 10s 移动速度肉眼可辨；长于 300s 等于在几乎同一个位置停留太久，
     * 保护效果随之下降。
     */
    public static final int MIN_BURN_IN_PERIOD = 10;
    public static final int MAX_BURN_IN_PERIOD = 300;
    /**
     * The trailing "A" may be the same size as the rest (100) or a little over
     * half of it (50). Below 50 the suffix stops reading as a letter at status
     * bar sizes, and above 100 it would be the main glyph's larger twin.
     */
    public static final int MIN_TYPE_SUFFIX_SCALE = 50;
    public static final int MAX_TYPE_SUFFIX_SCALE = 100;
    public static final int MIN_TYPE_WEIGHT = 100;
    public static final int MAX_TYPE_WEIGHT = 900;
    public static final int MIN_TRACK_ALPHA = 0;
    public static final int MAX_TRACK_ALPHA = 255;
    public static final int MIN_LOW_THRESHOLD = 5;
    public static final int MAX_LOW_THRESHOLD = 50;

    public static final int MIN_MOBILE_TYPE_MODE = MOBILE_TYPE_OFF;
    public static final int MAX_MOBILE_TYPE_MODE = MOBILE_TYPE_OUT_RING;

    public static final int MIN_TRIO_STYLE = STYLE_RING;
    public static final int MAX_TRIO_STYLE = STYLE_CAPSULE;

    public static final int MIN_SIGNAL_MODE = SIGNAL_IN_RING;
    public static final int MAX_SIGNAL_MODE = SIGNAL_OUT_RING;

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
