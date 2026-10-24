package com.jsj.samsungduo;

/**
 * Geometry of the "trio" glyph in a 120x120 design space. All units are design
 * units.
 */
final class TrioGeometry {

    private TrioGeometry() {
    }

    /** The SVG viewBox. Everything is drawn inside 0..120 on both axes. */
    static final float BOX = 120f;

    // ---------------------------------------------------------------- battery
    /** Arc centre: the two endpoints (15.5,88.25) and (103.5,88.25) with r=51.5. */
    static final float B_CX = 59.5f;
    static final float B_CY = 61.48715261785473f;
    static final float B_R = 51.5f;
    /** Default ring stroke; the user's {@code ring_stroke} setting overrides it. */
    static final float B_STROKE = 8f;
    /** Start angle (deg) from centre towards (15.5, 88.25). */
    static final float B_START = 148.69008689281117f;
    /** Clockwise sweep (deg) to (103.5, 88.25). The missing part is at the BOTTOM. */
    static final float B_SWEEP = 242.6198262143777f;

    /**
     * The whole open arc is further broken at 12 o'clock by stroke-dasharray over
     * pathLength=100: "35.3 29.4 35.3 4" -> gap covers [0.353, 0.647] of the path.
     *
     * <p>The reference figures are widened here: 29.4% of the path leaves only
     * ~60 design units of chord, which forced the percentage to shrink and left
     * the charging bolt looking pinched. Opening the mouth gives the top
     * indicator the room the ring interior already has.
     */
    static final float GAP_START_IDLE = 0.325f;
    static final float GAP_END_IDLE = 0.675f;
    /** Charging variant: "38.5 22.9 38.6 4" -> gap covers [0.385, 0.614]. */
    static final float GAP_START_CHARGE = 0.345f;
    static final float GAP_END_CHARGE = 0.655f;

    /**
     * The 12 o'clock gap used by the dual-SIM reading: exactly as wide as the
     * opening the arc already leaves bare at the bottom, so the two rows of dots
     * sit in matching mouths.
     *
     * <p>{@link #GAP_START_IDLE} is sized for a bolt or two digits. Measured
     * against the dots it is too narrow: the top row is {@link #DOTS} mirrored
     * about {@link #B_CY}, so its outer dots reach the arc's own radius, and the
     * rounded stroke ends are then only ~10.4 units from the outer dot centres
     * while a cap (stroke/2) plus a dot ({@link #DUAL_DOT_R}) is 14. The cap and
     * the dot merge into one blob and the top row reads as if the ring had cut it
     * off. The bottom row never had this problem because it sits in the arc's
     * natural opening, which is 32.5 degrees wider.
     *
     * <p>Opening the notch to that natural width is what "the rows are symmetric"
     * means geometrically, and it hands the top row the same clearance the bottom
     * row has (~10 units instead of an overlap of ~3.6).
     *
     * <p>Both bounds are derived from {@link #B_SWEEP} rather than typed in: the
     * bare part of the circle is {@code 360 - B_SWEEP}, so its half is
     * {@code 180 / B_SWEEP - 0.5} of the path - measured either side of t=0.5,
     * which is 12 o'clock.
     */
    static final float GAP_START_DUAL = 1f - 180f / B_SWEEP;
    static final float GAP_END_DUAL = 180f / B_SWEEP;

    /**
     * Gap used when nothing is drawn in the 12 o'clock slot.
     *
     * <p>A zero-width gap at position 0 makes the ring draw as one unbroken arc:
     * with {@code gapStart == gapEnd == 0} the two halves of
     * {@code batteryRing}'s split collapse into a single 0..to sweep. An empty
     * mouth is not a feature, so the ring simply closes.
     */
    static final float GAP_NONE = 0f;

    /** Path progress t (0..1) -> angle in degrees. */
    static float bAngle(float t) {
        return B_START + t * B_SWEEP;
    }

    // ----------------------------------------------------------------- charging
    /**
     * The charging bolt, scaled up from the reference path.
     *
     * <p>The reference path only spans x 51.3..67.6 and y 2.2..21.6 — a pinched
     * sliver next to a 103-unit ring. {@link #BOLT_SCALE} grows it about
     * {@link #BOLT_CX} and pins its top edge to {@link #BOLT_TOP}, so it fills
     * the opened 12 o'clock gap without leaving the 120x120 box.
     *
     * <p>The renderer draws the path with
     * {@code translate(boltOffsetX(), boltOffsetY())} then
     * {@code scale(BOLT_SCALE)}: a reference point {@code (x, y)} lands on
     * {@code (x * BOLT_SCALE + boltOffsetX(), y * BOLT_SCALE + boltOffsetY())}.
     */
    static final float BOLT_CX = 59.45f;
    static final float BOLT_TOP = 1.5f;
    /** Top edge and horizontal centre of the reference path, before scaling. */
    static final float BOLT_TOP_REF = 2.2f;
    static final float BOLT_CX_REF = 59.45f;
    /**
     * Chosen against the default ring stroke: the reference bolt is sized for a
     * stroke of 8 design units, and the shipping default is 14, so a much larger
     * scale is needed for the two to read as one icon.
     */
    static final float BOLT_SCALE = 1.6f;

    static float boltOffsetX() {
        return BOLT_CX - BOLT_CX_REF * BOLT_SCALE;
    }

    static float boltOffsetY() {
        return BOLT_TOP - BOLT_TOP_REF * BOLT_SCALE;
    }

    // ------------------------------------------------- 充电：闪电 + 电量（v82）
    /**
     * ★ v82：「闪电 + 电量」同显时用的缺口，比普通充电缺口（0.345–0.655）更宽。
     *
     * <p>普通充电缺口只按"一颗闪电"定宽（净宽约 49 设计单位）。要同时塞进
     * "闪电 + 两位数字"且两者上下边对齐，按 30–36 字号算需要 55–66 单位，
     * 原来的嘴不够 —— 所以同显时把嘴开到 0.30–0.70（半角 48.5°，净宽约 63）。
     *
     * <p>这不是随便放宽：缺口边界仍由 {@link #B_SWEEP} 推导（与
     * {@link #GAP_START_DUAL} 同一套算法），且只在"充电 + 同显开关开着"这一帧
     * 生效，其它情况一律沿用原缺口，环的形状不变。
     */
    static final float GAP_START_CHARGE_PAIR = 0.28f;
    static final float GAP_END_CHARGE_PAIR = 0.72f;

    /** 闪电参考路径的高与宽（缩放前），用于按字号反推缩放倍数。 */
    static final float BOLT_REF_H = 21.6f - 2.2f;
    static final float BOLT_REF_W = 67.6f - 51.3f;

    /** 闪电与电量数字之间的空隙（设计单位）。 */
    static final float CHARGE_PAIR_GAP = 4f;

