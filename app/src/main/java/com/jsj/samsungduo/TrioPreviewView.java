package com.jsj.samsungduo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Renders the trio glyph for the settings app's live preview.
 *
 * <p>It exists so the preview reuses {@link TrioRenderer} exactly as the status
 * bar does. A copy of the geometry inside the app would drift the moment either
 * side is tweaked, and the whole point of the preview is that what you see is
 * what the status bar will draw.
 *
 * <p>Nothing here touches the Xposed API: this class is loaded in the app's own
 * process.
 */
public final class TrioPreviewView extends View {

    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TrioSettings settings = TrioSettings.defaults();
    private int level = 72;
    private boolean charging;
    private boolean quickCharging;
    private boolean powerSave;
    private boolean low;
    private int wifiLevel = 3;
    private int mobileLevel = 4;
    /** Per-SIM levels for the dual reading, or null for the single row. */
    private int[] slotLevels;
    private String mobileType = "";
    private int foreground = 0xFFFFFFFF;
    private int backgroundColor = 0xFF1C1B1F;

    /** Fraction of the shorter edge left empty around the glyph. */
    private float inset = 0.06f;

    /** Draws the out-of-ring label on its own instead of the glyph. */
    private boolean outTypeOnly;

    /** Draws the out-of-ring signal reading on its own instead of the glyph. */
    private boolean outSignalOnly;

    /**
     * Height of the box the status bar draws the glyph in, in density pixels:
     * {@code status_bar_icon_height}. The out-of-ring label is positioned
     * against the icon it stands beside, so its size only means anything
     * relative to this box.
     */
    private static final float HOST_ICON_HEIGHT_DP = 20f;

    /**
     * Alpha of the two margin rules in the out-of-ring type cell, on 0..255.
     * Structure rather than ink, the same role the glyph's own track plays -
     * visible against the dark preview background, gone if it competed with
     * the label itself.
     */
    private static final int TRACK_RULE_ALPHA = 56;

    public TrioPreviewView(Context context) {
        super(context);
    }

    public TrioPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrioPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSettings(TrioSettings value) {
        this.settings = value == null ? TrioSettings.defaults() : value;
        invalidate();
    }

    public void setState(int level, boolean charging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel) {
        setState(level, charging, false, powerSave, low, wifiLevel, mobileLevel, null, null);
    }

    public void setState(int level, boolean charging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, String mobileType) {
        setState(level, charging, false, powerSave, low, wifiLevel, mobileLevel, null, mobileType);
    }

    public void setState(int level, boolean charging, boolean quickCharging, boolean powerSave,
                         boolean low, int wifiLevel, int mobileLevel, String mobileType) {
        setState(level, charging, quickCharging, powerSave, low, wifiLevel, mobileLevel,
                null, mobileType);
    }

    /**
     * The full state, including the per-SIM levels the dual reading draws.
     *
     * <p>Public because the settings screen lives in another package
     * ({@code com.jsj.samsungduo.ui}) and has to call this across it.
     *
     * @param slotLevels one level per SIM slot, SIM 1 first, or {@code null} to
     *                   preview the single row the status bar draws when only one
     *                   of the two rows is wanted
     */
    public void setState(int level, boolean charging, boolean quickCharging, boolean powerSave,
                         boolean low, int wifiLevel, int mobileLevel, int[] slotLevels,
                         String mobileType) {
        this.level = level;
        this.charging = charging;
        this.quickCharging = quickCharging;
        this.powerSave = powerSave;
        this.low = low;
        this.wifiLevel = wifiLevel;
        this.mobileLevel = mobileLevel;
        this.slotLevels = slotLevels;
        this.mobileType = mobileType == null ? "" : mobileType;
        invalidate();
    }

    public void setForeground(int color) {
        this.foreground = color;
        invalidate();
    }

    public void setPreviewBackground(int color) {
        this.backgroundColor = color;
        invalidate();
    }

    public void setInset(float fraction) {
        this.inset = Math.max(0f, Math.min(0.4f, fraction));
        invalidate();
    }

    /**
     * Shows the out-of-ring type label instead of the glyph.
     *
     * <p>That label is not part of the glyph: the hooked status bar lays it out
     * beside the icon and sizes it in raw pixels against the icon box. There is
     * no way to fold it into {@link TrioRenderer#drawInto}, so the preview draws
     * it here and at the same scale.
     */
    public void setOutTypeOnly(boolean value) {
        this.outTypeOnly = value;
        invalidate();
    }

