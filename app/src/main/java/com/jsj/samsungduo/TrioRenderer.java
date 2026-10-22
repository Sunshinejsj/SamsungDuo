package com.jsj.samsungduo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Draws the "trio" glyph (battery ring + Wi-Fi arcs + signal dots + level indicator)
 * into a host view's canvas, in the 120x120 design space of {@link TrioGeometry}.
 */
final class TrioRenderer {

    private TrioRenderer() {
    }

    /** Whether the user's clock is 24-hour; null until the host tells us. */
    private static Boolean sIs24Hour;

    /** Called by the host with the system's 12/24-hour setting. */
    static void set24Hour(boolean is24Hour) {
        sIs24Hour = Boolean.valueOf(is24Hour);
    }

    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path BOLT = buildBolt();
    private static final Path WIFI_DOT = buildWifiDot();
    private static final RectF ARC = new RectF();
    /** Scratch for the rectangular battery bar; rewritten every frame, never kept. */
    private static final Path RECT_BAR = new Path();
    private static final RectF RECT_RECT = new RectF();
    /**
     * Corner radii of the lit bar segment: rounded on the left so it follows the
     * track's capsule, square on the right so the level ends on a clean vertical
     * edge. Order is the {@link Path#addRoundRect} order - TL, TR, BR, BL.
     */
    private static final float[] RECT_RADII = new float[8];

    /**
     * The weight currently installed on {@link #TEXT}; -1 until first applied.
     *
     * <p>Cached against the installed face rather than per text role: the
     * percentage and the network type can carry different weights and both may
     * be drawn in the same frame, so a role-keyed cache would make the second
     * role skip a swap it still needs. With the shared default (700) this is
     * still zero swaps per frame.
     */
    private static int sTextWeight = -1;