    /**
     * 缺口在高度 {@code y} 上的可用净宽（设计单位）。
     *
     * <p>两个约束取最小：
     * <ol>
     *   <li><b>弧线内缘</b>：{@code y} 低于缺口边界点时这一高度有弧，净宽 =
     *       {@code 2×(该高度弦半宽 − stroke/2)}。缺口边界点高度处最窄，
     *       正好等于"弦长 − stroke"（与 {@link #gapClearWidth} 一致）。</li>
     *   <li><b>圆形笔帽</b>：弧线端点的 round cap 是半径 {@code stroke/2} 的圆，
     *       长在边界点上，会朝嘴里探进来 {@code sqrt(half² − dy²)}。</li>
     * </ol>
     * {@code y} 高于边界点且高出半个笔帽时，这儿既没有弧也没有笔帽 —— 嘴是全开的，
     * 返回一个足够大的数（"不受缺口约束"）。
     *
     * <p>这正是"闪电+电量"能放进去的原因：它们整体坐在边界点<b>之上</b>，
     * 只要底边（基线）不越过边界点高度，就不受最窄处限制。
     */
    static float gapClearWidthAt(float y, float gapStart, float gapEnd, float stroke) {
        final float half = stroke * 0.5f;
        final double beta = Math.toRadians((gapEnd - gapStart) * B_SWEEP * 0.5f);
        // 缺口边界点（环中心线上那个，右侧）
        final float xb = B_CX + B_R * (float) Math.sin(beta);
        final float yb = B_CY - B_R * (float) Math.cos(beta);
        float room = Float.MAX_VALUE;
        final float dy = y - yb;
        // ① 圆形笔帽
        if (dy * dy < half * half) {
            room = Math.min(room,
                    2f * (xb - (float) Math.sqrt(half * half - dy * dy) - B_CX));
        }
        // ② 弧线内缘（只有 y 低于边界点，这一高度才有弧）
        if (dy > 0f) {
            final float dyy = B_CY - y;
            if (Math.abs(dyy) < B_R) {
                final float dx = (float) Math.sqrt(B_R * B_R - dyy * dyy);
                room = Math.min(room, 2f * (dx - half));
            } else {
                room = 0f;
            }
        }
        return room;
    }

    // ------------------------------------------------------------------- value
    static final float VALUE_X = 59.5f;
    static final float VALUE_BASELINE = 24f;
    static final float VALUE_SIZE = 32f;
    /** SVG letter-spacing="-0.04em". */
    static final float VALUE_LETTER_SPACING = -0.04f;

    /**
     * The character the reference draws smaller than the rest of the network
     * type: the "A" of "5GA".
     *
     * <p>Kept here rather than in either drawing path so the ring canvas and the
     * out-of-ring label cannot end up disagreeing about which glyph is the
     * suffix. MIUI reports the type as one string ("5G", "5GA"), so the only
     * signal available is the trailing character.
     */
    static final char TYPE_SUFFIX = 'A';

    /**
     * Whether {@code type} ends in a suffix that should be drawn smaller.
     *
     * <p>Both conditions matter: {@code scalePercent} of 100 is "no shrink", and
     * a bare "A" is the whole label rather than a suffix of one - shrinking it
     * would just draw the label smaller, which is what the size slider is for.
     */
    static boolean hasShrunkSuffix(String type, int scalePercent) {
        return scalePercent < 100
                && type != null
                && type.length() > 1
                && type.charAt(type.length() - 1) == TYPE_SUFFIX;
    }

    /** {@code type} without its trailing {@link #TYPE_SUFFIX}. */
    static String typeBase(String type) {
        return type.substring(0, type.length() - 1);
    }

    /**
     * Vertical centre of the ring interior. The level is drawn here once the
     * Wi-Fi area is empty, so the hole left by a missing Wi-Fi glyph carries the
     * battery percentage instead of staying blank.
     */
    static final float CENTER_CY = 64f;
    /** Cap height of the digits, in em. Roboto's is ~0.71em. */
    static final float CAP_HEIGHT_RATIO = 0.71f;
    /**
     * Height of a digit's visual centre above its baseline, in em. Digits fill
     * the cap, so their centre sits half of the cap height above the baseline.
     */
    static final float CAP_CENTRE_RATIO = CAP_HEIGHT_RATIO / 2f;

    /**
     * Smallest y a value's cap may start at when it is drawn in the top gap.
     * Anything above this leaves the 120x120 box and gets clipped by the host.
     *
     * <p>Not 0: the cap sits on a curve, and font metrics differ by a hair
     * between Roboto and the measurement used here, so a cap exactly on the ink
     * edge loses its top row to antialiasing. Two and a half units of headroom
     * costs nothing visually and keeps every digit whole.
     */
    static final float GAP_TOP = 2.5f;

    /**
     * Baseline for a value drawn in the 12 o'clock gap. {@link #VALUE_BASELINE}
     * is the design baseline, but a larger font would push the cap above
     * {@link #GAP_TOP}, so the line is dropped just enough to keep it inside.
     */
    static float gapBaseline(float size) {
        return Math.max(VALUE_BASELINE, GAP_TOP + size * CAP_HEIGHT_RATIO);
    }

    /**
     * The centre of the ring gives the value far more room than the narrow top
     * gap it normally lives in, so a centred value is drawn this much larger.
     *
     * <p>1.5 crowded the ring: the digits ran into the stroke on both sides once
     * the ring was thickened. 1.3 still reads as "the big number" while keeping
     * a visible margin against the inner edge.
     */
    static final float CENTRE_SIZE_RATIO = 1.4f;
    /** Upper bound for {@link #CENTRE_SIZE_RATIO}: "100" must stay inside the ring. */
    static final float CENTRE_MAX_SIZE = 52f;

    /** Font size actually used for a centred value of configured {@code size}. */
    static float centreSize(float size) {
        return Math.min(size * CENTRE_SIZE_RATIO, CENTRE_MAX_SIZE);
    }

    /** Baseline that centres digits of {@code size} on {@link #CENTER_CY}. */
    static float centerBaseline(float size) {
        return CENTER_CY + size * CAP_CENTRE_RATIO;
    }

    /**
     * Clear horizontal room for text drawn inside the ring. The ring's inner
     * edge is one stroke width inside its centre line; the text's cap corners
     * must stay within that circle, so the room narrows as it gets taller.
     *
     * <p>Both centre slots go through here: the percentage when Wi-Fi is off,
     * and the mobile network type ({@code Prefs.KEY_TYPE_SIZE}) when it replaces
     * the percentage. Sizing them independently is safe because neither can
     * overrun the ring.
     */
    static float centreClearWidth(float size, float stroke) {
        final float inner = B_R - stroke * 0.5f - 1f;
        final float dy = size * CAP_HEIGHT_RATIO * 0.5f;
        final float dx = (float) Math.sqrt(Math.max(0f, inner * inner - dy * dy));
        return dx * 2f;
    }