    /**
     * Shows the out-of-ring signal reading instead of the glyph.
     *
     * <p>Like the label, this has nothing to do with the glyph: the status bar
     * folds the mobile slot away and draws the reading in a view of its own,
     * sized against the icon box. In out-of-ring mode the glyph itself carries
     * no signal ink at all, so without this cell the two switches would change
     * nothing anywhere on this screen.
     */
    public void setOutSignalOnly(boolean value) {
        this.outSignalOnly = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        background.setStyle(Paint.Style.FILL);
        background.setColor(backgroundColor);
        canvas.drawRect(0f, 0f, w, h, background);

        final float pad = Math.min(w, h) * inset;
        final int innerW = Math.round(w - 2f * pad);
        final int innerH = Math.round(h - 2f * pad);
        if (innerW <= 0 || innerH <= 0) {
            return;
        }

        final int save = canvas.save();
        if (outTypeOnly) {
            canvas.translate(pad, pad);
            drawOutTypeLabel(canvas, innerW, innerH);
        } else if (outSignalOnly) {
            canvas.translate(pad, pad);
            drawOutSignal(canvas, innerW, innerH);
        } else if (settings.trioStyle == Prefs.STYLE_CAPSULE) {
            // ★ 胶囊是 2.17:1 的宽扁形，格子却是正方形 —— 直接按正方形等比缩，
            //   宽度只能用掉一半，图标缩成中间一条。改成给它一个同比例的宽扁
            //   子区域（宽度铺满、垂直居中），预览才和状态栏里看到的一样大。
            // ★ 高度含上下开口带（CAP_INK_TOP/BOTTOM），否则顶部/底部开口件被预览画布裁掉。
            // ★ 胶囊长度：先同步本快照的长度再算宽扁比例（drawInto 里也会 set，
            //   但这里的 drawH 在那之前就算）。
            TrioGeometry.setCapsuleLength(settings.capsuleLength);
            final int drawH = Math.round(innerW
                    * ((TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP)
                            / TrioGeometry.capInkW()));
            final float top = (innerH - drawH) * 0.5f;
            if (drawH <= 0) {
                canvas.restoreToCount(save);
                return;
            }
            canvas.translate(pad, pad + top);
            // false: the glyph must not punch a hole through the preview background.
            TrioRenderer.drawInto(canvas, innerW, drawH, level, charging, quickCharging,
                    powerSave, low, wifiLevel, mobileLevel, slotLevels, mobileType, foreground,
                    settings, false);
        } else {
            canvas.translate(pad, pad);
            // false: the glyph must not punch a hole through the preview background.
            TrioRenderer.drawInto(canvas, innerW, innerH, level, charging, quickCharging,
                    powerSave, low, wifiLevel, mobileLevel, slotLevels, mobileType, foreground,
                    settings, false);
        }
        canvas.restoreToCount(save);
    }

    /**
     * Draws the out-of-ring label on its own, centred in the glyph box.
     *
     * <p>The size is taken the way {@code TrioHooks.outTypeSizePx} takes it: the
     * {@code outTypeSize} dp resolved against the display density, with the
     * weight from {@code typeWeight}. Those pixels only mean something against
     * the icon box they are measured in, so the label is scaled by the ratio of
     * this preview's box to {@link #HOST_ICON_HEIGHT_DP}.
     */
    private void drawOutTypeLabel(Canvas canvas, int innerW, int innerH) {
        final float density = getResources().getDisplayMetrics().density;
        final float hostHeight = HOST_ICON_HEIGHT_DP * density;
        if (mobileType.isEmpty() || hostHeight <= 0f) {
            return;
        }

        final TrioAppearance a = TrioAppearance.of(settings);
        labelPaint.setColor(foreground);
        labelPaint.setTypeface(TrioRenderer.typefaceFor(a.typeWeight));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextScaleX(1f);
        // dp setting to px exactly as the hooked side resolves it, then scaled
        // by this preview's box over the icon box it is tuned against.
        final float size = innerH * a.outTypeSize * density / hostHeight;
        labelPaint.setTextSize(size);

        // Shrink the trailing "A" the way the status bar label does, and the way
        // the ring canvas does not have to: this preview draws with a Paint, so
        // it takes the same two-run path TrioRenderer.drawType takes rather than
        // a span.
        final boolean shrunk = TrioGeometry.hasShrunkSuffix(mobileType, a.typeSuffixScale);
        final String base = shrunk ? TrioGeometry.typeBase(mobileType) : mobileType;
        final String suffix = shrunk ? String.valueOf(TrioGeometry.TYPE_SUFFIX) : "";
        final float suffixSize = size * a.typeSuffixScale / 100f;
        final float baseWidth = labelPaint.measureText(base);
        labelPaint.setTextSize(suffixSize);
        final float suffixWidth = labelPaint.measureText(suffix);
        final float width = baseWidth + suffixWidth;
        // On screen the label is wrap-content beside the icon box, so nothing
        // trims it; this cell is a square and would. Squeeze a too-wide glyph
        // horizontally instead of letting the view bounds cut it off. The height
        // is left exact - that is the dimension outTypeSize actually sets.
        final float scaleX = (width > innerW && width > 0f) ? innerW / width : 1f;
        labelPaint.setTextScaleX(scaleX);

        final Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        final float baseline = innerH * 0.5f - (metrics.ascent + metrics.descent) * 0.5f;
        // The label is placed as the status bar places it: one gap to the anchor
        // (the inward margin), one to the native icons (the outward margin). In
        // this cell those neighbours are the cell edges, so the text block is
        // centred in the room *between* the two margin rules rather than in the
        // whole cell - a one-sided margin then visibly shifts the label instead
        // of only moving a rule. With equal margins this is the plain centre.
        final float pxPerDp = innerH / HOST_ICON_HEIGHT_DP;
        final float roomLeft = a.outTypeMarginLeft * pxPerDp;
        final float roomRight = innerW - a.outTypeMarginRight * pxPerDp;
        final float scaledWidth = width * scaleX;
        final float left = (roomRight > roomLeft)
                ? roomLeft + (roomRight - roomLeft - scaledWidth) * 0.5f
                : innerW * 0.5f - scaledWidth * 0.5f;
        labelPaint.setTextSize(size);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(base, left, baseline, labelPaint);
        if (shrunk) {
            labelPaint.setTextSize(suffixSize);
            canvas.drawText(TrioGeometry.TYPE_SUFFIX + "", left + baseWidth * scaleX,
                    baseline, labelPaint);
        }
        labelPaint.setTextAlign(Paint.Align.CENTER);

        // The two margins, drawn as the reserved gaps they are: thin rules one
        // margin's width in from each end, on the label's mid-line. In the
        // status bar these lengths are padding the native icon row gives up, so
        // the honest picture of a wider margin is "the neighbours moved out" -
        // and the neighbours in this cell are the cell edges. The rules share
        // the foreground at reduced alpha, the way the glyph's own track reads
        // as structure rather than ink.
        final float midY = innerH * 0.5f;
        labelPaint.setColor((foreground & 0x00FFFFFF) | (TRACK_RULE_ALPHA << 24));
        labelPaint.setStrokeWidth(Math.max(1f, innerH / 40f));
        canvas.drawLine(roomLeft, midY, roomLeft, midY + innerH * 0.3f, labelPaint);
        canvas.drawLine(roomRight, midY, roomRight, midY + innerH * 0.3f, labelPaint);
        labelPaint.setColor(foreground);
    }

