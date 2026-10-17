package com.jsj.samsungduo;

/**
 * Every decision about <em>what</em> the trio glyph puts on screen, resolved once
 * from a single settings snapshot.
 *
 * <p>Before this existed the same rule was written out by hand in five places:
 * {@code TrioRenderer.drawRingLayout}, {@code TrioRenderer.drawRectLayout},
 * {@code TrioHooks.applyMeterText}, the two battery-style hooks - and, in a
 * different shape again, the settings screen's own row gates. Copies drift, and
 * two of them had already drifted away from what the documentation claimed: the
 * rectangular arrangement silently ignored {@code valueCentred}, and the
 * percentage took the centre whenever Wi-Fi was absent whether or not the user
 * had asked for it.
 *
 * <p>This class is now the one place that rule lives. The renderer asks it what
 * to draw, the hook side asks it what to suppress, and the settings screen asks
 * it which rows mean anything - so a row can no longer be greyed out for a
 * reason the drawing code does not share.
 *
 * <p>Deliberately free of Android types and of static state. The hooked SystemUI
 * process and the settings app both build it, and the settings app builds it
 * again on every recomposition; it is a handful of field reads and copies, cheap
 * enough to build per frame.
 *
 * <p>Immutable.
 */
public final class TrioAppearance {

    /** {@link Prefs#STYLE_RING} or {@link Prefs#STYLE_RECT}. */
    public final int style;
    /** True when {@link #style} is the rectangular arrangement. */
    public final boolean rect;
    /** The master switch. False hands every slot back to MIUI. */
    public final boolean glyph;
    /** The Wi-Fi arcs are wanted; ink also needs a signal - see {@link #wifiInk}. */
    public final boolean wifi;
    /** The mobile level dots are drawn. */
    public final boolean mobile;
    /**
     * The user wants a <em>second</em> row of level dots while the glyph has
     * neither Wi-Fi ink nor a charger to report - see {@link #dualReading}.
     */
    public final boolean dualSim;
    /** ★ v73：电池温度读数 wanted。★ v75：功率读数已删，只剩这一项。 */
    public final boolean perfTemp;
    /** ★ v73：两项读数在自适应字号之上的整体缩放（%）。 */
    public final int perfScale;
    /** ★ v73：两项读数的字重。 */
    public final int perfWeight;
    /**
     * ★ v73：三合一整体尺寸（%）。
     *
     * <p>只缩<b>绘制</b>、不动视图尺寸：视图那一块是占位，动了就会挤压原生图标。
     * 因此它必须进 {@link TrioRenderer#inkScale} 那一类的统一比例函数，
     * 让描边、弧线、数字、环外标签字号一起缩，而不是只缩某一个绘制调用。
     */
    public final int glyphScale;
    /** {@link Prefs#SIGNAL_IN_RING} or {@link Prefs#SIGNAL_OUT_RING}. */
    public final int signalMode;
    /** The signal is the glyph's own level dots. */
    public final boolean signalInRing;
    /**
     * The signal is drawn beside the battery instead of inside the glyph.
     *
     * <p>Alone this means "do not draw it, and do not claim it either" - the
     * native icon stays where MIUI put it. Whether the module draws a
     * replacement is {@link #stackedSignal}, and whether it takes the native
     * icon away is {@link #stackedOut}.
     */
    public final boolean signalOutOfRing;
    /** The user wants the bars-and-dots reading of the out-of-ring signal. */
    public final boolean stackedSignal;
    /** The user wants only the current data SIM in that reading. */
    public final boolean dataSimOnly;
    /** The battery percentage is drawn. */
    public final boolean value;
    /** The user wants the bolt while charging. Only ever drawn with {@link #value}. */
    public final boolean boltWanted;
    /**
     * The percentage takes the ring's centre even when the arcs are drawn.
     *
     * <p>Always false for the rectangular arrangement, which has no notch to move
     * the arcs into. Saying so here - rather than leaving the renderer to ignore
     * the switch quietly - is what lets the settings screen gate the row on the
     * style without a second copy of the rule.
     */
    public final boolean centreValue;
    /** {@link Prefs#MOBILE_TYPE_OFF}, {@code IN_RING} or {@code OUT_RING}. */
    public final int typeMode;
    /** The network type is drawn inside the glyph. */
    public final boolean typeInRing;
    /** The network type is drawn as a status-bar view outside the glyph. */
    public final boolean typeOutOfRing;

    public final boolean roleColors;
    public final int criticalOnDark;
    public final int criticalOnLight;
    public final int chargingOnDark;
    public final int chargingOnLight;
    public final int lowOnDark;
    public final int lowOnLight;
    public final int lowThreshold;
    /** ★ v77：省电模式是否参与电量着色（默认开；三星原生不染色，可关）。 */
    public final boolean powerSaveTint;
    /** ★ v82：充电时闪电与电量同时显示（缺口左闪电、右电量，上下边对齐）。 */
    public final boolean chargeWithValue;
    /** ★ v82：「闪电+电量」这一对的字号（30–36，空间不足时自动缩小）。 */
    public final int chargeValueSize;