    /**
     * Clear horizontal room for a value drawn in the 12 o'clock gap: the chord
     * between the two rounded arc ends, less one stroke width for those caps.
     */
    static float gapClearWidth(float stroke) {
        final double half = Math.toRadians((GAP_END_IDLE - GAP_START_IDLE) * B_SWEEP * 0.5f);
        return (float) (2.0 * B_R * Math.sin(half)) - stroke;
    }

    // ------------------------------------------------------------------- wi-fi

    static final float W_CX = 59.5f;
    static final float W_STROKE = 7f;
    /** Outer arc: chord (38.5,55.5)-(80.5,55.5), r=31 -> centre below the chord. */
    static final float W1_CY = 78.3035085f;
    static final float W1_R = 31f;
    static final float W1_START = 227.3423f;
    static final float W1_SWEEP = 85.3154f;
    /** Middle arc: chord (47,65.25)-(72,65.25), r=18.5. */
    static final float W2_CY = 78.8881817f;
    static final float W2_R = 18.5f;
    static final float W2_START = 227.5048f;
    static final float W2_SWEEP = 84.9904f;

    /** Topmost ink of the outer arc, at its apex. */
    static final float W_TOP = W1_CY - W1_R;
    /**
     * ★ v104：圆点的最低点 —— wifi 图标参考墨迹的<b>下边</b>（与
     * {@code TrioRenderer#buildWifiDot} 的最后一段贝塞尔终点一致）。
     */
    static final float WIFI_INK_BOTTOM = 80.95f;
    /**
     * ★ v104：wifi 图标在 1.0 倍时上下边的距离（外弧顶端到圆点底端）。
     *
     * <p>组合图标要按"配对高度"缩放 wifi，缺了这个就没法把弧线和数字/闪电
     * 的上下边对齐 —— 只有 {@link #GAP_WIFI_SCALE} 那种固定倍数是不够的。
     */
    static float wifiInkH(float stroke) {
        return WIFI_INK_BOTTOM - (W_TOP - stroke * 0.5f);
    }
    /** ★ v104：wifi 图标在 1.0 倍时的宽度（外弧定宽，圆点比它窄）。 */
    static float wifiInkW(float stroke) {
        return 2f * (W1_R + stroke * 0.5f);
    }
    /**
     * Scale of the Wi-Fi group when the percentage is centred
     * ({@code Prefs.KEY_VALUE_CENTRED}) and the group is moved into the 12
     * o'clock notch. The notch is 69.6 units of chord at the mouth and the group
     * is 49 wide at full size, so it has to shrink to read as a notch ornament
     * rather than spill over the arc ends.
     */
    static final float GAP_WIFI_SCALE = 0.8f;

    /** Keeps the group horizontally centred on the icon axis. */
    static float gapWifiOffsetX() {
        return W_CX * (1f - GAP_WIFI_SCALE);
    }

    /**
     * Puts the topmost ink - the outer arc's apex, half a stroke above its
     * centre line - at {@link #GAP_TOP}, the same clearance the digits get when
     * they sit in the notch.
     */
    static float gapWifiOffsetY(float stroke) {
        return GAP_TOP - (W_TOP - stroke * 0.5f) * GAP_WIFI_SCALE;
    }

    // ------------------------------------------------------- four level dots
    static final float DOT_R = 5.5f;
    static final float[][] DOTS = {
            {33f, 104.2f},
            {50.5f, 111.2f},
            {68.5f, 111.7f},
            {86f, 105.8f},
    };

    /**
     * Radius of a level dot in the dual-SIM reading.
     *
     * <p>Larger than {@link #DOT_R}: with two rows of four the dots are the
     * glyph's dominant feature and the reference draws them at 50px across a
     * 451px-wide image, i.e. 14.56 design units - a 7.28 radius. Rounded down to
     * 7.0 so the top row keeps a hair of clearance inside the ink box at the
     * largest reachable stroke.
     *
     * <p>Only used for the two rows of the dual reading; the single-row layout
     * keeps {@link #DOT_R} untouched, because that is the shipped look.
     */
    static final float DUAL_DOT_R = 7.0f;

    /**
     * The dual-SIM dot rows, eight entries: indices 0..3 are SIM 1 (slot 0) in
     * the top half of the ring, indices 4..7 are SIM 2 (slot 1) at
     * {@link #DOTS}.
     *
     * <p>The top row is {@link #DOTS} mirrored about {@link #B_CY}, which is what
     * makes the two rows sit symmetrically inside the ring - the bottom row
     * follows the ring's lower curve, and the mirror puts the top row on the
     * matching upper curve. The mirror line is the ring centre rather than the
     * middle of the design box: the ring itself is centred at {@code B_CY}, and
     * mirroring about anything else would leave the rows visibly off-centre
     * inside it.
     *
     * <p>Both rows also fall inside the ring's two mouths - the bottom row in the
     * arc's natural opening, the top row in the 12 o'clock notch - which is why
     * the dual reading forces a gap even though it draws no bolt.
     */
    static final float[][] DOTS_DUAL = new float[8][2];

    static {
        for (int i = 0; i < DOTS.length; i++) {
            DOTS_DUAL[i][0] = DOTS[i][0];
            DOTS_DUAL[i][1] = 2f * B_CY - DOTS[i][1];
            DOTS_DUAL[i + DOTS.length][0] = DOTS[i][0];
            DOTS_DUAL[i + DOTS.length][1] = DOTS[i][1];
        }
    }

    /** Opacity of the battery track / inactive level dots. */
    static final int TRACK_ALPHA = 56; // 0.22 * 255

    /** Below this battery level the ring turns the "critical" colour. */
    static final int CRITICAL_LEVEL = 20;

    // ------------------------------------------- stacked out-of-ring signal
    //
    // The out-of-ring reading stands in the status bar, not in the glyph, so
    // none of the ink box above applies to it. Its numbers are quoted in the
    // 350x350 reference image and kept in that image's own units; the view
    // measures itself against them and scales to whatever height the status bar
    // row gives it. Working in the reference's units rather than in dp is what
    // keeps the two rows, the bar steps and the round caps in the proportions
    // the reference draws, at every density.

    /** Columns in one reading: one capsule bar, and one dot beneath it. */
    static final int STACK_COLUMNS = 4;

    /** Bar width in reference pixels. Also the dot row's diameter. */
    static final float STACK_BAR_W = 50f;

    /** Column pitch: {@link #STACK_BAR_W} plus the 17px gap the reference leaves. */
    static final float STACK_PITCH = 67f;

    /**
     * Bar heights, ascending left to right, in reference pixels.
     *
     * <p>An even 25px step - 75, 100, 125, 150 - on a shared baseline, which is
     * what makes the row read as a level at a glance. The baseline is the
     * <em>bottom</em>: the bars grow upwards, so index {@code 3} is both the
     * tallest bar and the height of the whole row.
     */
    static final float[] STACK_BAR_H = {75f, 100f, 125f, 150f};

    /** Dot diameter in reference pixels. Equal to the bar width, as drawn. */
    static final float STACK_DOT_D = 50f;