    /**
     * Draws the out-of-ring signal reading on its own, centred in the glyph box.
     *
     * <p>The status bar does not draw this in the glyph either: it folds the
     * mobile slot away and gives the reading a view of its own, as tall as the
     * icon box and as wide as that height's reference aspect. So the reading is
     * first laid out at that on-screen size, exactly as the status bar measures
     * it, and only then scaled to this preview's box - the same host-height
     * ratio {@link #drawOutTypeLabel} uses, so both cells of the out-of-ring
     * pair are shown at one scale.
     *
     * <p>The data slot is left unknown ({@code -1}) on purpose: which SIM is the
     * data SIM is runtime state the settings app has not sampled, and guessing
     * one would show the switch's effect on the wrong card. With the switch on
     * the reading falls back to the first answered card, which is still the
     * point of the cell - one row instead of two.
     */
    private void drawOutSignal(Canvas canvas, int innerW, int innerH) {
        final float density = getResources().getDisplayMetrics().density;
        if (density <= 0f) {
            return;
        }

        final TrioAppearance a = TrioAppearance.of(settings);
        final int[] reading = new int[2];
        TrioRenderer.outSignalLevels(a.dataSimOnly, mobileLevel, slotLevels, -1, reading);
        if (reading[0] < 0) {
            reading[0] = 0;
        }

        // Same two steps the status bar takes: the setting, in dp, resolved
        // against the density, then the reference aspect. The setting is not a
        // ratio of the icon box here any more than it is on the status bar, so
        // a smaller reading really does show up smaller in this cell.
        final int hostHeight = TrioRenderer.outSignalHeight(a.outSignalSize, density);
        final int hostWidth = TrioRenderer.outSignalWidth(hostHeight);
        if (hostWidth <= 0) {
            return;
        }

        // The margin is the reading's distance from the battery, so the cell is
        // a window on the strip [reading][margin] with the battery standing at
        // its right edge. The window is the reading plus the largest margin the
        // slider offers, and the reading keeps exactly the configured gap from
        // that edge - so the slider's whole range stays visible instead of the
        // reading drifting out of the cell. No battery is drawn: this cell is
        // the reading's, and adding a second piece of the status bar here would
        // only compete with the glyph cells for the same square.
        final float maxMarginPx = Prefs.MAX_OUT_SIGNAL_MARGIN * density;
        final float regionW = hostWidth + maxMarginPx;
        final float regionH = hostHeight;
        final float scale = Math.min(innerW / regionW, innerH / regionH);
        final int drawW = Math.max(1, Math.round(hostWidth * scale));
        final int drawH = Math.max(1, Math.round(hostHeight * scale));
        final float marginPx = a.outSignalMargin * density * scale;
        final float regionWpx = regionW * scale;
        final float regionLeft = (innerW - regionWpx) * 0.5f;
        // The region's right edge is the battery; the reading sits `marginPx` in
        // from it, which is what a wider gap looks like.
        final float left = regionLeft + regionWpx - marginPx - drawW;
        final float top = (innerH - drawH) * 0.5f;

        final int save = canvas.save();
        canvas.translate(left, top);
        TrioRenderer.drawOutSignal(canvas, drawW, drawH, reading[0], reading[1], foreground, a);
        canvas.restoreToCount(save);
    }
}