    /**
     * Base face the weight is applied to. {@code Typeface.create(String, int)}
     * only takes a style bitmask, so a numeric weight has to go through the
     * {@code (Typeface, int, boolean)} overload with this as its family.
     */
    private static final Typeface TEXT_BASE = Typeface.create("sans-serif", Typeface.NORMAL);

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
        FILL.setStyle(Paint.Style.FILL);
        TEXT.setStyle(Paint.Style.FILL);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setLetterSpacing(TrioGeometry.VALUE_LETTER_SPACING);
    }

    // ------------------------------------------------------------------ public

    static void draw(Canvas canvas, View host, TrioState s) {
        draw(canvas, host, s, TrioConfig.get());
    }

    /**
     * Draws the glyph with the user's current settings. The text size is applied
     * here rather than in the static initialiser so a live settings change is
     * picked up by the very next frame.
     */
    static void draw(Canvas canvas, View host, TrioState s, TrioSettings cfg) {
        drawState(canvas, host.getWidth(), host.getHeight(), s, cfg);
    }

    /**
     * The same frame for a caller that has a canvas of its own instead of a host
     * view to measure: the glyph window of {@code TrioOverlay} is sized by its
     * own layout params, and there is no hooked view behind it to ask.
     */
    static void drawState(Canvas canvas, int w, int h, TrioState s, TrioSettings cfg) {
        if (w <= 0 || h <= 0 || s == null || cfg == null) {
            return;
        }
        drawInto(canvas, w, h, s.level, s.charging, s.quickCharging, s.powerSave, s.low,
                s.wifiPresent ? s.wifiLevel : -1, s.mobileLevel, s.slotLevels, s.mobileType,
                s.foreground(), cfg, true);
    }

    /**
     * Renders the glyph into any canvas of the given size. Kept free of
     * {@link View} so the settings app can preview the exact same drawing code
     * instead of a hand-maintained copy of it.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean powerSave, boolean low, int wifiLevel, int mobileLevel,
                         int fg, TrioSettings cfg) {
        drawInto(canvas, w, h, level, charging, false, powerSave, low, wifiLevel, mobileLevel,
                null, null, fg, cfg, true);
    }

    /**
     * @param quickCharging {@code true} while the host reports a fast charger;
     *   only the bolt's colour changes, never its shape or size.
     * @param slotLevels the per-SIM signal levels by slot - SIM 1 first - or
     *   {@code null} when the caller has none, which keeps the single row.
     * @param clearWhenDone {@code true} when this glyph owns the whole canvas
     *   (the hooked view case); {@code false} when drawing onto a canvas that
     *   already holds the settings app's background.
     */
    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean quickCharging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, int[] slotLevels, String mobileType,
                         int fg, TrioSettings cfg, boolean clearWhenDone) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return;
        }
        // Resolve what is on screen once, here, from the single rule source. The
        // layout methods below read positions off it instead of re-deriving the
        // switches themselves, which is what used to let the two arrangements
        // drift apart - and let the documentation drift from both.
        final TrioAppearance appearance = TrioAppearance.of(cfg);
        // The two arrangements have different ink extents, so each is fitted to
        // the host view with its own box.
        final boolean rect = appearance.rect;
        // ★ 胶囊必须用自己的墨迹盒拟合：它的宿主是 2.17:1 的宽扁形，若沿用圆环的
        //   117×117.5 方盒，min(w/117, h/117.5) 会被高度卡住，胶囊只画到约 54%
        //   宽度、两侧留大片空白 —— 这正是"大小不对"的根因。
        final boolean capsule = appearance.capsule;
        // ★ v73：「整体尺寸」≤100% 时只缩绘制、不动视图 —— 视图那一块是占位，
        //   动了就会挤压原生图标。所以乘在**统一的 scale** 上（与 INK_INSET
        //   同一个位置），描边/弧线/数字/信号点/环内类型字号一起缩。
        //   perfTextSize 里算"图标有多高"也用同一乘积，两者不会各说各话。
        // ★ v75：>100% 时视图已随之一起放大（wantWidth 含 scale，否则墨迹
        //   超出视图画布会被 View 自身 canvas 裁掉），这里**不能再乘**
        //   glyphFit —— 双重缩放会让墨迹重新超出视图，前功尽弃。
        final float scale = inkScale(w, h, capsule, rect)
                * ((cfg.glyphScale > 100) ? 1f : appearance.glyphFit());
        if (scale <= 0f) {
            return;
        }

        if (clearWhenDone) {
            // The native onDraw only clears the canvas on its non-legacy path
            // (MiuiBatteryMeterIconView.onDraw L543-544). On the legacy path it just
            // calls super.onDraw and returns, so the framework's battery drawable
            // would show through underneath the glyph. Clear unconditionally: our
            // glyph is the sole content of this view either way.
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        }

        final int role = roleColor(appearance, level, charging, powerSave, low, fg);

        // A slot with no reading counts as absent, so a single-SIM device - or a
        // poll that has not answered yet - is not mistaken for a second SIM.
        final int[] slots = (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS)
                ? slotLevels : null;
        final int sims = (slots == null) ? 0
                : (slots[0] >= 0 ? 1 : 0) + (slots[1] >= 0 ? 1 : 0);

        final int save = canvas.save();
        if (capsule) {
            canvas.translate(
                    (w - TrioGeometry.CAP_INK_W * scale) / 2f - TrioGeometry.CAP_INK_X * scale,
                    (h - TrioGeometry.CAP_INK_H * scale) / 2f - TrioGeometry.CAP_INK_Y * scale);
        } else if (rect) {
            canvas.translate(
                    (w - TrioGeometry.RECT_INK_W * scale) / 2f - TrioGeometry.RECT_INK_X * scale,
                    (h - TrioGeometry.RECT_INK_H * scale) / 2f - TrioGeometry.RECT_INK_Y * scale);
        } else {
            canvas.translate(
                    (w - TrioGeometry.INK_W * scale) / 2f - TrioGeometry.INK_X * scale,
                    (h - TrioGeometry.INK_H * scale) / 2f - TrioGeometry.INK_Y * scale);
        }
        canvas.scale(scale, scale);

        if (capsule) {
            drawCapsuleLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        } else if (rect) {
            drawRectLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        } else {
            drawRingLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        }

        canvas.restoreToCount(save);
    }

    /**
     * The ring arrangement: the battery arc, then whichever pieces claim the ring
     * centre and the 12 o'clock notch.
     *
     * <p>The canvas is already scaled into the design space and positioned on the
     * ink box, so everything here is plain design-unit drawing.
     *
     * <p>Which piece goes where is not decided here - {@link TrioAppearance.Ring}
     * already resolved it from the user's switches. This method only draws.
     */
    private static void drawRingLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Ring r = a.ring(level, charging, wifiLevel, mobileType, sims);
        // The 12 o'clock mouth is picked by what fills it. The bolt wants the
        // narrower charging mouth; the digits and the arcs need all the room the
        // idle mouth gives them; the two dot rows want the wide mouth, because
        // the top row reaches the arc's radius and a narrow mouth would run the
        // rounded stroke end straight into the outer dot. An empty mouth just
        // breaks the ring, so it closes instead.
        final boolean dualGap = r.dual;
        // ★ v104：组合图标落在缺口时，缺口要加宽到能同时容纳闪电和它的同伴
        //   （普通充电缺口只按一颗闪电定宽）。其余情况缺口一律不变。
        final boolean boltInGap = r.bolt && !r.pairInCentre;
        // 缺口加宽只在真的有同伴并排时才有意义 —— 只有一颗闪电却开着宽口，
        // 看起来就是环上莫名其妙缺了一大块。
        final boolean wideGap = r.pairInGap && (r.pairValue || r.pairWifi || r.pairType);
        final float gapStart = !r.gapUsed
                ? TrioGeometry.GAP_NONE
                : dualGap ? TrioGeometry.GAP_START_DUAL
                : wideGap ? TrioGeometry.GAP_START_CHARGE_PAIR
                : boltInGap ? TrioGeometry.GAP_START_CHARGE
                : TrioGeometry.GAP_START_IDLE;
        final float gapEnd = !r.gapUsed
                ? TrioGeometry.GAP_NONE
                : dualGap ? TrioGeometry.GAP_END_DUAL
                : wideGap ? TrioGeometry.GAP_END_CHARGE_PAIR
                : boltInGap ? TrioGeometry.GAP_END_CHARGE
                : TrioGeometry.GAP_END_IDLE;

        drawBattery(canvas, level, role, fg, a, gapStart, gapEnd);
        // When the percentage is centred the arcs only ever appear in the notch,
        // so a bolt that has taken the notch suppresses them outright rather than
        // letting them fall back onto the digits at the centre.
        //
        // ★ v104：组合图标里当同伴的那一件由 {@link #drawPair} 带出来，这里
        //   让开，免得画两遍（原位一份、配对里一份）。
        if (r.drawWifi && !r.pairWifi) {
            drawWifi(canvas, wifiLevel, fg, a, r.wifiInGap);
        }
        if (a.signalDots()) {
            drawLevelDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        if (r.typeInCentre && !r.pairType) {
            drawCentreType(canvas, mobileType, fg, a);
        }
        if (r.typeInGap && !r.pairType) {
            drawGapType(canvas, mobileType, fg, a);
        }
        if (r.pairInGap || r.pairInCentre) {
            // ★ v104：左闪电、右同伴（电量数字 / wifi / 5GA），两者上下边对齐
            drawPair(canvas, level, mobileType, wifiLevel, fg, a, quickCharging, r);
        } else if (r.bolt) {
            drawBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        }
        if (r.drawValue && !r.pairValue) {
            drawValue(canvas, level, fg, a, r.valueInCentre);
        }
    }

    /**
     * ★ v82：充电时「闪电 + 电量」同显 —— 缺口左侧画闪电、右侧画电量数字。
     *
     * <p><b>上下边对齐（用户定的规则）</b>：闪电的高度取成"数字的 cap 高度"
     * （{@code 0.71 × 字号}），于是两者的上边落在同一条水平线、下边（基线）也是。
     * 做不到这一点就会出现两种都不好看的样儿：闪电比数字高一截（ plan 1），
     * 或数字比闪电大一圈（ plan 2）—— 用户两个都否了。
     *
     * <p><b>宽度不够自动缩</b>：缺口净宽按弧线圆头逐高度算（
     * {@link TrioGeometry#gapClearWidthAt}），"闪电 + 空隙 + 数字"超宽时先缩字号，
     * 绝不让数字压到弧线的圆头上。所以字号设到 35/36 时可能略小于设定值。
     */
    private static void drawPair(Canvas c, int level, String mobileType, int wifiLevel,
                                 int fg, TrioAppearance a, boolean quickCharging,
                                 TrioAppearance.Ring r) {
        final boolean inGap = r.pairInGap;
        final float stroke = a.ringStroke;
        final float arcStroke = a.arcStroke;
        // 两个开关各带一个同伴：电量用「电量字号」，信号用「信号大小」。
        // 配对高度取两者较大的那个，矮的那个在这一行里垂直居中。
        final float vs = r.pairValue ? a.chargeValueSize : 0f;
        final float ss = (r.pairWifi || r.pairType) ? a.chargeSignalSize : 0f;
        final float base = Math.max(vs, ss);
        if (base <= 0f) {
            // 没有同伴可配对（理论上到不了这里）：退回普通闪电
            drawBolt(c, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
            return;
        }
        applyWeight(vs >= ss ? a.valueWeight : a.typeWeight);

        // 超宽就整体缩：配对高度小一点 ⇒ 净宽反而大一点，所以最多迭代几轮收住。
        float k = 1f;
        for (int i = 0; i < 3; i++) {
            final float h = base * k * TrioGeometry.CAP_HEIGHT_RATIO;
            final float clear = inGap
                    ? TrioGeometry.gapClearWidthAt(TrioGeometry.GAP_TOP + h,
                            TrioGeometry.GAP_START_CHARGE_PAIR,
                            TrioGeometry.GAP_END_CHARGE_PAIR, stroke)
                    : TrioGeometry.centreClearWidth(base * k, stroke);
            final float total = pairWidth(level, mobileType, arcStroke, k, vs, ss, a, r);
            if (total <= clear || total <= 0f) {
                break;
            }
            k = k * clear / total;
        }

        final float h = base * k * TrioGeometry.CAP_HEIGHT_RATIO;
        // 缺口：上边钉在 GAP_TOP（与原来单独画闪电/数字时同一条线）。
        // 环内：整对在环心那一行上下居中，与居中数字的基线规则一致。
        final float top = inGap ? TrioGeometry.GAP_TOP
                                : TrioGeometry.CENTER_CY - h * 0.5f;
        final float bw = TrioGeometry.BOLT_REF_W * (h / TrioGeometry.BOLT_REF_H);
        final float totalW = pairWidth(level, mobileType, arcStroke, k, vs, ss, a, r);
        float px = TrioGeometry.B_CX - totalW * 0.5f;

        // 闪电：高度 = 配对高度 ⇒ 与同伴上下边对齐；上边钉在 top
        final int save = c.save();
        final float pairScale = h / TrioGeometry.BOLT_REF_H;
        c.translate(px, top);
        c.scale(pairScale, pairScale);
        c.translate(-51.3f, -2.2f);
        FILL.setColor(quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
        px += bw + TrioGeometry.CHARGE_PAIR_GAP;

        if (vs > 0f) {
            // 电量数字
            final float hv = vs * k * TrioGeometry.CAP_HEIGHT_RATIO;
            final float ty = top + (h - hv) * 0.5f;
            TEXT.setTextAlign(Paint.Align.LEFT);
            TEXT.setTextSize(vs * k);
            TEXT.setColor(fg);
            c.drawText(String.valueOf(level), px, ty + hv, TEXT);
            TEXT.setTextAlign(Paint.Align.CENTER);
            px += TEXT.measureText(String.valueOf(level)) + TrioGeometry.CHARGE_PAIR_GAP;
        }
        if (ss > 0f) {
            final float hs = ss * k * TrioGeometry.CAP_HEIGHT_RATIO;
            final float ty = top + (h - hs) * 0.5f;
            if (r.pairType) {
                // 4G/5G/5GA 标签：组合图标里的 "A" 用自己那一档缩放
                final float w = measureType(mobileType, ss * k, a.chargeSuffixAScale);
                drawType(c, mobileType, px + w * 0.5f, ty + hs, ss * k, fg, a,
                        a.chargeSuffixAScale);
            } else {
                // wifi 弧线：整组按配对高度缩放，上边与闪电上边同一条线
                final int s2 = c.save();
                final float ws = hs / TrioGeometry.wifiInkH(arcStroke);
                c.translate(px + TrioGeometry.wifiInkW(arcStroke) * ws * 0.5f
                                - TrioGeometry.W_CX * ws,
                        ty - (TrioGeometry.W_TOP - arcStroke * 0.5f) * ws);
                c.scale(ws, ws);
                drawWifiArcs(c, wifiLevel, fg, a);
                c.restoreToCount(s2);
            }
        }
    }

    /**
     * ★ v104：「闪电 + 空隙 + 同伴（最多两个）」在整体缩到 {@code k} 倍时的总宽度。
     */
    private static float pairWidth(int level, String mobileType, float arcStroke,
                                   float k, float vs, float ss,
                                   TrioAppearance a, TrioAppearance.Ring r) {
        float total = TrioGeometry.BOLT_REF_W
                * (Math.max(vs, ss) * k * TrioGeometry.CAP_HEIGHT_RATIO
                   / TrioGeometry.BOLT_REF_H);
        if (vs > 0f) {
            TEXT.setTextSize(vs * k);
            total += TrioGeometry.CHARGE_PAIR_GAP + TEXT.measureText(String.valueOf(level));
        }
        if (ss > 0f) {
            if (r.pairType) {
                total += TrioGeometry.CHARGE_PAIR_GAP
                        + measureType(mobileType, ss * k, a.chargeSuffixAScale);
            } else {
                final float hs = ss * k * TrioGeometry.CAP_HEIGHT_RATIO;
                total += TrioGeometry.CHARGE_PAIR_GAP
                        + TrioGeometry.wifiInkW(arcStroke)
                        * (hs / TrioGeometry.wifiInkH(arcStroke));
            }
        }
        return total;
    }

    /**
     * The rectangular arrangement: two columns of four level dots, the top slot
     * between them, the enlarged percentage below that, and the battery bar
     * across the bottom.
     *
     * <p>Draw order is back to front and does not depend on which slot is used,
     * because none of the pieces overlap: the bar and the dots are the frame, and
     * the value never reaches either.
     *
     * <p>This arrangement has no notch and no ring centre, so the centring switch
     * means nothing here - and {@link TrioAppearance} already says so, which is
     * what lets the settings screen explain the switch's scope instead of
     * silently ignoring it.
     */
    private static void drawRectLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Rect r = a.rect(level, charging, wifiLevel, mobileType, sims);
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        drawRectBatteryBar(canvas, level, role, fg, a);

        // One slot at the top, and only one thing in it. The bolt outranks the
        // arcs exactly as it does in the ring arrangement, and the arcs outrank
        // the network type, which shares that slot rather than claiming a second
        // line of its own.
        if (r.bolt) {
            drawRectBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        } else if (r.wifi) {
            drawRectWifi(canvas, wifiLevel, fg, a);
        } else if (r.type) {
            drawRectType(canvas, mobileType, fg, a);
        }

        if (r.value) {
            drawRectValue(canvas, level, fg, a);
        }
    }

    // ------------------------------------------------------ rectangular pieces

    /**
     * The two mirrored dot columns. Both carry the same level: MIUI's own meter
     * is one column, and drawing two of them is what makes the arrangement
     * symmetrical around the text rather than a list of items on one side.
     *
     * <p>In the dual-SIM reading that symmetry becomes the point: the columns are
     * already two, so the left one reports SIM 1 and the right one SIM 2 - each
     * column being one SIM's four steps, the same shape MIUI's own meter has.
     *
     * @param slotLevels per-SIM levels, or {@code null} to repeat {@code level}
     */
    private static void drawRectDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int left = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int right = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int leftActive = left < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, left);
        final int rightActive = right < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, right);
        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float y = TrioGeometry.RECT_DOT_TOP + i * TrioGeometry.RECT_DOT_STEP;
            FILL.setColor(i < leftActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_LX, y, TrioGeometry.RECT_DOT_R, FILL);
            FILL.setColor(i < rightActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_RX, y, TrioGeometry.RECT_DOT_R, FILL);
        }
    }

    /**
     * The battery bar: the track as one capsule across the full width, then the
     * lit part on top of it.
     *
     * <p>The lit part is rounded on the left and square on the right, so its head
     * follows the track's cap while its tail stays a vertical edge wherever the
     * level happens to fall - a capsule at both ends would read as a pill that
     * shrinks, not as a bar that fills. The radii are clamped by the platform when
     * the segment is shorter than the corner, so a low level cannot draw a knot.
     *
     * <p>The thickness still comes from {@code ringStroke}: it is the one stroke
     * setting, and the settings screen relabels that row "bar thickness" for this
     * arrangement. A second key would only add a row that is inert in whichever
     * arrangement the user is not looking at.
     */
    private static void drawRectBatteryBar(Canvas c, int level, int role, int fg,
                                          TrioAppearance a) {
        final float left = TrioGeometry.RECT_BAR_LEFT;
        final float right = TrioGeometry.RECT_BAR_RIGHT;
        final float r = a.ringStroke * 0.5f;
        final float top = TrioGeometry.RECT_BAR_CY - r;
        final float bottom = TrioGeometry.RECT_BAR_CY + r;

        FILL.setColor(withAlpha(fg, a.trackAlpha));
        RECT_RECT.set(left, top, right, bottom);
        c.drawRoundRect(RECT_RECT, r, r, FILL);

        if (level <= 0) {
            return;
        }
        final float cut = left + (right - left) * clamp01(level / 100f);
        RECT_RECT.set(left, top, cut, bottom);
        RECT_RADII[0] = r;
        RECT_RADII[1] = r;
        RECT_RADII[2] = 0f;
        RECT_RADII[3] = 0f;
        RECT_RADII[4] = 0f;
        RECT_RADII[5] = 0f;
        RECT_RADII[6] = r;
        RECT_RADII[7] = r;
        RECT_BAR.reset();
        RECT_BAR.addRoundRect(RECT_RECT, RECT_RADII, Path.Direction.CW);
        FILL.setColor(role);
        c.drawPath(RECT_BAR, FILL);
    }

    /** The Wi-Fi group moved into the top slot; the arcs themselves are unchanged. */
    private static void drawRectWifi(Canvas c, int level, int fg, TrioAppearance a) {
        final int save = c.save();
        c.translate(TrioGeometry.rectWifiOffsetX(), TrioGeometry.rectWifiOffsetY(a.arcStroke));
        c.scale(TrioGeometry.RECT_WIFI_SCALE, TrioGeometry.RECT_WIFI_SCALE);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    private static void drawRectBolt(Canvas c, int color) {
        final int save = c.save();
        c.translate(TrioGeometry.rectBoltOffsetX(), TrioGeometry.rectBoltOffsetY());
        c.scale(TrioGeometry.BOLT_SCALE, TrioGeometry.BOLT_SCALE);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    /**
     * The network type in the top slot, between the dot columns.
     *
     * <p>Sized against {@link TrioGeometry#RECT_TYPE_SCALE} like the percentage
     * is against its own, so the two read as a pair, and shrunk by
     * {@link #fitSize} if the slot is too narrow for it.
     */
    private static void drawRectType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize * TrioGeometry.RECT_TYPE_SCALE;
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, TrioGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_TYPE_BASELINE,
                size, fg, a, a.typeSuffixScale);
    }

    /** The percentage, in the middle of the arrangement and at its own scale. */
    private static void drawRectValue(Canvas c, int level, int fg, TrioAppearance a) {
        final String text = String.valueOf(level);
        final float requested = a.valueSize * TrioGeometry.RECT_VALUE_SCALE;
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, TrioGeometry.RECT_TEXT_CLEAR);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_VALUE_BASELINE, TEXT);
    }

    /**
     * The colour of the lit part of the ring and the value text.
     *
     * <p>Priority: critical battery, then power-save/low battery, then charging,
     * then the framework's own foreground. {@code isDarkBackground(fg)} tells
     * which variant of a role colour to use: a light icon colour means the status
     * bar is dark, so the light-background variant applies.
     */
    static int roleColor(TrioAppearance a, int level, boolean charging,
                         boolean powerSave, boolean low, int fg) {
        if (a == null || !a.roleColors) {
            return fg;
        }
        final boolean dark = isDarkBackground(fg);
        if (level >= 0 && level < TrioGeometry.CRITICAL_LEVEL) {
            return dark ? a.criticalOnDark : a.criticalOnLight;
        }
        // ★ v77：省电模式是否参与染色改为可关（默认开 = MIUI 语义）。
        //   三星原生不因省电模式改电池图标颜色，他人实测"高电量也黄色"。
        if ((powerSave && a.powerSaveTint) || low || (level >= 0 && level <= a.lowThreshold)) {
            return dark ? a.lowOnDark : a.lowOnLight;
        }
        if (charging) {
            return dark ? a.chargingOnDark : a.chargingOnLight;
        }
        return fg;
    }

    // ----------------------------------------------------------------- battery

    private static void drawBattery(Canvas c, int level, int role, int fg,
                                    TrioAppearance a, float gapStart, float gapEnd) {
        STROKE.setStrokeWidth(a.ringStroke);

        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        batteryRing(c, 0f, 1f, gapStart, gapEnd);

        if (level > 0) {
            STROKE.setColor(role);
            batteryRing(c, 0f, Math.min(1f, level / 100f), gapStart, gapEnd);
        }
    }

    /**
     * Draws the open battery arc from progress {@code from} to {@code to},
     * skipping the segment hidden behind the top gap.
     *
     * <p>Both ends are progress of the arc that is <em>actually drawn</em>, not
     * of the full path: the two segments the mouth leaves behind divide the
     * level between them, so 50% ends exactly where the left segment does and
     * the mouth never consumes any of it. Measuring over the whole path instead
     * made the level stand still across the entire width of the mouth — the
     * right half did not start filling until the level had climbed past 67%.
     */
    private static void batteryRing(Canvas c, float from, float to, float gapStart, float gapEnd) {
        from = clamp01(from);
        to = clamp01(to);
        if (to <= from) {
            return;
        }
        final float mouthStart = clamp01(gapStart);
        final float mouthEnd = clamp01(gapEnd);
        // Path length that actually carries ink; the level is spread over it.
        final float drawn = mouthStart + (1f - mouthEnd);
        if (drawn <= 0f) {
            return;
        }
        final float visibleFrom = from * drawn;
        final float visibleTo = to * drawn;
        // First segment: visible progress [0, mouthStart] is path [0, mouthStart].
        final float firstEnd = Math.min(visibleTo, mouthStart);
        if (firstEnd > visibleFrom) {
            arcByProgress(c, visibleFrom, firstEnd, TrioGeometry.B_CX, TrioGeometry.B_CY,
                    TrioGeometry.B_R);
        }
        // Second segment: visible progress (mouthStart, drawn] is path (mouthEnd, 1].
        final float secondFrom = Math.max(visibleFrom, mouthStart);
        if (visibleTo > secondFrom) {
            arcByProgress(c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                    TrioGeometry.B_CX, TrioGeometry.B_CY, TrioGeometry.B_R);
        }
    }

    private static void arcByProgress(Canvas c, float from, float to, float cx, float cy, float r) {
        arc(c, cx, cy, r,
                TrioGeometry.B_START + from * TrioGeometry.B_SWEEP,
                (to - from) * TrioGeometry.B_SWEEP);
    }

    private static void arc(Canvas c, float cx, float cy, float r, float startDeg, float sweepDeg) {
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
    }

    // -------------------------------------------------------------------- wifi

    /**
     * @param gap {@code true} when the centred-value setting has moved the arcs
     *   up into the 12 o'clock notch. The whole group is then translated and
     *   scaled on the canvas, which keeps one set of reference coordinates for
     *   both slots - the same trick the bolt uses.
     */
    private static void drawWifi(Canvas c, int level, int fg, TrioAppearance a, boolean gap) {
        final int save = gap ? c.save() : 0;
        if (gap) {
            c.translate(TrioGeometry.gapWifiOffsetX(), TrioGeometry.gapWifiOffsetY(a.arcStroke));
            c.scale(TrioGeometry.GAP_WIFI_SCALE, TrioGeometry.GAP_WIFI_SCALE);
        }
        drawWifiArcs(c, level, fg, a);
        if (gap) {
            c.restoreToCount(save);
        }
    }

    /**
     * The arcs and the dot at their reference coordinates, with no transform of
     * their own: each caller positions the group first. Both arrangements draw
     * the identical group, which is what keeps the Wi-Fi icon the same in both.
     */
    private static void drawWifiArcs(Canvas c, int level, int fg, TrioAppearance a) {
        STROKE.setStrokeWidth(a.arcStroke);
        STROKE.setColor(fg);
        if (level >= 3) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W1_CY, TrioGeometry.W1_R,
                    TrioGeometry.W1_START, TrioGeometry.W1_SWEEP);
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        } else if (level == 2) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        }
        if (level >= 1) {
            FILL.setColor(fg);
            c.drawPath(WIFI_DOT, FILL);
        }
    }

    // ------------------------------------------------------------- level dots

    /**
     * The signal meter: four steps, lit from the left.
     *
     * <p>Single row: one SIM's level, the current data SIM. The meter keeps MIUI's
     * own {@code DOT_R} here, because a lone row has the whole ring to itself.
     *
     * <p>Dual reading: one row per SIM, SIM 1 above and SIM 2 below, each filled
     * from its own level. Both rows use {@link TrioGeometry#DUAL_DOT_R}: they are
     * symmetric about the ring's centre and have to read as one paired meter, so a
     * row sized for a lone arc would look shrunken next to its twin. A slot with no
     * reading lights nothing, which is how an empty slot reads as empty rather
     * than as a second full-strength SIM.
     *
     * @param slotLevels per-SIM levels by slot, or {@code null} for one row
     * @param dual whether the two rows are drawn instead of one
     */
    private static void drawLevelDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int inactive = withAlpha(fg, a.trackAlpha);
        if (!dual || slotLevels == null) {
            final int active = level < 0 ? 0 : Math.min(TrioGeometry.DOTS.length, level);
            for (int i = 0; i < TrioGeometry.DOTS.length; i++) {
                FILL.setColor(i < active ? fg : inactive);
                c.drawCircle(TrioGeometry.DOTS[i][0], TrioGeometry.DOTS[i][1],
                        TrioGeometry.DOT_R, FILL);
            }
            return;
        }
        final int row = TrioGeometry.DOTS.length;
        for (int i = 0; i < TrioGeometry.DOTS_DUAL.length; i++) {
            final int step = i % row;
            final int slotLevel = slotLevels[i / row];
            final int active = slotLevel < 0 ? 0 : Math.min(row, slotLevel);
            FILL.setColor(step < active ? fg : inactive);
            c.drawCircle(TrioGeometry.DOTS_DUAL[i][0], TrioGeometry.DOTS_DUAL[i][1],
                    TrioGeometry.DUAL_DOT_R, FILL);
        }
    }

    // ------------------------------------------------- out-of-ring signal

    /**
     * Scratch for one capsule bar; rewritten every draw, never kept.
     *
     * <p>The out-of-ring reading is painted on a view of its own rather than
     * inside the glyph's {@code onDraw}, but never reentrantly: it is one view
     * per battery container, and a container paints its children one at a time.
     */
    private static final RectF OUT_BAR = new RectF();

    /**
     * Resolves the two readings the out-of-ring glyph draws: the capsule row, and
     * the dot row beneath it.
     *
     * <p>Row one is the first SIM that answered and row two the second, which is
     * also why the dot row disappears when only one SIM answered. Four ascending
     * bars is what a lone reading looks like, so a single SIM must not be split
     * across two rows just to fill the space.
     *
     * <p>{@code dataSimOnly} collapses the pair the other way: the reading that
     * matters is the data SIM's, and the other one is dropped whether or not it
     * answered. The data SIM keeps the bars, because that is the shape a lone
     * reading has.
     *
     * <p>When telephony never answered, the aggregate reading the glyph itself
     * uses is the last resort, so a missing subscription list does not leave the
     * out-of-ring glyph blank while the in-ring one still reads a level.
     *
     * @param out receives {@code {barLevel, dotLevel}}; a dot level of {@code -1}
     *            means "no second row", and a bar level of {@code -1} means
     *            "nothing to read"
     */
    static void outSignalLevels(boolean dataSimOnly, int mobileLevel, int[] slotLevels,
                               int dataSlot, int[] out) {
        int slot0 = -1;
        int slot1 = -1;
        if (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS) {
            slot0 = slotLevels[0];
            slot1 = slotLevels[1];
        }
        final int firstAnswered = slot0 >= 0 ? slot0 : slot1;
        int bars;
        int dots = -1;
        if (dataSimOnly) {
            bars = firstAnswered;
            if (dataSlot == 0 && slot0 >= 0) {
                bars = slot0;
            } else if (dataSlot == 1 && slot1 >= 0) {
                bars = slot1;
            }
        } else if (slot0 >= 0 && slot1 >= 0) {
            bars = slot0;
            dots = slot1;
        } else {
            bars = firstAnswered;
        }
        if (bars < 0) {
            bars = mobileLevel;
        }
        out[0] = bars;
        out[1] = dots;
    }

    /**
     * How much of the reference box the drawing actually fills, with or without a
     * dot row. Used to centre the ink inside a box that is always
     * {@link TrioGeometry#STACK_INK_H} tall.
     *
     * <p>Deliberately no longer what the scale is divided by. It used to be, and
     * that made a reading without a dot row divide its pixels by a box 209/150
     * times shorter - so the very same four bars came out 1.39x taller beside no
     * dot row than beside one, and the single-SIM reading towered over the
     * dual-SIM one. The box is the reading's frame, not its content.
     */
    static float outSignalInkH(boolean dots) {
        return dots ? TrioGeometry.STACK_INK_H
                : TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1];
    }

    /**
     * The pixel height of the reading's box at the user's {@code sizeDp}, on a
     * display of the given {@code density}.
     *
     * <p>Deliberately independent of any view: the reading's size used to be a
     * share of the row it stood in, so MIUI laying that row out taller - which
     * is exactly what pulling the control centre down does, 88px to 134px on the
     * test device - made the reading grow with it. A dp is fixed.
     *
     * <p>One box for both shapes: a reading with a dot row and one without are
     * given the same box, so switching between them - a second SIM answering, or
     * the data-SIM-only switch - resizes nothing.
     */
    static int outSignalHeight(int sizeDp, float density) {
        if (sizeDp <= 0 || density <= 0f) {
            return 0;
        }
        return Math.max(1, Math.round(sizeDp * density));
    }

    /**
     * The pixel width that gives a box {@code height} pixels tall the reference
     * aspect, dot row or not.
     */
    static int outSignalWidth(int height) {
        if (height <= 0) {
            return 0;
        }
        return Math.max(1,
                Math.round(height * TrioGeometry.STACK_INK_W / TrioGeometry.STACK_INK_H));
    }

    /**
     * Paints the out-of-ring reading into a canvas of its own, in that canvas's
     * pixel space.
     *
     * <p>Laid out from the reference image's own units rather than from the
     * glyph's 120x120 design space: this reading is not inside the ring, and
     * scaling it by the ink box above would tie a status bar height to a number
     * that describes a battery. The reference units are uniform in both axes, so
     * one scale factor keeps the bar steps, the pitch and the round caps in the
     * proportions the reference draws.
     *
     * <p>A reading below four lights its remaining bars in the track colour
     * instead of drawing a partial row: the row length is the reading, and the
     * unlit part of it is what says how much is missing.
     *
     * @return whether anything was painted
     */
    static boolean drawOutSignal(Canvas c, int width, int height, int bars, int dots,
                                 int fg, TrioAppearance a) {
        if (bars < 0 || width <= 0 || height <= 0) {
            return false;
        }
        final boolean dotRow = dots >= 0;
        final float inkW = TrioGeometry.STACK_INK_W;
        // The scale is always taken from the full reference box, never from the
        // shorter ink a reading without a dot row actually fills: that shorter
        // box is only what centres the drawing here, and dividing the pixels by
        // it would make the same bars a third taller whenever the dot row is
        // absent.
        final float inkH = outSignalInkH(dotRow);
        final float scale = Math.min(width / inkW, height / TrioGeometry.STACK_INK_H);
        if (scale <= 0f) {
            return false;
        }
        final int columns = TrioGeometry.STACK_COLUMNS;
        final float x0 = (width - inkW * scale) / 2f;
        // One baseline for all four bars: the steps are in the tops, which is
        // what makes the row read as a level rather than as four separate bars.
        // ★ v76 回退：信号条自身位置用户明确要求保持原位（曾误改为贴盒底，
        //   把用户满意的信号条也放下了 —— 对齐只该挪 5G/温度，不动信号条）。
        final float baseline = (height - inkH * scale) / 2f
                + TrioGeometry.STACK_BAR_H[columns - 1] * scale;
        final float barW = TrioGeometry.STACK_BAR_W * scale;
        final float radius = barW / 2f;
        final float dotR = TrioGeometry.STACK_DOT_D * scale / 2f;
        final float dotCy = baseline
                + (TrioGeometry.STACK_DOT_GAP + TrioGeometry.STACK_DOT_D / 2f) * scale;
        final int inactive = withAlpha(fg, a.trackAlpha);
        for (int i = 0; i < columns; i++) {
            final float left = x0 + i * TrioGeometry.STACK_PITCH * scale;
            FILL.setColor(i < bars ? fg : inactive);
            OUT_BAR.set(left, baseline - TrioGeometry.STACK_BAR_H[i] * scale,
                    left + barW, baseline);
            c.drawRoundRect(OUT_BAR, radius, radius, FILL);
            if (dotRow) {
                FILL.setColor(i < dots ? fg : inactive);
                c.drawCircle(left + radius, dotCy, dotR, FILL);
            }
        }
        return true;
    }

    // ------------------------------------------------- 环外网络类型标签（v59）
    //
    // 环内的类型标签画在 120x120 设计空间里；环外这一块不一样：它是状态栏里
    // 一个**独立的 View**，文案写得很清楚——"这个标签由系统状态栏绘制"，
    // 尺寸直接由 outTypeSize(dp) 决定，不参与 120 设计框的缩放。
    // 所以这里单列一组按真实像素计算的入口，供那个 View 使用（预览格里
    // 那份是预览专用的，带边距标尺，不复用）。

    /** 环外类型标签的字号（px）。 */
    static float outTypePx(int sizeDp, float density) {
        if (sizeDp <= 0 || density <= 0f) {
            return 0f;
        }
        return sizeDp * density;
    }

    /**
     * 环外类型标签需要的像素宽度。
     *
     * <p>**不含两侧边距**：那两条边距在状态栏里是 LayoutParams 的 margin，
     * 由容器真正占位（这正是设置项里写的"会被占位"），不该由这里再算一遍。
     */
    static int outTypeWidth(String type, float density, TrioAppearance a) {
        final float size = (a == null) ? 0f : outTypePx(a.outTypeSize, density);
        if (type == null || type.isEmpty() || size <= 0f) {
            return 0;
        }
        final int scale = (a == null) ? 100 : a.typeSuffixScale;
        final boolean shrunk = TrioGeometry.hasShrunkSuffix(type, scale);
        final String base = shrunk ? TrioGeometry.typeBase(type) : type;
        final String suffix = shrunk ? String.valueOf(TrioGeometry.TYPE_SUFFIX) : "";
        TEXT.setTypeface(typefaceFor(a.typeWeight));
        TEXT.setTextScaleX(1f);
        TEXT.setTextSize(size);
        float w = TEXT.measureText(base);
        TEXT.setTextSize(size * scale / 100f);
        w += TEXT.measureText(suffix);
        return Math.max(1, (int) Math.ceil(w));
    }

    /** 环外类型标签需要的像素高度（按字体度量取整，确保不被上下的字形裁掉）。 */
    static int outTypeHeight(float density, TrioAppearance a) {
        final float size = (a == null) ? 0f : outTypePx(a.outTypeSize, density);
        if (size <= 0f) {
            return 0;
        }
        TEXT.setTypeface(typefaceFor(a.typeWeight));
        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        return Math.max(1, (int) Math.ceil(fm.descent - fm.ascent));
    }

    /**
     * 把环外类型标签画进属于它自己的 w×h 画布。
     *
     * <p>★ v76：文字<b>底边</b>落在 {@code bottomY}（信号条 baseline 的位置，
     * 由 {@code computeSideBaseline} 现算）—— 信号条自身位置不动，只把文字
     * 挪过去对齐它的下底边（修复用户实测"5G 比信号条高 12px"）。
     * 带「A」后缀时按 {@link TrioAppearance#typeSuffixScale} 缩小后接在右边，
     * 与环内的两段绘制一致。
     */
    static void drawOutType(Canvas c, int w, int h, String type, int fg,
                            float density, TrioAppearance a, float bottomY) {
        final float size = (a == null) ? 0f : outTypePx(a.outTypeSize, density);
        if (type == null || type.isEmpty() || size <= 0f || w <= 0 || h <= 0) {
            return;
        }
        final int scale = (a == null) ? 100 : a.typeSuffixScale;
        final boolean shrunk = TrioGeometry.hasShrunkSuffix(type, scale);
        final String base = shrunk ? TrioGeometry.typeBase(type) : type;
        final String suffix = shrunk ? String.valueOf(TrioGeometry.TYPE_SUFFIX) : "";
        final float suffixSize = size * scale / 100f;

        TEXT.setTypeface(typefaceFor(a.typeWeight));
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setTextScaleX(1f);
        TEXT.setColor(fg);

        TEXT.setTextSize(size);
        final float baseWidth = TEXT.measureText(base);
        TEXT.setTextSize(suffixSize);
        final float suffixWidth = TEXT.measureText(suffix);

        // 画布宽度是 wrap_content 量出来的，正常刚好装下；真机字体缩放等极端情况下
        // 宁可整体横向压一点，也不要让 View 边界把最后一位切掉。
        final float total = baseWidth + suffixWidth;
        final float scaleX = (total > w && total > 0f) ? w / total : 1f;
        TEXT.setTextScaleX(scaleX);

        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        // ★ v76：数字/大写字母的**墨迹底就在基线**上 —— descent 是留给 g/j/y
        //   下伸部的空隙，"5G""37°C" 并不占它。所以让"基线 = bottomY（信号条
        //   底边）"才是真对齐。之前误用 bottomY − descent，文字整体被抬高了
        //   正好一个 descent（实机实测 18px，用户反馈"反而往上去了、显示不全"）。
        //   这同时解释了"5G 本身看着偏上"：按字体度量盒居中/贴底都会偏上，
        //   因为 ascent 上半部本来就是空的。
        final float baseline = bottomY;
        c.drawText(base, 0f, baseline, TEXT);
        if (shrunk) {
            TEXT.setTextSize(suffixSize);
            c.drawText(suffix, baseWidth * scaleX, baseline, TEXT);
        }
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextScaleX(1f);
    }

    // ------------------------------------------- 充电功率 / 电池温度读数（v73）

    /**
     * ★ v73：两项读数的换行分隔符（功率一行、温度一行）。
     *
     * <p>用 {@code '\n'} 而不是画两次：文本是"内容"的唯一表述，
     * 测量、占位、绘制都从它出发，才不会有第三处各算一套。
     */
    private static final char PERF_BREAK = '\n';

    /**
     * ★ v73：两项都开时字号自适应的下限（基准字号的 60%）。
     *
     * <p>宿主还没被系统量出来时不许把读数缩到看不清，所以这里封底；
     * 后果是极端窄的行里两行会略高过一个图标，而不是缩成一团。
     */
    private static final float PERF_MIN_SIZE_RATIO = 0.6f;

    /**
     * ★ v75：读数文本（只剩电池温度）。没内容返回 {@code ""}（整块不占位）。
     *
     * <p>★ v75：充电功率读数已删除 —— 用户实测三星上不显示（平台不报
     * {@code CURRENT_NOW}），且打开开关还会让状态栏闪一下，明确说
     * "三星不需要这个"。温度走 {@code TrioState.readBattery} 的广播链路。
     */
    static String perfText(boolean tempOn, int tempC) {
        if (tempOn && tempC != TrioState.TEMP_UNKNOWN) {
            // 度数符号写成 unicode 转义，源码保持 ASCII：构建机上没有哪把键盘
            // 保证能敲出读数里唯一这一个非 ASCII 字符。
            return tempC + "\u00B0C";
        }
        return "";
    }

    /** 文本里有多少行读数（按 {@link #PERF_BREAK} 数）。 */
    static int perfLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == PERF_BREAK) {
                lines++;
            }
        }
        return lines;
    }

    /**
     * ★ v73：读数字号。
     *
     * <p>★ v75：只剩温度一项（功率已删），两行自适应分支暂时走不到，
     * 保留给将来。单项保持环外类型字号（单行，本来就放得下）。
     * 图标高度取<b>绘制出来的</b>高度（与 glyph 同一套 inkScale × glyphFit），
     * 所以整体尺寸缩到 60% 时读数也跟着缩，不会按一个已经不在那儿的图标去排。
     *
     * <p>用户的「读数整体尺寸」乘在<b>每一种</b>答案之上（单项的基准值、
     * 两项的自适应值、量不到宿主的兜底值）—— 只缩两项的话，
     * 开一个开关时拖动滑块什么都不会动，再开一个反而会跳。
     *
     * <p>★ v75：>100% 时 hostW/H 已含 scale（视图随墨迹一起放大），
     * 与 {@link #drawInto} 同理不再乘 glyphFit。
     */
    static float perfTextSize(String text, int hostW, int hostH,
                              float baseSize, TrioAppearance a) {
        final float factor = (a == null) ? 1f : a.perfFit();
        final int lines = perfLines(text);
        if (lines < 2 || hostW <= 0 || hostH <= 0) {
            return baseSize * factor;
        }
        final float fit = inkScale(hostW, hostH, a.rect)
                * (((a == null) ? 100 : a.glyphScale) > 100 ? 1f : a.glyphFit());
        if (fit <= 0f) {
            return baseSize * factor;
        }
        final float inkH = a.rect ? TrioGeometry.RECT_INK_H : TrioGeometry.INK_H;
        final float icon = inkH * fit;
        final float lineHeight = perfLineHeight(baseSize, a.perfWeight);
        if (lineHeight <= 0f) {
            return baseSize * factor;
        }
        final float size = baseSize * (icon / lines) / lineHeight;
        return Math.max(baseSize * PERF_MIN_SIZE_RATIO, Math.min(baseSize, size)) * factor;
    }

    /** 一行读数在该字号下占的高度（按字体度量，探针量，不靠 View）。 */
    static float perfLineHeight(float size, int weight) {
        TEXT.setTypeface(typefaceFor(weight));
        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    /**
     * ★ v73：读数占位宽度 —— <b>刻意不是当前文本的宽度</b>。
     *
     * <p>上游 1.6.12 的真机教训：功率约每秒更新一次，{@code 0.0W} 与 {@code 6.1W}
     * 宽度不同（102px 对 92px），预留宽度跟着变 → 整排原生图标每秒横移一次，
     * 用户原话"功率数字的变化会导致左侧图标位置跳动"。
     *
     * <p>所以这里量<b>最宽形式</b>：每位数字换成这个字体里最宽的那个数字
     * （问画笔要，不拍脑袋），逐行测、取最宽的一行。只剩"位数变化"
     * 会动预留（{@code 9.9W} → {@code 10W}），那是一步，不是抖动。
     *
     * <p> deliberately 不走布局：把探针串真的排一次会把布局拖进每秒路径。
     */
    static int perfWidth(String text, float size, TrioAppearance a) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return 0;
        }
        TEXT.setTypeface(typefaceFor(a == null ? 700 : a.perfWeight));
        TEXT.setTextScaleX(1f);
        TEXT.setTextSize(size);
        char widest = '0';
        float best = -1f;
        for (char c = '0'; c <= '9'; c++) {
            final float w = TEXT.measureText(String.valueOf(c));
            if (w > best) {
                best = w;
                widest = c;
            }
        }
        // 逐行测：整串一次量会把两行加一起（换行符对画笔只是一个字符），
        // 预留出两倍真实宽度。
        final StringBuilder probe = new StringBuilder(text.length());
        float width = 0f;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == PERF_BREAK) {
                width = Math.max(width, TEXT.measureText(probe.toString()));
                probe.setLength(0);
                continue;
            }
            final char c = text.charAt(i);
            probe.append(c >= '0' && c <= '9' ? widest : c);
        }
        return (int) Math.ceil(width);
    }

    /** 读数占位高度（行数 × 一行高度）。 */
    static int perfHeight(String text, float size, TrioAppearance a) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return 0;
        }
        final int lines = perfLines(text);
        return Math.max(1, (int) Math.ceil(perfLineHeight(size, a == null ? 700 : a.perfWeight)
                * lines));
    }

    /**
     * 把读数画进属于它自己的 w×h 画布，左对齐，整体底边落在 {@code bottomY}。
     *
     * <p>★ v76：最后一行文字底 = {@code bottomY}（信号条 baseline 位置，由
     * {@code computeSideBaseline} 现算）—— 信号条自身位置不动，只把读数挪过去
     * 对齐它的下底边（修复用户实测"5G/温度比信号条高 12px"）。
     *
     * <p>左对齐而不是居中：这块画布的宽度是"最宽形式"量出来的（见
     * {@link #perfWidth}），比当前文本宽；文字居中会随位数变化左右晃，
     * 左对齐则钉死在左边，读数长短变化时只是右边空多少的问题。
     */
    static void drawPerf(Canvas c, int w, int h, String text, float size, int fg,
                         TrioAppearance a, float bottomY) {
        if (text == null || text.isEmpty() || size <= 0f || w <= 0 || h <= 0) {
            return;
        }
        final int weight = (a == null) ? 700 : a.perfWeight;
        TEXT.setTypeface(typefaceFor(weight));
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setTextScaleX(1f);
        TEXT.setColor(fg);
        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        final float lineHeight = fm.descent - fm.ascent;
        final int lines = perfLines(text);
        // ★ v76：底边对齐 bottomY —— 最后一行**基线** = bottomY（数字墨迹底即
        //   基线，descent 不占），往上推 (lines-1) 行得第一行基线。
        float baseline = bottomY - (lines - 1) * lineHeight;
        int start = 0;
        for (int i = 0; i < lines; i++) {
            final String line;
            if (i == lines - 1) {
                line = text.substring(start);
            } else {
                final int br = text.indexOf(PERF_BREAK, start);
                line = text.substring(start, br);
                start = br + 1;
            }
            c.drawText(line, 0f, baseline, TEXT);
            baseline += lineHeight;
        }
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextScaleX(1f);
    }

    // ------------------------------------------------------------ top gap fill

    /**
     * Installs {@code weight} on {@link #TEXT}, skipping the work when that
     * weight is already the installed one. Every draw runs inside onDraw, so the
     * cached comparison keeps a per-frame {@code Typeface} allocation off the hot
     * path. The percentage and the network type share this cache because they
     * share the paint; when they differ both pay one swap per frame, which
     * {@link Typeface#create(Typeface, int, boolean)} answers from its own cache.
     */
    private static void applyWeight(int weight) {
        if (weight == sTextWeight) {
            return;
        }
        TEXT.setTypeface(typefaceFor(weight));
        sTextWeight = weight;
    }

    /**
     * Draws the value in whichever slot it belongs to.
     *
     * @param centre {@code true} to put the percentage in the middle of the ring,
     *   {@code false} to keep it in the 12 o'clock gap next to the charging bolt.
     */
    private static void drawValue(Canvas c, int level, int fg, TrioAppearance a, boolean centre) {
        final String text = String.valueOf(level);
        final float requested = centre ? TrioGeometry.centreSize(a.valueSize) : a.valueSize;
        final float clear = centre ? TrioGeometry.centreClearWidth(requested, a.ringStroke)
                                   : TrioGeometry.gapClearWidth(a.ringStroke);
        // The typeface has to be current before measuring: a heavier weight is
        // wider, and fitSize decides whether the text still clears the gap.
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, clear);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.VALUE_X,
                centre ? TrioGeometry.centerBaseline(size)
                       : TrioGeometry.gapBaseline(size),
                TEXT);
    }

    /**
     * Draws the mobile network type ("5G", "5GA", ...) in the middle of the ring.
     *
     * <p>Reached only when the percentage is not claiming the centre, so this
     * never competes with the value for the same space.
     */
    private static void drawCentreType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize;
        final float clear = TrioGeometry.centreClearWidth(requested, a.ringStroke);
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, clear, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.VALUE_X, TrioGeometry.centerBaseline(size),
                size, fg, a, a.typeSuffixScale);
    }

    /**
     * Draws the mobile network type in the 12 o'clock gap. Used when the
     * percentage has taken the ring centre, which is what the centred-value
     * setting promises, and the notch is otherwise empty: the type is squeezed
     * down to whatever the narrower slot affords rather than being dropped.
     */
    private static void drawGapType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize;
        final float clear = TrioGeometry.gapClearWidth(a.ringStroke);
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, clear, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.VALUE_X, TrioGeometry.gapBaseline(size),
                size, fg, a, a.typeSuffixScale);
    }

    /**
     * Draws the network type at {@code x} on {@code baseline}, shrinking the
     * trailing "A" when the user asked for it.
     *
     * <p>{@code Canvas.drawText(CharSequence, ...)} does <em>not</em> apply
     * spans - only a {@code TextPaint} through {@code StaticLayout} does - so
     * the two sizes are drawn as two runs. The {@link Paint} is left aligned for
     * the duration: {@link #TEXT} is centred, and centring each run separately
     * would stack them on the same spot instead of setting them side by side.
     *
     * <p>The suffix shares the main run's baseline, which is what puts its foot
     * on the same line as the "5G" rather than dropping it into the ring.
     *
     * @param x the centre of the whole label: the single-run path passes it
     *          straight to a centred {@link Paint}, and the two-run path uses it
     *          to centre the block it composes
     */
    private static void drawType(Canvas c, String type, float x, float baseline,
                                 float size, int fg, TrioAppearance a, int suffixPercent) {
        if (!TrioGeometry.hasShrunkSuffix(type, suffixPercent)) {
            TEXT.setTextAlign(Paint.Align.CENTER);
            TEXT.setTextSize(size);
            TEXT.setColor(fg);
            c.drawText(type, x, baseline, TEXT);
            return;
        }
        final String base = TrioGeometry.typeBase(type);
        final String suffix = String.valueOf(TrioGeometry.TYPE_SUFFIX);
        final float suffixSize = size * suffixPercent / 100f;
        // Measure both runs, then place them as one centred block: total width
        // is the layout, and the label's own centre is what stays put.
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setColor(fg);
        TEXT.setTextSize(size);
        final float baseWidth = TEXT.measureText(base);
        TEXT.setTextSize(suffixSize);
        final float suffixWidth = TEXT.measureText(suffix);
        float left = x - (baseWidth + suffixWidth) * 0.5f;
        TEXT.setTextSize(size);
        c.drawText(base, left, baseline, TEXT);
        left += baseWidth;
        TEXT.setTextSize(suffixSize);
        c.drawText(suffix, left, baseline, TEXT);
        // Restore the shared paint's centred default: every other text draw in
        // this renderer relies on it, and this method is not the last per frame.
        TEXT.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Draws the charging bolt scaled up from its reference path. The transform is
     * applied to the canvas rather than baked into the path so the reference
     * coordinates stay readable, and it is saved/restored so nothing else moves.
     *
     * <p>The bolt lives in the 12 o'clock gap at the size the reference path
     * implies. It is never enlarged into the ring centre: that slot belongs to
     * the percentage, which is the point of the centred-value setting.
     *
     * @param color bolt fill; amber while the battery reports quick charge.
     */
    private static void drawBolt(Canvas c, int color) {
        final int save = c.save();
        c.translate(TrioGeometry.boltOffsetX(), TrioGeometry.boltOffsetY());
        c.scale(TrioGeometry.BOLT_SCALE, TrioGeometry.BOLT_SCALE);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    /**
     * Largest font size at which {@code text} fits in {@code clearWidth} design
     * units, capped by {@code requested}. "100" is far wider than "9", so a
     * long value shrinks instead of overrunning the ring.
     */
    private static float fitSize(String text, float requested, float clearWidth) {
        return fitSize(text, requested, clearWidth, 100);
    }

    /**
     * The same fit, measuring the label the way {@link #drawType} will lay it
     * out: a shrunk trailing "A" carries only {@code scalePercent} of the main
     * size, so it takes less room and the main run is left correspondingly
     * larger. Measuring the whole string at {@code requested} would shrink a
     * label that in fact fits.
     */
    private static float fitSize(String text, float requested, float clearWidth,
                                 int scalePercent) {
        if (clearWidth <= 0f) {
            return requested;
        }
        TEXT.setTextSize(requested);
        final float width = measureType(text, requested, scalePercent);
        if (width <= clearWidth || width <= 0f) {
            return requested;
        }
        return requested * (clearWidth / width);
    }

    /**
     * Width of {@code text} at {@code size}, with the trailing suffix scaled.
     *
     * <p>Leaves {@link #TEXT} at {@code size}, which is what every caller wants
     * next: {@link #fitSize} returns and the draw path re-sets the size anyway.
     */
    private static float measureType(String text, float size, int scalePercent) {
        if (!TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            TEXT.setTextSize(size);
            return TEXT.measureText(text);
        }
        TEXT.setTextSize(size);
        final float base = TEXT.measureText(TrioGeometry.typeBase(text));
        TEXT.setTextSize(size * scalePercent / 100f);
        final float suffix = TEXT.measureText(String.valueOf(TrioGeometry.TYPE_SUFFIX));
        TEXT.setTextSize(size);
        return base + suffix;
    }

    // ----------------------------------------------------------------- helpers

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Pixels per design unit for a host of this size: the uniform scale that
     * fits the 120x120 design box into the view, i.e. the same factor
     * {@link #drawInto} applies to its canvas. Anything drawn outside the
     * canvas (the out-of-ring network type label) has to convert its design-unit
     * size with this, or it will not match the glyph next to it.
     */
    static float inkScale(int w, int h) {
        return inkScale(w, h, TrioGeometry.INK_W, TrioGeometry.INK_H);
    }

    /**
     * The fit for whichever arrangement is selected. The settings preview draws
     * its own label beside the glyph, so it has to know the same factor
     * {@link #drawInto} will use - and that depends on the style.
     */
    static float inkScale(int w, int h, boolean rect) {
        return rect
                ? inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H)
                : inkScale(w, h);
    }

    /**
     * 胶囊用自己的宽扁墨迹盒（CAP_INK_W×CAP_INK_H）拟合；圆环/矩形不变。
     * 宿主高度含开口带，但比例由宽度定（见 drawInto），故这里用**墨迹盒**而非含带盒。
     */
    static float inkScale(int w, int h, boolean capsule, boolean rect) {
        if (capsule) {
            return inkScale(w, h, TrioGeometry.CAP_INK_W, TrioGeometry.CAP_INK_H);
        }
        return inkScale(w, h, rect);
    }

    /**
     * ★ v51：适配余量。先按墨迹框算出"填满宿主"的比例，再整体乘 0.94 并居中。
     *
     * <p><b>为什么必须有这个余量</b>：{@code inkScale} 取 {@code min(w/INK_W, h/INK_H)}，
     * 而 {@link TrioGeometry#INK_H}(117.5) &gt; {@link TrioGeometry#INK_W}(117)，
     * 所以<b>正方形宿主下"高度"永远是紧的那一维</b> —— 墨迹高度恰好等于视图高度，
     * 图形上下边缘正好压在视图边界上，被画布裁剪 + 抗锯齿各啃掉半像素。
     * 观感就是用户反馈的"<b>上下感觉有一点点没显示出来</b>"。
     *
     * <p>注意：<b>把视图尺寸调小并不能解决它</b>——墨迹会重新缩放到刚好填满高度，
     * 边缘依然贴边。唯一有效的办法是在"墨迹↔视图"之间留出余量，即本常量。
     *
     * <p>0.94 一举两得：① 上下各留出约 3px（95px 视图时）不再贴边；
     * ② 图形整体略收，视觉更克制（用户要的"稍微小一丢丢"）。
     *
     * <p>写在 {@code inkScale} 里而不是 {@code drawInto} 里，是为了让同样依赖
     * 该比例的<b>环外网络制式标签</b>（用 inkScale 换算字号）跟着一起缩，两者不错位。
     */
    static final float INK_INSET = 0.94f;

    /**
     * 宿主视图要多大，才能让<b>画出来的墨迹</b>达到 {@code inkPx} 的边长。
     *
     * <p>即把 {@link #INK_INSET} 的余量补回来：{@code ceil(inkPx / INK_INSET)}。
     *
     * <p><b>为什么要这层换算</b>：余量是"墨迹↔视图"之间的关系，
     * 若只是把 INK_INSET 加进 {@code inkScale} 而不补视图，
     * 图形会跟着一起缩小 6% —— 用户要的是"<b>只为全显示，不要再收小</b>"。
     * 先按期望的墨迹边长倒推出视图边长，加余量之后图形本身大小就不变。
     */
    static int viewEdgeForInk(float inkPx) {
        if (inkPx <= 0f) {
            return 0;
        }
        return (int) Math.ceil(inkPx / INK_INSET);
    }

    /**
     * The same fit for an arbitrary ink box. The two arrangements have different
     * ink extents, so the scale that fits one does not fit the other.
     */
    static float inkScale(int w, int h, float inkW, float inkH) {
        if (w <= 0 || h <= 0) {
            return 0f;
        }
        return Math.min(w / inkW, h / inkH) * INK_INSET;
    }

    /**
     * The shared text face at a numeric weight. The settings app and the hooked
     * side have to agree on it, and so do the in-canvas glyph and the label view
     * drawn beside it.
     */
    static Typeface typefaceFor(int weight) {
        return Typeface.create(TEXT_BASE, weight, false);
    }

    // ----------------------------------------------------------- path builders

    /** The charging bolt outline, in 120x120 design units. */
    private static Path buildBolt() {
        final Path p = new Path();
        p.moveTo(62.1f, 2.2f);
        p.quadTo(62.8f, 2.5f, 62.6f, 3.3f);
        p.lineTo(61.2f, 7.8f);
        p.lineTo(65.9f, 7.8f);
        p.quadTo(66.9f, 7.8f, 67.3f, 8.6f);
        p.quadTo(67.6f, 9.3f, 67.0f, 10.0f);
        p.lineTo(57.0f, 21.3f);
        p.quadTo(56.4f, 22.0f, 55.6f, 21.6f);
        p.quadTo(55.0f, 21.3f, 55.3f, 20.5f);
        p.lineTo(57.4f, 14.1f);
        p.lineTo(52.9f, 14.1f);
        p.quadTo(52.0f, 14.1f, 51.6f, 13.3f);
        p.quadTo(51.3f, 12.6f, 51.9f, 12.0f);
        p.lineTo(61.1f, 2.7f);
        p.quadTo(61.6f, 2.1f, 62.1f, 2.2f);
        p.close();
        return p;
    }

    /** The innermost Wi-Fi dot, in 120x120 design units. */
    private static Path buildWifiDot() {
        final Path p = new Path();
        p.moveTo(59.5f, 69.9f);
        p.cubicTo(61.0f, 69.9f, 65.2f, 70.8f, 66.5f, 73.0f);
        p.cubicTo(66.7f, 73.8f, 66.7f, 74.3f, 66.5f, 75.0f);
        p.cubicTo(63.8f, 78.8f, 61.15f, 80.95f, 59.5f, 80.95f);
        p.cubicTo(57.85f, 80.95f, 55.2f, 78.8f, 52.5f, 75.0f);
        p.cubicTo(52.3f, 74.3f, 52.3f, 73.8f, 52.5f, 73.0f);
        p.cubicTo(53.8f, 70.8f, 58.0f, 69.9f, 59.5f, 69.9f);
        p.close();
        return p;
    }

    /** sRGB luminance test: a light icon colour implies a dark status bar. */
    static boolean isDarkBackground(int color) {
        final float lum = (0.2126f * Color.red(color)
                + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
        return lum > 0.5f;
    }


    // =====================================================================
    // 胶囊（CAPSULE）：跑道形外框 + 两行内容 + 底部信号点。
    //
    // 规则只有四条，全部与圆环同源（Ring 已经把该放哪一件算好了）：
    //   1) 轮廓 = 跑道形；底部中央留一个点行开口；顶部只有卡二点行时才断开；
    //   2) 第一行（顶部）= 圆环**缺口**里那一件：闪电 / 闪电+读数 / 电量 / 类型；
    //      Wi-Fi 弧**不放这里**（它高 39.65，第一行放不下）；
    //   3) 第二行 = 圆环**环心**里那件：时间在左列，读数在右列（**右对齐**到内缘）；
    //   4) 字号：时间、电量、类型统一取 max(valueSize, typeSize)，Wi-Fi 用图标高。
    // =====================================================================

    // =====================================================================
    //  胶囊（STYLE_CAPSULE）—— 整体系移植上游 HyperDuo v1.7 图形
    //  · 电量填充用 run()/cap() 逐段走线（不画闭合路径 → 无多余直线）
    //  · 三车道 Zone（TOP=缺口 / CENTRE=环心右列 / BOTTOM=底部信号点）各持一件，
    //    天然不重叠（WIFI 不再压电量数字）
    //  · 开口件落在轮廓线 capOpeningCy，宿主高度含开口带 capHostHeightPx → 尺寸正确
    //  · 取色：全程走 fg / role（与圆环同一条 foreground() 链），无胶囊专属默认黑
    // =====================================================================

    /** 胶囊宿主宽度：给定状态栏高度 edge，按墨迹盒宽高比换算。 */
    static int capsuleViewWidth(int edge) {
        return Math.round(edge * TrioGeometry.CAP_INK_W / TrioGeometry.CAP_INK_H);
    }

    /** 胶囊宿主高度：在墨迹盒基础上含上下两条开口带（宽度不变 ⇒ 胶囊本体大小不变）。 */
    static int capsuleViewHeight(int width) {
        return Math.round(width * (TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP)
                / TrioGeometry.CAP_INK_W);
    }

    private static void drawCapsuleLayout(Canvas canvas, int level, boolean charging,
                                          boolean quickCharging, int wifiLevel, int mobileLevel,
                                          int[] slotLevels, int sims, String mobileType,
                                          int role, int fg, TrioAppearance a) {
        final float stroke = a.ringStroke * TrioGeometry.CAP_STROKE_RATIO;
        final TrioAppearance.Capsule cap =
                a.capsule(level, charging, wifiLevel, mobileType, sims);

        STROKE.setStrokeWidth(stroke);
        STROKE.setStyle(Paint.Style.STROKE);
        final float topReach =
                openingReach(cap, TrioAppearance.Zone.TOP, stroke, level, mobileType, a);
        final float bottomReach =
                openingReach(cap, TrioAppearance.Zone.BOTTOM, stroke, level, mobileType, a);
        final Path outline = new Path();
        buildCapsuleOutline(outline, topReach, bottomReach);
        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        canvas.drawPath(outline, STROKE);

        if (level > 0) {
            STROKE.setColor(role);
            drawCapsuleFill(canvas, stroke, Math.min(1f, level / 100f), topReach, bottomReach);
        }

        if (cap.dots) {
            drawCapsuleDots(canvas, mobileLevel, slotLevels, cap.dual, stroke, fg, a, cap);
        }

        // 时钟固定左列，独立于三车道（与上游一致）。
        drawCapsuleTime(canvas, stroke, fg, a,
                TrioGeometry.capLeftCx(stroke), TrioGeometry.capInteriorCy(stroke), -1f);

        drawCapsuleCell(canvas, stroke, TrioAppearance.Zone.TOP, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
        drawCapsuleCell(canvas, stroke, TrioAppearance.Zone.CENTRE, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
        drawCapsuleCell(canvas, stroke, TrioAppearance.Zone.BOTTOM, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
    }

    /**
     * 画某车道（Zone）里那件东西，落在该车道自己的中心。
     * TOP/BOTTOM 开口件居中于开口（capOpeningCy，骑在轮廓线上）；CENTRE 落在内部右列。
     */
    private static void drawCapsuleCell(Canvas c, float stroke, TrioAppearance.Zone band,
                                        TrioAppearance.Capsule cap, int level,
                                        boolean quickCharging, int wifiLevel, String type,
                                        int fg, TrioAppearance a) {
        final int part = cap.parts[band.ordinal()];
        // 充电闪电成组：缺口档(pairTop)画「闪电+同伴」；右列档(pairCentre)画「闪电+按钮1那件」。
        final boolean pairHere = ((band == TrioAppearance.Zone.TOP && cap.pairTop)
                || (band == TrioAppearance.Zone.CENTRE && cap.pairCentre))
                && cap.pairMate != Prefs.PART_NONE;
        if (part == Prefs.PART_NONE && !pairHere) {
            return;
        }
        final float cx = (band == TrioAppearance.Zone.TOP || band == TrioAppearance.Zone.BOTTOM)
                ? (TrioGeometry.CAP_INK_X + TrioGeometry.CAP_INK_W * 0.5f)
                : TrioGeometry.capRightCx(stroke);
        final float cy;
        switch (band) {
            case TOP:
                cy = TrioGeometry.capOpeningCy(true, stroke) + TrioGeometry.CAP_TOP_OPEN_DROP;
                break;
            case BOTTOM:
                cy = TrioGeometry.capOpeningCy(false, stroke);
                break;
            default:
                cy = TrioGeometry.capInteriorCy(stroke);
                break;
        }
        if (pairHere) {
            final int mate = cap.pairMate;
            drawCapsulePair(c, mate, cx, cy,
                    capsulePairDrawSize(band, mate, stroke, level, type, a),
                    level, type, wifiLevel, stroke,
                    quickCharging ? TrioGeometry.QUICK_CHARGE : fg, fg, a);
            return;
        }
        switch (part) {
            case Prefs.PART_BOLT:
                // 缺口里的单件闪电按开口带高度（与圆环单件同尺寸；右列无单件闪电）。
                drawCapsuleBolt(c, cx, cy,
                        (band == TrioAppearance.Zone.TOP)
                                ? TrioGeometry.capOpeningTextRoomH(stroke)
                                : TrioGeometry.CAP_ICON_H,
                        quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
                break;
            case Prefs.PART_WIFI:
                // ★ 分车道给高度：开口带（TOP/BOTTOM）用开口可用墨高，内部右列
                //   用内部高度 —— 旧代码不分车道，缺口 Wi-Fi 也按内部高度算，
                //   在矮开口带里必然糊。
                drawCapsuleWifi(c, cx, cy,
                        (band == TrioAppearance.Zone.CENTRE)
                                ? Math.min(TrioGeometry.CAP_ICON_H,
                                        TrioGeometry.capInteriorHeight(stroke))
                                : TrioGeometry.capOpeningTextRoomH(stroke),
                        fg, a, wifiLevel);
                break;
            case Prefs.PART_VALUE:
                drawCapsuleValue(c, stroke, level, fg, a, cx, cy, openingRoom(band, stroke));
                break;
            case Prefs.PART_TYPE:
                drawCapsuleType(c, stroke, type, fg, a, cx, cy, openingRoom(band, stroke));
                break;
            default:
                break;
        }
    }

    /** 文本适配的垂直上限：-1 = 内部自带上限，否则开口带的 em 上限。 */
    private static float effectiveHeightLimit(float hint, float stroke) {
        return hint < 0f ? TrioGeometry.capTextMaxSize(stroke) : hint;
    }

    /**
     * 开口件宽度（整行）or 内部槽宽：开口是横贯胶囊的一整行（capOpeningWidth），
     * 内部才是时钟/电量两个槽。heightHint≥0 ⇒ 开口；-1 ⇒ 内部。
     */
    private static float capsuleSlotWidth(TrioAppearance.Zone band, float heightHint,
                                          float stroke, boolean clock) {
        if (heightHint >= 0f) {
            return TrioGeometry.capOpeningWidth();
        }
        return clock ? TrioGeometry.capTimeSlotWidth(stroke) : TrioGeometry.capValueSlotWidth(stroke);
    }

    /** 该车道的文本垂直房间：CENTRE=-1（内部自带），开口=capOpeningTextEm。 */
    private static float openingRoom(TrioAppearance.Zone band, float stroke) {
        return band == TrioAppearance.Zone.CENTRE
                ? -1f
                : TrioGeometry.capOpeningTextEm(stroke);
    }

    /**
     * 开口处轮廓退让多少：这一帧该车道实际画的那件的半宽 + 点间隙 + 半个描边。
     * 载点行⇒点行宽；空⇒0（轮廓闭合）。
     */
    private static float openingReach(TrioAppearance.Capsule cap, TrioAppearance.Zone band,
                                      float stroke, int level, String type, TrioAppearance a) {
        if (cap == null) {
            return 0f;
        }
        if (cap.dotsIn(band)) {
            return TrioGeometry.capDotReach(stroke);
        }
        final int part = cap.parts[band.ordinal()];
        if (part == Prefs.PART_NONE) {
            return 0f;
        }
        // 成组（闪电+同伴）落在缺口：开口必须容纳整组（含同伴半宽），否则同伴
        // 画出开口压在实线上 —— 用户实测"缺口太小、重叠了"。
        if ((band == TrioAppearance.Zone.TOP && cap.pairTop)
                || (band == TrioAppearance.Zone.CENTRE && cap.pairCentre)) {
            return capPairHalf(cap.pairMate, stroke, level, type, a, band)
                    + TrioGeometry.capDotClearance() + stroke * 0.5f
                    + TrioGeometry.CAP_OPENING_EXTRA;
        }
        return TrioGeometry.capPieceReach(part, level, type, stroke,
                TrioGeometry.capOpeningTextRoomH(stroke))
                + TrioGeometry.capDotClearance() + stroke * 0.5f
                + TrioGeometry.CAP_OPENING_EXTRA;
    }

    /**
     * 「闪电+同伴」整组在该车道<b>实际绘制</b>用的字号（设计单位）。
     *
     * <p>★ 轮廓让位（{@link #capPairHalf}）与这里必须吃同一个数：此前绘制端为了
     * 「充电/不充电信号同高」（10-21 修复）按开口带高度封了顶，让位端却仍用未封顶的
     * {@code base}，于是 Wi-Fi 同伴的缺口比真画出来的宽出一圈 —— 用户实测
     * 「闪电+wifi 时左右开口大了点」；换成 5GA 时文本宽度自身已按槽收敛，反而不明显。
     */
    private static float capsulePairDrawSize(TrioAppearance.Zone band, int mate, float stroke,
                                             int level, String type, TrioAppearance a) {
        final float base = capsulePairBaseSize(mate, stroke, level, type, a);
        final float avail = (band == TrioAppearance.Zone.CENTRE)
                ? TrioGeometry.capValueSlotWidth(stroke)
                : TrioGeometry.capOpeningWidth();
        final float size = fitPairSize(mate, base, avail, level, type, a);
        // ★ 缺口(pairTop)整组按开口带高度封顶：同伴信号/电量与「不充电单件」同字号
        //   （之前同伴走内部高度 ⇒ 充电比不充电大一圈，用户实测大小不一）。
        return (band == TrioAppearance.Zone.TOP)
                ? Math.min(size, TrioGeometry.capOpeningTextRoomH(stroke)
                        / TrioGeometry.CAP_HEIGHT_RATIO)
                : size;
    }

    /**
     * 「闪电+同伴」整组相对中心点的半宽（设计单位），与 {@link #drawCapsulePair}
     * 同基准：boltW + gap + mateW 的一半。超出该车道可用宽度时按 {@link #fitPairSize}
     * 同比例收缩，故半宽 = min(基准半宽, 可用宽/2)。同伴为空（仅闪电）时退回闪电半宽。
     */
    private static float capPairHalf(int mate, float stroke, int level, String type,
                                     TrioAppearance a, TrioAppearance.Zone band) {
        if (mate == Prefs.PART_NONE) {
            return TrioGeometry.BOLT_INK_W * TrioGeometry.capBoltScale(stroke) * 0.5f;
        }
        final float size = capsulePairDrawSize(band, mate, stroke, level, type, a);
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        final float boltW = TrioGeometry.BOLT_INK_W * (capH / TrioGeometry.BOLT_INK_H);
        final float mateW = capsuleReadingWidth(mate, level, type, size, a);
        final float halfBase = (boltW + TrioGeometry.CAP_PAIR_GAP + mateW) * 0.5f;
        final float avail = (band == TrioAppearance.Zone.CENTRE)
                ? TrioGeometry.capValueSlotWidth(stroke) : TrioGeometry.capOpeningWidth();
        return Math.min(halfBase, avail * 0.5f);
    }

    /**
     * 胶囊轮廓（体育场形），顶/底各按 reach 开口。两个开子路径，端帽用两段
     * 四分之一圆贝塞尔 —— 不建闭合路径，不会有多余连线。
     */
    private static void buildCapsuleOutline(Path p, float topReach, float bottomReach) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.CAP_RIGHT;
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        final float k = 0.5522847498307936f * r;

        p.reset();
        p.moveTo(cx + topReach, top);
        p.lineTo(right - r, top);
        p.cubicTo(right - r + k, top, right, cy - k, right, cy);
        p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom);
        p.lineTo(cx + bottomReach, bottom);
        p.moveTo(cx - bottomReach, bottom);
        p.lineTo(left + r, bottom);
        p.cubicTo(left + r - k, bottom, left, cy + k, left, cy);
        p.cubicTo(left, cy - k, left + r - k, top, left + r, top);
        p.lineTo(cx - topReach, top);
    }

    /**
     * ★ 电量填充：把轮廓当一条线，从底缺口左缘起顺时针逐段走（run 画线 + cap 画弧）。
     * 逐段画到画布上，不建闭合路径 —— 物理上不可能产生"83% 之后又多一条直线"。
     */
    private static void drawCapsuleFill(Canvas c, float stroke, float fraction,
                                        float topReach, float bottomReach) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.CAP_RIGHT;
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        final float topLeft = (cx - topReach) - (left + r);
        final float bottomLeft = (cx - bottomReach) - (left + r);
        final float bottomRight = (right - r) - (cx + bottomReach);
        final float topRight = (right - r) - (cx + topReach);
        final float capLen = (float) Math.PI * r;
        final float total = topLeft + capLen + bottomLeft + bottomRight + capLen + topRight;
        if (total <= 0f) {
            return;
        }
        float left0 = total * fraction;
        left0 = run(c, left0, bottomLeft, cx - bottomReach, bottom, left + r, bottom);
        left0 = cap(c, left0, capLen, left + r, cy, r, 90f, 180f);
        left0 = run(c, left0, topLeft, left + r, top, cx - topReach, top);
        left0 = run(c, left0, topRight, cx + topReach, top, right - r, top);
        left0 = cap(c, left0, capLen, right - r, cy, r, 270f, 180f);
        run(c, left0, bottomRight, right - r, bottom, cx + bottomReach, bottom);
    }

    /** 尽量画一段直走线，返回剩余预算。 */
    private static float run(Canvas c, float remaining, float length,
                             float x1, float y1, float x2, float y2) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        if (remaining >= length) {
            c.drawLine(x1, y1, x2, y2, STROKE);
            return remaining - length;
        }
        final float t = remaining / length;
        c.drawLine(x1, y1, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, STROKE);
        return 0f;
    }

    /** 尽量画一个半圆帽，sweepDeg 有符号，从到达端开始长。 */
    private static float cap(Canvas c, float remaining, float length,
                             float cx, float cy, float r, float startDeg, float sweepDeg) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        if (remaining >= length) {
            c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
            return remaining - length;
        }
        c.drawArc(ARC, startDeg, sweepDeg * (remaining / length), false, STROKE);
        return 0f;
    }

    /** 顶/底两个点行，各按其车道是否载点行而画。 */
    private static void drawCapsuleDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                        float stroke, int fg, TrioAppearance a,
                                        TrioAppearance.Capsule cap) {
        final boolean topRow = cap == null || cap.dotsIn(TrioAppearance.Zone.TOP);
        final boolean bottomRow = cap == null || cap.dotsIn(TrioAppearance.Zone.BOTTOM);
        final int top = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int bottom = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int topActive = top < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, top);
        final int bottomActive = bottom < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, bottom);
        final float topY = TrioGeometry.capDotTopY();
        final float bottomY = TrioGeometry.capDotBottomY();
        final float first = TrioGeometry.CAP_DOT_CX - TrioGeometry.CAP_DOT_PITCH * 1.5f;
        FILL.setStyle(Paint.Style.FILL);
        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float x = first + i * TrioGeometry.CAP_DOT_PITCH;
            if (topRow) {
                FILL.setColor(i < topActive ? fg : inactive);
                c.drawCircle(x, topY, TrioGeometry.CAP_DOT_R, FILL);
            }
            if (bottomRow) {
                FILL.setColor(i < bottomActive ? fg : inactive);
                c.drawCircle(x, bottomY, TrioGeometry.CAP_DOT_R, FILL);
            }
        }
    }

    /** 时钟，固定在左列，中心 {@code (cx, cy)}。 */
    private static void drawCapsuleTime(Canvas c, float stroke, int fg, TrioAppearance a,
                                        float cx, float cy, float heightHint) {
        final String text = clockText();
        if (text == null || text.isEmpty()) {
            return;
        }
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke,
                capsuleSlotWidth(TrioAppearance.Zone.CENTRE, heightHint, stroke, true), 100,
                effectiveHeightLimit(heightHint, stroke));
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        TEXT.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
    }

    /** 电量数字，落在某车道中心。 */
    private static void drawCapsuleValue(Canvas c, float stroke, int level, int fg,
                                         TrioAppearance a, float cx, float cy,
                                         float heightHint) {
        final String text = String.valueOf(level);
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke,
                capsuleSlotWidth(TrioAppearance.Zone.CENTRE, heightHint, stroke, false), 100,
                effectiveHeightLimit(heightHint, stroke));
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        TEXT.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
    }

    /** 网络类型，落在某车道中心。宽度按开口整行（上游同款），高度受 heightHint 约束。 */
    private static void drawCapsuleType(Canvas c, float stroke, String type, int fg,
                                        TrioAppearance a, float cx, float cy,
                                        float heightHint) {
        if (type == null || type.isEmpty()) {
            return;
        }
        applyWeight(a.typeWeight);
        // ★ 圈内 时间/电量/网络类型 同字号（用户规则）：类型基准字与电量一致，
        //   仅 5GA 的 "A" 后缀按 typeSuffixScale 单独缩放。
        final float size = capsuleTextSize(type, a.valueSize, stroke,
                TrioGeometry.capOpeningWidth(), a.typeSuffixScale,
                effectiveHeightLimit(heightHint, stroke));
        drawType(c, type, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f,
                size, fg, a, a.typeSuffixScale);
    }

    /** 读数字号：用户设定、高度上限、槽宽三者里取最紧。 */
    private static float capsuleTextSize(String text, int setting, float stroke,
                                         float slotWidth, int suffixPercent) {
        return capsuleTextSize(text, setting, stroke, slotWidth, suffixPercent,
                TrioGeometry.capTextMaxSize(stroke));
    }

    private static float capsuleTextSize(String text, int setting, float stroke,
                                         float slotWidth, int suffixPercent,
                                         float heightLimit) {
        final float requested = Math.min(setting * TrioGeometry.CAP_TEXT_SCALE, heightLimit);
        return fitSize(text, requested, slotWidth, suffixPercent);
    }

    /**
     * Wi-Fi 弧线，中心 {@code (cx, cy)}，capH 高。
     * ★ v106：照圆环 pair 的 wifi 分支重写 —— 整组（两段弧 + 端点圆点 + 半线宽）
     *   按 {@code capH / wifiInkH(arcStroke)} 整体缩放，缩放后用**未缩放的
     *   arcStroke** 画，视觉线宽随整体等比缩小。
     *   旧代码 scale=capH/W_INK_H 却把 strokeWidth 设成 arcStroke/scale（死守
     *   视觉线宽），在开口带那种 15 单位的高度里线宽占掉一半，两段弧 + 圆点
     *   糊成一坨（用户实测「WiFi 图标是一坨」）。
     *   与圆环一致：level 决定画几段弧（3=两段、2=内段、1=只画点）。
     */
    private static void drawCapsuleWifi(Canvas c, float cx, float cy, float capH, int fg,
                                        TrioAppearance a, int level) {
        final float inkH = TrioGeometry.wifiInkH(a.arcStroke);
        if (inkH <= 0f) {
            return;
        }
        final float scale = capH / inkH;
        // 墨盒（含上线宽）中心对齐 (cx, cy)：[W_TOP - arcStroke/2, WIFI_INK_BOTTOM]
        final float inkCy = (TrioGeometry.W_TOP - a.arcStroke * 0.5f
                + TrioGeometry.WIFI_INK_BOTTOM) * 0.5f;
        final int save = c.save();
        c.translate(cx - TrioGeometry.W_CX * scale, cy - inkCy * scale);
        c.scale(scale, scale);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    /** 闪电，中心 {@code (cx, cy)}，capH 高。 */
    private static void drawCapsuleBolt(Canvas c, float cx, float cy, float capH, int color) {
        final float scale = capH / TrioGeometry.BOLT_INK_H;
        final int save = c.save();
        c.translate(cx - TrioGeometry.BOLT_CX_REF * scale,
                cy - TrioGeometry.BOLT_INK_CY * scale);
        c.scale(scale, scale);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    /** 「闪电 + 同伴」组合图标，中心 {@code (cx, cy)}。 */
    private static void drawCapsulePair(Canvas c, int mate, float cx, float cy, float size,
                                        int level, String mobileType, int wifiLevel,
                                        float stroke, int boltColor, int fg,
                                        TrioAppearance a) {
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        final float boltW = TrioGeometry.BOLT_INK_W * (capH / TrioGeometry.BOLT_INK_H);
        final float mateW = capsuleReadingWidth(mate, level, mobileType, size, a);
        final float left = cx - (boltW + TrioGeometry.CAP_PAIR_GAP + mateW) * 0.5f;
        drawCapsuleBolt(c, left + boltW * 0.5f, cy, capH, boltColor);
        drawCapsuleMate(c, mate, left + boltW + TrioGeometry.CAP_PAIR_GAP + mateW * 0.5f, cy,
                capH, level, mobileType, wifiLevel, stroke, fg, a);
    }

    /** 组合里那件同伴（wifi/type/value）。 */
    private static void drawCapsuleMate(Canvas c, int mate, float cx, float cy, float capH,
                                        int level, String mobileType, int wifiLevel, float stroke,
                                        int fg, TrioAppearance a) {
        if (mate == Prefs.PART_WIFI) {
            drawCapsuleWifi(c, cx, cy, capH, fg, a, wifiLevel);
            return;
        }
        if (mate == Prefs.PART_TYPE) {
            final String type = (mobileType == null) ? "" : mobileType;
            drawType(c, type, cx, cy + capH * 0.5f, capH / TrioGeometry.CAP_HEIGHT_RATIO,
                    fg, a, a.typeSuffixScale);
            return;
        }
        applyWeight(a.valueWeight);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextSize(capH / TrioGeometry.CAP_HEIGHT_RATIO);
        TEXT.setColor(fg);
        c.drawText(String.valueOf(level), cx, cy + capH * 0.5f, TEXT);
    }

    /** 同伴的基准字号。 */
    private static float capsulePairBaseSize(int mate, float stroke, int level,
                                            String mobileType, TrioAppearance a) {
        if (mate == Prefs.PART_WIFI) {
            return Math.min(TrioGeometry.CAP_ICON_H, TrioGeometry.capInteriorHeight(stroke))
                    / TrioGeometry.CAP_HEIGHT_RATIO;
        }
        // ★ 与圆环一致：闪电+电量用电量档、闪电+信号用信号档（尺寸页两个滑块
        //   分别生效；胶囊下这两个键走 cap_ 命名空间，与圆环互不干扰）。
        //   单件右列（非 pair）仍走 valueSize —— 用户要求时间/电量/5G 一样大。
        final int base = (mate == Prefs.PART_VALUE) ? a.chargeValueSize : a.chargeSignalSize;
        return capsuleTextSize(capsuleReadingText(mate, level, mobileType),
                base, stroke,
                TrioGeometry.capValueSlotWidth(stroke), capsuleSuffixPercent(mate, a));
    }

    private static String capsuleReadingText(int mate, int level, String mobileType) {
        if (mate == Prefs.PART_TYPE) {
            return (mobileType == null) ? "" : mobileType;
        }
        if (mate == Prefs.PART_VALUE) {
            return String.valueOf(level);
        }
        return "";
    }

    private static int capsuleSuffixPercent(int mate, TrioAppearance a) {
        return (mate == Prefs.PART_TYPE) ? a.typeSuffixScale : 100;
    }

    /** 某字号下同伴的墨宽。 */
    private static float capsuleReadingWidth(int mate, int level, String mobileType, float size,
                                             TrioAppearance a) {
        if (mate == Prefs.PART_WIFI) {
            // ★ 与 drawCapsuleWifi 同基准：整组含半线宽（圆环 wifiInkW 同款），
            //   否则 pair 排位时算的宽度与画出来的不一致，间距又会错。
            final float scale = size * TrioGeometry.CAP_HEIGHT_RATIO
                    / TrioGeometry.wifiInkH(a.arcStroke);
            return TrioGeometry.wifiInkW(a.arcStroke) * scale;
        }
        applyWeight(mate == Prefs.PART_TYPE ? a.typeWeight : a.valueWeight);
        TEXT.setTextSize(size);
        if (mate == Prefs.PART_TYPE) {
            // ★ 与圆环 measureType / drawType 完全同款：base 和 A 分别测量再相加。
            //   旧算法 base+(full-base)*scale/100 靠"5GA"整体测量反推 A 宽，
            //   与 drawType 里 baseWidth+suffixWidth 的算法存在字距差，
            //   pair 排布时闪电与 5GA 块之间就会差出这一点（用户实测 A 甩飞）。
            final String type = (mobileType == null) ? "" : mobileType;
            if (!TrioGeometry.hasShrunkSuffix(type, a.typeSuffixScale)) {
                return TEXT.measureText(type);
            }
            final float base = TEXT.measureText(TrioGeometry.typeBase(type));
            TEXT.setTextSize(size * a.typeSuffixScale / 100f);
            final float suffix = TEXT.measureText(String.valueOf(TrioGeometry.TYPE_SUFFIX));
            TEXT.setTextSize(size);
            return base + suffix;
        }
        return TEXT.measureText(String.valueOf(level));
    }

    /** 把「闪电 + 同伴」压到能放进该列的宽度。 */
    private static float fitPairSize(int mate, float size, float avail, int level,
                                     String mobileType, TrioAppearance a) {
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        final float boltW = TrioGeometry.BOLT_INK_W * (capH / TrioGeometry.BOLT_INK_H);
        final float mateW = capsuleReadingWidth(mate, level, mobileType, size, a);
        final float total = boltW + TrioGeometry.CAP_PAIR_GAP + mateW;
        if (total <= avail || total <= 0f) {
            return size;
        }
        return size * (avail / total);
    }

    /** The clock, in the user's 12/24-hour setting. */
    private static String clockText() {
        final Calendar cal = Calendar.getInstance();
        final int hour = cal.get(Calendar.HOUR_OF_DAY);
        final int minute = cal.get(Calendar.MINUTE);
        if (sIs24Hour == null || sIs24Hour.booleanValue()) {
            return String.format(Locale.US, "%02d:%02d", Integer.valueOf(hour),
                    Integer.valueOf(minute));
        }
        final int h12 = hour % 12;
        return String.format(Locale.US, "%02d:%02d", Integer.valueOf(h12 == 0 ? 12 : h12),
                Integer.valueOf(minute));
    }

}