    /**
     * Gap between the bars' baseline and the top of the dot row, both measured
     * as edges.
     *
     * <p>The reference has no antialiasing, so its edges land on whole pixels
     * and the gap is exactly determinate: the bars' last ink row is 237, which
     * puts their bottom edge at 238, and the dots' first ink row is 247, so the
     * blank band is {@code 238..246} - nine units. Measuring the gap by
     * subtracting those two ink-row indices instead gives ten and a reading one
     * unit too tall, which is exactly the error the reference comparison in
     * {@code work/outringcheck/compare.py} exists to catch.
     */
    static final float STACK_DOT_GAP = 9f;

    /** Width of the two-row reading: {@code 59..309} in the reference. */
    static final float STACK_INK_W =
            STACK_PITCH * (STACK_COLUMNS - 1) + STACK_BAR_W;

    /** Height of the two-row reading: {@code 88..297} in the reference. */
    static final float STACK_INK_H =
            STACK_BAR_H[STACK_COLUMNS - 1] + STACK_DOT_GAP + STACK_DOT_D;

    // ------------------------------------------------- rectangular arrangement
    //
    // Everything below lays the same pieces out the other way: two columns of
    // four dots carry the mobile signal, the top slot between them carries the
    // Wi-Fi arcs, the network type or the charging bolt, the percentage sits
    // enlarged below that, and a full-width bar along the bottom is the battery.
    // There is no ring at all.
    //
    // The coordinates still live in the same 120x120 design space, so the two
    // arrangements differ only in where the pieces go and in the ink box used to
    // fit them into the host view.

    /** Radius of a level dot, and the centre x of each of the two columns. */
    static final float RECT_DOT_R = 8.6f;
    static final float RECT_DOT_LX = 9.6f;
    static final float RECT_DOT_RX = 110.4f;
    /** Centre y of the first dot; each following dot drops by {@link #RECT_DOT_STEP}. */
    static final float RECT_DOT_TOP = 15.6f;
    static final float RECT_DOT_STEP = 22.5f;
    /** Four dots per column, the number of steps a phone's signal meter has. */
    static final int RECT_DOT_ROWS = 4;

    /**
     * The battery bar: a capsule spanning the full width of the columns' outer
     * edges, so its ends line up with the dots above it.
     *
     * <p>Its thickness is {@code TrioSettings#ringStroke} - the ring the slider
     * would otherwise draw is absent here, and the bar is the one heavy line the
     * arrangement keeps, so the slider stays meaningful.
     */
    static final float RECT_BAR_LEFT = 1.0f;
    static final float RECT_BAR_RIGHT = 119.0f;
    static final float RECT_BAR_CY = 107.3f;

    /**
     * Horizontal room for text between the two dot columns. Unlike the ring's
     * clear width this does not depend on the font size: the columns are
     * vertical, so a taller glyph eats into the same gap.
     */
    static final float RECT_TEXT_CLEAR =
            (RECT_DOT_RX - RECT_DOT_R) - (RECT_DOT_LX + RECT_DOT_R);

    /** Baseline and scale of the network type in the top slot. */
    static final float RECT_TYPE_BASELINE = 42.3f;
    static final float RECT_TYPE_SCALE = 1.26f;

    /**
     * Baseline, x and scale of the percentage. The reference draws the digits
     * noticeably larger than the ring arrangement does, which is the point of
     * the arrangement: the columns leave the middle free to be read at a glance.
     */
    static final float RECT_VALUE_X = 60f;
    static final float RECT_VALUE_BASELINE = 92.7f;
    static final float RECT_VALUE_SCALE = 1.38f;

    /**
     * Scale of the Wi-Fi group in the top slot and the y of its topmost ink.
     *
     * <p>Only a translation and a uniform scale: the arcs themselves are the
     * same paths the ring arrangement uses, so both arrangements stay visually
     * identical in shape.
     *
     * <p>{@code 0.95} is measured against the reference: its outer arc's ink is
     * 3.05 dot diameters wide, and the untouched group at scale 1 is 3.20, so
     * the arcs are very nearly full size in this arrangement. It is not exactly
     * 1: the reference's own arcs are a little flatter than this module's
     * (wider ink, thinner stroke). That difference is deliberately not copied -
     * the arcs here are the existing icon, only repositioned.
     */
    static final float RECT_WIFI_SCALE = 0.95f;
    /**
     * The outer arc's apex, half a stroke above its centre line, sits here in
     * design units. The reference puts that ink two of its own pixels below the
     * top of the dot columns, which is about 1.5 design units, so this is a
     * little lower than the dots' ink top at {@link #RECT_DOT_TOP} - {@link
     * #RECT_DOT_R}.
     */
    static final float RECT_WIFI_TOP = 8.5f;

    /** Keeps the Wi-Fi group horizontally centred on the icon axis. */
    static float rectWifiOffsetX() {
        return 60f - W_CX * RECT_WIFI_SCALE;
    }

    /** Puts the outer arc's apex, half a stroke above its centre line, at {@link #RECT_WIFI_TOP}. */
    static float rectWifiOffsetY(float stroke) {
        return RECT_WIFI_TOP - (W_TOP - stroke * 0.5f) * RECT_WIFI_SCALE;
    }

    /**
     * The charging bolt sits in the same top slot. The ring arrangement puts its
     * ink centre at {@link #BOLT_CX} / y=17.26; the arrangement's axis is 60 and
     * its top slot centre slightly lower, so both are nudged by a hair.
     */
    static final float RECT_BOLT_DX = 0.55f;
    static final float RECT_BOLT_DY = 0.24f;

    static float rectBoltOffsetX() {
        return boltOffsetX() + RECT_BOLT_DX;
    }

    static float rectBoltOffsetY() {
        return boltOffsetY() + RECT_BOLT_DY;
    }

    // ---------------------------------------------------- capsule arrangement
    //
    // Ported from upstream HyperDuo v1.7.1's capsule (style 2): a stadium-shaped
    // outline IS the battery bar, the clock sits in a fixed left column, the
    // right-hand slot carries the charging bolt / Wi-Fi arcs / percentage /
    // network type by that priority, and two rows of four signal dots ride the
    // outline's centreline at 12 and 6 o'clock. Fixed default layout only - the
    // editor/Zones machinery is deliberately not ported.
    //
    // Same 120-based design space; the capsule is 2.34:1 wide, so CAP_RIGHT
    // runs past 120 and the host view must answer onMeasure with the width this
    // aspect asks for (see capHostWidthPx).

    /** Outer bounds of the capsule outline, in design units. */
    static final float CAP_LEFT = 1.0f;
    /**
     * Right edge of the outline at the reference's own 2.34:1 aspect.
     *
     * <p>这是<b>设计量宽</b>。实际画的右沿是 {@link #capRight()}（= 本值 + 用户的
     * 「胶囊长度」），所以所有需要横向位置的量都应问那个函数而不是这个常量。
     * 保留常量是因为参考图形是在这个数上量出来的，若干派生量以它为基准。
     * 高度永远不动。
     */
    static final float CAP_RIGHT = 139.0f;
    static final float CAP_TOP = 1.0f;
    static final float CAP_BOTTOM = 60.0f;