    public final int ringStroke;
    public final int arcStroke;
    public final int trackAlpha;
    public final int valueSize;
    public final int valueWeight;
    public final int typeSize;
    public final int outTypeSize;
    public final int outSignalSize;
    /**
     * The gap the out-of-ring label keeps on each side, in dp: {@code left} is
     * the side facing away from the battery in LTR and {@code right} the side
     * facing it. Both are physical sides of the label, not reading-order starts
     * and ends - the RTL branch swaps which gap each one supplies.
     */
    public final int outTypeMarginLeft;
    public final int outTypeMarginRight;
    /**
     * Gap on the out-of-ring reading's outer side, in half-dp — the reading
     * being the group's leftmost part, this spaces the whole group from the
     * native icons. May be negative to tighten past zero.
     */
    public final int outSignalMargin;
    /** Extra leftwards nudge for the ring, in dp; negative tightens. */
    public final int glyphLeftOffset;
    /** Scale of a trailing "A" in the network type, as a percentage. */
    public final int typeSuffixScale;
    public final int typeWeight;

    private TrioAppearance(TrioSettings c) {
        style = c.trioStyle;
        rect = c.trioStyle == Prefs.STYLE_RECT;
        glyph = c.enabled;
        wifi = c.showWifi;
        mobile = c.showMobile;
        dualSim = c.dualSim;
        perfTemp = c.perfTemp;
        perfScale = c.perfScale;
        perfWeight = c.perfWeight;
        glyphScale = c.glyphScale;
        signalMode = c.signalMode;
        signalInRing = c.signalMode == Prefs.SIGNAL_IN_RING;
        signalOutOfRing = c.signalMode == Prefs.SIGNAL_OUT_RING;
        stackedSignal = c.stackedSignal;
        dataSimOnly = c.dataSimOnly;
        value = c.showValue;
        boltWanted = c.showBolt;
        centreValue = c.valueCentred && !rect;
        typeMode = c.mobileTypeMode;
        typeInRing = c.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING;
        typeOutOfRing = c.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING;

        roleColors = c.roleColors;
        criticalOnDark = c.criticalOnDark;
        criticalOnLight = c.criticalOnLight;
        chargingOnDark = c.chargingOnDark;
        chargingOnLight = c.chargingOnLight;
        lowOnDark = c.lowOnDark;
        lowOnLight = c.lowOnLight;
        lowThreshold = c.lowThreshold;
        powerSaveTint = c.powerSaveColor;
        chargeWithValue = c.chargeWithValue;
        chargeValueSize = c.chargeValueSize;

        ringStroke = c.ringStroke;
        arcStroke = c.arcStroke;
        trackAlpha = c.trackAlpha;
        valueSize = c.valueSize;
        valueWeight = c.valueWeight;
        typeSize = c.typeSize;
        outTypeSize = c.outTypeSize;
        outSignalSize = c.outSignalSize;
        outTypeMarginLeft = c.outTypeMarginLeft;
        outTypeMarginRight = c.outTypeMarginRight;
        outSignalMargin = c.outSignalMargin;
        glyphLeftOffset = c.glyphLeftOffset;
        typeSuffixScale = c.typeSuffixScale;
        typeWeight = c.typeWeight;
    }

    /** The appearance of {@code cfg}, or of the shipped defaults when it is null. */
    public static TrioAppearance of(TrioSettings cfg) {
        return new TrioAppearance(cfg == null ? TrioSettings.defaults() : cfg);
    }

    /**
     * Whether the glyph draws the charging bolt itself.
     *
     * <p><b>★ v100：闪电开关独立化。</b>此前这里返回 {@code boltWanted && value}，
     * 把"充电闪电"和"电量数字"两个开关绑死了：关掉电量数字后，闪电也不再画，
     * 设置页里"充电时显示闪电"跟着被禁用（用户原话：关电量后这个按钮就废了）。
     * 现改为只认 {@code boltWanted}。
     *
     * <p>电量数字关掉时 {@code Ring.hasValue=false → valueInGap=false → chargePair=false}，
     * 于是走 {@code TrioRenderer} 的 {@code r.bolt && !chargePair} 分支 ——
     * 闪电单独画在 12 点充电缺口（{@code GAP_START_CHARGE} 窄缺口），正是想要的效果。
     *
     * <p>Not gated on {@link #glyph}, because the preview keeps drawing the
     * chosen arrangement while the master switch is off.
     */
    public boolean drawsBolt() {
        return boltWanted;
    }

