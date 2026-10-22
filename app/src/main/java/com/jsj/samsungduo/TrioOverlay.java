package com.jsj.samsungduo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;

/**
 * SamsungDuo · 三星 One UI 8 / Android 16 叠加层（v22：回退到 v14 已验证可显示的窗口配置）。
 *
 * <p><b>为什么回退：</b>v14 是<b>唯一被实机证实"三合一真的显示出来"</b>的版本，
 * 日志实测：
 * <pre>
 *   overlay: window attached (type=2012) at x=1230 y=19 w=198 h=109
 *   draw ok canvas=198x109 level=52 wifi=4 mobile=2
 * </pre>
 * 用户当时反馈"这次保持了好几秒"。v15 我把窗口参数改了三处（Gravity 改 RIGHT、
 * 去掉 FLAG_LAYOUT_NO_LIMITS、高度改 MATCH_PARENT），结果画布高度被解释成整屏 2992px，
 * 窗口漂到屏幕中部。v16~v21 改走 View 树路线则一直找不到正确容器、不显示。
 * <b>所以这里完整恢复 v14 的参数，只修"显示一会儿就消失"那一个问题。</b>
 *
 * <p><b>v14 的窗口配置（勿再改动，全部经实机验证）：</b>
 * <ul>
 *   <li>类型 {@code TYPE_STATUS_BAR_SUB_PANEL}(2012) + {@code createWindowContext} 同类型 Context
 *       （2000/2040/2038 全部试过，分别撞车/撞车/被遮挡，只有 2012 可用）；</li>
 *   <li>{@code Gravity.TOP|START} + <b>屏幕绝对坐标</b>（getLocationOnScreen 的 x/y）——
 *       实测这样才对，v15 改成 TOP|RIGHT 反而错了；</li>
 *   <li>高度用<b>状态栏实际高度</b>（109px），<b>绝不能用 MATCH_PARENT</b>
 *       ——MATCH_PARENT 在子面板窗口里会被当成整屏高度（2992px），导致漂到屏幕中部；</li>
 *   <li>必须保留 {@code FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_NO_LIMITS}。</li>
 * </ul>
 *
 * <p><b>v22 唯一修复点：</b>v14 靠 {@code root.isShown()} 决定是否隐藏，而三星那个状态栏
 * 容器 View 在 SystemUI 里 {@code isShown()} 长期为 false，导致叠加层几秒后被 GONE
 * （日志 {@code visibility -> GONE (isShown=false)}）。<b>本版彻底不再用 isShown 判断显隐</b>
 * ——窗口挂在状态栏层级，状态栏隐藏时该窗口会被系统一起收起，不需要我们判断。
 */
public final class TrioOverlay {

    /** 三合一宽度（dp）：62 约 217px，超过 system_icons 容器 146px。 */
    private static final float GLYPH_WIDTH_DP = 62f;
    /**
     * 期望的【墨迹边长】（dp）—— 也就是真正看到的图形大小。
     *
     * <p>25.5dp × density(3.5) = 89.25px。
     *
     * <p><b>这里给的是"墨迹"而不是"视图"边长。</b>
     * 视图边长由 {@link TrioRenderer#viewEdgeForInk} 把防贴边余量
     * （{@code INK_INSET = 0.94}）补回来，即 {@code ceil(89.25 / 0.94) = 95px}。
     * 这样加余量不会让图形缩水。
     *
     * <p><b>v52：27.2dp → 25.5dp</b>。原因：v51 为了塞下"墨迹 95 + 余量"把视图撑到 102px，
     * 而状态栏行高只有 107px → 视图距上下边缘仅剩 2.5px，**看着顶到边**
     * （用户反馈"上下没显示全"）。退回 25.5dp 后视图 95px、上下各留 6px，
     * 与 v50 的视图尺寸一致，但图形自带余量、不再贴边。
     *
     * <p><b>上限 = 所在行的高度</b>（{@link #HEIGHT_RATIO} = 1.0）：
     * <pre>
     *   普通/锁屏状态栏  行高 107px → 视图 95（距上下边缘各 6px）
     *   下拉面板        行高  84px → 视图 95（★v53 关掉该行裁剪后溢出到头部空白区）
     * </pre>
     * 落地结果（density 3.5）：<b>三处一致，视图 95 / 墨迹 ≈ 89</b>。
     *
     * <p><b>★ v53：下拉不再是 84。</b>那一行高度被系统固定为 84px（实测塞 102px 也不长高），
     * 但它的外壳头部有 212px、行上下全是空白，因此改为
     * {@link #allowOverflow} 关闭该行及其外壳的 {@code clipChildren}，
     * 让三合一画到行外 —— 于是三处终于能一样大。
     *
     * <p>想调只改这一个数字；但它会被 {@link #HEIGHT_RATIO} 的行高上限收口。
     */
    private static final float GLYPH_WIDTH_TREE_DP = 25.5f;

    /**
     * ★ v65：行内几何 dump 的日志前缀。
     *
     * <p>⚠️ **必须同步加进 {@code SamsungDuoModule.isCritical()} 白名单**，
     * 否则会被 100ms 日志限流整段吃掉（这个坑踩过一次：v47 的 dump 代码是对的，
     * 却被限流吞掉，白跑一轮实机）。
     */
    private static final String GEOM_TAG = "geometry ";

    /**
     * 目标边长相对【容器宽度】的倍数上限。
     *
     * <p><b>★ v50：0.50 → 1.20，v51：1.20 → 1.25</b>。
     * 原值 0.50 的意图是"最多占容器一半宽度，别挤压邻居"，
     * 但实测状态栏的 {@code system_icons} 只有 84px 宽 → 0.5 只有 42px，
     * 反被 {@link #MIN_EDGE_DP} 兜到 70px，**这个比例实际从没生效过**。
     *
     * <p>改为 &gt;1 即"允许图标行被撑宽"（LinearLayout 会自己长到容纳新子 View），
     * 真正的防裁剪由 {@link #HEIGHT_RATIO} 负责。
     *
     * <p><b>为什么 v51 要提到 1.25</b>：视图边长现在是
     * {@code ceil(期望墨迹边长 / INK_INSET)}，即 102px 而不是 95px；
     * 而状态栏容器只有 84px 宽，1.20 × 84 = 100 &lt; 102，会把视图压掉 2px。
     * 1.25 × 84 = 105 ≥ 102，刚好放行。
     *
     * <p>撑宽后的余量复核：状态栏 1279+207 = 1486（通知区自动让位）；
     * 下拉 1028+338 = 1366 &lt; 1440。
     */
    private static final float SIZE_RATIO = 1.25f;

    /**
     * ★ 胶囊样式占所在容器的宽度比例。
     *
     * <p>胶囊天生 2.17:1 的宽扁形，用方形那套 1.25 倍会超出容器 25%，
     * 把原生图标挤掉；窄容器（锁屏只有 93px）里更是直接放不下。
     * 这里给 1.0 倍：宽度不超容器，高度按同比例一起降，形状保持不变。
     */
    private static final float CAPSULE_CONTAINER_RATIO = 1.0f;

    /** 三合一边长下限（dp），防止容器很小时被压到不可辨认。 */
    private static final float MIN_EDGE_DP = 20f;

    /**
     * 边长相对【所在行高度】的硬上限比例。
     *
     * <p><b>v52：0.96 → 1.0</b>。既然行高就是裁剪边界，取 1.0 表示"最多铺满整行、
     * 一像素都不越界"，这是不会溢出前提下的最大尺寸。
     * 之所以敢取满：图形自身在视图内还留了 {@link TrioRenderer#INK_INSET} 的余量，
     * 墨迹并不会真的贴到行边界上。
     *
     * <p><b>这个上限必须是"硬的"</b>——不能写成
     * {@code cap = max(containerH * ratio, MIN_EDGE_DP * density)}：
     * 那个 MIN_EDGE 下限会让 cap 反过来**超过** containerH，
     * 一旦抬高 MIN_EDGE 就会让三合一溢出被裁（v49 的隐患，v50 起已去掉下限）。
     *
     * <p><b>也不允许换基准</b>……**唯一例外是 v53 的下拉面板**：那一行已被
     * {@link #allowOverflow} 关掉裁剪，不再存在"行边界"这个约束，
     * 故改用头部高度作基准（见 {@code tryAttachIntoIconTree}）。
     * 注意 v51 曾<b>在没有关裁剪的情况下</b>换基准，结果被裁（见下），
     * 所以"换基准"必须与"关裁剪"成对出现，缺一不可。
     */
    private static final float HEIGHT_RATIO = 1.0f;

    /** TREE 模式的目标边长（dp）。想微调只改这里。 */
    /**
     * 三合一期望的【墨迹】边长（dp）。
     *
     * <p>★ v75：「整体尺寸」&gt;100% 时视图随之一起放大；≤100% 保持老规矩 ——
     * 只缩绘制、视图恒定 95px 占位不变（见 {@link TrioAppearance#glyphFit}）。
     * >100% 若不动视图，墨迹会超出视图画布、被 View <b>自身</b>的 canvas 裁掉
     * （父容器 {@code clipChildren=false} 管不了那一层），所以必须让视图跟着长，
     * 再靠"跳行高上限 + 关容器裁剪"让它溢出状态栏行 —— 用户明确许可。
     */
    private static float wantWidth() {
        final int scale = effectiveGlyphScale();
        return (scale > 100) ? GLYPH_WIDTH_TREE_DP * scale / 100f : GLYPH_WIDTH_TREE_DP;
    }

    /** ★ 胶囊样式：墨迹盒接近 2:1，宿主尺寸要按宽扁比例另算。 */
    private static boolean isCapsuleStyle() {
        final TrioSettings cfg = TrioConfig.get();
        return cfg != null && cfg.trioStyle == Prefs.STYLE_CAPSULE;
    }

    // ------------------------------------------------- 横屏尺寸回退（v100 → v101 模式无关）
    //
    // ★ 问题：整体尺寸 >100% 时，横屏会把三合一裁掉显示不全。
    //   v100 初版只在 attachWindow()（窗口模式）注册 DisplayListener、用窗口 Context
    //   测方向 —— 但实机日志(44)证明三合一走的是【挂树模式】(MODE=TREE)，
    //   attachWindow 从不执行 ⇒ 监听没注册、方向恒当竖屏 ⇒ 横屏没回退。
    //
    // 方案（用户定）：尺寸 >100% 且横屏 → 一律回退 100%；切回竖屏再恢复原大小。
    //
    // ★ v108（本版）：方向来源换成 **实时读 Configuration.orientation**，
    //   DisplayManager.DisplayListener 整套删掉。
    //
    //   为什么必须换（实机日志 50~53 + 反编译 OneXP 新版 1.0.261016 交叉验证）：
    //   ① v107b 的 bug：`sRotation` 是静态的，三套 overlay（状态栏/锁屏/下拉）共用，
    //      而"方向变了才动作"的判断在 onDisplayChanged 与 checkRotation 里各有一份，
    //      **谁先命中就把静态量改掉** ⇒ 只有最先发现的那个实例会重挂，另外两个看到
    //      "newRot == sRotation" 直接 return。日志 53 里 26 次旋转全部只有
    //      keyguard=true 在响应，状态栏一直停在竖屏的 105px ⇒ 横屏被切。
    //   ② DisplayListener 的 onDisplayChanged 会被以每秒十几次的频率回调（刷新率/
    //      显示模式变化都算 display changed），必须额外做去重/合并，白养一层复杂度。
    //   ③ Configuration.orientation 是**全局值**：三套 overlay 读到的必然一样，
    //      不存在"谁先把共享量改掉"这回事；而且它是配置变化的一部分，
    //      **旋转那一帧就已经是新值**，不需要等布局、不需要缓存行高、不需要 pre-draw。
    //   OneXP 也是这么干的（全 dex 只有 3 处 Configuration.orientation，
    //   没有 Display.getRotation、没有 registerDisplayListener）。
    private static volatile boolean sLandscape = false;