    /**
     * ★ 胶囊长度在 force 的那一帧的额外直线段长（设计单位），0 = 参考形状。
     *
     * <p>单个静态而非给六个函数穿参数：读它的有轮廓、填充走线、点行、内缘、
     * 墨迹盒、宿主测量 —— 一帧之内它是常量，穿参数要摸遍所有胶囊调用点及其
     * 调用者。渲染层在每次绘制开头 {@code set}，预览用自己的快照 {@code set}，
     * 两个进程不共享（volatile 保可见性）。<b>不在这里读活动设置</b>：本类保持
     * 纯几何，由调用方把设置值喂进来。
     */
    private static volatile int sCapLength = 0;

    /** 设定本帧要画的额外直线段长（先夹到合法区间）。 */
    static void setCapsuleLength(int extra) {
        sCapLength = Prefs.clamp(extra, Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH);
    }

    /** 供无设置可拿的调用方镜像（宿主测量等）。 */
    static int capsuleLength() {
        return sCapLength;
    }

    // ★ 时间列位置（2026-10-24 用户要求）：CAP_TIME_LEFT = 时间在左、同伴在右（默认，
    //   原行为）；CAP_TIME_RIGHT = 两者互换，槽宽跟随内容（时间在哪侧哪侧为宽槽）。
    private static volatile int sTimeSide = Prefs.DEF_CAPSULE_TIME_SIDE;

    static void setCapsuleTimeSide(int side) {
        sTimeSide = Prefs.clamp(side, Prefs.MIN_CAPSULE_TIME_SIDE, Prefs.MAX_CAPSULE_TIME_SIDE);
    }

    static int capsuleTimeSide() {
        return sTimeSide;
    }

    /**
     * 画的右沿：参考宽 + 用户加长。胶囊的<b>所有横向量</b>都从它派生 ——
     * 端帽圆心、点行中心、内缘、墨迹盒。加长它 = 两条直线段各长这么多，
     * 别的什么都不动：端帽半径不变、高度不变、按高度表述的比例不变。
     */
    static float capRight() {
        return CAP_RIGHT + sCapLength;
    }

    /** Corner radius: half the height, which makes the outline a stadium. */
    static final float CAP_RADIUS = (CAP_BOTTOM - CAP_TOP) * 0.5f;

    /**
     * ★ 三星风格胶囊端帽：超椭圆指数 n（squircle）。圆心/宽高与常规端帽完全一致，
     * 仅把四分之一圆换成 {@code |x/r|^n + |y/r|^n = 1}：n=2 退化为原半圆（体育场），
     * n 越大端部越"方"、直线段延伸更长、曲率从 0 平滑增大再回落（与 S25U 屏幕圆角同语言）。
     * 实机先 n=3.0，用户实测端部过方、拐角处出现楔形缝，改回 n=2.5（L63）。
     */
    static final float CAP_SHAPE_SAMSUNG_N = 2.5f;

    /**
     * The outline's thickness, as a fraction of {@code ringStroke}. The
     * reference draws it at 12.6% of the capsule's height; ringStroke defaults
     * to 14, so this factor lands there. Kept as a ratio so the stroke slider
     * still means something in this arrangement.
     */
    static final float CAP_STROKE_RATIO = 0.53f;

    /** Radius of one signal dot, and the pitch between their centres. */
    static final float CAP_DOT_R = 3.7f;
    static final float CAP_DOT_PITCH = 10.9f;

    /** Centre x of the four-dot row: the capsule's own centre (follows capRight). */
    static float capDotCx() {
        return (CAP_LEFT + capRight()) * 0.5f;
    }

    /**
     * The clear space the stroke leaves on each side of a dot row, as a
     * fraction of the capsule's path height. 9% against the reference's 6.67%.
     * Measured against the PATH rather than the ink height so the clearance
     * does not move when the stroke slider moves.
     */
    static final float CAP_DOT_CLEARANCE_RATIO = 0.09f;

    /** The clear space on one side of a dot row, in design units. */
    static float capDotClearance() {
        return CAP_DOT_CLEARANCE_RATIO * (CAP_BOTTOM - CAP_TOP);
    }

    /**
     * Half the horizontal span of one dot row: how far the outline's path is
     * cut back at 12 and 6 o'clock. Three terms - the dots' own half-span, the
     * clearance, and half the stroke, because the cut end inks a ROUND cap of
     * radius stroke/2 past its endpoint.
     */
    static float capDotReach(float stroke) {
        return CAP_DOT_PITCH * 1.5f + CAP_DOT_R + capDotClearance() + stroke * 0.5f;
    }

    /** The same at the default stroke, for callers with no stroke to hand. */
    static float capDotReach() {
        return capDotReach(Prefs.DEF_RING_STROKE * CAP_STROKE_RATIO);
    }

    /**
     * Centre y of the top and bottom dot rows: exactly the outline's path
     * coordinates. The stroke straddles the path, so the path's own y is the
     * line's centre - putting the dots on the inner edge instead would hang
     * them below the line they are set into.
     */
    static float capDotTopY() {
        return CAP_TOP;
    }

    static float capDotBottomY() {
        return CAP_BOTTOM;
    }

    /** Where the two end caps stop and the flat runs begin. */
    static float capFlatLeft() {
        return CAP_LEFT + CAP_RADIUS;
    }

    static float capFlatRight() {
        return capRight() - CAP_RADIUS;
    }

    static float capInteriorLeft(float stroke) {
        return CAP_LEFT + stroke * 0.5f;
    }

    static float capInteriorRight(float stroke) {
        return capRight() - stroke * 0.5f;
    }

    /**
     * The interior's vertical extent - the bound that matters. The two dot
     * rows ride the outline's centreline, so anything drawn inside has to live
     * between them.
     */
    static float capInteriorTop(float stroke) {
        return CAP_TOP + stroke * 0.5f + CAP_DOT_R;
    }

    static float capInteriorBottom(float stroke) {
        return CAP_BOTTOM - stroke * 0.5f - CAP_DOT_R;
    }

    static float capInteriorHeight(float stroke) {
        return capInteriorBottom(stroke) - capInteriorTop(stroke);
    }

    static float capInteriorCy(float stroke) {
        return (capInteriorTop(stroke) + capInteriorBottom(stroke)) * 0.5f;
    }

    /** The gap the two readings keep between each other. */
    static final float CAP_TEXT_GAP = 6f;

    /**
     * The margin a reading keeps from the outline's inner edge, as a fraction
     * of the capsule's height (about 8.6%, read off the reference).
     */
    static final float CAP_TEXT_INSET_RATIO = 0.086f;

    /** How wide a reading may be inside a column of {@code slotWidth}. */
    static float capTextClearWidth(float stroke, float slotWidth, int suffixPercent) {
        return Math.max(1f, slotWidth - capTextInset() * 2f - stroke);
    }