    /**
     * Whether MIUI's own charging bolt has to stay out of the way, which is
     * exactly while this module is on <em>and</em> draws one of its own.
     */
    public boolean hidesNativeBolt() {
        return glyph && drawsBolt();
    }

    /** Whether the Wi-Fi arcs carry ink at this signal level. */
    public boolean wifiInk(int wifiLevel) {
        return wifi && wifiLevel >= 1;
    }

    /**
     * Whether a row of level dots is drawn per SIM - SIM 1 on top, SIM 2 below -
     * rather than the usual single row for the current data SIM.
     *
     * <p>The user asks for this while neither Wi-Fi ink nor a charger is on
     * screen. Both of those own the glyph's slots: the arcs take the notch the
     * bottom row needs, and charging already reports itself through the colour
     * and the bolt. Hiding the second row there keeps one reading per state
     * instead of stacking two.
     *
     * <p>Also needs two SIMs to read: with one - or with the levels not yet
     * sampled - the single row falls back to the current data SIM, which is what
     * the switch degrades to rather than drawing a row of empty dots.
     *
     * @param wifiInk whether the arcs actually carry ink this frame
     * @param charging whether the device reports a charger
     * @param sims how many SIM signal levels are known this frame
     */
    public boolean dualSimRows(boolean wifiInk, boolean charging, int sims) {
        return dualSim && signalDots() && sims >= 2 && !wifiInk && !charging;
    }

    /**
     * ★ v73：读数（电池温度）有没有被打开。
     *
     * <p>这只是"想要"，不等于"有内容"：温度还要读得到。没有内容时
     * 读数整块不占位（宽度 0），与原生电池隐藏同理。
     */
    public boolean perfAnywhere() {
        return perfTemp;
    }

    /** ★ v73：读数整体缩放系数（1.0 = 不缩）。乘在每一种自适应答案之上。 */
    public float perfFit() {
        return perfScale / 100f;
    }

    /** ★ v73：三合一整体缩放系数（1.0 = 吃满系统给的预留区域）。 */
    public float glyphFit() {
        return glyphScale / 100f;
    }

    /**
     * Whether the glyph itself draws the mobile level dots.
     *
     * <p>Both arrangements go through here so the two can never disagree about
     * where the signal lives. Out of ring the answer is no in both: the reading
     * has moved to the status bar, and leaving the dots behind would show the
     * same signal twice.
     */
    public boolean signalDots() {
        return mobile && signalInRing;
    }

    /**
     * Whether the module takes the native signal icon away and draws its own
     * reading in its place.
     *
     * <p>This is the one condition that folds the mobile slots while the signal
     * is out of ring. With the switch off the slots are left alone, so MIUI
     * keeps showing its own icon exactly where it always was - which is the
     * whole difference between the two positions of the switch.
     *
     * <p>Also gated on {@link #mobile}: the meter switch means "no mobile
     * reading anywhere", and it already answers that way in ring through
     * {@link #signalDots()}. Leaving it out here would let the mobile meter be
     * off and the stacked reading drawn anyway - a signal the user turned off,
     * showing up because a different switch was the one they did not touch.
     */
    public boolean stackedOut() {
        return glyph && mobile && signalOutOfRing && stackedSignal;
    }

    /**
     * Whether MIUI's own mobile-signal slot has to be handed over, which is
     * exactly while this module draws that signal itself.
     *
     * <p>Stated here rather than inside {@code TrioHooks.foldedSlots()} so the
     * rule can be exercised without the Xposed API. The trap this encodes is the
     * out-of-ring position with the stacked switch off: nothing is drawn and the
     * native icon has to stay, so folding there would simply delete a signal
     * indicator from the status bar.
     */
    public boolean foldsMobile() {
        return signalDots() || stackedOut();
    }

    /** Whether the network type is drawn at all, in either position. */
    public boolean typeAnywhere() {
        return typeMode != Prefs.MOBILE_TYPE_OFF;
    }

    /** The ring arrangement's slot assignment for one frame. */
    public Ring ring(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return new Ring(this, level, charging, wifiLevel, mobileType, sims);
    }

    /** The rectangular arrangement's slot assignment for one frame. */
    public Rect rect(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return new Rect(this, level, charging, wifiLevel, mobileType, sims);
    }

