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
    public static final String NAME = "hyperduo_settings";

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
     * The ring, for every install that has never chosen.
     *
     * <p>Only the styles themselves are honoured: the settings screen offers no
     * third entry, so an unrecognised stored value degrades to the ring rather
     * than to a blank glyph.
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
    /** ★ v82：充电时同时显示闪电与电量（默认关）。 */
    public static final String KEY_CHARGE_WITH_VALUE = "charge_with_value";
    /** ★ v82：「闪电+电量」这一对的字号（30–36）。 */
    public static final String KEY_CHARGE_VALUE_SIZE = "charge_value_size";
    public static final boolean DEF_POWER_SAVE_COLOR = true;
    /**
     * ★ v82：充电时「闪电 + 电量」同时显示，默认<b>关</b>。
     *
     * <p>要它的原因：网络类型在环内时，充电帧的缺口被闪电占着、环中心被类型占着，
     * 电量数字按现有规则整个不画（{@code valueInGap = hasValue && !valueInCentre && !bolt}）
     * —— 用户实测"充电时看不到当前电量"。
     *
     * <p>开着时：缺口左侧画闪电、右侧画电量数字，两者<b>上下边严格对齐</b>
     * （闪电高度 = 数字 cap 高度 = 0.71×字号），5G 仍留在环中心不受影响。
     */
    public static final boolean DEF_CHARGE_WITH_VALUE = false;
    /** ★ v82：上面那对"闪电+电量"的字号，默认 30（缺口装得下且两侧有余量）。 */
    public static final int DEF_CHARGE_VALUE_SIZE = 30;

    public static final int DEF_RING_STROKE = 14;
    /** ★ v76：WiFi 弧线粗细默认值按用户要求由 12 改为 7（范围 3–16 不变）。 */
    public static final int DEF_ARC_STROKE = 7;
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
     * ★ v82 「闪电+电量」字号范围：30–36（用户指定）。
     *
     * <p>上界 36 是用户要的上限；但缺口净宽只有约 63 设计单位，"闪电 + 两位数字"
     * 在 34 以上就装不满了 —— 所以渲染层仍会按剩余净宽自动缩小（见
     * {@code TrioRenderer#fitSize}），字号设到 35/36 时会略小于设定值，
     * 而不是把数字压到弧线的圆头上去。
     */
    public static final int MIN_CHARGE_VALUE_SIZE = 30;
    public static final int MAX_CHARGE_VALUE_SIZE = 36;
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
    public static final int MAX_GLYPH_SCALE = 130;
    /**
     * ★ v80：「独立窗口绘制」关闭时，「整体尺寸」的天花板 = 100。
     *
     * <p>用户定的规则：>100% 那一档由「独立窗口绘制」这个开关看着 —— 关着就只能到
     * 100%，开着才解锁 100–130%。原因：关着时三合一是挂在状态栏那一行里参与排布的
     * （{@code system_icons}），放大只能靠逐层放开 {@code clipChildren}，而
     * {@code clipChildren} 对 **Android 窗口边界**无效；开着时是独立小窗口，
     * 可以靠把窗口自身加高来容纳放大的部分。
     *
     * <p>这不是 UI 上的假限制：SystemUI 侧读到的值也会被同样压到 100
     * （见 {@link TrioSettings#from}），避免"设置里偷偷存着 130、实际只显示 100%"。
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
    public static final int MAX_TRIO_STYLE = STYLE_RECT;

    public static final int MIN_SIGNAL_MODE = SIGNAL_IN_RING;
    public static final int MAX_SIGNAL_MODE = SIGNAL_OUT_RING;

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