    static float capTextInset() {
        return CAP_TEXT_INSET_RATIO * (CAP_BOTTOM - CAP_TOP);
    }

    static float capTextLeft(float stroke) {
        return capInteriorLeft(stroke) + capTextInset();
    }

    static float capTextRight(float stroke) {
        return capInteriorRight(stroke) - capTextInset();
    }

    /**
     * Natural width of each reading per unit of font size, measured off the
     * platform's own metrics: "19:41" is 2.52 units wide per unit of size and
     * "100" is 1.68. Their ratio divides the two slots - not a half.
     */
    static final float CAP_TIME_TEXT_PER_UNIT = 2.52f;
    static final float CAP_VALUE_TEXT_PER_UNIT = 1.68f;

    /** The share of the readings' width the time takes; the rest goes right. */
    static final float CAP_TIME_WIDTH_SHARE =
            CAP_TIME_TEXT_PER_UNIT / (CAP_TIME_TEXT_PER_UNIT + CAP_VALUE_TEXT_PER_UNIT);

    /** The width the two readings share, once the gap between them is out. */
    static float capReadingsWidth(float stroke) {
        return capTextRight(stroke) - capTextLeft(stroke) - CAP_TEXT_GAP;
    }

    /**
     * 时间列所在槽的可用宽：按两者**自然字宽比**恒定划分（2.52:1.68），
     * 与时间在哪一侧无关 —— side 只决定中心在哪侧（见 {@link #capTimeCx}）。
     * ★ L74 修：此前写成「槽宽随 side 互换」，时间在右时同伴反而拿到宽槽字号
     *   却画进窄槽，字大溢出（用户实机截图：76 巨大、08:23 过小）。
     */
    static float capTimeSlotWidth(float stroke) {
        return capReadingsWidth(stroke) * CAP_TIME_WIDTH_SHARE;
    }

    /** 同伴列所在槽的可用宽：电量数字、Wi-Fi 弧、闪电或类型。恒定取 40%。 */
    static float capValueSlotWidth(float stroke) {
        return capReadingsWidth(stroke) * (1f - CAP_TIME_WIDTH_SHARE);
    }

    /**
     * 时间列中心：LEFT = 原左列（默认），RIGHT = 原右列。
     * 槽宽不随 side 变（内容自然宽度比恒定），换侧后字号与「时间在左」时完全一致。
     */
    static float capTimeCx(float stroke, int side) {
        return side == Prefs.CAP_TIME_LEFT
                ? capTextLeft(stroke) + capTimeSlotWidth(stroke) * 0.5f
                : capTextRight(stroke) - capTimeSlotWidth(stroke) * 0.5f;
    }

    /** 同伴列中心（电量/弧线/闪电/类型）：与时间列互补，永远在另一侧。 */
    static float capValueCx(float stroke, int side) {
        return side == Prefs.CAP_TIME_LEFT
                ? capTextRight(stroke) - capValueSlotWidth(stroke) * 0.5f
                : capTextLeft(stroke) + capValueSlotWidth(stroke) * 0.5f;
    }

    /**
     * Where a line of digits sits, so its cap height is centred in the
     * interior: half the cap above and half below the interior's own middle.
     */
    static float capTextBaseline(float stroke, float size) {
        return capInteriorCy(stroke) + size * CAP_HEIGHT_RATIO * 0.5f;
    }

    /**
     * How tall a reading may be, as a fraction of the interior. A backstop,
     * not the binding bound - the width fit decides the type first.
     */
    static final float CAP_TEXT_HEIGHT_RATIO = 0.82f;

    /**
     * The requested reading size, as a multiple of {@code valueSize}: the
     * reference draws text whose cap height is 26.5% of the capsule's height,
     * and an em is that cap height divided by {@link #CAP_HEIGHT_RATIO}.
     */
    static final float CAP_TEXT_SCALE = 0.265f * CAP_BOTTOM / (CAP_HEIGHT_RATIO * 36f);

    /** Largest size the capsule's interior allows. */
    static float capTextMaxSize(float stroke) {
        return capInteriorHeight(stroke) * CAP_TEXT_HEIGHT_RATIO / CAP_HEIGHT_RATIO;
    }

    /**
     * The Wi-Fi group's ink extents, quoted at the reference's own 12-unit arc
     * stroke; the offset compensates for the stroke actually in force. The
     * ink centre is what the arcs are optically centred on.
     */
    static final float W_INK_W = 54.0f;
    /**
     * ★ Wi-Fi 弧的**实际水平跨度**（120 设计空间）。
     *
     * <p>{@link #W_INK_W} 记的是叶形点那一小块的墨宽，可**弧本身**比它宽：
     * 弧心在 W_CX、半径 W1_R，水平范围是 W_CX ± 31 = 62。算断口宽度时若用
     * W_INK_W，弧的右边就会压到轮廓线上（用户实机截图）。
     */
    static final float W_ARC_W = 62.0f;
    static final float W_INK_H = 39.65f;
    static final float W_INK_CY = 61.13f;

    /** The bolt's ink extremes, from {@code buildBolt}'s own coordinates. */
    static final float BOLT_INK_H = 19.9f;
    static final float BOLT_INK_CY = 12.05f;
    /** The bolt's ink width, measured off the same path (x spans 51.3..67.6). */
    static final float BOLT_INK_W = 16.3f;

    // ------------------------------------------------- 顶部空位 / 闪电+同伴 组合
    /**
     * ★ 顶部那一格的<b>绝对字号上限</b>（em）—— 只是极端设置下的兜底。
     *
     * <p>正常情况下<b>不生效</b>：顶部那件东西用回它自己的字号（5GA 用 typeSize、
     * 电量用 valueSize），与它在中部时一模一样。此前这里封顶 18，把 5GA 压小了一号
     * —— 用户一眼就看出来了（"闪电在圈内时 5G 大小对的，闪电到圈上就变小了"）。
     *
     * <p>断口的宽度约束改由 {@code TrioRenderer} 按实际宽度兜底：装不下就整体缩小，
     * 而不是先把字号砍小。
     */
    /**
     * ★ 顶部那件东西相对右列的**比例缩放**。
     *
     * <p>顶部只有一行（内部高的一半不到），所以比右列小一号；关键是**跟着设置走**
     * —— 此前这里是个硬上限（24），于是调「网络类型字号」时顶部纹丝不动，
     * 反而是时间跟着变了（用户反馈）。
     */
    static final float CAP_TOP_SCALE = 0.8f;
    /**
     * 「闪电 + 同伴」组合里两件之间的间距，设计单位。
     * ★ v106：3.0 → 4.0，与圆环 {@link #CHARGE_PAIR_GAP} 对齐 —— 用户实测
     * 3.0 时闪电与电量/信号/5GA 全都"贴住"，要圆环那种一丢丢空隙。
     */
    static final float CAP_PAIR_GAP = 3.0f;