    /**
     * ★ v108：无条件刷新 {@link #sLandscape}（幂等），返回当前是否横屏。
     *
     * <p><b>"无条件"是关键</b>：不做 {@code newRot == sRotation} 那种早退判断 ——
     * 那个判断正是 v107b 只响应一套的元凶。写进去的值由全局 Configuration 决定，
     * 谁先写、写几次，结果都一样。
     */
    private static boolean refreshLandscape(View v) {
        if (v == null) {
            return sLandscape;
        }
        try {
            final android.content.res.Configuration c = v.getResources().getConfiguration();
            if (c != null) {
                sLandscape = (c.orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            }
        } catch (Throwable ignored) {
            // 取不到就沿用上次的值
        }
        return sLandscape;
    }

    /** 当前是否横屏。值由 {@link #refreshLandscape(View)} 每帧刷新。 */
    private static boolean isLandscapeNow() {
        return sLandscape;
    }

    /**
     * 有效尺寸：横屏且设定 >100% 时封顶 100%，否则用设定值。
     *
     * <p>树/窗口两模式都走它。注意树模式 glyphScale 也可能 >100%
     * （实机日志44：树模式 130%），故横屏回退对树模式同样必要、不再假设"树=100%"。
     */
    private static int effectiveGlyphScale() {
        final int scale = TrioConfig.get().glyphScale;
        return (scale > 100 && isLandscapeNow()) ? 100 : scale;
    }
    /**
     * 窗口类型：TYPE_STATUS_BAR_SUB_PANEL(2012)，唯一被实机验证可用的类型。
     * 仅作"挂树失败"时的安全兜底（v14 起）；它被状态栏区域框住，故 >100% 走挂树 +
     * 逐层放开 clipChildren，不依赖它。独立悬浮窗方案已彻底放弃（脱离树不抢位子会遮挡），
     * 曾试过的候选类型逐个试(2015/2012/2038)一并移除。
     */
    private static final int WINDOW_TYPE = 2012;
    /** 重绘间隔（ms）：信号无广播，只能轮询。 */
    private static final long REFRESH_MS = 1000L;
    /** 定位重试间隔（ms）。 */
    private static final long RETRY_MS = 600L;
    /** 重试上限，到顶归零继续重试（永不永久放弃）。 */
    private static final int RETRY_MAX = 16;
    /** 右边距（dp），避开曲面屏边缘。 */
    private static final float RIGHT_MARGIN_DP = 4f;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** 每套状态栏各持一个实例（普通 + 锁屏两套都要显示）。 */
    private static final java.util.Map<View, TrioOverlay> sLiveMap =
            new java.util.HashMap<>();
    private static boolean sBusy = false;

    // ---- ★ v104：瞬时重挂（旋转/拖尺寸滑块时不再有"放大缩小"的过渡动画）----
    /** 向上关掉几层父容器的 LayoutTransition：挂载点自身 + 外层壳。 */
    private static final int LT_DEPTH = 5;
    /**
     * 抑制窗口：与 {@link #endInstantLayout()} 之间隔这么久再恢复。
     * 不能"改完立刻恢复"——addView 只把布局标脏，真正的 layout 遍历在下一帧；
     * 若在遍历前就还原，其它子 View 的位置/尺寸变化又会被动画化。
     * 默认 LayoutTransition 时长 300ms，取 400ms 一次盖住。
     */
    private static final long LT_SUPPRESS_MS = 400L;
    /**
     * ★ v107b：挂载那一刻容器还没按最终布局量完（日志 53 实测状态栏
     * container=84x107，稳定后 202x90）。过这么久再校一次尺寸。
     */
    private static final long ROT_SETTLE_MS = 600L;
    private static final Object LT_LOCK = new Object();
    /** 被临时关掉的容器 → 它原本的 LayoutTransition（可能为 null）。 */
    private static final java.util.IdentityHashMap<ViewGroup, android.animation.LayoutTransition>
            sLtSaved = new java.util.IdentityHashMap<>();
    private static boolean sLtArmed = false;

    /**
     * ★ v58：状态栏图标颜色变了，立刻重绘所有活着的叠加层。
     *
     * <p><b>为什么必须主动推一把：</b>浅色/深色背景切换会改状态栏图标颜色
     * （由系统的暗色图标调度器下发，见 {@link TrioState#noteDarkTint}），
     * 但这件事<b>和电量、信号、Wi-Fi 读数完全无关</b>，不会走我们的常规重绘路径。
     * 不主动 invalidate 的话，要等到下一次轮询（约 2s）才变色，
     * 用户看到的就是"切到浅色页面后图标还是一片白"。
     */
    static void invalidateAll() {
        try {
            MAIN.post(() -> {
                for (TrioOverlay inst : sLiveMap.values()) {
                    if (inst != null && inst.glyph != null) {
                        inst.glyph.invalidate();
                        if (inst.sideSignal != null) {
                            inst.sideSignal.invalidate();
                        }
                        if (inst.sideType != null) {
                            inst.sideType.invalidate();
                        }
                        if (inst.sidePerf != null) {
                            inst.sidePerf.invalidate();
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
            // 重绘失败绝不能影响系统
        }
    }

    /** 陪衬的种类：环外信号读数 / 环外网络类型标签。 */
    private static final int SIDE_SIGNAL = 0;
    private static final int SIDE_TYPE = 1;
    /** ★ v73：充电功率 / 电池温度读数（两块内容共用一块 View，各占一行）。 */
    private static final int SIDE_PERF = 2;

    static {
        // ★ v59：设置一变就重新核对"陪衬是否该存在"。
        //
        //   <p><b>为什么必须有这一段：</b>环外读数与环外类型标签是**挂载时创建**的
        //   子 View，而设置变更原本只更新配置快照、不会重跑挂载。于是用户在设置里
        //   把位置改成"环外"之后，什么都不会发生 —— 实测日志正是如此：
        //   {@code tree-attached ... sideSignal=- sideType=-}（两块一个都没建）。
        //   TrioConfig 本来就有配置通知（广播 + prefs 监听两条路），这里接上去即可。
        try {
            TrioConfig.addListener(new TrioConfig.Listener() {
                @Override
                public void onConfigChanged() {
                    reevaluateAll();
                }
            });
        } catch (Throwable ignored) {
            // 注册失败绝不能影响模块加载
        }
    }

    /**
     * 配置变了：让每个活着的叠加层重走一遍挂载判定。
     *
     * <p>集合与设置一致时 {@code tryAttachIntoIconTree} 会立刻返回，开销可忽略，
     * 所以不必在这里自己比对——判定的唯一权威留在挂载逻辑里。
     */
    private static void reevaluateAll() {
        MAIN.post(() -> {
            for (TrioOverlay inst : sLiveMap.values()) {
                if (inst == null) {
                    continue;
                }
                try {
                    if (inst.treeAttached) {
                        inst.tryAttachIntoIconTree();
                    } else {
                        // ★ v76：窗口模式那批（treeAttached=false）以前被整个跳过，
                        //   于是「整体尺寸」之类只影响挂进树的那两套 —— 用户实测
                        //   "锁屏没变，状态栏和下拉变了"。attachWindow 自身幂等
                        //   （已 attached 时只同步位置、不重复 addView），可安全重入。
                        inst.attachOrSync();
                    }
                    // ★ v82：配置变了立刻重绘一次。左边距/平移这类"每帧从配置现读"
                    //   的值原本要等系统下一次自然重绘（≤1s）才生效，主动 invalidate
                    //   一下就让所有只读配置的绘制层当场跟上。
                    if (inst.glyph != null) {
                        inst.glyph.invalidate();
                    }
                } catch (Throwable ignored) {
                    // 重挂失败绝不能影响系统
                }
            }
        });
    }

    /** 容器查找失败只提示一次，避免高频重复打日志。 */
    private static boolean containerMissLogged = false;

    private View statusBarRoot;
    private final TrioState state;
    private final GlyphView glyph;
    /**
     * ★ v59：环外的两块陪衬（读数 / 网络类型），各自一个挂在同一容器里的子 View。
     *
     * <p>为什么不做进 {@link GlyphView}：那个 View 的宽度就是三合一那个正方形，
     * 而这两块要**在它左边各自占位**、把原生图标整体挤出去。只有作为容器的子
     * View 参与 LinearLayout 排布，才真正"占位"——与三合一挂树同一套机制。
     */
    private SideView sideSignal;
    private SideView sideType;
    /** ★ v73：充电功率 / 电池温度读数，紧贴三合一左侧（外侧由最左块规则承担）。 */
    private SideView sidePerf;
    /**
     * ★ v76：挂载时实际用的「整体尺寸」与所在行高 —— 提前返回的判定要用。
     *
     * <p>视图边长只在挂载那一次算（见 {@code tryAttachIntoIconTree}），所以任何
     * 影响它的设置变了就必须整体重挂，否则滑块拖了也不会有反应。
     */
    private int attachedGlyphScale = -1;
    /** ★ v101：挂载时的 effectiveGlyphScale()（横屏会压到 100）。旋转导致它变化→强制重挂。 */
    private int attachedEffScale = -1;
    private int attachedRowH = -1;
    /** ★ v106：本次实际用的【行高基准】，供 tree-attached 那行核对。 */
    private int lastHeightBase;

    /** ★ v65：行内几何只 dump 一次（每个实例一次）。 */
    private boolean rowGeomLogged = false;
    /** ★ v78：总开关关闭的提示只打一次（配置每变一次就会走一遍 hideAll）。 */
    private boolean masterOffLogged = false;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private final int[] location = new int[2];
    private boolean attached;
    /** 是否已挂进系统图标 View 树（v23：TREE 模式，优先，能抢位子）。 */
    private boolean treeAttached = false;
    /** 当前这套是不是锁屏（KeyguardStatusBarView）。★ v82fix：锁屏不应用「整组左右平移」。 */
    private boolean keyguardRow = false;
    private int retryCount;
    private int lastLoggedX = Integer.MIN_VALUE;
    private int lastLoggedY = Integer.MIN_VALUE;

    private TrioOverlay(View statusBarRoot, TrioState state) {
        this.statusBarRoot = statusBarRoot;
        this.state = state;
        this.glyph = new GlyphView(statusBarRoot.getContext());
    }

    /**
     * 由 TrioHooks 调用：下拉面板（shade）头部就绪。
     *
     * <p>与状态栏走完全相同的"挂树抢位子"逻辑，只是 root 不同：
     * 状态栏 root 是 {@code PhoneStatusBarView}/{@code KeyguardStatusBarView}，
     * 下拉面板 root 是 {@code ShadeHeaderController.header}
     * （实机实测为 {@code split_shade_status_bar}，1440x212）。
     *
     * <p><b>★ v49 修正</b>：以前传的是 {@code iconContainer}（StatusIconContainer），
     * 那是 v39/v41 崩溃的根源。<b>现在传的是 header 自身</b>，
     * 由 {@link #findIconContainer} 在它的子树里按 id 名找到
     * {@code hover_system_icons_container}（普通 LinearLayout）再挂——
     * 与状态栏挂 {@code system_icons} 是同一条已验证稳定的路径。
     *
     * <p>两者存在 {@link #sLiveMap} 里各持一个实例，互不干扰；
     * 靠 {@code onWindowVisibilityChanged} 各自跟随所属窗口的显隐。
     */
    static void onShadeReady(ViewGroup shadeRoot) {
        if (shadeRoot == null) {
            return;
        }
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                doReady(shadeRoot);
            } else {
                MAIN.post(() -> doReady(shadeRoot));
            }
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: onShadeReady failed: " + t);
        }
    }

    /** 由 TrioHooks 调用：幂等 + 切主线程（addView/WindowManager 必须主线程）。 */
    static void onStatusBarReady(View root) {
        if (root == null) {
            return;
        }
        try {
            TrioState.attachContext(root.getContext());
            if (Looper.myLooper() == Looper.getMainLooper()) {
                doReady(root);
            } else {
                final View r = root;
                MAIN.post(() -> doReady(r));
            }
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: onStatusBarReady failed: " + t);
        }
    }

    /** 主线程执行挂载；两套状态栏各持一个实例。 */
    private static void doReady(View root) {
        if (sBusy) {
            return;
        }
        sBusy = true;
        try {
            TrioOverlay inst = sLiveMap.get(root);
            if (inst == null) {
                inst = new TrioOverlay(root, new TrioState(root));
                sLiveMap.put(root, inst);
                TrioHooks.log(TrioHooks.LOG_INFO, "overlay: new instance for "
                        + root.getClass().getSimpleName()
                        + " w=" + root.getWidth() + " h=" + root.getHeight());
            }
            inst.attachOrSync();
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: doReady failed: " + t);
        } finally {
            sBusy = false;
        }
    }

    /** 造一个类型匹配的 WindowContext（2012），否则 Android 抛 Window type mismatch。 */
    private Context createOverlayContext() {
        try {
            final Context base = statusBarRoot.getContext();
            final android.view.Display display = base.getDisplay();
            if (display == null) {
                return null;
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return base.createWindowContext(display, WINDOW_TYPE, null);
            }
            return null;
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: createWindowContext failed: " + t);
            return null;
        }
    }

    /**
     * v14 已验证的窗口参数，<b>勿改</b>：
     * Gravity.TOP|START + 屏幕绝对坐标 + 具体高度 + LAYOUT_NO_LIMITS。
     */
    private WindowManager.LayoutParams buildParams() {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.type = WINDOW_TYPE;
        // v14 实测：TOP|START 配绝对坐标才在正确位置（v15 改 TOP|RIGHT 就错了）
        p.gravity = Gravity.TOP | Gravity.START;
        p.width = 1;
        // ★ 高度初始给状态栏高度，绝不用 MATCH_PARENT（会被当成整屏高 2992）
        p.height = Math.max(statusBarRoot.getHeight(), 1);
        p.format = PixelFormat.TRANSLUCENT;
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.setTitle("SamsungDuo");
        p.windowAnimations = 0;
        return p;
    }

    /**
     * ★ v78：总开关是否打开（{@link TrioSettings#enabled} → {@link TrioAppearance#glyph}）。
     *
     * <p>它是本模块<b>一切绘制</b>的唯一总闸：三合一本体 + 三块环外陪衬
     * （信号读数 / 网络类型 / 温度）全部由它决定有没有。
     */
    private static boolean masterOn() {
        try {
            return TrioAppearance.of(TrioConfig.get()).glyph;
        } catch (Throwable t) {
            return true; // 读不到配置就按"开"处理，绝不因为异常把图标弄没
        }
    }

    /**
     * ★ v78：总开关关闭 —— 把本模块<b>额外加的</b>所有 View 全部摘掉。
     *
     * <p><b>不需要（也不应该）做任何"还原"：</b>本模块从没替换、没隐藏过系统
     * 原生图标，只是往状态栏那一行里<b>多挂了</b>几个自绘 View。用户已明确：
     * 关掉总开关 = 我们画的东西一个都不显示即可，系统图标本来就在那儿，
     * 动它反而会踩 v39/v41 那种崩溃。
     *
     * <p>两条挂载路径都要收干净：
     * <ul>
     *   <li>挂树模式（{@code treeAttached}）：从父容器 removeView；</li>
     *   <li>独立窗口模式（{@code attached}）：{@code windowManager.removeView}。</li>
     * </ul>
     * 循环一并停掉（否则每秒还在空转重绘一个已经不存在的 View）。
     */
    private void hideAll() {
        try {
            detachGlyphAndSides();
            if (attached && windowManager != null) {
                try {
                    windowManager.removeView(glyph);
                } catch (Throwable ignored) {
                    // 移除失败不影响：窗口模式下系统收起状态栏时窗口也会一起收
                }
                attached = false;
                retryCount = 0;
                lastLoggedX = Integer.MIN_VALUE;
                lastLoggedY = Integer.MIN_VALUE;
            }
            glyph.stopLoop();
            if (!masterOffLogged) {
                masterOffLogged = true;
                TrioHooks.log(LOG_OK, "overlay: MASTER OFF -> all overlay views detached ("
                        + (statusBarRoot == null ? "?"
                        : statusBarRoot.getClass().getSimpleName()) + ")");
            }
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: hideAll failed: " + t);
        }
    }

    /** 挂载或更新定位。 */
    private void attachOrSync() {
        try {
            // ★ v78：总开关关着 —— 什么都不挂，并且把已挂的摘干净。
            //   放在最前面：doReady / 重试 / 配置变更三条路都会经过这里，
            //   一处拦住就够了（reevaluateAll 会直接调 tryAttachIntoIconTree，
            //   那里也有一道同样的闸）。
            if (!masterOn()) {
                hideAll();
                return;
            }
            masterOffLogged = false;
            // ★ v23 合并策略：先尝试「挂进 system_icons 参与系统排布（能抢位子）」，
            //   挂不上再退回「v14 已验证的独立窗口」。两条路互为备份。
            // ★ v102c 回退：曾试"开了独立窗口就强制跳窗口"(v102b)，但选中的窗口类型(2015)在状态栏
            //   **下层被遮挡**，且引起重复图标/切换失灵/定位间距全变。独立悬浮窗需完整吃透 OneXP
            //   的窗口类（它用 createWindowContext + app token + 特定类型的完整方案）后再做，
            //   先回退到稳定的"树优先"，不再硬跳窗口。
            if (!treeAttached) {
                if (tryAttachIntoIconTree()) {
                    treeAttached = true;
                    TrioHooks.log(LOG_OK, "overlay: MODE=TREE (in system_icons, 参与排布)");
                }
            }
            if (treeAttached) {
                // ★ v36：跟随状态栏可见性。
                //   息屏↔锁屏切换时，两套状态栏（PhoneStatusBarView /
                //   KeyguardStatusBarView）交替显示；若某套已不可见却仍在渲染，
                //   就会在切换瞬间出现"亮一下"的残影。
                //   这里每帧同步：所在状态栏不可见 → 隐藏；可见 → 恢复。
                syncVisibility();
                glyph.startLoop();
                return;
            }
            // 退回独立窗口（v14 已验证配置）
            attachWindow();
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: attachOrSync failed: " + t);
        }
    }

    /**
     * 依据所在窗口的可见性同步三合一显隐（消除切换残影）。
     *
     * <p><b>v37修正的关键点</b>：
     * <ol>
     *   <li>隐藏用 {@code GONE} 而非 {@code INVISIBLE}——INVISIBLE 仍参与布局、
     *       会留下空位（用户反馈"消失但位置空出来"）；GONE 不占位。</li>
     *   <li>判断只看 {@code getRootView().getWindowVisibility()}（窗口级），
     *       <b>不检查祖先链的 visibility</b>——祖先 View 在布局/动画期间会短暂
     *       GONE，导致误判为不可见（v36 用 INVISIBLE + 遍历祖先链 =
     *       该显示时也不显示）。</li>
     * </ol>
     */
    private void syncVisibility() {
        try {
            if (glyph.getParent() == null) {
                return; // 已被摘除，等下次挂载
            }
            final View rootView = glyph.getRootView();
            final boolean visible = rootView != null
                    && rootView.getWindowVisibility() == View.VISIBLE;
            // GONE：不显示也不占位，切换回来时恢复 VISIBLE
            final int want = visible ? View.VISIBLE : View.GONE;
            if (glyph.getVisibility() != want) {
                glyph.setVisibility(want);
                TrioHooks.log(LOG_OK, "overlay: visibility -> "
                        + (visible ? "VISIBLE" : "GONE")
                        + " (" + statusBarRoot.getClass().getSimpleName()
                        + ", winVis=" + (rootView == null ? "?" : rootView.getWindowVisibility()) + ")");
            }
        } catch (Throwable ignored) {
            // 可见性判断失败绝不影响其它功能
        }
    }

    private static final int LOG_OK = TrioHooks.LOG_INFO;

    /**
     * 尝试把三合一挂进系统图标容器（{@code system_icons}），参与系统排布、能抢位子。
     *
     * <p>关键点（全部来自实机日志实证，不猜）：
     * <ul>
     *   <li>容器 id = {@code system_icons}（LinearLayout），与电池/WiFi/信号同父；</li>
     *   <li>该容器宽度仅 146px，故三合一宽度用 38dp（约133px）而非 62dp；</li>
     *   <li>LayoutParams 从容器已有子 View 拷贝（同父必同类型，杜绝 ClassCastException）；</li>
     *   <li>不隐藏、不修改任何系统控件，只新增一个自绘子 View。</li>
     * </ul>
     *
     * @return 成功挂载返回 true。
     */
    private boolean tryAttachIntoIconTree() {
        try {
            // ★ v78：总开关关着 —— 摘掉全部（含三合一本体），且不退回窗口模式。
            //   这道闸是给 reevaluateAll 用的：它会对已挂树的实例直接调本方法，
            //   绕过 attachOrSync 里那道闸。返回 true = "已处理完，别再试窗口"。
            if (!masterOn()) {
                hideAll();
                return true;
            }
            masterOffLogged = false;
            final ViewGroup parent = findIconContainer(statusBarRoot);
            if (parent == null) {
                // 容器找不到时明确记录：下拉面板/息屏等场景可能是另一套树结构
                if (!containerMissLogged) {
                    containerMissLogged = true;
                    TrioHooks.log(LOG_OK, "overlay: HINT icon container not found in "
                            + statusBarRoot.getClass().getSimpleName()
                            + " (system_icons/system_icons_container/status_icon_area all missed)"
                            + " -> falling back to window mode");
                }
                return false;
            }
            // ★ v53：下拉图标行需要"允许溢出"。
            //   该行高度固定 84px 且不会长高，但它所在的头部
            //   split_shade_status_bar 高 212px、行上下都是空白（无任何可见内容），
            //   用户已确认"可以溢出、只要图标显示全"。
            //   这里关闭该行及其外壳的 clipChildren，让三合一能画到行外，
            //   从而与状态栏同尺寸。
            //   注意：必须在"已挂载就返回"之前调用——Samsung 重建/重布局
            //   可能把 clipChildren 重置回 true，每次走到这里都重新确保一次。
            final boolean shadeRow = isShadeIconRow(parent);
            // ★ v75：整体尺寸 >100% 时三处（状态栏/锁屏/下拉）都允许溢出所在行。
            //   下拉本来就要（头部 212px 空白区）；状态栏/锁屏是用户明确许可
            //   "可以出状态栏，只要图像显示完全"。120% 时上下各溢约 3.5px
            //   （density 3.5），不会画到相邻元素上。
            // ★ v101b：overCap 改用【有效尺寸】effectiveGlyphScale()，不是原始 glyphScale。
            //   横屏 >100% 已回退到 effective 100%，若这里仍按原始 130 判 >100% = 真，
            //   会跳过下面的"行高上限"保护（!overCap 才收高度）→ 渲染到 100% 大小却不按
            //   行高收，差一点被裁（用户实测：130% 横屏"缩小了但没到干净 100%、还差一点
            //   露在外面"）。改用 effective 后，横屏自动回退与手动设 100% 走同一条路。
            final boolean overCap = effectiveGlyphScale() > 100;
            if (shadeRow || overCap) {
                // >100% 时逐层放开（上到窗口外壳），否则放大后上下会被裁掉几像素
                allowOverflow(parent, overCap);
            }
            // ★ v59：环外的两块陪衬（读数 / 网络类型）是否该存在，由设置决定。
            //   已挂载且"陪衬集合"与当前设置一致 → 什么都不用做；
            //   不一致（用户在设置里改了位置开关）→ 整体重挂一次。
            final TrioSettings cfg = TrioConfig.get();
            final TrioAppearance app = TrioAppearance.of(cfg);
            // ★ v59：移植版语义 —— **关闭＝隐藏，环外＝模块自己画**。
            //   所以这里不再要求「堆叠信号」开关：「堆叠」在原版里的含义是
            //   "关闭则沿用系统原生的移动信号图标"，而本机原生图标已被用户
            //   手动隐藏，沿用＝什么都不显示。用户已明确要本模块自己画。
            final boolean wantSignal = app.glyph && app.mobile && app.signalOutOfRing;
            // 类型标签同理：关闭（OFF）＝隐藏，环内＝画进环里，环外＝这块独立 View。
            final boolean wantType = app.typeOutOfRing && app.glyph;
            // ★ v73：功率/温度读数。只是"想要"——有没有内容要等取数器答话
            //   （见 TrioRenderer.perfText），两块都没内容时这块宽度为 0、不占位。
            final boolean wantPerf = app.perfAnywhere() && app.glyph;
            final boolean haveSignal = sideSignal != null && sideSignal.getParent() == parent;
            final boolean haveType = sideType != null && sideType.getParent() == parent;
            final boolean havePerf = sidePerf != null && sidePerf.getParent() == parent;
            // ★ v76 修正：「整体尺寸」(glyphScale) 之前**不在**这个提前返回的判定
            //   里，而视图边长只在挂载时算一次 → 用户拖「整体尺寸」滑块时，这里
            //   直接 return true，尺寸永远停在旧值（实测 100–120% 完全没效果）。
            //   行高一并纳入：它决定"行高上限"，行高变了 cap 也得重算。
            // ★ v107：这段判定搬到下面（算完 w/h 之后）。原因见那边的 v107 分支 ——
            //   尺寸变了现在走「原地改 LayoutParams」，不再摘了重挂。
            // ★ v49 安全护栏：绝不允许挂进 StatusIconContainer。
            //   它是自定义容器（onMeasure/onLayout 只认 StatusIconDisplayable 子 View），
            //   v39/v41 两次 SystemUI 崩溃都是因为它。这里按类名硬拦截：
            //   命中就放弃挂树（退回窗口模式），宁可三合一不显示，也绝不让系统崩。
            if (parent.getClass().getName()
                    .equals("com.android.systemui.statusbar.phone.StatusIconContainer")) {
                TrioHooks.log(TrioHooks.LOG_WARN,
                        "overlay: REFUSE to mount into StatusIconContainer (crash risk)");
                return false;
            }
            final float density = statusBarRoot.getResources().getDisplayMetrics().density;
            // ★ v77：是否锁屏那一行。实机日志实测三处容器：锁屏 75×63、
            //   状态栏 205×90、下拉 253×84 —— 锁屏最小，130%（124px）在它上面
            //   放不下、会被窗口边界切掉。用户明确要求：**锁屏最大 100%，
            //   下拉与状态栏照样 130%**。
            final String rootName = (statusBarRoot == null)
                    ? "" : statusBarRoot.getClass().getSimpleName();
            final boolean keyguardRow = "KeyguardStatusBarView".equals(rootName);
            this.keyguardRow = keyguardRow;
            // ★ v29 起统一为【正方形 + 目标 dp】：正方形保证 inkScale 只由边长决定、形状不变形。
            // ★ v50：宽度只作为"容器太窄时的兜底"（SIZE_RATIO 已放到 1.20，见其注释），
            //   真正的硬约束是下面的【容器高度上限】。三步走：
            //     ① 取目标边长 wantWidth()×density
            //     ② 宽度兜底：不超过 容器宽 × SIZE_RATIO
            //     ③ 高度硬上限：不超过 容器高 × HEIGHT_RATIO（保证不被裁）
            final int containerW = parent.getWidth();
            // ★ v51：wantWidth() 给出的是期望的【墨迹边长】（真正看到的图形大小）。
            //   视图边长要把 TrioRenderer.INK_INSET 的防贴边余量补回来，
            //   否则加了余量图形会跟着缩 6%。
            //   用户要求：只为"上下不再被裁"，不要顺便把图标收小。
            // ★ v77：锁屏把目标墨迹边长压回 100%（= GLYPH_WIDTH_TREE_DP）。
            //   下拉与状态栏不受影响、仍由「整体尺寸」控制。只压这一个数，其余逻辑不动。
            float wantInk = wantWidth();
            if (keyguardRow && wantInk > GLYPH_WIDTH_TREE_DP) {
                wantInk = GLYPH_WIDTH_TREE_DP;
            }
            int w = TrioRenderer.viewEdgeForInk(wantInk * density);
            // ★ v76 已撤回：这里曾经改成 `if (containerW > 0 && !overCap)`（>100% 时
            //   跳过宽度兜底）。结果是锁屏从 93px 跳到 124px，随后一路引出"被窗口
            //   边界剪切 / 封顶 / 三处不一致"等一连串问题，用户反馈"越改越离谱"，
            //   已整段撤回，恢复原样：三处一律受这条兜底约束。
            if (containerW > 0) {
                // 容器里通常有 3~4 个图标（电池/信号/网速/三合一），
                // 三合一最多占 RATIO 比例，剩下的留给原生图标。
                final int share = (int) (containerW * SIZE_RATIO);
                w = Math.min(w, share);
            }
            // 下限保护：容器很小时也别缩到不可见（小于 INK_W 的一半会完全糊掉）
            w = Math.max(w, (int) (MIN_EDGE_DP * density));
            // ★ v52：默认【高度硬上限 = 所在行的高度】。行就是会裁剪我们的边界。
            //   （下限 MIN_EDGE 绝不能并进来算 cap，否则 cap 会超过 containerH，
            //    防裁剪保护失效——v49 的隐患。）
            // ★ v53：下拉面板例外 —— 那一行已被 allowOverflow() 关掉裁剪，
            //   不会再有"行边界"这个约束，于是改用头部高度作基准，让它与状态栏同尺寸。
            //   判定用上面已算好的 shadeRow。
            final int containerH = parent.getHeight();
            // ★ v108：行高基准就是**当前实测量**，行高缓存（v106/v107 那套）整段撤掉。
            //   缓存是为了"旋转当时就算准、不等布局"，但实测证明它反而是污染源
            //   （日志 52 抓到瞬态 107 被写进缓存 ⇒ 之后每次都拿 107 当基准）。
            //   v108 不再靠"一次算准"，改为**改完之后再复核几帧**（见 armSettle），
            //   让尺寸自己收敛到稳定值 —— 不需要猜、不需要缓存。
            // ★ L12：封顶基准改用**内容高度**（扣掉父容器上下 padding）。
            //   别的模块（OneuiXP 等）会给图标行 setPadding；行总高不变，但可用区变矮，
            //   我们按总高封顶就会多出一截伸进 padding 区 → 被更上层的边界裁掉。
            //   同时把 padding 打一次日志，便于确认行内边距是否被外部改过。
            final int padTop = parent.getPaddingTop();
            final int padBottom = parent.getPaddingBottom();
            // ★ L25：行 padding **变化时才打**（原来只打一次）。
            //   OneuiXP 会按自己的配置调 setPaddingRelative（反编译见 La43;.b，
            //   实参来自它配置里的浮点 dp），行内可用区因此变矮 → 我们 130% 越过裁剪线。
            if (padTop != lastPadTop || padBottom != lastPadBottom) {
                lastPadTop = padTop;
                lastPadBottom = padBottom;
                TrioHooks.log(LOG_OK, "row padding changed: top=" + padTop
                        + " bottom=" + padBottom
                        + " padL=" + parent.getPaddingLeft()
                        + " padR=" + parent.getPaddingRight()
                        + " rowH=" + containerH
                        + " rowW=" + parent.getWidth()
                        + " (cap uses content height=" + (containerH - padTop - padBottom) + ")");
            }
            final int contentH = containerH - padTop - padBottom;
            final int heightBase = (contentH > 0 ? contentH
                    : (containerH > 0 ? containerH : lastHeightBase));
            this.lastHeightBase = heightBase;
            int h = w; // 正方形
            // ★ v75：整体尺寸 >100% 时跳过"行高上限"。该上限本来的职责是防止
            //   被行边界裁剪，而 >100% 已靠上面的 allowOverflow 关掉裁剪 ——
            //   再压高度就放大不上去（还会把宽一起压回来，白放大）。
            // ★ L11：这一行**不再按 >100% 跳过**。跳过是独立悬浮窗时代（v75/v80）的做法；
            //   v103 放弃悬浮窗回到树模式后，状态栏窗口本身就只有行高那么高 ——
            //   实机日志 2122/2913 条 `h > rowH`（视图 114~124px 塞进 84/90px 的行），
            //   超出的部分被**窗口边界**裁掉，用户看到"放大后被剪切、拉一下才还原、过一会又剪切"。
            //   树模式下行高/窗口高度就是物理上限：封顶后缩到刚好放下，而不是被切。
            if (heightBase > 0 && !overCap) {
                int base = heightBase;
                if (shadeRow) {
                    final int rootH = (statusBarRoot == null) ? 0 : statusBarRoot.getHeight();
                    if (rootH > base) {
                        base = rootH;
                    }
                }
                final int cap = Math.max(1, (int) (base * HEIGHT_RATIO));
                h = Math.min(h, cap);
                w = h;
            }
            // ★ 胶囊：宽扁形（墨迹盒接近 2:1），高度沿用同一套算法，宽度按同高比例另给。
            //   加宽之后要重新走一遍"宽度兜底 + 下限"，否则会吃掉整条状态栏图标区。
            // ★ bug 修复：宽度被容器兜底压住时，**高度必须按同比例一起降** ——
            //   锁屏那一行只有 93px 宽，胶囊 2.17:1 的宽扁形放不下 1:1；只压宽度
            //   不压高度的话，墨迹会等比缩到 0.6，整个图标"很小很小"。
            //   胶囊天生就宽，占容器的比例也比方形的紧（SIZE_RATIO 的 1.25 倍
            //   会超出容器 25%，把原生图标挤掉）。
            if (isCapsuleStyle()) {
                w = TrioRenderer.capsuleViewWidth(h);
                if (containerW > 0) {
                    final int limit = (int) (containerW * CAPSULE_CONTAINER_RATIO);
                    if (w > limit) {
                        w = limit;
                        h = TrioRenderer.capsuleViewHeight(w);
                    }
                }
                w = Math.max(w, (int) (MIN_EDGE_DP * density));
            }
            // ★ v76 已撤销：曾试过"锁屏放大到刚好填满本行"的窗口边界兜底
            //   （KeyguardStatusBarView 且 h > containerH 时压到 containerH）——
            //   用户反馈"130% 根本不对"，已整段撤掉，锁屏恢复与其他两处同尺寸。
            //   保留记录：锁屏那一行被 Android **窗口边界**裁剪（三处裁剪链都已
            //   逐层放开到 ViewRootImpl，clipChildren 对窗口无效），日志里的
            //   `container=宽x高` 可核对锁屏确切行高。
            // ★ v65：2dp → **0**（用户反馈"还是没变化，缩不小"）。
            //
            //   像素级实测（用户截图，状态栏，原生「5G+5条」↔ 我们的圆环）：
            //     信号组最右墨迹 x=161，圆环最左墨迹 x=195 → 空档 33px
            //     截图缩放比 0.775（圆环墨迹 69px ÷ 真机 89px）⇒ 真机 42.6px ≈ 12.2dp
            //   而我们自己的 margin 只占 6dp，剩下 ~6dp 来自别处 —— 已置 0 到底，
            //   **margin 能做的到此为止**。
            //   剩下的 6dp 有三种可能，靠 v65 新增的 `geometry ` 行内 dump 判定：
            //     ① 原生图标 View 自身的内边距（View 比墨迹宽）
            //     ② 中间夹着一个 INVISIBLE（占位不绘制）或宽度非 0 的空 View
            //     ③ 父容器的 padding
            // ★ v66：左边不留白（0）还是补一点（2dp），交给"左边的可见邻居是谁"决定。
            //   规则与理由见 {@link #gapBeforeLeftNeighbor}。
            //（注意：先算好 LayoutParams 再摘旧的——上面几处 return false 一旦触发，
            //  若已经摘掉就再也接不回来，图标会整个消失。摘除放到最后。）
            final int gap = leftGapPx(parent, density);
            // ★ v59：三合一左边只要站了陪衬（环外读数/类型标签/功率温度读数），
            //   边距就交给陪衬自己带，三合一不再另加这份边距 ——
            //   否则两段边距叠加，设置里承诺的"读数与电池之间的空隙"就不准了。
            final boolean hasSide = wantSignal || wantType || wantPerf;
            LayoutParams lp = cloneExistingLayoutParams(parent);
            if (lp == null) {
                lp = createLayoutParamsFor(parent);
            }
            if (lp == null) {
                return false;
            }
            lp.width = w;
            lp.height = h;
            // 间距/对齐：优先按 LinearLayout 参数设置（LinearLayout 及其子类）。
            // 注意不能只靠 instanceof——必须确保 lp 确实是 LinearLayout.LayoutParams，
            // 否则 margin 静默失效（v30~v32 就是栽在这里）。
            boolean lpApplied = false;
            if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                final android.widget.LinearLayout.LayoutParams llp =
                        (android.widget.LinearLayout.LayoutParams) lp;
                llp.gravity = android.view.Gravity.CENTER_VERTICAL;
                llp.weight = 0f;
                llp.leftMargin = hasSide ? 0 : gap; // 有陪衬时不重复占边距
                lpApplied = true;
            } else if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                final android.widget.FrameLayout.LayoutParams flp =
                        (android.widget.FrameLayout.LayoutParams) lp;
                flp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END;
                flp.leftMargin = hasSide ? 0 : gap;
                lpApplied = true;
            }
            if (!lpApplied) {
                // 兜底：基类 ViewGroup.LayoutParams 没有 margin 字段，
                // 需强转 MarginLayoutParams 才能设margin（LinearLayout.LayoutParams
                // 的父链必然经过它）。用反射兜住任何异常，绝不让间距设置抛出去。
                try {
                    final android.view.ViewGroup.MarginLayoutParams mlp =
                            (android.view.ViewGroup.MarginLayoutParams) lp;
                    mlp.leftMargin = hasSide ? 0 : gap;
                } catch (Throwable ignored) {
                    // 连 MarginLayoutParams 都不是（极罕见），放弃 margin 但不崩
                }
            }
            // ★ v107：已经挂在这个父容器上、陪衬集合也没变 —— 只是尺寸/行高/间距变了
            //   → 【原地改 LayoutParams】，绝不摘了重挂。
            //
            //   为什么必须这样（实机日志 52 实测）：v106 每次"改尺寸"都是
            //   detachGlyphAndSides() + addView()。而 removeView 会让我们的 View
            //   收到 onWindowVisibilityChanged(GONE)、addView 再收到 VISIBLE ——
            //   日志里 windowVis GONE/VISIBLE 成对出现 **2131 次**，
            //   rot: instant remount 峰值 **18 次/秒**、tree-attached 累计 **6214 次**。
            //   摘+挂本身只要 0~5ms，问题在于它会让整条状态栏反复重排、我们的
            //   图标整体闪一下 —— 这就是用户看到的"整个状态栏消失一下再回来"。
            //   原地改 LayoutParams 不触发任何显隐回调，也没有重排风暴。
            final boolean sameParent = glyph.getParent() == parent;
            final boolean sameSides = haveSignal == wantSignal
                    && haveType == wantType && havePerf == wantPerf;
            if (sameParent && sameSides) {
                final int rowHNow = heightBase > 0 ? heightBase : 0;
                boolean changed = applyGlyphLp(w, h, hasSide ? 0 : gap);
                if (wantSignal && sideSignal != null) {
                    final int sigH = clampToRow(
                            TrioRenderer.outSignalHeight(cfg.outSignalSize, density), rowHNow);
                    changed |= applySideLp(sideSignal,
                            TrioRenderer.outSignalWidth(sigH), sigH,
                            (int) (cfg.outSignalMargin * density), 0);
                }
                if (wantType && sideType != null) {
                    final int typeH = clampToRow(
                            TrioRenderer.outTypeHeight(density, app), rowHNow);
                    changed |= applySideLp(sideType,
                            TrioRenderer.outTypeWidth(state.mobileType, density, app), typeH,
                            (int) (cfg.outTypeMarginLeft * density),
                            (int) (cfg.outTypeMarginRight * density));
                }
                if (wantPerf && sidePerf != null) {
                    final float perfBase = TrioRenderer.outTypePx(app.outTypeSize, density);
                    changed |= applySideLp(sidePerf,
                            TrioRenderer.perfWidth("99\u00B0C", perfBase, app),
                            TrioRenderer.perfHeight("99\u00B0C", perfBase, app), 0, 0);
                }
                attachedGlyphScale = cfg.glyphScale;
                attachedEffScale = effectiveGlyphScale();
                attachedRowH = statusBarRoot.getHeight();
                if (changed) {
                    TrioHooks.log(LOG_OK, "overlay: resized in place w=" + w + " h=" + h
                            + " rowH=" + rowHNow + " hBase=" + heightBase
                            + " scale=" + effectiveGlyphScale()
                            + " land=" + isLandscapeNow() + " keyguard=" + keyguardRow
                            + " root=" + rootName
                            + " cw=" + containerW);
                    // 与挂载路径一致：整组边距立刻按新尺寸重算一次（之后 onDraw 每帧复核）。
                    glyph.writeGroupMargins(parent, density, true);
                    glyph.invalidate();
                    if (sideSignal != null) {
                        sideSignal.invalidate();
                    }
                    if (sideType != null) {
                        sideType.invalidate();
                    }
                    if (sidePerf != null) {
                        sidePerf.invalidate();
                    }
                }
                return true;
            }
            // ★ v104：重挂是无动画的硬切换。先把父容器链上的 LayoutTransition 借走
            //   （400ms 后原样还原），否则下面的"摘 + 挂"会被自动装上的
            //   animateLayoutChanges 动画化 = 旋转瞬间肉眼可见的"图标缩小/放大过程"+
            //   掉帧（用户反馈：旋转每次第一次都卡一下）。
            //   拿到的诊断串用于判定"到底是哪几层在动画"，见下面的 lt= 字段。
            final long remountT0 = android.os.SystemClock.uptimeMillis();
            final String ltInfo = beginInstantLayout(parent);
            // 走到这里不会再 return 了：把旧的三个一起摘掉再按新设置重挂。
            detachGlyphAndSides();
            // ★ v59：先把两块陪衬挂上，再挂三合一。
            //   陪衬在前 → 落在三合一**左边**；LinearLayout 会把它们的宽度算进去，
            //   原生图标整体被挤到更左，这正是"占位"的含义。
            //   左→右顺序：类型标签 → 环外读数 → 三合一
            //   （与设置项文案一致：类型的"内侧"朝向环外读数与电池）。
            // ★ v106：陪衬也用同一个基准（旋转当时 = 缓存行高），免得那一帧三合一
            //   已是新尺寸、陪衬还按旧行高钳着。
            final int rowH = heightBase > 0 ? heightBase : 0;
            if (wantSignal) {
                final int sigH = clampToRow(
                        TrioRenderer.outSignalHeight(cfg.outSignalSize, density), rowH);
                sideSignal = new SideView(statusBarRoot.getContext(), SIDE_SIGNAL);
                // ★ v75：outSignalMargin 的语义（见 Prefs 与设置文案）是
                //   "读数与电池之间的空隙" = leftMargin(outer)。v73 误传给
                //   inner(rightMargin)，而每帧复核又只写 leftMargin 且不读 cfg
                //   —— 滑块因此完全没反应（用户实测"环外信号间距调不动"）。
                addSideView(parent, sideSignal, TrioRenderer.outSignalWidth(sigH), sigH,
                        (int) (cfg.outSignalMargin * density), 0);
            }
            if (wantType) {
                final int typeH = clampToRow(
                        TrioRenderer.outTypeHeight(density, app), rowH);
                sideType = new SideView(statusBarRoot.getContext(), SIDE_TYPE);
                addSideView(parent, sideType,
                        TrioRenderer.outTypeWidth(state.mobileType, density, app), typeH,
                        (int) (cfg.outTypeMarginLeft * density),
                        (int) (cfg.outTypeMarginRight * density));
            }
            // ★ v73：功率/温度读数插在三合一**正前方**（index 一定小于三合一、
            //   大于先挂的两块），于是左→右顺序固定为
            //   「环外读数 → 类型标签 → 功率/温度 → 三合一」。
            //   不用"按 addView 先后猜最左端"：注释与代码顺序本来就对不上过，
            //   这里直接用 index 说话（writeGroupMargins 也是按 index 找最左块）。
            //   它外侧不另设设置项 —— 当它是整组最左端时，自动基准
            //   （电池邻居 2dp / 否则 0 + 用户微调）由 writeGroupMargins 给它。
            if (wantPerf) {
                sidePerf = new SideView(statusBarRoot.getContext(), SIDE_PERF);
                // 初始尺寸取"最宽形式"（温度 99°C，3 字符），之后每帧由 onDraw
                // 里的 perfWidth/perfHeight 现算校正 —— 位数变化才动布局。
                // ★ v74 修复：高度也必须一开始就按最宽形式给足（v73 硬编码 0
                //   → 视图 0 高度 → onDraw 不跑 → ensureBox 自愈到不了 →
                //   永久 137x0 不可见）。
                // ★ v75：功率读数删除，只剩温度；数据走 TrioState 既有电池
                //   广播链路（readBattery → readTemperature），不需要取数器。
                final float perfBase = TrioRenderer.outTypePx(app.outTypeSize, density);
                addSideView(parent, sidePerf,
                        TrioRenderer.perfWidth("99\u00B0C", perfBase, app),
                        TrioRenderer.perfHeight("99\u00B0C", perfBase, app),
                        0, 0);
            }
            parent.addView(glyph, lp);
            glyph.requestLayout();
            // ★ v76：记下这次挂载用的尺寸与行高，供下次"要不要重挂"的判定。
            attachedGlyphScale = cfg.glyphScale;
            attachedEffScale = effectiveGlyphScale();
            attachedRowH = statusBarRoot.getHeight();
            // ★ v69：挂载后立刻同步一次整组边距（尤其陪衬那几块的 offset）。
            //   挂载瞬间布局还没跑、左邻居宽度不可知，之后由 onDraw 每帧复核兜住。
            glyph.writeGroupMargins(parent, density, true);
            TrioHooks.log(LOG_OK, "overlay: tree-attached into "
                    + parent.getClass().getSimpleName()
                    + " lp=" + lp.getClass().getSimpleName()
                    + " w=" + w + " h=" + h
                    + " container=" + (containerW > 0 ? containerW : "?")
                    + "x" + (containerH > 0 ? containerH : "?")
                    // ★ v101d：尺寸诊断 —— 核对各挂载点的根高/缩放/是否跳上限（用于排查尺寸问题）
                    + " rootH=" + ((statusBarRoot == null) ? 0 : statusBarRoot.getHeight())
                    // ★ v106：实际用的行高基准（旋转当时那次 = 缓存值，其余 = 实测量）
                    + " hBase=" + lastHeightBase
                    + " scale=" + effectiveGlyphScale()
                    + " overCap=" + overCap
                    + " keyguard=" + keyguardRow
                    + " lpType=" + lp.getClass().getName()
                    + " parentType=" + parent.getClass().getName()
                    + " marginSet=" + lpApplied + " lpMargin=" + readLeftMargin(lp)
                    + " gapPx=" + gap
                    + " offDp=" + (cfg.glyphLeftOffset * GLYPH_OFFSET_UNIT_DP)
                    + " siblings=" + parent.getChildCount()
                    // ★ v104：父容器链上各层的 LayoutTransition 时长（0 = 该层本来就没有）
                    + " lt=" + ltInfo
                    // ★ v104：这次"摘+挂"本身耗了多久（>8ms 就是掉帧级别的主线程开销）
                    + " remountMs=" + (android.os.SystemClock.uptimeMillis() - remountT0)
                    // ★ v59：环外陪衬的实际尺寸，一眼能核对"挂上了没、多大"
                    + " sideSignal=" + sideInfo(sideSignal)
                    + " sideType=" + sideInfo(sideType)
                    + " sidePerf=" + sideInfo(sidePerf));
            // ★ v65：把所在行的每个子 View 几何打出来。纯只读。
            //   起因：用户反馈"和原生信号图标之间还有间距、缩小边距没变化"。
            //   想减少间距必须先知道那 12dp 到底是谁占的（我们的 margin？原生图标
            //   View 自己的内边距？还是中间夹着一个 INVISIBLE 但占位的 View？）。
            //   靠截图猜了三轮了，这次直接量。
            dumpRowGeometry(parent);
            // ★ v107b：挂载那一刻容器还没按最终布局量完（日志 53 实测状态栏
            //   container=84x107 → 宽度兜底把图标压成 105px，稳定后 202x90 → 应是 124px）。
            //   ★ v108：复核走"之后几帧"（armSettle），另加一次延时兜底
            //   （万一这几帧都没绘制：息屏/面板收起）。
            armSettle();
            MAIN.removeCallbacks(settleFallback);
            MAIN.postDelayed(settleFallback, ROT_SETTLE_MS);
            return true;
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: tree attach failed: " + t);
            return false;
        }
    }

    /**
     * ★ v59：把三合一和两块陪衬一起从父容器摘掉。
     *
     * <p>重挂之前必须先摘：一是避免同一个 View 被重复 addView（父不为空会被忽略、
     * 顺序也乱），二是在设置里改了位置开关后，旧的陪衬必须消失。
     */
    private void detachGlyphAndSides() {
        removeFromParent(glyph);
        removeFromParent(sideSignal);
        removeFromParent(sideType);
        removeFromParent(sidePerf);
        sideSignal = null;
        sideType = null;
        // ★ v78：sidePerf 之前漏了置空 —— 关掉温度后重挂时它仍是上一次那个
        //   （已摘除、parent 为 null），而 computeSideBoxH 只看 "!= null"，
        //   于是统一盒高会被一块根本不存在的陪衬顶高 → 5G/温度底边对不齐。
        sidePerf = null;
    }

    /**
     * ★ v104：重挂前临时关掉父容器链上的 {@link android.animation.LayoutTransition}。
     *
     * <p><b>为什么需要它</b>：旋转（或拖「整体尺寸」滑块）时有效尺寸变了 ⇒
     * {@code tryAttachIntoIconTree} 走"摘掉旧的 → 按新尺寸再 addView"这条路。
     * 而 SystemUI 的状态栏布局（{@code system_icons} / {@code system_icon_area} /
     * 外壳）常带 {@code android:animateLayoutChanges="true"}，inflate 时会自动装上
     * 一套默认 LayoutTransition（进出场 300ms 缩放+淡入淡出，相邻 View 位移也动画）。
     * 于是"摘 = 出场动画（图标缩小淡出）"+"挂 = 入场动画（图标放大淡入）"，
     * 用户看到的就是旋转瞬间"图标放大/缩小过程卡一下"。
     *
     * <p>这里只临时置 null（不是永久剥夺），并在 {@value #LT_SUPPRESS_MS}ms 后
     * 由 {@link #endInstantLayout()} 原样还原 —— 之后原生图标该动画照旧动画。
     *
     * @return 诊断串，形如 {@code |system_icons:300|LinearLayout:0}，
     *         数字 = 该层入场动画时长（0 表示该层本来就没有 LayoutTransition）。
     */
    private static String beginInstantLayout(ViewGroup from) {
        final StringBuilder sb = new StringBuilder();
        if (from == null) {
            return sb.toString();
        }
        try {
            synchronized (LT_LOCK) {
                android.view.ViewParent p = from;
                for (int i = 0; i < LT_DEPTH && (p instanceof ViewGroup); i++) {
                    final ViewGroup g = (ViewGroup) p;
                    final String name = g.getClass().getSimpleName();
                    if (sLtSaved.containsKey(g)) {
                        // 上一轮还在抑制窗口内：它已被管着，别再记一次（否则还原会写成 null）
                        sb.append('|').append(name).append(":sup");
                    } else {
                        final android.animation.LayoutTransition lt = g.getLayoutTransition();
                        sLtSaved.put(g, lt);
                        if (lt != null) {
                            g.setLayoutTransition(null);
                            sb.append('|').append(name).append(':')
                                    .append(lt.getDuration(
                                            android.animation.LayoutTransition.APPEARING));
                        } else {
                            sb.append('|').append(name).append(":0");
                        }
                    }
                    p = g.getParent();
                }
                if (!sLtArmed) {
                    sLtArmed = true;
                    MAIN.postDelayed(() -> endInstantLayout(), LT_SUPPRESS_MS);
                }
            }
        } catch (Throwable ignored) {
            // 关不掉也不影响挂载，最多还是会被动画化
        }
        return sb.toString();
    }

    /** ★ v104：{@link #beginInstantLayout} 的收尾 —— 把借走的 LayoutTransition 还回去。 */
    private static void endInstantLayout() {
        try {
            synchronized (LT_LOCK) {
                for (java.util.Map.Entry<ViewGroup, android.animation.LayoutTransition> e
                        : sLtSaved.entrySet()) {
                    e.getKey().setLayoutTransition(e.getValue());
                }
                sLtSaved.clear();
                sLtArmed = false;
            }
        } catch (Throwable ignored) {
            // 还原失败 = 那几层永久没有进出场动画，不致命
        }
    }

    /**
     * ★ v65：一次性打印所在行的子 View 几何（**纯只读**，不碰任何布局）。
     *
     * <p><b>为什么加它</b>：用户反馈"三合一和手机自带信号图标之间还有间距、
     * 把左边距调小也没变化"。仅凭截图无法判断那截空白是谁占的，有三种可能：
     * <ol>
     *   <li>我们自己的 {@code leftMargin}（可控）</li>
     *   <li>原生图标 View 自身的右侧内边距 —— 图标 View 往往比它的墨迹宽（不可控）</li>
     *   <li>中间夹着一个 {@code INVISIBLE}（占位但不绘制）或宽度非 0 的空 View</li>
     * </ol>
     * 这条日志给出每个子 View 的 left/right/width/visibility/margin，
     * 以及"相对上一个可见子 View 右边缘的空档"，
     * 一眼就能算出空档到底是多少、是谁占的 —— 不再靠截图猜。
     *
     * <p>⚠️ 前缀 {@link #GEOM_TAG} 必须同步加进
     * {@code SamsungDuoModule.isCritical()} 白名单，否则会被 100ms 日志限流整段吃掉
     * （这个坑踩过一次：v47 的 dump 代码正确却被限流吞了，白跑一轮装机）。
     */
    private void dumpRowGeometry(final ViewGroup parent) {
        if (parent == null || rowGeomLogged) {
            return;
        }
        rowGeomLogged = true;
        try {
            MAIN.postDelayed(() -> {
                try {
                    TrioHooks.log(TrioHooks.LOG_INFO, GEOM_TAG + "row "
                            + parent.getClass().getSimpleName()
                            + " w=" + parent.getWidth() + " h=" + parent.getHeight()
                            + " padL=" + parent.getPaddingLeft()
                            + " padR=" + parent.getPaddingRight()
                            + " n=" + parent.getChildCount()
                            + " density=" + parent.getResources().getDisplayMetrics().density);
                    int prevVisibleRight = -1;
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        final View c = parent.getChildAt(i);
                        if (c == null) {
                            continue;
                        }
                        final ViewGroup.LayoutParams clp = c.getLayoutParams();
                        String marg = "-";
                        if (clp instanceof ViewGroup.MarginLayoutParams) {
                            final ViewGroup.MarginLayoutParams m =
                                    (ViewGroup.MarginLayoutParams) clp;
                            marg = m.leftMargin + "/" + m.rightMargin;
                        }
                        final int gap = (prevVisibleRight < 0) ? -1 : (c.getLeft() - prevVisibleRight);
                        TrioHooks.log(TrioHooks.LOG_INFO, GEOM_TAG + "  [" + i + "] "
                                + c.getClass().getSimpleName()
                                + (c == glyph ? "<==OURS" : "")
                                + " id=" + idNameOf(c)
                                + " l=" + c.getLeft() + " r=" + c.getRight()
                                + " w=" + c.getWidth()
                                + " vis=" + visNameOf(c.getVisibility())
                                + " m=" + marg
                                + " gapFromPrev=" + gap);
                        if (c.getVisibility() == View.VISIBLE) {
                            prevVisibleRight = c.getRight();
                        }
                    }
                } catch (Throwable t) {
                    TrioHooks.log(TrioHooks.LOG_WARN, GEOM_TAG + "row dump failed: " + t);
                }
            }, 800L);
        } catch (Throwable ignored) {
            // 诊断绝不能影响系统
        }
    }

    /**
     * ★ v66：三合一左边留多少 —— 由"左边那个**有宽度**的兄弟是谁"决定。
     *
     * <p><b>为什么需要区分</b>（v65 的 `geometry ` dump + 用户截图双重实证）：
     * 这一行的所有原生子 View 都是 {@code margin=0/0}、容器 {@code padding} 也是 0，
     * 而且行宽 = 各子宽度之和、一分不多（{@code 246 = 0+67+84+95}）。
     * 也就是说 **原生图标之间的"间距"完全画在各自 drawable 内部**，不是布局间距。
     * 我们的三合一是外来户，若再叠一段 margin，就成了"原生画内留白 + 我们的 margin"
     * —— 看起来双倍（用户自己推断出来的，与 dump 完全吻合）。
     *
     * <p>所以规则只有两条：
     * <ul>
     *   <li>左邻居是<b>电池</b> → <b>2dp</b>（v68：回到 2dp，用户要收紧的是
     *       "电池隐藏时"那一档，不是这一档）。电池 drawable 基本画满它 84px 的 View
     *       （墨迹 84~91px，反推自用户 shade 截图），**右侧几乎没有留白**，
     *       一点不加会贴住。</li>
     *   <li>其它（含电池被隐藏时）→ <b>0</b>。只靠彼此的画内留白分隔，
     *       与原生同一套规则 —— 用户实测这一档"舒服"，是定稿行为。</li>
     * </ul>
     *
     * <p>⚠️ <b>"隐藏电池"在这台机器上是把宽度置 0，visibility 仍是 VISIBLE</b>
     * （dump 实证），所以这里必须按 {@code getWidth() > 0} 判定，看可见性会判错。
     * 电池被隐藏时它宽度为 0 → 跳过它 → 实际左邻居是信号容器 → 返回 0，
     * 与用户已验证满意的状态**完全一致**（这条改动对当前配置是零影响的）。
     */
    private int gapBeforeLeftNeighbor(ViewGroup parent, float density) {
        try {
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                final View c = parent.getChildAt(i);
                if (c == null || c.getWidth() <= 0) {
                    continue;               // 宽度 0 = 被隐藏，等于不占位
                }
                if (c == glyph || c == sideSignal || c == sideType || c == sidePerf) {
                    continue;               // 我们自己的三块，跳过（重挂前可能还在树上）
                }
                return needsGapBefore(c) ? (int) (2f * density) : 0;
            }
        } catch (Throwable ignored) {
            // 判定失败一律退回 0（= 与原生同规则，最安全）
        }
        return 0;
    }

