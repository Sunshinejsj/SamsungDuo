package com.jsj.samsungduo;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

/**
 * SamsungDuo · 三星 One UI 8 / Android 16 移植版 hook 层（v8）。
 *
 * <p><b>当前职责（已大幅收窄）：</b>只负责让三合一叠加层能显示并读到正确数值：
 * <ol>
 *   <li>捕获状态栏根 View（{@code StatusBarIconView.onAttachedToWindow} 上溯 +
 *       {@code CollapsedStatusBarFragment.onViewCreated} 兜底），交给
 *       {@link TrioOverlay} 挂载；</li>
 *   <li>抓系统真实图标色（{@code StatusBarIconView.setIconColor}）供叠加层跟随明暗主题。</li>
 * </ol>
 *
 * <p><b>刻意不做的事（历史教训，勿轻易加回）：</b>不隐藏任何原生状态栏图标。
 * <ul>
 *   <li>One UI 8 的 wifi/信号图标由 {@code MobileIconViewModel/WifiViewModel.getShadowDrawable}
 *       等新 pipeline ViewModel 绘制，这些 ViewModel 被多个 binder 共用，
 *       <b>hook 它返回 null 会破坏 pipeline 内部状态 → SystemUI 崩溃、LSPosed 进安全模式</b>；
 *       真参考 app_freezer 的做法是 {@code setResult(自建的合法 Drawable)}，绝不返回 null。</li>
 *   <li>{@code StatusBarIconController.getIconHideList} / {@code TunerServiceImpl.getValue}
 *       篡改返回值、{@code BatteryMeterView} 构造器里提前 setVisibility 均属同类高危。</li>
 *   <li>按用户要求，隐藏原生图标（移动信号/WLAN/电池）统一用三星系统自带开关完成。</li>
 * </ul>
 *
 * <p><b>安全约定（所有回调必须遵守）：</b>每个 {@code intercept} 都先 {@code chain.proceed()}
 * 再把自有逻辑包在 try/catch 里，任何异常只写日志，绝不抛回 SystemUI。
 */
public final class TrioHooks {

    private static final String TAG = "SamsungDuo";

    /** 与原项目日志级别常量保持一致，供 TrioState 调用。 */
    public static final int LOG_INFO = android.util.Log.INFO;
    public static final int LOG_WARN = android.util.Log.WARN;