    /**
     * ★ 顶部开口里那件东西的垂直中心：**悬浮在轮廓线上方**。
     *
     * <p>顶边贴墨迹盒上沿、底边正好压到线上 —— 这是用户认可的那版预览里的位置。
     * 此前先后改成"骑在线上"（0.4h）和"坐在缺口内侧"，都被否了
     * （"下沉了""位置都不对"）。
     */
    static float capTopSlotCy(float height) {
        // ★ 与 L41 一致（用户确认"L41 图标位置是好的"）：顶边贴墨迹盒上沿，
        //   底部那件悬浮在轮廓线上方。
        return CAP_INK_Y + height * 0.5f;
    }

    /**
     * ★ 顶部那件东西的固定 cap 高 —— 与用户认可的那版预览一致（比右列大一号）。
     *
     * <p>顶部只有它自己一件东西，可以比右列大一点才压得住场；右列那一格
     * 要和时钟并排，反而要小一号。
     */
    static final float CAP_TOP_CAP_H = 18.0f;


    /**
     * ★ 顶部开口里那件东西离线头的距离。
     *
     * <p>信号点行开口用 {@link #capDotClearance()}（5.31）—— 那是**点与点之间**
     * 要留的呼吸。顶部放的是**一整件东西**（闪电+读数），用同一套让位就会在
     * 内容两侧各空出 5.31 + 半个描边 ≈ 9 个单位，看着"空白一大块"（用户实机截图）。
     * 顶部用自己的一点点间隙即可。
     */
    static final float CAP_TOP_CLEARANCE = 1.0f;

    /**
     * ★ 顶部开口按内容宽度让位：内容半宽 + 顶部自己的间隙 + 半个描边。
     */
    static float capTopSlotReach(float contentWidth, float stroke) {
        return contentWidth * 0.5f + CAP_TOP_CLEARANCE + stroke * 0.5f;
    }

    /**
     * The height every reading and icon in the capsule is drawn at: the
     * reference's 26.5% of the capsule's height, as a cap height.
     */
    static final float CAP_ICON_H = 0.265f * CAP_BOTTOM;

    /**
     * The arcs' and the bolt's scales, each sized so its own ink is
     * {@link #CAP_ICON_H} tall, each also capped by the interior so an extreme
     * stroke setting makes the icons give way rather than cross the dot rows.
     */
    static float capWifiScale(float stroke) {
        return Math.min(CAP_ICON_H / W_INK_H, capInteriorHeight(stroke) / W_INK_H);
    }

    static float capBoltScale(float stroke) {
        return Math.min(CAP_ICON_H / BOLT_INK_H, capInteriorHeight(stroke) / BOLT_INK_H);
    }

    /** Places the arcs' ink centre on the right-hand slot's own centre. */
    static float capWifiOffsetX(float stroke) {
        return capValueCx(stroke, capsuleTimeSide()) - W_CX * capWifiScale(stroke);
    }

    static float capWifiOffsetY(float stroke) {
        return capInteriorCy(stroke) - W_INK_CY * capWifiScale(stroke);
    }

    /**
     * The bolt's offset, written against {@code BOLT_CX_REF} and
     * {@link #BOLT_INK_CY} - the bolt path's own ink centre. Never against
     * {@code BOLT_CX}/{@code BOLT_TOP}: those are the ring's targets, and
     * reusing them as path coordinates puts the bolt down and to the right.
     */
    static float capBoltOffsetX(float stroke) {
        return capValueCx(stroke, capsuleTimeSide()) - BOLT_CX_REF * capBoltScale(stroke);
    }

    static float capBoltOffsetY(float stroke) {
        return capInteriorCy(stroke) - BOLT_INK_CY * capBoltScale(stroke);
    }