    /**
     * 左邻居是这两类时三合一要额外让出 2dp：<b>电池</b>与<b>网速</b>。
     *
     * <p><b>为什么是这两类</b>：它们的 drawable / 文字几乎画满自己那个 View，
     * <b>右侧没有留白</b>（电池墨迹 84~91px 对 84px 的 View，反推自用户 shade 截图；
     * 网速是 TextView，字宽即内容宽）。其余原生图标的间距是画在 drawable 内部的
     * 透明留白（`geometry ` dump 实证：容器 padding=0、所有子 View margin=0、
     * 行宽严格等于子宽之和），再叠加我们的 margin 就成"双倍"，所以只有这两类需要补。
     */
    private static boolean needsGapBefore(View v) {
        return isBatteryView(v) || isNetspeedView(v);
    }

    /**
     * ★ v68：三合一左边距的最终值 = 基准（见 {@link #gapBeforeLeftNeighbor}）+ 用户微调。
     *
     * <p><b>为什么需要微调</b>：电池显示时基准 2dp、电池隐藏时基准 0 ——
     * 而隐藏时那 0dp 已经是"margin 到底"了，剩下的间距来自<b>原生图标 drawable 内部的
     * 透明留白</b>（`geometry ` dump 实证：容器 padding=0、所有子 View margin=0、
     * 行宽严格等于子宽之和），从布局层面根本收不掉。用户还想再收紧时，
     * 只剩"给负 margin 反向吃掉那截留白"一条路 —— 而该吃多少取决于原生留白的实际宽度
     * （量出来才知道），猜不准，所以做成可调，把决定权交回用户手上。
     *
     * <p>微调<b>每帧</b>读取（调用方 {@code ensureLeftMargin} 在 {@code onDraw} 里跑），
     * 因此改设置<b>立即生效</b>：不必重新挂载，也不必重启 SystemUI。
     */
    private int leftGapPx(ViewGroup parent, float density) {
        return gapBeforeLeftNeighbor(parent, density) + groupOffsetPx(density);
    }