    /**
     * Where each piece goes in the ring arrangement.
     *
     * <p>The rule worth reading twice is {@code valueInCentre}: the percentage
     * takes the middle when the user asked for it, and <em>also</em> whenever
     * neither the arcs nor an in-glyph type label is competing for that space.
     * With Wi-Fi off nothing competes, so the switch is not needed to get the
     * centre - which is why its row is gated on Wi-Fi. Stated once here, the
     * renderer, the settings screen and the documentation cannot disagree about
     * it again.
     *
     * <p>Immutable.
     */
    public static final class Ring {
        /** The arcs leave ink this frame. */
        public final boolean wifi;
        /** The arcs are drawn at all. */
        public final boolean drawWifi;
        /** The arcs are shrunk into the notch to make room for the centre. */
        public final boolean wifiInGap;
        /** The bolt takes the notch. */
        public final boolean bolt;
        /** The percentage is drawn at all. */
        public final boolean drawValue;
        public final boolean valueInCentre;
        public final boolean valueInGap;
        /** The in-glyph network type is drawn, and where. */
        public final boolean typeInCentre;
        public final boolean typeInGap;
        /** Something occupies the notch, so the ring has to open for it. */
        public final boolean gapUsed;
        /**
         * The level dots are drawn as two rows, one per SIM, instead of one row
         * for the current data SIM.
         */
        public final boolean dual;

        private Ring(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims) {
            wifi = a.wifiInk(wifiLevel);
            bolt = charging && a.drawsBolt();
            final boolean type = a.typeInRing && mobileType != null && !mobileType.isEmpty();
            final boolean hasValue = a.value && level >= 0;

            // The plug, not the bolt: charging suppresses the second row whether
            // or not this frame happens to draw a bolt for it (the bolt is
            // conditional on its own switch since v100, the plug never is).
            dual = a.dualSimRows(wifi, charging, sims);
            // The dual reading's top row owns the 12 o'clock notch, so the
            // percentage cannot be drawn there: it moves to the ring centre,
            // which is where it already goes whenever the notch is spoken for.
            // The in-glyph type label then yields, exactly as it does to the
            // percentage everywhere else.
            valueInCentre = hasValue && (a.centreValue || dual || (!wifi && !type));
            // ★ v82：充电时「闪电+电量」同显（用户要"充电也能看到电量"）。
            //   默认情况下 bolt 占了缺口、type 占了环中心，电量就整个不画；
            //   开关打开后让电量与闪电**共用**缺口：左闪电、右电量、上下边对齐。
            final boolean chargePair = bolt && a.chargeWithValue && hasValue;
            valueInGap = hasValue && !valueInCentre && (!bolt || chargePair);
            wifiInGap = wifi && a.centreValue && !bolt;
            typeInCentre = type && !wifi && !valueInCentre;
            // The second row sits in the notch the gap label would otherwise use,
            // so the label yields rather than overlapping SIM 1's dots.
            typeInGap = type && valueInCentre && !wifi && !bolt && !dual;
            // The rows live in the ring's two openings, so the ring has to open
            // even when nothing else claimed the notch: a closed ring would draw
            // its arc straight through the top row.
            gapUsed = bolt || wifiInGap || valueInGap || typeInGap || dual;
            // With the centre reserved and nothing to move into the notch, the
            // arcs are dropped outright rather than drawn behind the number.
            drawWifi = a.wifi && (!a.centreValue || wifiInGap);
            drawValue = valueInCentre || valueInGap;
        }
    }

    /**
     * Where each piece goes in the rectangular arrangement.
     *
     * <p>Note what is <em>not</em> decided here: the centring switch, the notch,
     * and any second thickness setting. The bar takes its thickness from
     * {@code ringStroke} and the arcs keep {@code arcStroke}; the settings screen
     * relabels that one row for this style rather than offering a near-identical
     * twin the renderer would then have to keep in step.
     *
     * <p>The single top slot is filled by the bolt, the arcs or the type label, in
     * that order, so those three are mutually exclusive by construction.
     *
     * <p>Immutable.
     */
    public static final class Rect {
        /** The mobile level dots are drawn. */
        public final boolean dots;
        /** The bolt takes the top slot. */
        public final boolean bolt;
        /** The Wi-Fi arcs take the top slot. */
        public final boolean wifi;
        /** The in-glyph network type takes the top slot. */
        public final boolean type;
        /** The percentage is drawn below. */
        public final boolean value;
        /**
         * The two dot columns carry one SIM each - SIM 1 left, SIM 2 right -
         * instead of both repeating the current data SIM.
         */
        public final boolean dual;

        private Rect(TrioAppearance a, int level, boolean charging, int wifiLevel,
                     String mobileType, int sims) {
            dots = a.signalDots();
            bolt = charging && a.drawsBolt();
            wifi = !bolt && a.wifiInk(wifiLevel);
            type = !bolt && !wifi && a.typeInRing
                    && mobileType != null && !mobileType.isEmpty();
            value = a.value && level >= 0;
            // Same plug-not-bolt rule as the ring: a charger on screen means one
            // reading, and the bar's colour already carries it.
            dual = a.dualSimRows(wifi, charging, sims);
        }
    }
}