    /**
     * The capsule's ink box: the outline plus its stroke, sized against the
     * largest stroke the slider offers rather than the default, the way
     * {@link #RECT_INK_W} is.
     *
     * <p>★ 胶囊长度：横向要用 {@link #capInkW()}（随长度变），纵向仍用常量
     * （高度不动）。比例尺由高度定，所以加长只改宿主宽度、不改绘制比例。
     */
    static final float CAP_INK_X = CAP_LEFT - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f;
    static final float CAP_INK_Y = CAP_TOP - Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f;
    static final float CAP_INK_W =
            (CAP_RIGHT + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_X;
    static final float CAP_INK_H =
            (CAP_BOTTOM + Prefs.MAX_RING_STROKE * CAP_STROKE_RATIO * 0.5f) - CAP_INK_Y;

    /** 实际画的墨迹盒宽：参考宽 + 胶囊长度（纵向 {@link #CAP_INK_H} 不变）。 */
    static float capInkW() {
        return CAP_INK_W + sCapLength;
    }

    /**
     * 开口带高度（设计单位）。★ 上游 v1.7 同值 9：在墨迹盒上下各加一条带，
     * 让顶部/底部开口里的那件东西有落点，而胶囊本体像素不变（宿主高度含这两条带）。
     */
    static final float CAP_BAND_H = 9f;

    /**
     * 含开口带的墨迹盒上下沿：绘制比例由宽度决定（CAP_INK_W），所以胶囊本体
     * 像素不变，只是宿主更高，多出来的空间正是开口件要用的。
     */
    static final float CAP_INK_TOP = CAP_INK_Y - CAP_BAND_H;
    static final float CAP_INK_BOTTOM = CAP_INK_Y + CAP_INK_H + CAP_BAND_H;

    /**
     * 宿主高度：在状态栏给出的高度基础上，按比例尺补上上下两条开口带。
     * 宽度仍由 {@link #capHostWidthPx} 决定（见其注释），所以胶囊本体大小不变，
     * 只是视图更高，给开口件留出空间，避免被宿主画布裁掉。
     */
    static int capHostHeightPx(int heightPx) {
        if (heightPx <= 0) {
            return 0;
        }
        final float scale = heightPx / CAP_INK_H;
        final int grown = Math.round(heightPx + 2f * CAP_BAND_H * scale);
        return Math.max(heightPx, grown);
    }

    /**
     * 开口（TOP/BOTTOM 那一行）里那件东西的垂直中心：落在<b>轮廓线本身</b>上
     * （openingLaneCy 返回 0），即骑在缺口开口处 —— 与上游一致。
     * 顶部开口件半边在带里、半边在胶囊体内；底部同理。
     */
    static float capOpeningCy(boolean top, float stroke) {
        return top ? CAP_TOP + openingLaneCy(stroke) : CAP_BOTTOM - openingLaneCy(stroke);
    }

    /**
     * 开口件相对轮廓线向外偏移多少。返回 0：件骑在线上（上游同款），带里的空间
     * 由 {@link #CAP_BAND_H} 提供，绘制比例不变。
     */
    private static float openingLaneCy(float stroke) {
        return 0f;
    }

    /** 开口件可用墨高（设计单位）：带高减去描边与间隙。 */
    static float capOpeningTextRoomH(float stroke) {
        final float inward = capInteriorTop(stroke) - CAP_TOP;
        final float outward = CAP_TOP - CAP_INK_TOP;
        final float half = Math.min(inward, outward) - CAP_OPENING_GAP;
        final float ink = 2f * half;
        return Math.max(CAP_DOT_R * 2f, ink);
    }

    /** 同上，但作为字号（em）：文本适配吃的是 em。 */
    static float capOpeningTextEm(float stroke) {
        return capOpeningTextRoomH(stroke) / CAP_HEIGHT_RATIO;
    }

    /** 开口件离线头的间隙。 */
    private static final float CAP_OPENING_GAP = 1f;

    /**
     * 顶部开口件整体下移多少设计单位（三星挂载专用）。
     *
     * <p>实机日志（debug(58)）证实：状态栏 GlyphView 被负值 {@code lpMargin} 上移塞进
     * 状态栏行，而某父层 clip 未被解除（{@code setClipToPadding not found → guard skipped}），
     * 于是顶部带（开口里的信号/类型）被裁掉一点点；底部带没被裁（点 {@code dotY=104.2} 正常）。
     *
     * <p><b>用户 10-22 实机标定 = 1.0</b>（1 设计单位 ≈ 1.35px @画布198）：
     * 先前 9.0 实测过头（用户：\"太下来了\"，实移 12.2px）；作为量级参照，缺口里 5GA 的
     * \"G\" 那一竖本身也才 ≈2.2 设计单位 ≈ 3px —— 用户要的就是比它还小的\"一丢丢\"。
     * 仅三星侧需要——上游在 HyperOS 原链上 clipChildren=false，骑线不触发。
     */
    static final float CAP_TOP_OPEN_DROP = 1.0f;

    /**
     * 开口（缺口）轮廓在内容半宽之外再额外退让多少：让带缺口的地方整体多
     * 留一点缝隙（用户：每个带缺口的地方都稍微再加一点缝）。不含成对时同伴
     * 那件的半宽——那部分由 {@link #capPairHalf} 算进开口。
     */
    static final float CAP_OPENING_EXTRA = 2.0f;

    /**
     * 开口件（文字或图标）的水平半宽，用于按"这一帧实际画的那件"回退轮廓缺口。
     * measured 而非估计：电量/类型/时钟宽度差 2:1 以上，单一常量会要么压线、要么留空。
     */
    static float capPieceReach(int part, int level, String type, float stroke, float roomH) {
        switch (part) {
            case Prefs.PART_WIFI:
                return W_INK_W * capWifiScale(stroke) * 0.5f;
            case Prefs.PART_BOLT:
                return 6.25f * capBoltScale(stroke);
            case Prefs.PART_VALUE:
                return capTextReach("100", stroke, roomH);
            case Prefs.PART_TYPE:
                return capTextReach("5GA", stroke, roomH);
            case Prefs.PART_CLOCK:
                return capTextReach("19:41", stroke, roomH);
            default:
                return 0f;
        }
    }

    /** 文本在开口字号下实际会画多宽（半宽）。 */
    private static float capTextReach(String text, float stroke, float roomH) {
        final float em = Math.min(Prefs.DEF_VALUE_SIZE * CAP_TEXT_SCALE, roomH);
        final float half = Math.max(0, text.length()) * em * 0.31f;
        return Math.min(half, capOpeningAvailWidth(stroke) * 0.5f);
    }

    /** 开口整行可用宽度：平直线段范围（弧端之外会悬空在弧上）。 */
    static float capOpeningWidth() {
        return capFlatRight() - capFlatLeft();
    }

    /**
     * 开口退让上限：平直段的一半。负长度把平直段缩短后，退让若仍按内容全宽走，
     * 切口会切进端帽 —— 填充走线的负长度段被跳过，下一个端帽又从对面当前点
     * 隐式连线到帽起点，画出横穿开口的弦（用户实测 −20 起绿色填充越界）。
     * 所有开口退让不得超过此值。
     */
    static float capOpeningMaxReach() {
        return (capRight() - CAP_LEFT) * 0.5f - CAP_RADIUS;
    }

    /**
     * 开口里那件内容的可用整行宽：退让上限扣掉点间隙、半个描边与额外缝。
     * 内容按它收缩后，「内容半宽 + 间隙 + 半描边 + 额外缝」才不会超过退让上限，
     * 图标不会顶到切口的圆头。
     */
    static float capOpeningAvailWidth(float stroke) {
        return 2f * Math.max(0f, capOpeningMaxReach()
                - (capDotClearance() + stroke * 0.5f + CAP_OPENING_EXTRA));
    }

    /**
     * The width the capsule's host view has to ask for, so the shape can be as
     * tall as the bar allows: the ink box's own aspect ratio. Deliberately not
     * scaled by glyphFit - that multiplier is already applied to the drawing
     * scale, and folding it in here would apply it twice.
     */
    static int capHostWidthPx(int heightPx) {
        if (heightPx <= 0) {
            return 0;
        }
        return Math.round(heightPx * (capInkW() / CAP_INK_H));
    }

    // ------------------------------------------------------------------- frame
    /**
     * Tight ink box of the whole glyph (battery arc + stroke, Wi-Fi arcs + dot,
     * level dots, top indicator) in design units. Used to fit 120x120 artwork
     * into the host view without non-uniform scaling.
     */
    static final float INK_X = 1.0f;
    static final float INK_Y = 0.5f;
    static final float INK_W = 117.0f;
    static final float INK_H = 117.5f;

    /**
     * The same box for the rectangular arrangement.
     *
     * <p>Wider and slightly shorter, and - unlike {@link #INK_W}/{@link #INK_H} -
     * sized against the <em>extremes</em> every slider can reach rather than
     * against the default look: the bolt's tip at {@code showValue} with the
     * thickest arc strokes, and the bar's underside at
     * {@code Prefs#MAX_RING_STROKE}. Fitting the extremes means no setting can
     * push ink outside the host view, where it would be clipped; the cost is
     * that the default glyph ends up a few percent smaller than it could be.
     */
    static final float RECT_INK_X = 1.0f;
    static final float RECT_INK_Y = 1.0f;
    static final float RECT_INK_W = 118.0f;
    static final float RECT_INK_H = 114.4f;

    // ------------------------------------------------------------------ colours
    static final int CRITICAL = 0xFFFF3B30;
    static final int CHARGING_LIGHT = 0xFF34C759;
    static final int CHARGING_DARK = 0xFF1F8F3D;
    static final int LOW_POWER_LIGHT = 0xFFF2B900;
    static final int LOW_POWER_DARK = 0xFFC99700;

    /**
     * Amber used for the charging bolt while the battery reports quick charge.
     * At the real 60px status-bar size colour is the only channel that stays
     * instantly legible, so quick charge is told apart by hue rather than by
     * extra glyphs (two bolts merge into an unreadable blob at that size).
     */
    static final int QUICK_CHARGE = 0xFFFFBA28;
}
