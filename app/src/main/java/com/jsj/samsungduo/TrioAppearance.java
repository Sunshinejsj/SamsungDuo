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

    /** {@link Prefs#STYLE_RING} or {@link Prefs#STYLE_RECT} or {@link Prefs#STYLE_CAPSULE}. */
    public final int style;
    /** True when {@link #style} is the rectangular arrangement. */
    public final boolean rect;
    /** ★ 胶囊移植：True when {@link #style} is the capsule arrangement. */
    public final boolean capsule;
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
    /** ★ v104：充电同显三选项（0 隐藏 / 1 圈上 / 2 圈内），**只管电量那一对**。 */
    public final int chargePairMode;
    /**
     * ★ v104：信号要不要也去缺口跟闪电并排（＝ L29 的样子）。
     *
     * <p>「电量数字居中」关着时本开关灰色不可用，代码里也强制当作关 ——
     * 那时信号本来就在环内正常显示，不需要它。
     */
    public final boolean chargeSignalAlso;
    /** ★ v104：三选项已开启（真画出来还要这一帧确实有闪电）。 */
    public final boolean chargePairWanted;
    /** ★ v82：「闪电+电量」这一对的字号（30–35，空间不足时自动缩小）。 */
    public final int chargeValueSize;
    /** ★ v104：「闪电+信号」这一对的信号大小（30–35，与电量字号同一档位）。 */
    public final int chargeSignalSize;
    /** ★ v104：组合图标里 5GA 的 "A" 相对主字号的百分比（50–100）。 */
    public final int chargeSuffixAScale;

    /**
     * ★ 胶囊三按钮显式控制（2026-10-21 用户拍板）。圆环/矩形不用这三个字段
     * （它们继续走 chargePairMode 派生）；胶囊在 {@link Capsule} 里按它们显式落位。
     * 按钮1（右列，恒显）：电量 / 信号；按钮2（缺口）：隐藏 / 闪电 / 电量 / 信号；
     * 按钮3（充电闪电）：隐藏 / 缺口独立 / 缺口+同伴 / 右侧+同伴。
     */
    public final int capsuleRight;
    public final int capsuleGap;
    public final int capsuleBoltPos;

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
        capsule = c.trioStyle == Prefs.STYLE_CAPSULE;
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
        chargePairMode = c.chargePairMode;
        chargeSignalAlso = c.chargeSignalAlso && c.valueCentred;
        // 这一排存在吗：三选项不是"隐藏"，或者信号开关开着（那也有一排）。
        chargePairWanted = c.chargePairMode != Prefs.CHARGE_PAIR_OFF
                || (c.chargeSignalAlso && c.valueCentred);
        chargeValueSize = c.chargeValueSize;
        chargeSignalSize = c.chargeSignalSize;
        chargeSuffixAScale = c.chargeSuffixAScale;

        capsuleRight = c.capsuleRight;
        capsuleGap = c.capsuleGap;
        capsuleBoltPos = c.capsuleBoltPos;

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
        /**
         * ★ v104：这一帧画的是「闪电 + 同伴」的组合图标，且它落在 12 点缺口。
         *
         * <p>缺口因此要用 {@link TrioGeometry#GAP_START_CHARGE_PAIR} 那档加宽口，
         * 普通充电缺口只按一颗闪电定宽，装不下两个。
         */
        public final boolean pairInGap;
        /** ★ v104：组合图标落在环内（环心那一行）。 */
        public final boolean pairInCentre;
        /** ★ v104：组合图标里闪电的同伴是 wifi 弧线。 */
        public final boolean pairWifi;
        /** ★ v104：同伴是网络类型标签（4G/5G/5GA）。 */
        public final boolean pairType;
        /** ★ v104：同伴是电量数字。 */
        public final boolean pairValue;
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
            final boolean valueCentreDefault = hasValue
                    && (a.centreValue || dual || (!wifi && !type));

            // ★ v104：两个控件。
            //
            //   · 三选项（隐藏 / 圈上 / 圈内）**只管电量那一对**：闪电去选中的槽，
            //     电量跟着并排，上下边对齐。取代 v82 那个布尔开关。
            //   · 「信号也要去缺口」那个开关**只管信号**：开着时缺口归信号，缺口里
            //     画「闪电 + 信号」（＝ L29 的样子）；三选项这时对缺口不再起作用，
            //     电量回它「电量数字居中」给它的位置。
            //
            //   两者天然与「电量数字居中」联动 —— 上面的构造函数已经在居中关着时
            //   把 chargeSignalAlso 强制成了关，所以居中关时下面这一整套就是正式版：
            //   信号在环内正常显示，只有三选项管电量。
            final boolean signalAlso = a.chargeSignalAlso;
            // 三选项照常（隐藏 / 圈上 / 圈内），信号开关只决定"信号受不受它调动"：
            //
            // · 「电量数字居中」**关**着 —— 信号开关被构造函数强制成了关（三选项管
            //   电量那一对，也就是 v82 那个开关的三选项化）：圈上 = 缺口，圈内 = 环内。
            // · 「电量数字居中」**开**着 + 信号开关**关** —— 三选项整体失效，一律
            //   按"隐藏"画：信号不参与缺口，缺口只剩闪电，87 留在圈内 = 正式版。
            // · 「电量数字居中」**开**着 + 信号开关**开** —— 三选项管的是**信号**
            //   的位置：隐藏 = 信号不显示，圈上 = 信号去缺口，圈内 = 信号去环内。
            final int mode = (a.centreValue && !a.chargeSignalAlso)
                    ? Prefs.CHARGE_PAIR_OFF : a.chargePairMode;
            final boolean pairOn = bolt && mode != Prefs.CHARGE_PAIR_OFF;
            pairInGap = pairOn && mode == Prefs.CHARGE_PAIR_GAP;
            pairInCentre = pairOn && !pairInGap;
            // 闪电是否占着 12 点缺口：只有"圈内"这一档才把缺口让出来。
            final boolean boltInGap = bolt && !pairInCentre;

            // 三槽规则（L29 起就没变过）：每个槽在"没有闪电"时站着谁，同伴就从
            // 这里挑 —— 优先级按渲染层原本的让位顺序：wifi > 类型 > 电量（缺口），
            // 电量 > 类型 > wifi（圈内）。
            //
            // ★ 不要在这里另开"信号让位、电量顶上"的分支：「数字居中」关着时信号
            //   开关被强制成关，可那一档环内站着的本来就是信号 —— 让电量顶上会
            //   把 87 和信号画到同一行（用户实测重叠）。这三槽优先级天然就给出
            //   正确结果：居中关 + 圈内 ⇒ 同伴是信号、缺口留 87。
            valueInCentre = valueCentreDefault;
            final boolean wifiTakesGap = wifi && a.centreValue;
            final boolean typeTakesGap = type && valueInCentre && !wifi && !dual;
            final boolean valueTakesGap = hasValue && !valueInCentre;
            if (pairInGap) {
                pairWifi = wifiTakesGap;
                pairType = !pairWifi && typeTakesGap;
                pairValue = !pairWifi && !pairType && valueTakesGap;
            } else if (pairInCentre) {
                pairValue = valueInCentre;
                pairType = !pairValue && type && !wifi;
                pairWifi = !pairValue && !pairType && wifi;
            } else {
                pairWifi = false;
                pairType = false;
                pairValue = false;
            }
            wifiInGap = wifi && a.centreValue && (!bolt || pairOn);
            typeInCentre = type && !wifi && !valueInCentre && !pairType;
            typeInGap = type && valueInCentre && !wifi && !boltInGap && !dual;
            // 数字：缺口被闪电占着时不画（v82 之前的老规则）；但当闪电的同伴
            // 正是数字时，它由组合图标那一笔带出来。
            valueInGap = hasValue && !valueInCentre && (!boltInGap || pairValue);
            // The rows live in the ring's two openings, so the ring has to open
            // even when nothing else claimed the notch: a closed ring would draw
            // its arc straight through the top row.
            gapUsed = boltInGap || wifiInGap || valueInGap || typeInGap || dual;
            // ★ Wi-Fi 弧只画一处：被组合图标带走（pairWifi）时让路，否则就画。
            //   画在哪由 wifiInGap 决定（缺口 or 环心），不是"画不画"的条件——
            //   旧式子 `!wifiInGap` 会在「电量数字居中」开着且不充电时把信号整段
            //   抹掉：那时 wifiInGap 为真（缺口归信号）、却没有同伴成对去带它，
            //   于是环心不画、缺口也没人画 ⇒ 信号凭空消失（用户实测"缺口的信号没了"）。
            //   正确条件：只要没被 pair 带走就画，位置交给 wifiInGap。
            drawWifi = a.wifi && !pairWifi;
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

    /** The capsule arrangement's slot assignment for one frame. */
    public Capsule capsule(int level, boolean charging, int wifiLevel, String mobileType, int sims) {
        return new Capsule(this, level, charging, wifiLevel, mobileType, sims);
    }

    /**
     * 胶囊三车道：TOP=缺口开口 / CENTRE=环心右列 / BOTTOM=底部信号点。
     * 与上游 HyperDuo v1.7 对齐。
     */
    public enum Zone { TOP, CENTRE, BOTTOM }

    /**
     * ★ 胶囊 = 圆环，只是外框形状不同 —— 但落位由**三按钮显式控制**（2026-10-21
     * 用户拍板），不再走圆环 chargePair 派生：
     *
     * <ul>
     *   <li>按钮1（右列，恒显、无隐藏）：电量 / 信号；</li>
     *   <li>按钮2（缺口）：隐藏 / 闪电 / 电量 / 信号；</li>
     *   <li>按钮3（充电闪电位置）：隐藏 / 缺口独立 / 缺口+同伴 / 右侧+同伴。</li>
     * </ul>
     *
     * <p>★ 同伴 = 按钮1没选的那件；缺口与右列绝不出现同一组件。
     * ★ 右侧永远要有东西（右侧是前提）：按钮3 两种同伴档右列照常显示按钮1。
     * ★ 「信号」件内容 = 有 Wi-Fi 时画 Wi-Fi 弧（圆环 wifi 优先），否则 5G/5GA。
     * ★ 信号点（双卡）完全照圆环 {@code dualSimRows}：充电 / 有 Wi-Fi 时只留卡一。
     *
     * <p>与上游对齐：每件东西只落在一条车道里（{@link #parts}），天然不重叠
     * —— 这正是修掉「WIFI 压电量数字」的关键。
     *
     * <p>Immutable.
     */
    public static final class Capsule {
        /** 圆环那套决策，信号点行仍复用它。 */
        public final Ring ring;
        /** 三车道各持一件（{@code Prefs.PART_NONE}=空），索引按 {@link Zone#ordinal()}。 */
        public final int[] parts = new int[Prefs.ZONE_COUNT];
        /** 三车道哪条载信号点行，索引按 {@link Zone#ordinal()}。 */
        public final boolean[] dotBands = new boolean[Prefs.ZONE_COUNT];
        /** 任一车道载点行 ⇒ 画点。 */
        public final boolean dots;
        /** 双卡：两行点各属一卡。 */
        public final boolean dual;
        /** 按钮3=缺口+同伴：TOP 车道画「闪电+同伴」整组。 */
        public final boolean pairTop;
        /** 按钮3=右侧+同伴：CENTRE 车道画「闪电+按钮1那件」整组。 */
        public final boolean pairCentre;
        /** 成组时跟闪电并肩的那一件（缺口档=按钮1没选的；右列档=按钮1那件）。 */
        public final int pairMate;

        private Capsule(TrioAppearance a, int level, boolean charging, int wifiLevel,
                        String mobileType, int sims) {
            ring = a.ring(level, charging, wifiLevel, mobileType, sims);
            final boolean boltOn = charging && a.drawsBolt();
            final int bp = a.capsuleBoltPos;
            final boolean boltHidden = !boltOn || bp == Prefs.CAP_BOLT_HIDDEN;

            // 「信号」件的内容：有 Wi-Fi 画 Wi-Fi 弧（与圆环 wifi 优先于 type 一致），
            // 否则网络类型 5G/5GA。
            final boolean signalIsWifi = a.wifiInk(wifiLevel);
            final int signalPart = signalIsWifi ? Prefs.PART_WIFI : Prefs.PART_TYPE;
            // 按钮1：右列恒显那件。总开关关掉该组件时退到另一件 —— 右侧永远要有东西。
            int rightPart = (a.capsuleRight == Prefs.CAP_RIGHT_VALUE)
                    ? Prefs.PART_VALUE : signalPart;
            if (rightPart == Prefs.PART_VALUE && !a.value) rightPart = signalPart;
            if (rightPart == Prefs.PART_TYPE && !a.mobile) rightPart = Prefs.PART_VALUE;
            if (rightPart == Prefs.PART_WIFI && !a.wifi) rightPart = Prefs.PART_VALUE;
            // 同伴 = 按钮1没选的那件（缺口档用）。
            final int matePart = (a.capsuleRight == Prefs.CAP_RIGHT_VALUE)
                    ? signalPart : Prefs.PART_VALUE;

            // ---- TOP（缺口）----
            int top = Prefs.PART_NONE;
            boolean topPair = false, centrePair = false;
            int mate = Prefs.PART_NONE;

            // 按钮2：缺口基础内容。充电时闪电位置归按钮3，缺口=闪电只有充电才显示；
            // 充电时按钮2 的电量/信号始终生效（此前被按钮3 整段覆盖 ⇒ 充电全失效）。
            int gapContent = Prefs.PART_NONE;
            final int gap = a.capsuleGap;
            if (gap == Prefs.CAP_GAP_VALUE && a.capsuleRight != Prefs.CAP_RIGHT_VALUE) {
                gapContent = Prefs.PART_VALUE;
            } else if (gap == Prefs.CAP_GAP_SIGNAL && a.capsuleRight != Prefs.CAP_RIGHT_SIGNAL) {
                gapContent = signalPart;
            }
            // ★ CAP_GAP_BOLT（缺口=闪电）不在这里产内容：用户规则 —— 缺口选「闪电」
            //   **只有充电才显示**，不充电什么都不显示。旧代码在这写成
            //   `gap == CAP_GAP_BOLT && !boltOn → PART_BOLT`（不充电才放），逻辑整个反了，
            //   导致不充电往缺口落一颗白色闪电。充电时的落位由下面的按钮3 分支统一处理。

            if (!boltHidden && (bp == Prefs.CAP_BOLT_GAP
                    || bp == Prefs.CAP_BOLT_GAP_PAIR)) {
                // 闪电进缺口：缺口 = 闪电 + 按钮2 内容（按钮2=隐藏则仅闪电）。
                top = Prefs.PART_BOLT;
                topPair = gapContent != Prefs.PART_NONE;
                mate = topPair ? gapContent : Prefs.PART_NONE;
            } else if (gapContent != Prefs.PART_NONE) {
                // 闪电不进缺口（隐藏 / 右侧+同伴 / 未充电）⇒ 缺口纯按按钮2。
                top = gapContent;
            }
            parts[Zone.TOP.ordinal()] = top;

            // ---- CENTRE（右列，恒显）----
            parts[Zone.CENTRE.ordinal()] = rightPart;
            if (!boltHidden && bp == Prefs.CAP_BOLT_CENTRE_PAIR) {
                centrePair = true;
                mate = rightPart;   // 右侧 = 闪电 + 按钮1那件（用户：右侧为前提）
            }
            pairTop = topPair;
            pairCentre = centrePair;
            pairMate = mate;

            parts[Zone.BOTTOM.ordinal()] = Prefs.PART_NONE;

            // 信号点：照圆环。上排＝卡二，仅双卡行且缺口没被件占用时；下排＝卡一。
            dotBands[Zone.TOP.ordinal()] =
                    a.dualSimRows(a.wifiInk(wifiLevel), charging, sims)
                            && top == Prefs.PART_NONE && !pairTop;
            dotBands[Zone.BOTTOM.ordinal()] = a.signalDots();
            dots = dotBands[Zone.TOP.ordinal()] || dotBands[Zone.BOTTOM.ordinal()];
            dual = ring.dual;
        }

        /** 某车道是否载点行。 */
        public boolean dotsIn(Zone band) {
            return band != null && dotBands[band.ordinal()];
        }
    }
}