    /**
     * ★ v82：整组左侧"额外量" = 「整组左侧间距」−「整组左右平移」。
     *
     * <p>平移量走两条路（见 {@link Prefs#MIN_GLYPH_SHIFT}）：margin 这条负责把
     * 原生图标推走/放回，translationX 那条负责平移我们自己。两条路必须用同一个量，
     * 组内各块之间的相对距离才不变 —— 看起来才是"整组在动"，而不是某一方在动。
     *
     * <p>符号：平移为负（往左）时 margin 要<b>加</b>（把原生图标推向左）；
     * 平移为正（往右）时 margin 要<b>减</b>（原生图标回位、让出地方）。
     */
    private int groupOffsetPx(float density) {
        return glyphOffsetPx(density) - glyphShiftPx(density);
    }

    /** ★ v82：整组左右平移量换算成像素；配置读不到时退回 0（= 不动）。
     *  ★ v82fix：锁屏不应用左右平移 → 直接退回 0，间距（glyphOffsetPx）照常保留。 */
    private int glyphShiftPx(float density) {
        try {
            return keyguardRow ? 0 : (int) (TrioConfig.get().glyphShift * density);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 左侧间距微调的存储单位：设置项 {@link TrioSettings#glyphLeftOffset} 存的是
     * <b>半 dp 的整数倍</b>，所以滑块走一格 = 0.5dp（用户要的精度比 1dp 更细一档）。
     */
    private static final float GLYPH_OFFSET_UNIT_DP = 0.5f;

    /** 用户微调量换算成像素；配置读不到时退回 0（= 不动），绝不影响挂载。 */
    private static int glyphOffsetPx(float density) {
        try {
            return (int) (TrioConfig.get().glyphLeftOffset * GLYPH_OFFSET_UNIT_DP * density);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 判断某个子 View 是不是原生电池图标（按类名或资源 id 名）。 */
    private static boolean isBatteryView(View v) {
        try {
            final String cn = v.getClass().getSimpleName();
            if (cn != null && cn.startsWith("Battery")) {
                return true;
            }
            final String idn = idNameOf(v);
            return "battery".equals(idn) || "batteryRemainingIcon".equals(idn);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 判断某个子 View 是不是原生<b>网速</b>指示（按类名或资源 id 名）。
     *
     * <p>v72 新增：用户要求"左边是网速也按电池那条规则加 2dp"。
     * 判据按三星实机 dump 来的两条实证写死：`geometry ` 里它长这样 ——
     * {@code [0] NetspeedView id=networkSpeed w=0}，即类名 {@code NetspeedView}、
     * id 名 {@code networkSpeed}。两者都判是因为不同挂载点可能只有其中一条成立。
     *
     * <p>⚠️ 不要只判 {@code contains("netspeed")}：id 名是 {@code networkSpeed}，
     * 转小写后是 {@code networkspeed}，并不包含 {@code netspeed}。
     * 同理类名是 {@code NetspeedView} 而不是 {@code NetworkSpeedView}。
     */
    private static boolean isNetspeedView(View v) {
        try {
            final String cn = v.getClass().getSimpleName();
            if (cn != null) {
                final String lc = cn.toLowerCase(java.util.Locale.US);
                if (lc.contains("netspeed") || lc.contains("networkspeed")) {
                    return true;
                }
            }
            final String idn = idNameOf(v);
            if (idn != null) {
                final String lc = idn.toLowerCase(java.util.Locale.US);
                if (lc.contains("netspeed") || lc.contains("networkspeed")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    /**
     * ★ v72：防烧屏（burn-in protection）—— 让三合一与两块陪衬在极小范围内缓慢游走。
     *
     * <p><b>为什么必须自己做</b>：反编译三星 SystemUI 确认它<b>确实有一整套</b>防烧屏
     * （{@code AodBurnInLayer}、{@code BurnInInteractor}、
     * {@code DreamOverlayContainerViewController.updateBurnInLayer()}，
     * 连电源菜单都有 {@code GlobalActionsDialogLite.rescheduleBurninTimeout()}），
     * 实现方式就是对一个承载层做 {@code setTranslationX/Y}。
     * <b>但它的适用范围里没有状态栏</b>：
     * {@code grep "statusbar...Burn" classes*.dex} 返回空 ——
     * 原生只管 AOD / 锁屏 / 息屏 / 电源菜单。而状态栏图标恰恰是"常年不动、
     * 高对比度、位置固定"的典型残影来源，所以这一段只能自己加，没有原生参数可抄。
     *
     * <p><b>为什么用 translation 而不是改 margin</b>：margin 会触发重新布局，
     * 把原生图标推来推去（每秒一次的重排还是实打实的开销）；
     * {@code setTranslationX/Y} 只影响绘制、<b>不触发 requestLayout</b>，
     * 与三星 {@code AodBurnInLayer} 的做法一致。
     *
     * <p><b>轨迹</b>：X 与 Y 各走一条正弦，周期取 {@code period} 与 {@code period * 0.75}
     * （非整数比），合成出利萨如曲线 —— <b>不会周期性精确回到同一点</b>，
     * 否则轨迹的交点会重新烧出痕迹。默认 0.5dp（本机约 1.7px）/ 60s：
     * 肉眼基本看不出，但足够打散像素老化。
     */
    private static final float BURN_IN_UNIT_DP = 0.25f;

    /** 算出当前时刻的防烧屏位移（px），写入 {@code out[0]=x, out[1]=y}。 */
    private static void burnInOffset(long nowMs, float amplitudePx, float periodSec, float[] out) {
        final double tx = Math.max(1.0, periodSec) * 1000.0;
        final double ty = tx * 0.75;               // 非整数比 → 轨迹不成闭环
        out[0] = (float) (amplitudePx * Math.sin(2.0 * Math.PI * nowMs / tx));
        out[1] = (float) (amplitudePx * Math.sin(2.0 * Math.PI * nowMs / ty + Math.PI / 3.0));
    }

    /**
     * 把防烧屏位移写到一个 View 上（关闭时归零）。
     *
     * <p>只在位移变化 ≥ 0.05px 时才真正写：{@code setTranslationX} 会请求重绘，
     * 稳态下不写就不会凭空多出一轮绘制。{@code last} 是<b>每个 View 各自</b>的
     * 上次位移，调用方持有。
     */
    private void applyBurnIn(View v, float[] tmp, float[] last) {
        try {
            final TrioSettings cfg = TrioConfig.get();
            // ★ v82：整组左右平移是位移的"基准"，防烧屏在它之上叠加。
            //   两条路共用一个量（margin 那条见 groupOffsetPx），所以这里必须
            //   先取基准再叠加游走，否则平移会被下一次防烧屏写入整个覆盖掉。
            final float base = shiftBaseFor(v);
            if (!cfg.burnInProtect || cfg.burnInAmplitude <= 0) {
                if (last[0] != 0f || last[1] != 0f
                        || Math.abs(v.getTranslationX() - base) >= 0.05f) {
                    v.setTranslationX(base);
                    v.setTranslationY(0f);
                    last[0] = 0f;
                    last[1] = 0f;
                }
                return;
            }
            final float density = v.getResources().getDisplayMetrics().density;
            burnInOffset(android.os.SystemClock.elapsedRealtime(),
                    cfg.burnInAmplitude * BURN_IN_UNIT_DP * density,
                    cfg.burnInPeriodSec, tmp);
            if (Math.abs(tmp[0] - last[0]) >= 0.05f || Math.abs(tmp[1] - last[1]) >= 0.05f) {
                v.setTranslationX(base + tmp[0]);
                v.setTranslationY(tmp[1]);
                last[0] = tmp[0];
                last[1] = tmp[1];
            } else if (Math.abs(v.getTranslationX() - (base + last[0])) >= 0.05f) {
                // 防烧屏没动但平移量变了（用户刚拖了滑块）→ 只补基准
                v.setTranslationX(base + last[0]);
            }
        } catch (Throwable ignored) {
            // 防烧屏是锦上添花，任何异常都不许影响绘制
        }
    }

    /**
     * ★ v82：这块 View 的平移基准。
     *
     * <p>挂树模式才用 translationX（窗口边界不管 translation，压出去会被裁）；
     * 窗口模式反过来 —— translation 会把内容推出窗口自己的 Surface（同样被裁），
     * 所以那边由 {@link #sync()} 直接改 {@code params.x} 挪整个窗口，这里必须给 0，
     * 否则同一条设置会被应用两遍。
     */
    private float shiftBaseFor(View v) {
        if (!treeAttached) {
            return 0f;
        }
        return glyphShiftPxF(v);
    }

    /** ★ v82：平移基准（像素）。防烧屏关掉时循环会停（v74 省电），所以每条绘制路径都要自己能补上。
     *  ★ v82fix：锁屏不应用左右平移 → 退回 0（translationX 与窗口模式两条路都经此取值）。 */
    private float glyphShiftPxF(View v) {
        try {
            return keyguardRow ? 0f
                    : TrioConfig.get().glyphShift
                    * v.getResources().getDisplayMetrics().density;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    /**
     * ★ v82：把平移基准同步到整组四块 View 上（在防烧屏游走值之上叠加）。
     *
     * <p>为什么放在 {@code onDraw}：防烧屏关着时 {@code mFrame} 循环是停的
     * （v74 省电），{@code applyBurnIn} 根本不会被调用；而平移设置随时会变，
     * 又不能为它单独养一个每秒循环。绘制是唯一"每条路径都会经过"的时刻，
     * 且只在值真变时写回，稳态下零开销。
     */
    private void syncGroupShift() {
        try {
            if (glyph == null) {
                return;
            }
            final float base = shiftBaseFor(glyph);
            applyShift(glyph, glyph.burnLast, base);
            if (sideSignal != null) {
                applyShift(sideSignal, sideSignal.burnLast, base);
            }
            if (sideType != null) {
                applyShift(sideType, sideType.burnLast, base);
            }
            if (sidePerf != null) {
                applyShift(sidePerf, sidePerf.burnLast, base);
            }
        } catch (Throwable ignored) {
            // 平移是锦上添花，绝不影响绘制
        }
    }

    /** ★ v82：单块 View 的平移 = 基准 + 防烧屏游走值（last 里存的是纯游走值）。 */
    private static void applyShift(View v, float[] burnLast, float base) {
        try {
            final float burnX = (burnLast == null) ? 0f : burnLast[0];
            final float burnY = (burnLast == null) ? 0f : burnLast[1];
            if (Math.abs(v.getTranslationX() - (base + burnX)) >= 0.05f) {
                v.setTranslationX(base + burnX);
            }
            if (Math.abs(v.getTranslationY() - burnY) >= 0.05f) {
                v.setTranslationY(burnY);
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    /** 资源 id 名（拿不到就返回 "?"），仅用于几何诊断。 */
    private static String idNameOf(View v) {
        try {
            final int id = v.getId();
            if (id == View.NO_ID) {
                return "none";
            }
            return v.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 可见性缩写：V=VISIBLE / I=INVISIBLE / G=GONE。 */
    private static String visNameOf(int visibility) {
        if (visibility == View.VISIBLE) {
            return "V";
        }
        if (visibility == View.INVISIBLE) {
            return "I";
        }
        return "G";
    }

    /** 陪衬的实际尺寸，供挂载日志核对（"挂上了没、多大"）。 */
    private static String sideInfo(View v) {
        if (v == null) {
            return "-";
        }
        final android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) {
            return "?";
        }
        return lp.width + "x" + lp.height;
    }

    private static void removeFromParent(View v) {
        try {
            if (v != null && v.getParent() instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
        } catch (Throwable ignored) {
            // 摘除失败不影响后续（下次 addView 到同父会被忽略，但不会崩）
        }
    }

    /** 陪衬的高度上限 = 所在行高（行就是会裁剪它的边界），与三合一同一套规则。 */
    private static int clampToRow(int h, int rowH) {
        if (rowH <= 0) {
            return Math.max(0, h);
        }
        return Math.max(1, Math.min(h, (int) (rowH * HEIGHT_RATIO)));
    }

    /**
     * ★ v76：三块环外陪衬的<b>统一盒高</b> = 在场者各自需要高度的 max。
     *
     * <p>用户要求"5G/温度底边对齐信号条下底边"。三块都是 CENTER_VERTICAL 挂进
     * 同一行，CENTER 下<b>底边差 = (盒高₁ − 盒高₂)/2</b> —— 盒子高度不同（信号条按
     * outSignalSize、类型按 outTypeSize、温度按文字行高）就永远对不齐（实测差 12px）。
     * 所以先把盒高统一，再让绘制各自贴盒底（见 TrioRenderer 的 baseline 改动）。
     *
     * <p>每帧现算而不是挂载快照：用户改 size 后要立即生效（v69~v74 快照的教训）。
     * 幂等无状态，三块 onDraw 各调一次得到同一个 max，无跨 View 时序问题。
     * 取 max 而不是某一个，保证谁都不被自己的盒子裁掉。
     */
    private int computeSideBoxH() {
        final TrioSettings cfg = TrioConfig.get();
        final TrioAppearance app = TrioAppearance.of(cfg);
        if (statusBarRoot == null) {
            return 0;
        }
        final float density =
                statusBarRoot.getResources().getDisplayMetrics().density;
        final int rowH = statusBarRoot.getHeight();
        int need = 0;
        if (sideSignal != null) {
            need = Math.max(need,
                    clampToRow(TrioRenderer.outSignalHeight(cfg.outSignalSize, density), rowH));
        }
        if (sideType != null) {
            need = Math.max(need,
                    clampToRow(TrioRenderer.outTypeHeight(density, app), rowH));
        }
        if (sidePerf != null) {
            final String txt = TrioRenderer.perfText(cfg.perfTemp, TrioState.sBatteryTempC);
            if (!txt.isEmpty()) {
                final float base = TrioRenderer.outTypePx(app.outTypeSize, density);
                final int gw = (glyph == null) ? 0 : glyph.getWidth();
                final int gh = (glyph == null) ? 0 : glyph.getHeight();
                final float sz = TrioRenderer.perfTextSize(txt, gw, gh, base, app);
                need = Math.max(need, TrioRenderer.perfHeight(txt, sz, app));
            }
        }
        return need;
    }

    /**
     * ★ v76：信号条<b>底边</b>（baseline）在统一盒高盒子里的 y 坐标 —— 5G 与温度
     * 的文字底边对齐到这里。
     *
     * <p><b>信号条自身绘制位置不动</b>（用户明确要求保持原位），只把文字挪过去。
     * 公式与 {@link TrioRenderer#drawOutSignal} 内部同一套：单卡 inkH=150
     * （条底落在 0.859·boxH），双卡 inkH=209（条底落在 0.717·boxH），按当前
     * 卡数现算。scale 取 boxH/209：盒宽已按 boxH·251/209 预留，两者一致。
     */
    private float computeSideBaseline(int boxH) {
        if (boxH <= 0) {
            return 0f;
        }
        final TrioSettings cfg = TrioConfig.get();
        final float s = boxH / (float) TrioGeometry.STACK_INK_H;
        final int[] lv = new int[2];
        TrioRenderer.outSignalLevels(cfg.dataSimOnly, state.mobileLevel,
                state.slotLevels, state.dataSlot, lv);
        final float inkH = (lv[1] >= 0)
                ? TrioGeometry.STACK_INK_H
                : TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1];
        return (boxH - inkH * s) / 2f
                + TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1] * s;
    }

    /**
     * 把一块陪衬挂进容器。
     *
     * @param outer 朝外一侧的边距（背对三合一的那侧）
     * @param inner 朝内一侧的边距（朝着三合一的那侧）
     *
     * <p>RTL 下两侧互换：这样"外侧/内侧"在镜像语言里仍然指向同一对邻居，
     * 与设置项里写的"RTL 下整行镜像，这条边距会跟着翻到另一侧"一致。
     */
    private void addSideView(ViewGroup parent, View v, int w, int h, int outer, int inner) {
        try {
            LayoutParams lp = cloneExistingLayoutParams(parent);
            if (lp == null) {
                lp = createLayoutParamsFor(parent);
            }
            if (lp == null) {
                return;
            }
            lp.width = Math.max(0, w);
            lp.height = Math.max(0, h);
            final boolean rtl =
                    parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final int start = rtl ? inner : outer;
            final int end = rtl ? outer : inner;
            boolean applied = false;
            if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                final android.widget.LinearLayout.LayoutParams llp =
                        (android.widget.LinearLayout.LayoutParams) lp;
                llp.gravity = android.view.Gravity.CENTER_VERTICAL;
                llp.weight = 0f;
                llp.leftMargin = start;
                llp.rightMargin = end;
                applied = true;
            }
            if (!applied) {
                try {
                    final android.view.ViewGroup.MarginLayoutParams mlp =
                            (android.view.ViewGroup.MarginLayoutParams) lp;
                    mlp.leftMargin = start;
                    mlp.rightMargin = end;
                } catch (Throwable ignored) {
                    // 极罕见：连 MarginLayoutParams 都不是
                }
            }
            parent.addView(v, lp);
            if (v instanceof SideView) {
                // ★ v75：只记"已写入值"供每帧去重；基准不再快照，每帧从 cfg 现读。
                ((SideView) v).lastOuterPx = start;
                ((SideView) v).lastRightPx = end;
            }
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: side attach failed: " + t);
        }
    }

    /**
     * ★ v107：原地改三合一自己的 LayoutParams（**不摘不挂**）。
     *
     * <p>用途：尺寸/行高/间距变了、但挂载位置与陪衬集合都没变时，用它代替
     * 「detach + addView」。addView/removeView 会让 View 收到
     * {@code onWindowVisibilityChanged}（GONE→VISIBLE）并强制整条状态栏重排，
     * 日志 52 实测成对出现 2131 次；改 LayoutParams 两者都不会发生。
     *
     * @return 是否真的改了（没改就不必重绘）
     */
    /**
     * 重新打开整条祖先链的裁剪（clipChildren + clipToPadding 都设 false）。
     * 只在应用尺寸时调用；若发现标志被别人关回去了，打一次日志。
     */
    private void reassertClipOpen() {
        try {
            // ★ 限频：60fps 下每 3 帧查一次（≈20 次/秒）。实测对方**每分钟整点**才设一次裁剪
            //   （间隔 56~64 秒），正常情况下这里几乎全是"读了发现没被开、直接返回"，
            //   真正的写入每分钟只有几次；提到 3 帧是为了在漏拦时把可见窗口压到 ~50ms。
            if ((drawTick++ & 0x0B) != 0) {
                return;
            }
            final android.view.ViewParent start = glyph.getParent();
            if (!(start instanceof View)) {
                return;
            }
            android.view.View v = (android.view.View) start;
            int guard = 0;
            boolean flipped = false;
            boolean flippedOutline = false;
            while (v instanceof ViewGroup && guard++ < 12) {
                final ViewGroup vg = (ViewGroup) v;
                if (vg.getClipChildren() || vg.getClipToPadding()) {
                    flipped = true;
                }
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
                // ★ 第三种裁剪机制：**轮廓裁剪**（clipToOutline）—— 这一条**只为
                //   OneuiXP 而设**（实测停用 OneuiXP 不裁、启用就裁，而前两项都没被
                //   改过，OneuiXP 的 dex 里正好有 setOutlineProvider/setClipToOutline）。
                //   用户 2026-10-22 弃用 OneuiXP ⇒ 这段**已停用**；将来要用，
                //   把下面四行加回来即可（boolCallCached / setClipToOutlineCached
                //   两个辅助方法仍在，R8 会在停用期间自动剔除）。
                // if (Boolean.TRUE.equals(boolCallCached(vg))) {
                //     flippedOutline = true;
                // }
                // setClipToOutlineCached(vg, false);
                final android.view.ViewParent up = vg.getParent();
                if (!(up instanceof android.view.View)) {
                    break;
                }
                v = (android.view.View) up;
            }
            if (flipped || flippedOutline) {
                    // ★ 计频：别的模块恢复裁剪的频率，决定我们限频该取多少。
                    //   前 12 次逐条打，之后每 12 条打一次，带与上次的间隔。
                    final long now = android.os.SystemClock.uptimeMillis();
                    final long gap = (lastClipResetMs == 0) ? -1 : (now - lastClipResetMs);
                    lastClipResetMs = now;
                    clipResetCount++;
                    if (clipResetCount <= 12 || (clipResetCount % 12) == 0) {
                        TrioHooks.log(LOG_OK, "clip reset #" + clipResetCount
                                + " gap=" + (gap < 0 ? "first" : gap + "ms")
                                + " via=" + (flipped ? "children/padding" : "outline")
                                + " root=" + v.getClass().getSimpleName());
                    }
                }
                if (flippedOutline && !outlineConflictLogged) {
                    outlineConflictLogged = true;
                    TrioHooks.log(LOG_OK, "overlay: clipToOutline was ON (OutlineProvider "
                            + "clipping) -> set false, root=" + v.getClass().getSimpleName()
                        + "  <== 若裁剪随之消失，真凶就是它");
            }
            // ★ 实测"行高/行布局是不是被别的模块动了"（哪怕它的功能开关是关的）：
            //   记录行 LayoutParams 的 height 与 gravity 的变化，变了就打一行。
            //   与我们自己的 `resized in place` 对照时间，就能分辨是谁改的。
            try {
                final android.view.ViewParent par = glyph.getParent();
                if (par instanceof android.view.View) {
                    final android.view.View p0 = (android.view.View) par;
                    final android.view.ViewGroup.LayoutParams lp = ((android.view.View) p0).getLayoutParams();
                    if (lp != null) {
                        final int h0 = lp.height;
                        int g0 = -1;
                        if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                            g0 = ((android.widget.LinearLayout.LayoutParams) lp).gravity;
                        } else if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                            g0 = ((android.widget.FrameLayout.LayoutParams) lp).gravity;
                        }
                        if (h0 != lastSeenRowLpH || g0 != lastSeenRowGravity) {
                            TrioHooks.log(LOG_OK, "row-lp: height " + lastSeenRowLpH + " -> " + h0
                                    + ", gravity " + lastSeenRowGravity + " -> " + g0
                                    + " (row=" + p0.getClass().getSimpleName() + ")");
                            lastSeenRowLpH = h0;
                            lastSeenRowGravity = g0;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /**
 * ★ L20 配套：判断某个 View 是否在**任意一个三合一的祖先链**上（含窗口根）。
     * 是的话就不允许把它的裁剪打开。
     */
    /** 窗口根重建后我们把它的裁剪关掉的计数日志。 */
    static void noteWindowClipFixed(View v) {
        try {
            windowClipFixedCount++;
            if (windowClipFixedCount <= 12 || (windowClipFixedCount % 12) == 0) {
                TrioHooks.log(LOG_OK, "window-clip-fixed #" + windowClipFixedCount
                        + " on=" + ((v == null) ? "?" : v.getClass().getSimpleName())
                        + " (clipChildren/clipToPadding=false)");
            }
        } catch (Throwable ignored) {
        }
    }

    private static int windowClipFixedCount = 0;

    static boolean shouldBlockClip(View v) {
        if (v == null) {
            return false;
        }
        try {
            // ① 祖先链判定（三合一已挂载时最准）
            for (final TrioOverlay inst : sLiveMap.values()) {
                if (inst == null) {
                    continue;
                }
                final View g = inst.glyph;
                if (g != null && isAncestorOrSelf(v, g)) {
                    return true;
                }
                // ② root 也算：刚重建/切锁屏那一瞬 glyph 可能还没挂上，
                //    但 statusBarRoot 已经在树上，用它兜住。
                final View r = inst.statusBarRoot;
                if (r != null && isAncestorOrSelf(v, r)) {
                    return true;
                }
            }
            // ③ 类名兜底：状态栏窗口根。实测对方每分钟在这里也开一次裁剪，
            //    而那一瞬未必有实例挂载，祖先链判不到。
            final String n = v.getClass().getName();
            if (n.contains("StatusBarWindowView") || n.contains("PhoneStatusBarView")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** v 是否为 target 自身或其祖先。 */
    private static boolean isAncestorOrSelf(View v, View target) {
        View p = target;
        for (int i = 0; p != null && i < 24; i++,
                p = (p.getParent() instanceof View) ? (View) p.getParent() : null) {
            if (p == v) {
                return true;
            }
        }
        return false;
    }

    /** 被别的模块试图打开裁剪时的计数日志（前 12 次逐条，之后每 12 条一条）。 */
    static void noteClipBlocked(View v, String via) {
        try {
            clipBlockedCount++;
            if (clipBlockedCount <= 12 || (clipBlockedCount % 12) == 0) {
                TrioHooks.log(LOG_OK, "clip-blocked #" + clipBlockedCount
                        + " via=" + via
                        + " on=" + ((v == null) ? "?" : v.getClass().getSimpleName())
                        + " (forced false)");
            }
        } catch (Throwable ignored) {
        }
    }

    private static int clipBlockedCount = 0;

    private boolean clipConflictLogged = false;
    private int clipResetCount = 0;
    private long lastClipResetMs = 0;
    private int drawTick = 0;
    /** ★ 静态缓存反射 Method：每帧调用，避免反复查找（getClipToOutline 是 API 31+）。 */
    private static final java.lang.reflect.Method M_GET_CLIP_TO_OUTLINE =
            cachedMethod("getClipToOutline");
    private static final java.lang.reflect.Method M_SET_CLIP_TO_OUTLINE =
            cachedMethod("setClipToOutline", boolean.class);

    private static java.lang.reflect.Method cachedMethod(String name, Class<?>... args) {
        try {
            return android.view.View.class.getMethod(name, args);
        } catch (Throwable ignored) {
            return null;
        }
    }
    private boolean outlineConflictLogged = false;
    private boolean padLogged = false;
    private int lastPadTop = Integer.MIN_VALUE;
    private int lastPadBottom = Integer.MIN_VALUE;

    /** 反射调一个无参 boolean getter（getClipToOutline 等）；失败返回 null。 */
    /** 用**静态缓存**的 Method 调 getClipToOutline，避免每帧做方法查找。 */
    private static Object boolCallCached(Object target) {
        final java.lang.reflect.Method m = M_GET_CLIP_TO_OUTLINE;
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setClipToOutlineCached(Object target, boolean v) {
        final java.lang.reflect.Method m = M_SET_CLIP_TO_OUTLINE;
        if (m == null) {
            return;
        }
        try {
            m.invoke(target, Boolean.valueOf(v));
        } catch (Throwable ignored) {
        }
    }

    /** 反射调一个无参 boolean getter（getClipToOutline 等）；失败返回 null。 */
    private static Object boolCall(Object target, String name) {
        try {
            final java.lang.reflect.Method m = findMethod(target.getClass(), name);
            return (m == null) ? null : m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 反射调一个 (boolean) 无返回值方法（setClipToOutline 等）。 */
    private static void voidCall(Object target, String name, boolean arg) {
        try {
            final java.lang.reflect.Method m = findMethod(target.getClass(), name);
            if (m != null) {
                m.invoke(target, Boolean.valueOf(arg));
            }
        } catch (Throwable ignored) {
        }
    }

    private static java.lang.reflect.Method findMethod(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private int lastSeenRowLpH = -1;
    private int lastSeenRowGravity = -1;

    private boolean applyGlyphLp(int w, int h, int leftMargin) {
        try {
            // ★ 与其它模块共处：同一条状态栏行上，OneuiXP 等模块也会 setClipChildren/
            //   setClipToPadding/setPadding，谁后写谁赢。挂载时关一次不够 ——
            //   每次应用尺寸都重新assert一遍（幂等、只在尺寸变化时调用，开销可忽略），
            //   被别人改回去时打一行日志，便于确认冲突来源。
            reassertClipOpen();
            final ViewGroup.LayoutParams lp = glyph.getLayoutParams();
            if (lp == null) {
                return false;
            }
            int lm = -1;
            if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                lm = ((android.view.ViewGroup.MarginLayoutParams) lp).leftMargin;
            }
            if (lp.width == w && lp.height == h && (lm < 0 || lm == leftMargin)) {
                return false;
            }
            lp.width = w;
            lp.height = h;
            if (lm >= 0) {
                ((android.view.ViewGroup.MarginLayoutParams) lp).leftMargin = leftMargin;
            }
            glyph.setLayoutParams(lp);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** ★ v107：{@link #applyGlyphLp} 的陪衬版（RTL 下两侧互换，与 addSideView 一致）。 */
    private static boolean applySideLp(View v, int w, int h, int outer, int inner) {
        try {
            if (v == null || v.getParent() == null) {
                return false;
            }
            final ViewGroup parent = (ViewGroup) v.getParent();
            final ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp == null) {
                return false;
            }
            final boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final int start = rtl ? inner : outer;
            final int end = rtl ? outer : inner;
            final int ww = Math.max(0, w);
            final int hh = Math.max(0, h);
            int lm = -1;
            int rm = -1;
            if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                final android.view.ViewGroup.MarginLayoutParams mlp =
                        (android.view.ViewGroup.MarginLayoutParams) lp;
                lm = mlp.leftMargin;
                rm = mlp.rightMargin;
            }
            if (lp.width == ww && lp.height == hh && (lm < 0 || (lm == start && rm == end))) {
                return false;
            }
            lp.width = ww;
            lp.height = hh;
            if (lm >= 0) {
                final android.view.ViewGroup.MarginLayoutParams mlp =
                        (android.view.ViewGroup.MarginLayoutParams) lp;
                mlp.leftMargin = start;
                mlp.rightMargin = end;
            }
            v.setLayoutParams(lp);
            if (v instanceof SideView) {
                // ★ v75：只记"已写入值"供每帧去重；基准不再快照，每帧从 cfg 现读。
                ((SideView) v).lastOuterPx = start;
                ((SideView) v).lastRightPx = end;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 找系统图标容器（system_icons → system_icons_container → status_icon_area
     * → hover_system_icons_container）。
     *
     * <p><b>v42→v49 演进</b>：v39/v41 两次尝试挂进下拉面板的 StatusIconContainer
     * 均导致 SystemUI 崩溃（安全模式），当时没有实机 View 树 dump，只能猜、猜错了。
     *
     * <p>v48 拿到真实 dump 后真相清楚了：下拉头部 {@code split_shade_status_bar} 里
     * 有一个 {@code hover_system_icons_container}（普通 LinearLayout），
     * 与状态栏 {@code system_icons} 结构完全同构。v49 起按 id 名精确找它，
     * 挂载路径与状态栏完全一致（而非挂进 StatusIconContainer）。
     */
    private static ViewGroup findIconContainer(View root) {
        try {
            final ViewGroup icons = findByIdName(root, "system_icons");
            if (icons != null) {
                return icons;
            }
            final ViewGroup box = findByIdName(root, "system_icons_container");
            if (box != null) {
                return box;
            }
            final ViewGroup area = findByIdName(root, "status_icon_area");
            if (area != null) {
                return area;
            }
            // ★ v49：下拉面板（shade）的图标行。
            //   实机 dump 实证，下拉头部 split_shade_status_bar 内是：
            //     FrameLayout  shade_header_system_icons        216x84
            //       └ LinearLayout hover_system_icons_container 216x84   ← 就是它
            //           ├ NetspeedView        networkSpeed
            //           ├ StatusIconContainer statusIcons
            //           └ BatteryMeterView    batteryRemainingIcon
            //   与状态栏 system_icons 结构完全同构（都是普通 LinearLayout 装图标），
            //   所以"挂进 LinearLayout 抢位子"这条已验证稳定的路径可以直接复用；
            //   注意挂的是【父 LinearLayout】，绝不挂进 statusIcons(StatusIconContainer)。
            return findByIdName(root, "hover_system_icons_container");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 判断该容器是不是<b>下拉面板的图标行</b>（id = {@code hover_system_icons_container}）。
     *
     * <p>只有它需要特殊处理：那一行高度被系统固定为 84px，
     * 但它外面还套着一层 212px 的头部、上下全是空白，
     * 所以对它关掉裁剪就能让三合一"溢出"到行外、与状态栏同尺寸（见 {@link #allowOverflow}）。
     */
    private static boolean isShadeIconRow(ViewGroup v) {
        try {
            final int id = v.getId();
            if (id == View.NO_ID) {
                return false;
            }
            return "hover_system_icons_container".equals(
                    v.getResources().getResourceEntryName(id));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * ★ v76：允许溢出的日志标志改成<b>实例字段</b>（每个叠加层各打一次）。
     *
     * <p>原来是 {@code static}，全进程只打一次 —— 结果先跑的那个（锁屏）把标志
     * 用掉了，状态栏与下拉的裁剪链<b>在日志里完全没有记录</b>，一旦"放大被裁"
     * 就无法判断是哪几层没放开。
     */
    private boolean overflowLogged = false;

    /**
     * ★ v53：允许下拉图标行<b>溢出绘制</b>——关闭该行及其外壳的 {@code clipChildren}。
     *
     * <h3>为什么</h3>
     * 实测（日志 25/26/27）：下拉图标行 {@code hover_system_icons_container}
     * 的**高度被系统固定为 84px**（塞进 102px 的子 View 也纹丝不动，
     * 虽然它的宽度会随内容伸缩），而状态栏的图标行有 107px。
     * 三合一是正方形，于是下拉被高度卡在 84px 以内、明显比状态栏小。
     *
     * <p>但它所在的头部 {@code split_shade_status_bar} 高 <b>212px</b>，
     * 行上下各有大段空白（dump 证实那一带没有任何可见内容），
     * 因此只要不裁剪，三合一完全可以画到行外。用户已确认"可以溢出、只要图标显示全"。
     *
     * <h3>安全性</h3>
     * {@code clipChildren} 只影响<b>绘制裁剪</b>，不改布局、不改数据、不改测量，
     * 可逆（系统重建视图时自然恢复）。且该行其余子 View 都比行矮，不受影响。
     * 这与 v39/v41 的崩溃（挂错容器、破坏自定义 onMeasure 约定）性质完全不同。
     *
     * <p>头部本身（212px）仍有 107px 的余量，溢出量（约 5~6px）远在其内，不会画到面板外。
     */
    private void allowOverflow(ViewGroup row, boolean deep) {
        try {
            final String who = (statusBarRoot == null)
                    ? "?" : statusBarRoot.getClass().getSimpleName();
            if (deep) {
                // ★ v76：>100% 时逐层向上放开 clipChildren。
                //   之前只关"所在行 + 直接父"两层：三合一放大到 120%（视图 95→114px
                //   对 107px 的行）时，更上层（PhoneStatusBarView / 窗口外壳）仍在
                //   裁剪，上下各被切掉约 3.5px —— 用户实测"放大后显示不全"。
                //   clipChildren 只影响<b>绘制裁剪</b>，不改布局、不改测量、不改数据，
                //   逐层放开是安全的（原生图标都小于行高，不受影响）。
                android.view.View v = row;
                int guard = 0;
                final StringBuilder chain = new StringBuilder();
                while (v instanceof ViewGroup && guard++ < 12) {
                    final ViewGroup vg = (ViewGroup) v;
                    vg.setClipChildren(false);
                    // ★ 实机日志抓到：只关 clipChildren 不够 —— 每个祖先默认
                    //   clipToPadding=true，子 View 超出**padding 边界**照样被裁。
                    //   日志里 rowH=84/90 而视图 121px（scale≈126）被切，就是这一层。
                    vg.setClipToPadding(false);
                    if (chain.length() < 160) {
                        if (chain.length() > 0) {
                            chain.append(" < ");
                        }
                        chain.append(vg.getClass().getSimpleName())
                                .append('[').append(guard).append(']');
                    }
                    final android.view.ViewParent up = vg.getParent();
                    if (!(up instanceof android.view.View)) {
                        chain.append(" |stop:not-a-view");
                        break;
                    }
                    v = (android.view.View) up;
                }
                if (!overflowLogged) {
                    overflowLogged = true;
                    TrioHooks.log(TrioHooks.LOG_INFO, "overlay: clipChildren=false up the chain: "
                            + chain + "  (root=" + who + ", 整体尺寸>100%，允许三合一溢出所在行)");
                }
                return;
            }
            row.setClipChildren(false);
            row.setClipToPadding(false);
            final android.view.ViewParent p = row.getParent();
            if (p instanceof ViewGroup) {
                // 外壳 FrameLayout(shade_header_system_icons) 也只有 84px 高，
                // 同样会裁掉溢出的部分，必须一起关。
                ((ViewGroup) p).setClipChildren(false);
                ((ViewGroup) p).setClipToPadding(false);
            }
            if (!overflowLogged) {
                overflowLogged = true;
                TrioHooks.log(TrioHooks.LOG_INFO, "overlay: clipChildren=false on "
                        + row.getClass().getSimpleName()
                        + " + parent  (root=" + who
                        + ", 允许下拉三合一溢出所在行, 到头部 212px 的空白区)");
            }
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: allowOverflow failed: " + t);
        }
    }

    /** 按资源 id 名在状态栏树里精确查找容器。 */
    private static ViewGroup findByIdName(View root, String name) {
        try {
            final java.util.ArrayList<View> queue = new java.util.ArrayList<>();
            queue.add(root);
            int guard = 0;
            while (!queue.isEmpty() && guard++ < 400) {
                final View v = queue.remove(queue.size() - 1);
                final int vid = v.getId();
                if (vid != View.NO_ID) {
                    try {
                        if (name.equals(v.getResources().getResourceEntryName(vid))
                                && v instanceof ViewGroup) {
                            return (ViewGroup) v;
                        }
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
                if (v instanceof ViewGroup) {
                    final ViewGroup vg = (ViewGroup) v;
                    final int n = vg.getChildCount();
                    for (int i = 0; i < n; i++) {
                        final View c = vg.getChildAt(i);
                        if (c != null) {
                            queue.add(c);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }

    /** 从父容器已有子 View 拷贝 LayoutParams（同父必同类型）。 */
    private static LayoutParams cloneExistingLayoutParams(ViewGroup parent) {
        try {
            final int n = parent.getChildCount();
            for (int i = 0; i < n; i++) {
                final View c = parent.getChildAt(i);
                if (c == null) {
                    continue;
                }
                final ViewGroup.LayoutParams src = c.getLayoutParams();
                if (src == null) {
                    continue;
                }
                // ★ 必须用 src.getClass() 的拷贝构造，保留真实子类
                //   （如 LinearLayout.LayoutParams）。此前写成 new LayoutParams(src)
                //   得到的是基类 ViewGroup.LayoutParams，导致下游
                //   `instanceof LinearLayout.LayoutParams` 永远为 false，
                //   leftMargin / gravity 根本没被设置 → 间距"一点都没变化"。
                try {
                    return (LayoutParams) src.getClass()
                            .getConstructor(ViewGroup.LayoutParams.class).newInstance(src);
                } catch (Throwable ignored) {
                    // 退化：用带 width/height 的构造
                    try {
                        return (LayoutParams) src.getClass()
                                .getConstructor(int.class, int.class)
                                .newInstance(src.width, src.height);
                    } catch (Throwable ignored2) {
                        // ignore
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }

    /**
 * 自检：view 是否具备 StatusIconDisplayable 的全部方法签名。
 *
 * <p>这是 v39 崩溃的**前置护栏**：接口方法缺任何一个，挂进 StatusIconContainer
 * 都会在布局阶段炸。与其赌，不如挂载前拦住——最坏结果是三合一不显示，
 * 而不是 SystemUI 崩溃进安全模式。
 */
    private static boolean hasDisplayableMethods(View v) {
        try {
            final String[] required = {
                    "getSlot", "getVisibleState", "isIconBlocked", "isIconVisible",
                    "setDecorColor", "setStaticDrawableColor", "setVisibleState",
            };
            for (int i = 0; i < required.length; i++) {
                boolean found = false;
                Class<?> k = v.getClass();
                while (k != null) {
                    for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                        if (required[i].equals(m.getName())) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                    k = k.getSuperclass();
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 诊断用：安全读取 leftMargin（非MarginLayoutParams 返回 -1）。 */
    private static int readLeftMargin(ViewGroup.LayoutParams lp) {
        try {
            return ((android.view.ViewGroup.MarginLayoutParams) lp).leftMargin;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 无子 View 可拷贝时按容器类型构造。 */
    private static LayoutParams createLayoutParamsFor(ViewGroup parent) {
        try {
            if (parent instanceof android.widget.LinearLayout) {
                return new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
            if (parent instanceof android.widget.FrameLayout) {
                return new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }

    /** 退回 v14 已验证的独立窗口方案。 */
    /** @return true=窗口已挂上(或已 attached)，false=没建成(供强制窗口模式判断是否兜底挂树)。 */
    private boolean attachWindow() {
        try {
            if (windowManager == null || params == null) {
                final Context overlayCtx = createOverlayContext();
                if (overlayCtx == null) {
                    return false;
                }
                windowManager = (WindowManager) overlayCtx.getSystemService(Context.WINDOW_SERVICE);
                params = buildParams();
            }
            if (windowManager == null || params == null) {
                return false;
            }
            if (!attached) {
                try {
                    windowManager.addView(glyph, params);
                    attached = true;
                    retryCount = 0;
                    TrioHooks.log(LOG_OK, "overlay: MODE=WINDOW (type=2012, v14 定位)");
                } catch (Throwable t) {
                    final String cn = t == null ? "" : t.getClass().getName();
                    attached = false;
                    if (cn.contains("BadTokenException")
                            || cn.contains("IllegalArgumentException")) {
                        return false;
                    }
                    if (retryCount < RETRY_MAX) {
                        retryCount++;
                        MAIN.postDelayed(this::attachOrSync, RETRY_MS);
                    }
                    return false;
                }
            }
            sync();
            glyph.startLoop();
            return true;
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: attachWindow failed: " + t);
            return false;
        }
    }

    /**
     * ★ v108：按当前方向/尺寸重挂或原地改尺寸。
     *
     * <p>树模式下走 {@link #tryAttachIntoIconTree()} 的<b>原地改 LayoutParams</b> 分支
     * （已挂在同一父容器、陪衬集合没变 ⇒ 只改宽高与边距，不摘不挂，日志 52 实测
     * 摘挂会引起 windowVis GONE/VISIBLE 成对风暴 = "整个状态栏消失一下再回来"）。
     */
    private void remountNow() {
        try {
            if (treeAttached) {
                tryAttachIntoIconTree();
            } else if (attached) {
                sync();
            }
            if (glyph != null) {
                glyph.invalidate();
            }
        } catch (Throwable ignored) {
            // 尺寸调整失败绝不影响系统
        }
    }

    /**
     * ★ v108：挂载/改尺寸之后，再复核几帧。
     *
     * <p><b>为什么还要复核</b>：布局不是一步到位的。日志 53 实测状态栏挂载那一刻
     * 量到 {@code container=84x107}，稳定后才是 {@code 202x90} —— 第一帧算出来的
     * 尺寸（105px）是错的，要等布局走完才会变成对的（124px）。
     * 这里在"刚改过尺寸"之后再复核 {@value #SETTLE_FRAMES} 帧，让尺寸收敛到稳定值；
     * 值稳定后 {@code tryAttachIntoIconTree} 会早退空转，没有额外开销。
     */
    private static final int SETTLE_FRAMES = 8;
    private int settleFrames = 0;

    /** 排一次事后复核（由 {@link GlyphView#checkSize} 每帧消费）。 */
    private void armSettle() {
        settleFrames = SETTLE_FRAMES;
    }

    /** 兜底跑腿：万一那几帧都没绘制（息屏/面板收起），到点照旧复核一次。 */
    private final Runnable settleFallback = this::remountNow;

    /**
     * 重新定位窗口（屏幕绝对坐标，v14 实测有效）。
     *
     * <p><b>v22 修复：不再用 {@code root.isShown()} 决定显隐</b>——v14 就是因为
     * "isShown=false 就 GONE" 导致三合一显示几秒后消失。状态栏隐藏时该窗口会被
     * 系统自行收起，我们不需要（也不应该）判断状态栏可见性。
     */
    private void sync() {
        if (!attached) {
            return;
        }
        try {
            final View root = statusBarRoot;
            if (root == null) {
                return;
            }
            root.getLocationOnScreen(location);
            final int barTop = location[1];
            final int barRight = location[0] + root.getWidth();
            final int barH = root.getHeight();
            if (barH <= 0 || barRight <= 0) {
                // 状态栏还没布局完成，稍后重试（到顶归零继续，永不永久放弃）
                if (retryCount >= RETRY_MAX) {
                    retryCount = 0;
                }
                retryCount++;
                MAIN.postDelayed(this::attachOrSync, RETRY_MS);
                return;
            }
            retryCount = 0;
            final float density = root.getResources().getDisplayMetrics().density;
            // ★ v76 修正：窗口模式与挂树模式统一成**同一套**算法 ——
            //   wantWidth() 给的是【墨迹】边长，必须经 viewEdgeForInk 补回
            //   INK_INSET 的防贴边余量才是【视图】边长（否则窗口会小 6%，
            //   且与挂树模式大小不一致）。之前这里用的是 v14 遗留的
            //   GLYPH_WIDTH_DP(62dp≈217px，视图语义、不含任何缩放设置)，
            //   改尺寸设置对窗口模式完全无效；上一版直接拿 wantWidth() 当宽度
            //   又漏了这次换算 —— 锁屏因此"只能到 100%"。现在三处真正一致：
            //   100% → 视图 95px，130% → 124px，与挂树模式逐像素相同。
            // ★ v100：横屏 >100% 回退 100%（见 effectiveGlyphScale），竖屏恢复设定值。
            // ★ v108：窗口模式不一定每帧都走 onDraw，这里用之前先刷新一次方向。
            refreshLandscape(root);
            final int scale = effectiveGlyphScale();
            final int edge = TrioRenderer.viewEdgeForInk(wantWidth() * density);
            // ★ 胶囊：高度沿用同一套算法，宽度按宽扁比例另给。
            final int w = isCapsuleStyle() ? TrioRenderer.capsuleViewWidth(edge) : edge;
            // ★ v76：>100% 时把**窗口本身**加高，否则窗口边界（不是 clipChildren）
            //   会把放大的部分裁掉 —— 挂树模式靠逐层放开裁剪，窗口只能加高自己。
            // ★ 胶囊：窗口高度含上下开口带（capHostHeightPx 同款算法），否则顶部/底部
            //   开口件被窗口 Surface 裁掉；并据此把窗口上移半个带，让胶囊本体仍对齐状态栏。
            final boolean capWin = isCapsuleStyle();
            final int winH = capWin
                    ? TrioRenderer.capsuleViewHeight(w)
                    : ((scale > 100) ? Math.max(barH, edge) : barH);
            params.width = w;
            // ★ 用状态栏实际高度，绝不用 MATCH_PARENT（否则整屏高、漂到中部）
            params.height = winH;
            // ★ v82：窗口模式下「整组左右平移」直接挪窗口自己（translation 会把内容
            //   推出窗口 Surface 被裁，见 shiftBaseFor）。正平移 = 往右 = x 增大。
            params.x = barRight - w - (int) (RIGHT_MARGIN_DP * density)
                    + Math.round(glyphShiftPxF(glyph));
            params.y = capWin
                    ? barTop - Math.round((winH - edge) / 2f)
                    : barTop;
            windowManager.updateViewLayout(glyph, params);
            // 只在位置变化时记一条日志，避免每秒刷屏
            if (params.x != lastLoggedX || params.y != lastLoggedY) {
                lastLoggedX = params.x;
                lastLoggedY = params.y;
                TrioHooks.log(TrioHooks.LOG_INFO, "overlay: positioned x=" + params.x
                        + " y=" + params.y + " w=" + w + " h=" + params.height + " scale=" + scale
                        + " barTop=" + barTop + " barRight=" + barRight
                        + " root=" + root.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "overlay: sync failed: " + t);
        }
    }

    // ----------------------------------------------------------- 环外陪衬（v59）

    /**
     * 环外那一块陪衬：要么是信号读数，要么是网络类型标签。
     *
     * <p><b>为什么不画进三合一那个 View：</b>三合一的宽度就是那个正方形，
     * 而这两块必须在状态栏里<b>各自占位</b>（把原生图标整体往左挤），
     * 只有作为容器的子 View 参与 LinearLayout 排布才做得到。
     *
     * <p><b>读数与环内四点同源：</b>{@link TrioRenderer#outSignalLevels} 取的就是
     * {@code state.mobileLevel}/{@code state.slotLevels} 那套 0~4 档 ——
     * 与环内四个点完全同一份数据。以后按 One UI 阈值重算档位时，
     * 环内环外会一起跟着变，不存在"环外另算一套"。
     */
    private final class SideView extends View {
        private final int kind;
        /**
         * ★ v75：上一次真正写进 LayoutParams 左右边距；MIN_VALUE = 还没写过。
         *
         * <p>v69~v74 这里还挂着一个 {@code baseOuterPx}（挂载时的外边距基准），
         * 每帧在它上面叠加整组微调。问题是<b>基准是挂载时拍的快照</b>：用户
         * 之后改设置，已挂载的陪衬永远按旧值写（左侧）或干脆不写（右侧）——
         * 这就是"环外信号间距调不动、类型边距无效"的直接原因。
         * v75 起基准每帧从 {@link TrioConfig} 现读，快照字段删除。
         */
        int lastOuterPx = Integer.MIN_VALUE;
        /** ★ v75：内侧（右）边距的上次写入值，与 {@link #lastOuterPx} 同理。 */
        int lastRightPx = Integer.MIN_VALUE;
        /** ★ v75 诊断：温度读数链路的去重状态（值变才打日志）。 */
        boolean perfDiagLogged;
        String perfDiagText;
        /** ★ v72 防烧屏：上一次写进本 View 的微位移（x, y），单位 px。 */
        final float[] burnLast = {0f, 0f};

        SideView(Context context, int kind) {
            super(context);
            this.kind = kind;
            setVisibility(View.VISIBLE);
            // 与三合一同样：不要背景层（切换时不会泛出色块），也不进硬件层缓存。
            setBackground(null);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            final android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            final int w = (lp != null && lp.width > 0) ? lp.width : 0;
            final int h = (lp != null && lp.height > 0) ? lp.height : 0;
            setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
        }

        /**
         * 需要的尺寸会随内容变（网络类型 4G↔5G、读数有没有第二排圆点），
         * 所以每次画之前核对一次 LayoutParams：不一致就写回去并请求重新排布，
         * 让"占位"的宽度始终跟着内容走、邻居跟着让位。
         * 相等时一个字都不写，避免每次绘制都引发一轮布局。
         */
        private void ensureBox(int wantW, int wantH) {
            final android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp == null) {
                return;
            }
            final int w = Math.max(0, wantW);
            final int h = Math.max(0, wantH);
            if (lp.width != w || lp.height != h) {
                lp.width = w;
                lp.height = h;
                setLayoutParams(lp);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            try {
                state.refresh();
                final TrioSettings cfg = TrioConfig.get();
                final TrioAppearance app = TrioAppearance.of(cfg);
                final float density = getResources().getDisplayMetrics().density;
                if (kind == SIDE_SIGNAL) {
                    // ★ 与环内四点同一份数据、同一套 0~4 档
                    final int[] out = new int[2];
                    TrioRenderer.outSignalLevels(cfg.dataSimOnly, state.mobileLevel,
                            state.slotLevels, state.dataSlot, out);
                    // ★ v76：盒高取三块统一值（max）—— 5G/温度要跟信号条底边对齐，
                    //   三块盒子必须一样高（CENTER 下底边差 = 半盒高差）。
                    //   信号条自身绘制位置不动（用户明确要求保持原位）。
                    final int h = computeSideBoxH();
                    ensureBox(TrioRenderer.outSignalWidth(h), h);
                    if (getWidth() > 0 && getHeight() > 0) {
                        TrioRenderer.drawOutSignal(canvas, getWidth(), getHeight(),
                                out[0], out[1], state.foreground(), app);
                    }
                } else if (kind == SIDE_PERF) {
                    // ★ v75：电池温度（充电功率读数已删）。没内容时宽度 0（不占位）。
                    //   数据是静态量（TrioState.readBattery 写），不走 state.refresh()。
                    final String text = TrioRenderer.perfText(
                            cfg.perfTemp, TrioState.sBatteryTempC);
                    // ★ v75 诊断：用户报"读数不显示"时，旧日志里没有任何 perf
                    //   数据行，无从判断是开关没开、平台没给温度、还是渲染没跑。
                    //   现在把三样一次打齐（值变才打，不刷屏）：
                    //   tempC=-2147483648 就是平台从没给过 EXTRA_TEMPERATURE。
                    if (text.isEmpty()) {
                        if (!perfDiagLogged || perfDiagText != null) {
                            perfDiagLogged = true;
                            perfDiagText = null;
                            TrioHooks.log(TrioHooks.LOG_WARN, "perf: EMPTY text"
                                    + " perfTemp=" + cfg.perfTemp
                                    + " tempC=" + TrioState.sBatteryTempC
                                    + " (TEMP_UNKNOWN=" + TrioState.TEMP_UNKNOWN
                                    + " => platform never gave EXTRA_TEMPERATURE)");
                        }
                        ensureBox(0, 0);
                        return;
                    }
                    if (!perfDiagLogged || !text.equals(perfDiagText)) {
                        perfDiagLogged = true;
                        perfDiagText = text;
                        TrioHooks.log(TrioHooks.LOG_INFO, "perf: text=" + text
                                + " perfTemp=" + cfg.perfTemp
                                + " tempC=" + TrioState.sBatteryTempC
                                + " box=" + getWidth() + "x" + getHeight());
                    }
                    // 基准字号 = 环外类型字号；两项都开时按图标高度自适应缩小。
                    final float base = TrioRenderer.outTypePx(app.outTypeSize, density);
                    final int glyphW = (glyph == null) ? 0 : glyph.getWidth();
                    final int glyphH = (glyph == null) ? 0 : glyph.getHeight();
                    final float size = TrioRenderer.perfTextSize(
                            text, glyphW, glyphH, base, app);
                    // ★ 防横移：占位宽度按"最宽形式"算，不按当前文本 ——
                    //   否则功率每秒变一次，整排原生图标跟着每秒横移一次。
                    final int boxH = computeSideBoxH();
                    ensureBox(TrioRenderer.perfWidth(text, size, app), boxH);
                    if (getWidth() > 0 && getHeight() > 0) {
                        // ★ v76：文字底边对齐信号条底边（信号条不动，只挪读数）。
                        TrioRenderer.drawPerf(canvas, getWidth(), getHeight(),
                                text, size, state.foreground(), app,
                                computeSideBaseline(boxH));
                    }
                } else {
                    // ★ v76：盒高统一；文字底边对齐信号条底边（信号条不动）。
                    final int h = computeSideBoxH();
                    final int w = TrioRenderer.outTypeWidth(state.mobileType, density, app);
                    ensureBox(w, h);
                    if (w > 0 && getHeight() > 0) {
                        TrioRenderer.drawOutType(canvas, w, getHeight(),
                                state.mobileType, state.foreground(), density, app,
                                computeSideBaseline(h));
                    }
                }
            } catch (Throwable t) {
                TrioHooks.log(TrioHooks.LOG_WARN, "overlay: side onDraw error: " + t);
            }
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            try {
                final int want = (visibility == VISIBLE) ? VISIBLE : GONE;
                if (getVisibility() != want) {
                    setVisibility(want);
                }
            } catch (Throwable ignored) {
                // 绝不能因显隐处理抛异常
            }
        }
    }

    // ----------------------------------------------------------- 自绘 View

    private final class GlyphView extends View {
        GlyphView(Context context) {
            super(context);
            setVisibility(View.VISIBLE);
            // ★ v39：彻底不要背景。
            //   此前用 setBackgroundColor(Color.TRANSPARENT) —— 看似透明，实际会
            //   **创建一个 ColorDrawable 背景层**。息屏↔锁屏切换时三星对状态栏做
            //   整体过渡，这个"带背景的 View"会在合成瞬间泛出一个**方形色块**，
            //   就是用户看到的残影（形状/尺寸与图标一致，可佐证）。
            //   setBackground(null) 让 View 走"无背景直绘"，彻底消除该色块。
            setBackground(null);
            // 自绘 View 不参与硬件层缓存，避免切换时被整层合成出色块
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        // ---------------------------------------------------------------
        // ★ v41：实现 StatusIconDisplayable 的方法签名。
        //
        //   三星 statusbar.phone.StatusIconContainer 有自定义测量逻辑：
        //     onMeasure():
        //       for (每个 child)
        //         if (child instanceof StatusIconDisplayable && !isIconBlocked())
        //            mMeasureViews.add(child);      // 只有"合规图标"参与测量
        //       for (v : mMeasureViews) { measureChild(v); total += ...; }
        //
        //   ★ v39 崩溃的真正原因：我塞进去的是普通 View，不实现该接口 →
        //     不被纳入测量名单，却占着 getChildCount() 的位置 →
        //     测量宽度与布局假设不一致 → onLayout 抛异常 → SystemUI 崩溃。
        //
        //   解决：直接按接口签名声明这些方法（Java 允许方法名/签名与外部接口
        //   一致，编译期无需引用 systemui 类），运行时容器用反射调用它们时
        //   能命中真实实现 → instanceof 判断通过 → 三合一成为"正规图标"，
        //   由系统全权处理排布与间距，这才是真正的"抢位子"。
        //
        //   接口签名（反编译自 StatusIconDisplayable，9 个方法）：
        //     String getSlot() / int getVisibleState() / boolean isIconBlocked()
        //     boolean isIconVisible() / void setDecorColor(int)
        //     void setStaticDrawableColor(int) / void setStaticDrawableColor(int,int)
        //     void setVisibleState(int) / void setVisibleState(int,boolean)
        // ---------------------------------------------------------------

        /** 槽位名：返回独立 slot，不与电池/信号冲突，也不会进 mIgnoredSlots。 */
        public String getSlot() {
            return "samsungduo";
        }

        /** 显示状态：VISIBLE→0，其余→1（与系统状态机约定一致）。 */
        public int getVisibleState() {
            return getVisibility() == View.VISIBLE ? 0 : 1;
        }

        /** 本图标永不"被屏蔽"（那是系统隐藏原生图标用的语义）。 */
        public boolean isIconBlocked() {
            return false;
        }

        public boolean isIconVisible() {
            return getVisibility() == View.VISIBLE;
        }

        /** SystemUI 同步图标色：接住它给的颜色，让三合一跟随主题。 */
        public void setDecorColor(int color) {
            try {
                // ★ v77：带上归属 View（这个格子属于哪套状态栏），
                //   避免三处共享一个颜色。
                TrioState.noteIconTint(statusBarRoot, color);
            } catch (Throwable ignored) {
                // 取色失败绝不影响绘制
            }
        }

        public void setStaticDrawableColor(int color) {
            setDecorColor(color);
        }

        public void setStaticDrawableColor(int color, int animatedColor) {
            setDecorColor(color);
        }

        public void setVisibleState(int state) {
            setVisibleState(state, false);
        }

        public void setVisibleState(int state, boolean animate) {
            try {
                setVisibility(state == 0 ? View.VISIBLE : View.INVISIBLE);
            } catch (Throwable ignored) {
                // ignore
            }
        }

        private boolean mLooping;
        private boolean drawLogged;
        /** ★ v67：上一次写进 LayoutParams 的左边距，用于"值变了才写回"，避免每帧请求布局。 */
        private int lastLeftGap = -1;
        /** ★ v72 防烧屏：当前帧算出的位移（复用数组）与本 View 上一次写进去的位移。 */
        private final float[] burnTmp = {0f, 0f};
        private final float[] burnLast = {0f, 0f};

        void startLoop() {
            if (mLooping) {
                return;
            }
            mLooping = true;
            postDelayed(mFrame, REFRESH_MS);
        }

        /**
         * ★ v78：停掉每秒循环（总开关关闭时用）。
         *
         * <p>与 v74 省电那次停循环是同一套开关：{@code mLooping=false} 让
         * {@code mFrame} 自己退出，{@code removeCallbacks} 把已排队的那次一并撤掉
         * —— 否则摘除后还会多跑一帧。重新打开总开关时由
         * {@code attachOrSync → glyph.startLoop()} 拉回来（onDraw 里也有自愈）。
         */
        void stopLoop() {
            mLooping = false;
            try {
                removeCallbacks(mFrame);
            } catch (Throwable ignored) {
                // ignore
            }
        }

        /** ★ v74：防烧屏是否真的在漂移（开 + 幅度 > 0）。判据与 applyBurnIn 一致。 */
        private boolean burnInActive() {
            final TrioSettings cfg = TrioConfig.get();
            return cfg.burnInProtect && cfg.burnInAmplitude > 0;
        }

        private final Runnable mFrame = new Runnable() {
            @Override
            public void run() {
                if (!mLooping) {
                    return;
                }
                // ★ v74 省电：防烧屏关掉后，TREE 模式（不靠 sync 重定位）下
                //   这个循环已无事可做 —— applyBurnIn 归零位移就返回，但
                //   invalidate 是无条件的，关掉防烧屏仍每秒重绘一遍纯属空转。
                //   这里直接把循环停掉；恢复交给 onDraw 自愈（防烧屏开着时
                //   系统一重绘本 View 就把 startLoop 重新拉起，见 onDraw）。
                if (treeAttached && !burnInActive()) {
                    mLooping = false;
                    return;
                }
                try {
                    // 只有 WINDOW 模式需要每秒重定位；TREE 模式由系统排布，不用动
                    if (!treeAttached) {
                        sync();
                    }
                    // 注意：TREE 模式的可见性由 onWindowVisibilityChanged 事件驱动
                    // （切换动画只有几百毫秒，每秒轮询来不及），不在这里轮询。
                    //
                    // ★ v72 防烧屏：先把微位移写好再 invalidate —— setTranslationX/Y
                    //   自己就会请求重绘，两步放在一起，一轮只重绘一次。
                    //   四块（三合一 + 三块环外陪衬）各算各的，整组同步游走。
                    // 注意是 GlyphView.this：这里身处匿名 Runnable，裸 this 指 Runnable。
                    applyBurnIn(GlyphView.this, burnTmp, burnLast);
                    if (sideSignal != null) {
                        applyBurnIn(sideSignal, burnTmp, sideSignal.burnLast);
                    }
                    if (sideType != null) {
                        applyBurnIn(sideType, burnTmp, sideType.burnLast);
                    }
                    if (sidePerf != null) {
                        applyBurnIn(sidePerf, burnTmp, sidePerf.burnLast);
                    }
                    invalidate();
                    if (sideSignal != null) {
                        sideSignal.invalidate();
                    }
                    if (sideType != null) {
                        sideType.invalidate();
                    }
                    if (sidePerf != null) {
                        sidePerf.invalidate();
                    }
                } catch (Throwable ignored) {
                    // 重绘循环绝不能抛异常
                }
                postDelayed(this, REFRESH_MS);
            }
        };

        /**
         * ★ v67：每次绘制时复核一次左边距 —— 只有在"该有的值 ≠ 已写的值"时才写回。
         *
         * <p><b>为什么挂载时算好还不够</b>：挂载发生在 addView 之后、首次 layout 之前，
         * 那一刻左邻居（电池）往往还是宽度 0（未测量），于是
         * {@link #gapBeforeLeftNeighbor} 跳过它、错误地返回 0 —— v66 的 bug：
         * 日志 `gapPx=7`（判定命中了电池）但几何里左边距仍是 0（写入发生在电池量出 84px 之前）。
         *
         * <p>这里放在 {@code onDraw} 里：绘制一定发生在 layout 之后，此时左邻居宽度已知。
         * 只在值真正变化时写回 LayoutParams + 请求重排，否则一个字不写，
         * 不会造成多余布局。若左邻居在运行中变化（电池显隐），下一帧就会校正。
         */
        private void ensureLeftMargin() {
            try {
                final ViewGroup p = (ViewGroup) getParent();
                if (p == null) {
                    return;
                }
                final float density = getResources().getDisplayMetrics().density;
                // ★ v69：整组（三合一 + 环外陪衬）一起算，理由见 writeGroupMargins。
                writeGroupMargins(p, density, false);
            } catch (Throwable ignored) {
                // 复核失败绝不影响绘制
            }
        }

        /**
         * ★ v69：把"<b>整组</b>（三合一 + 两块环外陪衬）与左侧原生图标之间的间距"写回布局。
         *
         * <p><b>为什么必须按"组"处理</b>：环外读数与类型标签是插在三合一<b>左边</b>的，
         * 所以这一组的最左端 <b>不是三合一，而是排在最前那一块陪衬</b>。
         * 而 v68 把 offset 只写在三合一身上、陪衬在场时又被 {@code hasSide} 强制归零
         * —— 于是<b>"环外一显示，间距就调不动了"</b>（用户实测）。
         *
         * <p>这里统一取组内 {@code indexOfChild} 最小的那块，把 offset 加到它身上、
         * 其余恢复各自基准，于是 <b>一个滑块 = 整组一起移动</b>（用户要的"调整一个等于全调"）。
         * 用 index 而不是"陪衬按什么顺序 addView"来判定最左端：注释与代码顺序本来就对不上过，
         * 拿代码自身的位置当依据才不会错。
         *
         * @param force true = 无条件写回（挂载后立刻同步一次；此时 last* 可能是旧值）；
         *              false = 值变了才写（每帧复核，稳态下一个字都不写）
         */
        private void writeGroupMargins(ViewGroup p, float density, boolean force) {
            // ★ v82：offset 里并入「整组左右平移」的 margin 那一半
            //   （正平移=margin 减小=原生图标回位；负平移=margin 增大=原生图标被推向左）。
            final int offset = groupOffsetPx(density);

            // 组内最靠左的那块
            View leftmost = this;
            int minIdx = p.indexOfChild(this);
            if (minIdx < 0) {
                return;                   // 三合一自己都不在树上，无从谈起
            }
            for (SideView s : new SideView[]{sideSignal, sideType, sidePerf}) {
                if (s == null || s.getParent() != p) {
                    continue;
                }
                final int i = p.indexOfChild(s);
                if (i >= 0 && i < minIdx) {
                    minIdx = i;
                    leftmost = s;
                }
            }

            // 三合一自己：只有当它是组的最左端时，才需要让出与原生图标的距离
            final int mine = (leftmost == this)
                    ? gapBeforeLeftNeighbor(p, density) + offset
                    : 0;
            if (force || mine != lastLeftGap) {
                final ViewGroup.LayoutParams lp = getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).leftMargin = mine;
                    lastLeftGap = mine;
                    setLayoutParams(lp);
                }
            }

            // 陪衬：★ v75 起每帧从 cfg 现读，不再用挂载时拍的 baseOuterPx 快照。
            //   v69~v74 用快照的后果：用户改设置后，已挂载的陪衬左侧永远按旧值
            //   写、右侧干脆没人写 —— 用户实测"间距/边距调不动"。
            //
            //   规则（左→右布局 [signal, type, perf, 三合一]）：
            //   · 每块 leftMargin = 自己的外边距设置（最左那块再加整组 offset）
            //   · 每块 rightMargin = 自己的内边距设置，但**左侧邻居在场时归 0**
            //     —— 块间空隙只由"右侧块的 leftMargin"承担，否则两道边距叠加
            //   · perf 是 v75 新增的设置项（默认 2dp），语义与「环外信号间距」同款
            final TrioSettings cfg = TrioConfig.get();
            if (sideSignal != null && sideSignal.getParent() == p) {
                final int l = (int) (cfg.outSignalMargin * density)
                        + ((sideSignal == leftmost) ? offset : 0);
                writeSideMargins(sideSignal, l, 0, force);
            }
            if (sideType != null && sideType.getParent() == p) {
                final int l = (int) (cfg.outTypeMarginLeft * density)
                        + ((sideType == leftmost) ? offset : 0);
                // ★ v76 修正（用户实测"右边距调到 0 还是很宽"）：右边距**始终生效**。
                //   此前温度在场时这里被写 0（沿用"空隙只由右侧块的 leftMargin 承担"
                //   的规则），等于把本设置项在温度开启时**整个忽略**——用户调它毫无
                //   反应，实际间距来自「温度读数边距」。现在改为：本项独自承担这段
                //   空隙，温度的左边距让位为 0（仍然不双重计算）。
                final int r = (int) (cfg.outTypeMarginRight * density);
                writeSideMargins(sideType, l, r, force);
            }
            if (sidePerf != null && sidePerf.getParent() == p) {
                // ★ v76：左侧是类型标签时，这段空隙已由 type.right 独自承担，
                //   本块左边距让位为 0；类型标签关闭时才用自己这份外边距。
                final boolean typeOnLeft = (sideType != null && sideType.getParent() == p);
                final int l = typeOnLeft ? 0
                        : ((int) (cfg.perfOuterMargin * density)
                                + ((sidePerf == leftmost) ? offset : 0));
                // ★ v76：右侧边距也接上（v75 时 perf.right 恒 0 → 温度贴着三合一）。
                //   glyph 在 perf 右侧时自身 left 归 0，这块空隙只由 perf.right 承担，
                //   与"块间空隙由右侧块的 left 承担"规则不冲突、不双重计算。
                writeSideMargins(sidePerf, l,
                        (int) (cfg.perfOuterMarginRight * density), force);
            }
        }

        /** ★ v75：写一块陪衬的左右边距，同样只在值变化时才动布局。 */
        private void writeSideMargins(SideView s, int left, int right, boolean force) {
            try {
                if (!force && s.lastOuterPx == left && s.lastRightPx == right) {
                    return;
                }
                final ViewGroup.LayoutParams lp = s.getLayoutParams();
                if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
                    return;
                }
                final ViewGroup.MarginLayoutParams mlp =
                        (ViewGroup.MarginLayoutParams) lp;
                mlp.leftMargin = left;
                mlp.rightMargin = right;
                s.lastOuterPx = left;
                s.lastRightPx = right;
                s.setLayoutParams(lp);
            } catch (Throwable ignored) {
                // 陪衬边距写失败绝不影响绘制
            }
        }

        /**
         * 窗口可见性变化时立即响应（v38）。
         *
         * <p><b>为什么不用每秒轮询</b>：息屏↔锁屏的切换动画只有几百毫秒，
         * 每秒一次的检查根本来不及，会在切换瞬间留下残影（用户实测确认）。
         * {@code onWindowVisibilityChanged} 由系统在窗口显隐变化的<b>同一帧</b>回调，
         * 能准确跟随。
         *
         * <p>隐藏用 GONE：既不可见、也不占布局位置（INVISIBLE 会留空位）。
         */
        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            try {
                final int want = (visibility == VISIBLE) ? VISIBLE : GONE;
                if (getVisibility() != want) {
                    setVisibility(want);
                    final View r = statusBarRoot;
                    TrioHooks.log(LOG_OK, "overlay: windowVis -> "
                            + (visibility == VISIBLE ? "VISIBLE" : "GONE")
                            + " (win=" + visibility + ", "
                            + (r == null ? "?" : r.getClass().getSimpleName()) + ")");
                }
            } catch (Throwable ignored) {
                // 绝不能因显隐处理抛异常
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            // ★ v29：LayoutParams 里已经算好了最终边长（按容器比例 + 正方形），
            //   这里直接沿用它，避免 onMeasure 用固定 dp 把它顶回去导致尺寸不一致。
            //   挂载前（addView 尚未完成）才退回按 dp 计算。
            final android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            int w;
            int h;
            if (lp != null && lp.width > 0 && lp.height > 0) {
                w = lp.width;
                h = lp.height;
            } else {
                final float dp = treeAttached ? wantWidth() : GLYPH_WIDTH_DP;
                w = (int) (dp * getResources().getDisplayMetrics().density);
                h = w; // 正方形
            }
            // ★ 胶囊：宽扁形，宿主必须同高给出对应宽，否则墨迹只占中间一条。
            // ★ bug 修复：lp 里已经带着"按容器算好的"宽高（挂载那段做过宽度兜底
            //   与高度同比例下调），这里再无条件重算会把兜底**覆盖掉** ——
            //   容器窄时视图反而要得比容器还宽，被父容器压扁、还挤掉原生图标。
            //   lp 有值时以它为准。
            if (isCapsuleStyle() && (lp == null || lp.width <= 0 || lp.height <= 0)) {
                w = TrioRenderer.capsuleViewWidth(h);
            }
            setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
        }

        /** ★ v108：上次按它算过尺寸的方向（每个实例各存一份，不共享，见 checkSize）。 */
        private boolean appliedLandscape = false;
        /** ★ v108：上次按它算过尺寸的「整体尺寸」滑块值。 */
        private int appliedScale = -1;
        /** ★ 已应用尺寸时的行高；行高一变就必须重算，否则会被更矮的行裁掉。 */
        private int appliedRowH = -1;

        /**
         * ★ v108：配置变化（旋转 / 密度 / 折叠展开）由 ViewRootImpl 递归分发到每个
         * 子树节点，我们挂在系统图标行里必然收到。这里只做一件事：<b>立即 invalidate</b>，
         * 让 {@link #checkSize} 在下一帧就跑，不等下一次状态栏自发重绘。
         */
        @Override
        protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            try {
                invalidate();
            } catch (Throwable ignored) {
                // 重绘请求失败无害（onDraw 迟早也会被系统调用）
            }
        }

        /**
         * ★ v108：每帧复核尺寸（旋转 / 拖尺寸滑块 / 布局瞬态，全走这一条路）。
         *
         * <p><b>为什么放在 onDraw</b>：旋转是一次配置变化，会强制状态栏整体重排重绘，
         * 每个可见 View 的 onDraw 都会被调用 —— 这是树模式最可靠的时机。
         * 且 {@code Configuration.orientation} 在旋转<b>那一帧就已经是新值</b>，
         * 不需要等布局、不需要 DisplayListener、不需要 pre-draw、不需要行高缓存。
         *
         * <p><b>为什么是"每个实例各自判断"而不是共享静态量 + 广播</b>（日志 53 实测）：
         * v107b 用静态 {@code sRotation} + {@code newRot == sRotation} 早退，
         * 结果三套 overlay 里只有最先发现的那个会命中，另外两个永远不动
         * ⇒ 状态栏一直停在竖屏的 105px，横屏被切。
         * 这里改成<b>每个实例各存一份</b> {@code appliedLandscape}，各判各的，
         * 值来自同一个全局 Configuration（必然一致），于是三套都会各自命中。
         *
         * <p>改动触发后还会再复核 {@value #SETTLE_FRAMES} 帧 —— 布局不是一步到位的
         * （日志 53：状态栏挂载那刻 {@code container=84x107}，稳定后 {@code 202x90}），
         * 多复核几帧让尺寸收敛到稳定值。值稳定后 {@code tryAttachIntoIconTree} 早退空转。
         */
        private void checkSize() {
            try {
                // ★ v108：无条件刷新全局方向标记（幂等，值由 Configuration 决定）。
                final boolean land = refreshLandscape(this);
                final int scale = TrioConfig.get().glyphScale;
                // ★ 行高也必须参与判定：锁屏↔状态栏切换、下拉收起↔展开、状态栏两行互换，
                //   行高都会变，而方向与「整体尺寸」没动 —— 旧逻辑只看这两项，
                //   于是尺寸停在上一行的取值上，被更矮的那一行裁掉（用户报的「偶尔图标裁剪缺失」）。
                final int rowH = (statusBarRoot == null) ? 0 : statusBarRoot.getHeight();
                if (land == appliedLandscape && scale == appliedScale && rowH == appliedRowH) {
                    // 方向/尺寸/行高都没变：只在"刚改过、还没收敛"时继续复核。
                    if (settleFrames > 0) {
                        settleFrames--;
                        remountNow();
                    }
                    return;
                }
                appliedLandscape = land;
                appliedScale = scale;
                appliedRowH = rowH;
                TrioHooks.log(TrioHooks.LOG_INFO, "rot: size change land=" + land
                        + " scale=" + scale
                        + " effScale=" + effectiveGlyphScale()
                        + " rowH=" + rowH
                        + " tree=" + treeAttached
                        + " root=" + ((statusBarRoot == null)
                                ? "?" : statusBarRoot.getClass().getSimpleName()));
                remountNow();
                armSettle();
            } catch (Throwable ignored) {
                // 尺寸复核绝不能影响绘制
            }
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // ★ 每帧校验裁剪标志，而不是只在"应用尺寸"时。
            //   实机现象：停用 OneuiXP 不裁、启用就"过一会儿被上下裁"，打开我们 App 又立刻还原
            //   —— 说明别的模块会周期性重建/重排状态栏那棵树，把祖先的
            //   clipChildren / clipToPadding 恢复成 XML 默认（true），上下溢出部分被裁。
            //   我们此前只在改尺寸时重开，没触发尺寸变化就一直被裁。
            //   内部只在标志真的被打开时才写回，正常情况下零开销、不引发重排。
            reassertClipOpen();
            // ★ v108：每帧尺寸复核（取代 v101b 的 checkRotation）。
            checkSize();
            try {
                // ★ v74：防烧屏开着但循环已停（上次关掉时停的）→ 重新拉起。
                //   关时停循环是为了不空转；重新打开后必须由一次重绘把循环叫回来，
                //   否则没人再驱动位移。系统状态栏随时会重绘（时钟/电量/信号），
                //   届时这里自愈，最坏晚一个状态栏刷新周期。
                if (burnInActive() && !mLooping) {
                    startLoop();
                }
                state.refresh();
                // ★ v67：每帧复核一次左边距 —— "左邻居是不是电池"会变，
                //   而且挂载那一刻电池可能还没测量（宽度 0），那时算出来必然是 0。
                ensureLeftMargin();
                // ★ v82：整组左右平移的另一半（translationX）。放这里是因为
                //   防烧屏关着时每秒循环是停的，只有绘制这条路每条路径都经过。
                syncGroupShift();
                TrioRenderer.drawState(canvas, getWidth(), getHeight(), state, TrioConfig.get());
                if (!drawLogged) {
                    drawLogged = true;
                    // 打印 inkScale：宽度/高度小于 INK_W(117) 时整体会缩小，
                    // 电量环与信号点会跟着变小（v24 信号点消失的根因）。
                    final float inkScale = TrioRenderer.inkScale(
                            canvas.getWidth(), canvas.getHeight());
                    // ★ 信号四点判定链诊断：一次定位"没画"还是"画了看不见"
                    final TrioSettings cfg = TrioConfig.get();
                    final TrioAppearance app = TrioAppearance.of(cfg);
                    TrioHooks.log(TrioHooks.LOG_INFO, "overlay: draw ok canvas="
                            + canvas.getWidth() + "x" + canvas.getHeight()
                            + " inkScale=" + inkScale
                            + " level=" + state.level
                            + " wifi=" + state.wifiLevel
                            + " mobile=" + state.mobileLevel
                            + " [" + TrioState.rawSignalDiag() + "]"
                            + " [chargeType=" + TrioState.chargeTypeDiag() + "]"
                            // ★ v77：去抖后的充电/省电/低电量三态，用来核对颜色是否还跳
                            + " [chg=" + state.charging + " quick=" + state.quickCharging
                            + " low=" + state.low + " ps=" + state.powerSave + "]"
                            + " fg=" + Integer.toHexString(state.foreground())
                            // 信号四点判定链
                            + " || DOTS mobile=" + app.mobile
                            + " signalInRing=" + app.signalInRing
                            + " signalDots=" + app.signalDots()
                            + " showMobile=" + cfg.showMobile
                            + " signalMode=" + cfg.signalMode
                            + " slots=" + java.util.Arrays.toString(state.slotLevels)
                            + " dotY=" + TrioGeometry.DOTS[0][1]
                            + " dotR=" + TrioGeometry.DOT_R);
                    // 自诊断：有蜂窝信号、但因设置开关导致四点/信号被跳过时，
                    // 直接给出可执行的修复提示（避免用户反复猜测是渲染问题）。
                    if (!app.signalDots() && state.mobileLevel > 0) {
                        TrioHooks.log(LOG_OK, "overlay: HINT signal dots hidden by settings"
                                + " (showMobile=" + cfg.showMobile
                                + ", signalMode=" + cfg.signalMode
                                + ") -> enable 'Show mobile signal' in SamsungDuo settings");
                    }
                }
            } catch (Throwable t) {
                TrioHooks.log(TrioHooks.LOG_WARN, "overlay: onDraw error: " + t);
            }
        }
    }
}