    /** 状态栏根 View（由 icon-attach/onViewCreated 捕获），供叠加层定位。 */
    private static volatile View sStatusBarView;

/**
     * 已启动叠加层的状态栏根 View 集合。
     *
     * <p>三星有两套状态栏（普通 {@code PhoneStatusBarView} / 锁屏
     * {@code KeyguardStatusBarView}），<b>两边都要显示三合一</b>，所以不能用一个
     * 全局布尔锁——那样第二套（通常后触发）就永远挂不上。按 root 分别记录，
     * 每套各启动一次，icon-attach 的重复触发则被 contains 挡掉。
     */
    private static final Set<View> sStartedRoots =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<View, Boolean>());

    /** {@link #onStatusBarViewReady} 在尺寸未就绪时的时间窗节流间隔（ms）。 */
    private static final long READY_THROTTLE_MS = 500L;

    /** 上一次放行 {@link #onStatusBarViewReady} 的时刻（uptimeMillis）。 */
    private static volatile long sLastReadyAt = 0L;

    /**
     * 尺寸未就绪时记下的候选状态栏根 View（只记录不启动叠加层）。
     * 等它布局完成（尺寸 >0）后再真正启动，避免用 0x0 的中间容器建窗口。
     */
    private static volatile View sPendingRoot = null;

    /**
     * 最近一次 attach 的原生图标 View 样本（用于诊断：看原生图标挂在哪一层）。
     */
    private static volatile View sIconSample = null;

    /** 安装时捕获的模块句柄，供静态日志使用。 */
    private static volatile XposedModule sModule;

    /** 幂等：SystemUI 进程内只装一次。 */
    private static volatile boolean sInstalled = false;

    private TrioHooks() {
    }

    public static int hookAll(XposedModule module, ClassLoader cl) {
        sModule = module;
        // 绑定框架远程偏好，使设置 app 的改动能下发到 SystemUI（与原项目一致）。
        TrioConfig.install(module);
        if (sInstalled) {
            return 0;
        }
        sInstalled = true;
        int n = 0;
        // v6：只负责三合一叠加层的显示与取数，不再隐藏原生图标
        //（隐藏交给系统自带功能）。原因：隐藏需篡改 ViewModel/控制器返回值，
        // One UI 8 的 signal ViewModel 被多 binder 共用，篡改会破坏 pipeline ->
        // SystemUI 崩溃；三星自带隐藏开关更安全。链路只保留：
        // 1) 捕获状态栏根 View -> 叠加层挂载（icon-attach + onViewCreated 双锚点）；
        // 2) 抓系统真实图标色（跟随明暗主题）。
        //    ★ v58 更正：正路是 onDarkChanged（见文件末尾 6)），
        //       setIconColor 只是通知图标搬运颜色的旁路，抓它拿不到浅色背景的深色。
        // ★ v42：下拉面板 hook 再次停用（v41 实机仍崩溃 → 安全模式）。
//   已尝试并失败的两条路：
//     v39 = 普通 View 直接 addView进 StatusIconContainer → onLayout 崩溃
//     v41 = 让 GlyphView 声明 StatusIconDisplayable 的 9 个方法 → 仍崩溃
//   说明仅靠"让 instanceof 通过"不够——该容器还有 mMeasureViews /
//   getViewTotalMeasuredWidth / onLayout 的其它隐含约定，
//   在没有实机 View 树 dump 的情况下继续试是拿系统崩溃换进度。
//   现彻底关闭下拉面板，只保留已验证稳定的【状态栏 + 锁屏】两套。
// ★ v43：下拉面板【诊断模式】—— 只 dump 结构，绝不修改系统 View 树。
//   v39/v41 两次崩溃的共同原因：读 dex 猜容器结构，连续猜错。
//   本版只读不写（无 addView / 无 setVisibility / 无 setLayoutParams），
//   因此结构上不可能导致 SystemUI 崩溃。
//   dump 出真实结构后，再决定能否挂载以及该用哪种 LayoutParams。
        int shadeHooks = hookShadeHeader(module, cl); // 诊断专用
        int barHooks = hookStatusBarView(module, cl);
        n += barHooks;
        n += hookIconTint(module, cl);
        // ★ v58：真正的取色路径（浅色背景下和别的图标一起变深）。
        final int darkHooks = hookDarkReceivers(module, cl);
        n += darkHooks;
        log(LOG_INFO, "Samsung hooks installed: " + n
                + " (bar=" + barHooks
                + ", shade=" + shadeHooks
                + ", dark=" + darkHooks
                + ", tint=" + (n - barHooks - shadeHooks - darkHooks) + ")");
        return n;
    }

    // ------------------------------------------------------------------
    // 0) 下拉面板（通知栏/快捷设置）的状态栏图标区。
    //    反编译三星 SystemUI（One UI 8）确认：
    //      com.android.systemui.shade.ShadeHeaderController
    //        ├─ iconContainer : StatusIconContainer  ← 电池/信号/蓝牙/网速/闹钟都在这
    //        ├─ batteryIcon   : BatteryMeterView
    //        └─ onViewAttached() / onViewDetached()  ← 干净的生命周期钩子（无参）
    //    挂载方式与状态栏完全一致：把 iconContainer 当 root，
    //    TrioOverlay 会按 id 找到它内部的图标 LinearLayout 挂进去、抢位子。
    //    ★ 不猜任何 id：iconContainer 是从字段精确读出来的真实 ViewGroup。
    // ------------------------------------------------------------------
    private static int hookShadeHeader(XposedModule module, ClassLoader cl) {
        final Class<?> shc = Refl.cls(
                "com.android.systemui.shade.ShadeHeaderController", cl);
        if (shc == null) {
            log(LOG_WARN, "ShadeHeaderController missing (shade icons)");
            return 0;
        }
        final java.lang.reflect.Field fIconBox = Refl.field(shc, "iconContainer");
        // ★ v45：改用 header(MotionLayout) 作为 dump 起点。
        //   实测(v43/v44)：iconContainer(StatusIconContainer) 在 onViewAttached 时
        //   尺寸恒为 0x0，且之后似乎不再被布局 → 以它为根永远 dump 不到内容。
        //   header 是 ShadeHeaderController 构造器首参(MotionLayout)，
        //   是下拉面板头部的真实根 View，一定有尺寸，从它向下 dump 能拿到整棵树，
        //   iconContainer 也会作为其中一层被记录到。
        final java.lang.reflect.Field fHeader = Refl.field(shc, "header");
        final Method mAttached = Refl.method(shc, "onViewAttached");
        if (mAttached == null || fHeader == null) {
            log(LOG_WARN, "shade header members missing"
                    + " (onViewAttached=" + (mAttached != null)
                    + ", header=" + (fHeader != null) + ")");
            return 0;
        }
        // ★ v46：多锚点采集。实测 v43/v44/v45：onViewAttached 时
        //   iconContainer恒为 0x0，且 header 可能尚未赋值 → dump 拿不到结构。
        //   改为再 hook 两个更早/更全的时机，任何一个能拿到 header 就 dump：
        //     ① onInit()            —— 初始化，header 刚从构造器传入
        //     ② onViewAttached()    —— 视图 attach
        //   仍然纯只读（只调 Refl.get 读字段 + dumpOnce 输出日志）。
        int n = 0;
        final XposedInterface.Hooker reader = new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                final Object result = chain.proceed();
                try {
                    final Object self = chain.getThisObject();
                    sLastShadeInstance = self; // 供 retryShadeHeader 重试读取
                    final Object header = Refl.get(fHeader, self);
                    if (header instanceof View) {
                        final View hv = (View) header;
                        log(LOG_INFO, "shade header root observed "
                                + hv.getWidth() + "x" + hv.getHeight()
                                + " id=" + safeEntryName(hv));
                        // ★ v49：尺寸已就绪就直接挂载（onInit/onViewAttached 时机
                        //   通常 header 还是 0x0，会走 scheduleShadeDump 的延时重试）。
                        if (hv.getWidth() > 0 && hv.getHeight() > 0) {
                            onShadeHeaderReady(hv);
                        } else {
                            scheduleShadeDump(hv);
                        }
                    } else {
                        log(LOG_INFO, "shade header field null (not assigned yet)");
                    }
                } catch (Throwable t) {
                    log(LOG_WARN, "shade read failed: " + t);
                }
                return result;
            }
        };
        n += hook(module, mAttached, "samsungduo-shade", reader);
        final Method mInit = Refl.method(shc, "onInit");
        if (mInit != null) {
            n += hook(module, mInit, "samsungduo-shade-init", reader);
        }
        return n;
    }
    /** 下拉面板图标容器尺寸未就绪时的重试次数上限（防死循环）。 */
    private static int sShadeRetry = 0;

    /**
     * 等下拉面板图标容器布局完成后再dump 结构。
     *
     * <p>实测(v43)：{@code onViewAttached()} 触发时容器尺寸为 {@code 0x0}
     * （下拉面板还没展开、布局未完成），此时 dump 得不到有效结构。
     *
     * <p>方案：挂 {@link android.view.View.OnLayoutChangeListener}，
     * 首次拿到非零尺寸时 dump 并自动摘除监听（只读操作，不改任何 View 属性）。
     * 另加 post 延迟兜底，应对某些设备不回调布局变化的情况。
     */
    /**
     * 下拉面板头部布局就绪：dump 结构（有限次）并把三合一挂进下拉图标行。
     *
     * <p><b>★ v49：真正启用挂载。</b>依据是 v48 实机 dump（首次拿到真实结构）：
     * <pre>
     *   NoRemeasureMotionLayout split_shade_status_bar 1440x212 VISIBLE
     *     └ FrameLayout  shade_header_system_icons         216x84 VISIBLE
     *         └ LinearLayout hover_system_icons_container  216x84 VISIBLE
     *             ├ NetspeedView        networkSpeed
     *             ├ StatusIconContainer statusIcons        132x53  (20 children)
     *             └ BatteryMeterView    batteryRemainingIcon 84x49
     * </pre>
     *
     * <p>结构与状态栏的 {@code system_icons} <b>完全同构</b>：
     * 都是普通 {@code android.widget.LinearLayout} 装着 电池/信号/网速。
     * 所以"挂进 LinearLayout 抢位子"这条已经被实机验证稳定的路径可以直接复用，
     * 挂载点 = 那个 <b>普通 LinearLayout 父容器</b>。
     *
     * <p><b>为什么以前会崩</b>：v39/v41 读 dex 时猜错了层级，把 View 挂进了
     * {@code StatusIconContainer}（自定义 onMeasure/onLayout，只认 StatusIconDisplayable
     * 子 View），才导致 SystemUI 崩溃。现在：
     * <ol>
     *   <li>挂载点由 {@link TrioOverlay#findIconContainer} 按 id 名
     *       {@code hover_system_icons_container} 精确查找，是普通 LinearLayout；</li>
     *   <li>{@link TrioOverlay} 里另加<b>硬护栏</b>：类名命中 StatusIconContainer 直接拒绝挂载。</li>
     * </ol>
     */
    private static void onShadeHeaderReady(final View header) {
        if (header == null) {
            return;
        }
        try {
            ShadeDiagnostics.dumpOnce(header);
            ShadeDiagnostics.dumpChain(header);
            if (header instanceof ViewGroup) {
                TrioOverlay.onShadeReady((ViewGroup) header);
            }
        } catch (Throwable t) {
            log(LOG_WARN, "onShadeHeaderReady failed: " + t);
        }
    }

    private static void scheduleShadeDump(final View vg) {
        try {
            // ★ v47：无论尺寸如何，先无条件 dump 一次（纯读取，0 尺寸也能拿层级结构）
            ShadeDiagnostics.dumpOnce(vg);
            if (vg.getWidth() > 0 && vg.getHeight() > 0) {
                return;
            }
            // ★ v47：加"延时重试读字段"——onInit 时header 可能尚未赋值，
            //   且下拉面板可能直到用户真的下拉才布局。
            //   这里用 handler 做有限次重试（每 500ms，最多 20 次 = 10s），
            //   一旦拿到有尺寸的 header 立即 dump 并停止重试。纯读取，无副作用。
            retryShadeDump(0);
        } catch (Throwable t) {
            log(LOG_WARN, "scheduleShadeDump failed: " + t);
        }
    }

    /**
     * 有限次重试读取 {@code ShadeHeaderController.header} 并 dump（纯读取）。
     *
     * <p>实测(v43~v46)：{@code onInit} / {@code onViewAttached} 时 header 都是
     * {@code 0x0}（下拉面板尚未布局），甚至读到的 id 是
     * {@code split_shade_status_bar}（三星"双栏分屏下拉"专用布局，单栏下拉不参与布局）。
     * 因此改为每 500ms 重试一次、最多 20 次（10s），
     * 等用户真正下拉、header 拿到真实尺寸后再 dump。
     */
    private static void retryShadeDump(final int attempt) {
        if (attempt >= 20) {
            log(LOG_INFO, "shade header give up after 20 attempts");
            return;
        }
        MAIN_SHADE.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    final Object inst = sLastShadeInstance;
                    if (inst == null) {
                        retryShadeDump(attempt + 1);
                        return;
                    }
                    final java.lang.reflect.Field f = Refl.field(
                            inst.getClass(), "header");
                    final Object header = (f == null) ? null : Refl.get(f, inst);
                    if (header instanceof View) {
                        final View hv = (View) header;
                        if (hv.getWidth() > 0 && hv.getHeight() > 0) {
                            log(LOG_INFO, "shade header ready after "
                                    + (attempt + 1) + " attempts: "
                                    + hv.getWidth() + "x" + hv.getHeight()
                                    + " id=" + safeEntryName(hv));
                            // ★ v49：拿到就绪尺寸后真正挂载。
                            //   统一走 onShadeHeaderReady（内含 dump + 挂载），
                            //   避免与另一条路径重复维护。
                            onShadeHeaderReady(hv);
                            return;
                        }
                    }
                    retryShadeDump(attempt + 1);
                } catch (Throwable t) {
                    log(LOG_WARN, "retryShadeDump failed: " + t);
                }
            }
        }, 500L);
    }

    /** 最近一次见到的 ShadeHeaderController 实例（供重试读取 header 字段）。 */
    private static volatile Object sLastShadeInstance = null;

    /** 诊断用的主线程 handler（延时重试读字段）。 */
    private static final android.os.Handler MAIN_SHADE =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /** 安全读资源 id 名（诊断用）。 */
    private static String safeEntryName(View v) {
        try {
            final int id = v.getId();
            return id == View.NO_ID ? "none" : v.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return "?";
        }
    }

    // ------------------------------------------------------------------
    // 1) 捕获状态栏根 View（叠加层挂载锚点）。
    //    双锚点保证必触发：icon-attach 最可靠（图标一定 attach 到状态栏容器，
    //    沿 parent 上溯找状态栏根 View，不依赖 Fragment 生命周期）；
    //    CollapsedStatusBarFragment.onViewCreated 作兜底。
    //    两者都走同一个幂等且防刷屏的 onStatusBarViewReady()。
    // ------------------------------------------------------------------
    private static int hookStatusBarView(XposedModule module, ClassLoader cl) {
        int n = 0;
        // 锚点 A（普通状态栏主锚点）：CollapsedStatusBarFragment.onViewCreated(View, Bundle)
        // 反编译确认：onViewCreated 内部 check-cast 成 PhoneStatusBarView，
        // 所以参数就是【普通状态栏】(statusbar/phone/PhoneStatusBarView)。
        // ★ v101：One UI 9.0 已删除 CollapsedStatusBarFragment（对 9.0 SystemUI dex
        //   实测 0 命中）。旧代码"类找不到就 return 0"会连带跳过下面的锚点 B，
        //   导致 9.0 上状态栏整体不挂（日志 bar=0）。现改为：类找不到只跳过锚点 A、
        //   继续 hook 锚点 B —— StatusBarIconView.onAttachedToWindow 在 9.0 仍存在
        //   （dex 实证，含该方法），沿 parent 上溯找 PhoneStatusBarView 可独立挂载。
        final Class<?> frag = Refl.cls(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment", cl);
        if (frag == null) {
            log(LOG_WARN, "CollapsedStatusBarFragment missing (9.0 removed?) -> rely on StatusBarIconView anchor");
        } else {
            final Method mCreated = Refl.method(frag, "onViewCreated", View.class, Bundle.class);
            if (mCreated != null) {
                n += hook(module, mCreated, "samsungduo-view", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        final Object viewArg = chain.getArg(0);
                        if (viewArg instanceof View) {
                            onStatusBarViewReady((View) viewArg, "onViewCreated");
                        }
                        return result;
                    }
                });
            }
        }
        // 锚点 B（补充）：StatusBarIconView.onAttachedToWindow —— 图标 attach 时上溯。
        // ★ 必须排除锁屏那套：三星有【两套独立状态栏】
        //   普通：statusbar/phone/PhoneStatusBarView
        //   锁屏：statusbar/phone/KeyguardStatusBarView
        // v16 实机只��锁屏显示，正是因为上溯命中了 KeyguardStatusBarView。
        final Class<?> iconViewCls = Refl.cls(
                "com.android.systemui.statusbar.StatusBarIconView", cl);
        if (iconViewCls != null) {
            final Method mAttach = Refl.method(iconViewCls, "onAttachedToWindow");
            if (mAttach != null) {
                n += hook(module, mAttach, "samsungduo-icon-attach", new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        final Object result = chain.proceed();
                        try {
                            final Object self = chain.getThisObject();
                            if (self instanceof View) {
                                final View selfView = (View) self;
                                // 记录原生图标样本，用于诊断它挂在哪一层
                                if (sIconSample == null) {
                                    sIconSample = selfView;
                                }
                                // 只接受两套状态栏（普通 / 锁屏）——图标属于哪套，三合一就挂哪套
                                final View root = findStatusBarRootOf(selfView);
                                if (root != null) {
                                    onStatusBarViewReady(root, "icon-attach");
                                }
                            }
                        } catch (Throwable ignored) {
                            // ignore
                        }
                        return result;
                    }
                });
            }
        }
        if (n == 0) {
            log(LOG_WARN, "no statusbar view anchor hooked");
        }
        return n;
    }

    /**
     * 从一个图标 View 沿 parent 上溯，找出它所属的<b>状态栏根</b>。
     *
     * <p>三星 One UI 8 有<b>两套独立状态栏</b>，三合一在两边都要显示：
     * <ul>
     *   <li>普通界面：{@code statusbar/phone/PhoneStatusBarView}</li>
     *   <li>锁屏：{@code statusbar/phone/KeyguardStatusBarView}</li>
     * </ul>
     * 之前按"高度像状态栏"来猜根会命中锁屏那套（v16 只有锁屏显示）；
     * 现在沿链<b>精确匹配这两个类名</b>，先命中谁就返回谁——图标属于哪套，
     * 三合一就挂到哪套，两套互不干扰。
     */
    private static View findStatusBarRootOf(View v) {
        try {
            View cur = v;
            int guard = 0;
            while (cur != null && guard++ < 40) {
                final String cn = cur.getClass().getName();
                if (cn.equals("com.android.systemui.statusbar.phone.PhoneStatusBarView")
                        || cn.equals("com.android.systemui.statusbar.phone.KeyguardStatusBarView")) {
                    return cur;
                }
                cur = (cur.getParent() instanceof View) ? (View) cur.getParent() : null;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }

        /**
     * 把状态栏 View 树结构打进日志（限深/限节点）。
     *
     * <p>用途：icon-attach 上溯找到的"状态栏容器"实测是锁屏专用的（只有锁屏可见），
     * 光看类名猜不准。直接把真实的类名 + id + 尺寸打出来，就能确定三合一该挂哪一层。
     */
    private static void dumpTree(View root, String tag) {
        try {
            final StringBuilder sb = new StringBuilder();
            final ArrayList<View> queue = new ArrayList<>();
            final ArrayList<Integer> depths = new ArrayList<>();
            queue.add(root);
            depths.add(0);
            int guard = 0;
            while (!queue.isEmpty() && guard++ < 60) {
                final View v = queue.remove(queue.size() - 1);
                final int d = depths.remove(depths.size() - 1);
                if (v == null || d > 5) {
                    continue;
                }
                String idStr = "?";
                try {
                    final int id = v.getId();
                    if (id != View.NO_ID) {
                        idStr = v.getResources().getResourceEntryName(id);
                    }
                } catch (Throwable ignored) {
                    // ignore
                }
                sb.append("\n  ");
                for (int i = 0; i < d; i++) {
                    sb.append("  ");
                }
                sb.append(v.getClass().getSimpleName())
                        .append(" id=").append(idStr)
                        .append(" ").append(v.getWidth()).append("x").append(v.getHeight())
                        .append(" vis=").append(v.getVisibility());
                if (v instanceof ViewGroup) {
                    final int n = ((ViewGroup) v).getChildCount();
                    sb.append(" children=").append(n);
                    for (int i = 0; i < n && i < 12; i++) {
                        final View c = ((ViewGroup) v).getChildAt(i);
                        queue.add(c);
                        depths.add(d + 1);
                    }
                }
            }
            log(LOG_INFO, "TREE[" + tag + "]" + sb);
        } catch (Throwable t) {
            log(LOG_WARN, "dumpTree failed: " + t);
        }
    }

    /**
     * 幂等地接收状态栏根 View：初始化上下文并挂载叠加层。
     *
     * <p>防刷屏是这里的第一要务：{@code icon-attach} 会在每个图标 attach 时触发，
     * 实测可达每 5ms 一次；若不设防，几百次重复回调会把 SystemUI 拖死并刷爆日志。
     *
     * <p>三道防线：
     * <ol>
     *   <li><b>一次性锁</b>：{@link #sOverlayStarted} 一旦置 true 永不重复初始化
     *       （状态栏重建由 {@link TrioOverlay} 内部换 root 处理，不需要再走这里）；</li>
     *   <li><b>对象判重</b>：同一个 root 且尺寸已就绪则直接返回；</li>
     *   <li><b>时间窗节流</b>：尺寸为 0 时至少间隔 {@link #READY_THROTTLE_MS} 才放行一次，
     *       避免未布局期间以毫秒级频率反复进入。</li>
     * </ol>
     */
    private static void onStatusBarViewReady(View root, String via) {
        try {
            if (root == null) {
                return;
            }
            // 注意：这里<b>不能</b>用全局一次性锁——三星有两套状态栏（普通/锁屏）
            // 都要显示三合一，全局锁会把后触发的那一套挡掉。改为按 root 分别记录
            // （见防线 2 的 sStartedRoots）。
            final int w = root.getWidth();
            final int h = root.getHeight();
            // 防线 1：这个 root 已启动过（两套状态栏各自只启动一次）
            if (sStartedRoots.contains(root) && w > 0 && h > 0) {
                return;
            }
            // 防线 3：尺寸未就绪时按时间窗节流，避免毫秒级刷屏。
            if (w <= 0 || h <= 0) {
                final long now = android.os.SystemClock.uptimeMillis();
                if (now - sLastReadyAt < READY_THROTTLE_MS) {
                    return;
                }
                sLastReadyAt = now;
                // 尺寸未就绪：只记为候选，不启动叠加层。
                // 原因：icon-attach 在系统刚起来时会给出大量尺寸为 0 的中间容器，
                // 用它们启动随后又要重建，导致图标"闪一下就没"。等有效尺寸再启动。
                sPendingRoot = root;
                log(LOG_INFO, "statusbar root pending (w=" + w + " h=" + h + " via " + via + ")");
                return;
            }
            sStatusBarView = root;
            sPendingRoot = null;
            final android.content.Context ctx = root.getContext();
            TrioConfig.installReceiver(ctx);
            TrioState.attachContext(ctx);
            // 诊断：把状态栏真实 View 树打出来，确认挂载层是否正确
            dumpTree(root, "statusbar-root:" + root.getClass().getSimpleName());
            TrioOverlay.onStatusBarReady(root);
            // 记录该 root 已启动（普通/锁屏两套各记一次）
            sStartedRoots.add(root);
            log(LOG_INFO, "statusbar view ready via " + via + " (w=" + w + " h=" + h
                    + ") root=" + root.getClass().getSimpleName());
        } catch (Throwable t) {
            log(LOG_WARN, "onStatusBarViewReady(" + via + ") failed: " + t);
        }
    }

    // ------------------------------------------------------------------
    // 5) 截图标真实颜色（StatusBarIconView.setIconColor(int tint, boolean animate)）
    // ------------------------------------------------------------------
    private static int hookIconTint(XposedModule module, ClassLoader cl) {
        final Class<?> iconView = Refl.cls(
                "com.android.systemui.statusbar.StatusBarIconView", cl);
        if (iconView == null) {
            log(LOG_WARN, "StatusBarIconView missing (tint capture)");
            return 0;
        }
        final Method m = Refl.method(iconView, "setIconColor", int.class, boolean.class);
        if (m == null) {
            log(LOG_WARN, "StatusBarIconView.setIconColor(int,boolean) missing");
            return 0;
        }
        return hook(module, m, "samsungduo-tint", new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                final Object tintObj = chain.getArg(0);
                if (tintObj instanceof Integer) {
                    // ★ v77：带上来源 View，才能归属到对应那套状态栏
                    final Object self = chain.getThisObject();
                    TrioState.noteIconTint(self instanceof View ? (View) self : null,
                            ((Integer) tintObj).intValue());
                }
                return chain.proceed();
            }
        });
    }

    // ------------------------------------------------------------------
    // 6) 截"状态栏图标真实颜色"—— 浅色/深色背景切换的正路（v58）
    // ------------------------------------------------------------------

    /** 真正接收暗色图标调度的三个状态栏 View：图标、网速、电池。 */
    private static final String[] DARK_RECEIVERS = {
            "com.android.systemui.statusbar.StatusBarIconView",
            "com.android.systemui.statusbar.policy.NetspeedView",
            "com.android.systemui.battery.BatteryMeterView",
    };

    /**
     * ★ v58：抓 {@code onDarkChanged(ArrayList, float, int)}。
     *
     * <p><b>这是反汇编实证得出的正确取色路径。</b>浅色背景下状态栏图标变深色，
     * 走的是暗色图标调度器 → {@code DarkReceiver.onDarkChanged} →
     * {@code ImageView.setImageTintList}，<b>不经过 {@code setIconColor}</b>。
     * 而 {@code setIconColor} 全工程唯一的调用者是
     * {@code NotificationIconContainer$IconState.applyToView}（通知图标搬运），
     * 所以旧实现抓到的颜色来自通知图标，浅色背景下不会变 —— 这正是
     * "三合一没跟其他图标一起变色"的根因。
     *
     * <p>做法：在回调<b>返回之后</b>再读这个 View 已经被系统涂好的颜色。
     * 好处是不必复算调度器的调色公式（那个公式要 areas + darkIntensity，
     * 自己算容易错），拿到的就是系统当场画出去的颜色，天然一致。
     */
    private static int hookDarkReceivers(XposedModule module, ClassLoader cl) {
        int n = 0;
        for (String cls : DARK_RECEIVERS) {
            n += hookDarkReceiver(module, cl, cls);
        }
        return n;
    }

    private static int hookDarkReceiver(XposedModule module, ClassLoader cl, final String className) {
        final Class<?> c = Refl.cls(className, cl);
        if (c == null) {
            log(LOG_WARN, className + " missing (dark tint)");
            return 0;
        }
        final Method m = Refl.method(c, "onDarkChanged", ArrayList.class, float.class, int.class);
        if (m == null) {
            log(LOG_WARN, className + ".onDarkChanged(ArrayList,float,int) missing");
            return 0;
        }
        final String label = className.substring(className.lastIndexOf('.') + 1);
        return hook(module, m, "samsungduo-dark-" + label, new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                final Object result = chain.proceed();
                try {
                    final Object self = chain.getThisObject();
                    if (self instanceof View) {
                        final int color = tintFrom((View) self);
                        if (color != 0) {
                            // ★ v77：带上来源 View，归属到它真正所属的状态栏实例
                            TrioState.noteDarkTint((View) self, color);
                        }
                    }
                } catch (Throwable ignored) {
                    // 取色失败绝不能影响系统绘制
                }
                return result;
            }
        });
    }

    /**
     * 从一个"刚被系统涂好色"的 View 里读出当前颜色。
     *
     * <p>ImageView 读 {@code getImageTintList()}（{@code onDarkChanged} 就是用
     * {@code setImageTintList} 上色的，所以读回来就是最终色）；
     * TextView 读 {@code getCurrentTextColor()}（网速就是这么被染色的）；
     * ViewGroup（电池是自绘容器）则往下找第一个有色的子 View。
     */
    /** 反射取一个无参 int getter（如 Drawable.getTint / PorterDuffColorFilter.getColor）；取不到返回 0。 */
    private static int intGetter(Object target, String name) {
        if (target == null) {
            return 0;
        }
        try {
            for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
                final java.lang.reflect.Method m;
                try {
                    m = k.getDeclaredMethod(name);
                } catch (Throwable missing) {
                    continue;
                }
                final Object v = m.invoke(target);
                if (v instanceof Integer) {
                    return ((Integer) v).intValue();
                }
                return 0;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    static int tintFrom(View v) {
        if (v instanceof android.widget.ImageView) {
            final android.widget.ImageView iv = (android.widget.ImageView) v;
            final android.content.res.ColorStateList csl = iv.getImageTintList();
            if (csl != null) {
                // ★ 9.9 原样（照抄，勿动顺序）：优先取当前显示状态的色；
                //   三星部分图标（尤其锁屏）用状态化 ColorStateList，
                //   getDefaultColor() 在默认态返回 0，只有 getColorForState 才拿得到真色。
                final int c = csl.getColorForState(v.getDrawableState(), csl.getDefaultColor());
                if (c != 0) {
                    return c;
                }
                final int d = csl.getDefaultColor();
                if (d != 0) {
                    return d;
                }
            }
            // ★ OneUI 9.0 兜底：若染色没落在 ImageTintList 上（落在 drawable 自身 tint 或
            //   ColorFilter），上面会读 0，探针失效就会去抄状态栏的颜色。
            //   排在 ImageTintList 之后 ⇒ **8.5 完全走不到这里**（那里 csl 必非空且有值）。
            //   这两个 getter 在本工程 compileSdk stub 里不可见，故走反射取。
            final android.graphics.drawable.Drawable dr = iv.getDrawable();
            if (dr != null) {
                final int dt = intGetter(dr, "getTint");
                if (dt != 0) {
                    return dt;
                }
            }
            final int cc = intGetter(iv.getColorFilter(), "getColor");
            if (cc != 0) {
                return cc;
            }
        }
        if (v instanceof android.widget.TextView) {
            return ((android.widget.TextView) v).getCurrentTextColor();
        }
        if (v instanceof ViewGroup) {
            final ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                final int c = tintFrom(g.getChildAt(i));
                if (c != 0) {
                    return c;
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // 工具：安装一个 hook（容错：方法缺失或 hook 被拒都返回 0）
    // ------------------------------------------------------------------
    private static int hook(XposedModule module, Method m, String id,
                            XposedInterface.Hooker hooker) {
        if (m == null) {
            log(LOG_WARN, "skip " + id + ": method not found");
            return 0;
        }
        try {
            module.hook(m).setId(id).intercept(hooker);
            return 1;
        } catch (Throwable t) {
            log(LOG_WARN, "skip " + id + ": " + t);
            return 0;
        }
    }

    public static View getStatusBarView() {
        return sStatusBarView;
    }

    /**
     * 与本模块统一的日志入口：TrioState / TrioOverlay 都经由它打日志。
     *
     * <p>★ v10.x：去掉 LSPosed 框架日志（不再向框架写日志），只在本机
     * /sdcard/SamsungDuo_debug.log 落盘，且是否落盘由 SamsungDuoModule.flog 根据
     * 关于页“调试日志”开关决定。level 形参保留以兼容现有调用点，不再使用。
     */
    public static void log(int level, String msg) {
        SamsungDuoModule.flog(msg);
    }
}
