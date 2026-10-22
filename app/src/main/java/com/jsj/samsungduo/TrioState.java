package com.jsj.samsungduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.CarrierConfigManager;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.view.View;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Samsung One UI 8 / Android 16 移植版取数层。
 *
 * <p>原小米版的电量与 Wi‑Fi 全部读 MIUI 私有字段 / 信号图标资源名，三星上都不存在。
 * 本文件把它们换成 Android 标准 API：
 * <ul>
 *   <li><b>电量 / 充电</b>：标准 {@link BatteryManager} 的 {@link Intent#ACTION_BATTERY_CHANGED}
 *       粘性广播，读 {@code EXTRA_LEVEL}/{@code EXTRA_SCALE}/{@code EXTRA_PLUGGED}/
 *       {@code EXTRA_STATUS}；快充走三星私有 {@code SemBatteryManager}（反射，失败即降级）。</li>
 *   <li><b>Wi‑Fi</b>：{@link WifiManager} + {@link ConnectivityManager}，监听
 *       {@code RSSI_CHANGED_ACTION}/{@code NETWORK_STATE_CHANGED_ACTION}，
 *       用 {@link WifiManager#calculateSignalLevel} 算 0..4 级，连接状态决定 Wi‑Fi 弧是否显示。</li>
 *   <li><b>移动信号 / 双卡</b>：<b>原样保留</b> 原项目的 {@link TelephonyManager} +
 *       {@link SubscriptionManager} 直读（AOSP 标准，小米版本就这么读）。</li>
 *   <li><b>图标颜色</b>：由 {@code TrioHooks} 截 {@code StatusBarIconView.setIconColor}
 *       拿到 SystemUI 此刻真正画的图标色（{@link #sIconTint}），比小米那套多行 tint 字段稳。</li>
 * </ul>
 *
 * <p>对外暴露的字段 / 静态量（{@code level}/{@code charging}/{@code wifiLevel}/
 * {@code wifiPresent}/{@code mobileType}/{@code slotLevels}/{@code sims}/
 * {@link #foreground()} 等）与原文件保持一致，渲染层 {@code TrioOverlay}/
 * {@code TrioRenderer} 无需改动即可继续工作。
 */
final class TrioState {

    // ----------------------------------------------------- 共享信号量（名字不变）
    static volatile int sWifiLevel = -1;
    static volatile int sMobileLevel = -1;
    static volatile boolean sWifiPresent = false; // 三星：连上才显示
    static volatile boolean sIslandHideBattery = false; // One UI 无超级岛，恒 false
    static volatile String sMobileType = "";
    static final int SIM_SLOTS = 2;
    static volatile int[] sSlotLevels = { -1, -1 };
    static volatile int sDataSlot = -1;

    // ------------------------------------------------- 信号读取：回调式缓存（v100）
    //
    // ★ One UI 9.0 适配：此前 levelOf 里用 TelephonyManager.getSignalStrength()
    //   **同步轮询**取 SignalStrength，实测 9.0 上该方法返回的是 cells=[]、
    //   rssi=-127、level=-1 的空对象（vendor=-1、cells=[]、mLevel=-1），
    //   导致环内四点/环外信号读数整个失效。
    //   参照 OneUI XP（OneuiXP_1.0.261013.apk，SDK37）的做法：改用**回调式**
    //   PhoneStateListener.onSignalStrengthsChanged 缓存每张卡最新的真实
    //   SignalStrength，levelOf 优先读缓存，读不到再退回同步轮询。
    //   同时补一条公开 API 兜底 levelFromCells（getCellSignalStrengths 取最高档），
    //   让 9.0 上 getVendorLevel 缺失时也能拿到合理档位。
    /** 回调缓存：subId → 最近一次真实 SignalStrength。 */
    private static final SparseArray<SignalStrength> sSigBySub = new SparseArray<>();
    private static final Object sSigLock = new Object();
    /** 当前已绑定监听的 subId（按 slot 存，-1 = 未占用）。 */
    private static volatile int[] sBoundSubs = { -1, -1 };
    private static final PhoneStateListener[] sBoundListeners = new PhoneStateListener[SIM_SLOTS];

    /** v56 只读诊断：阈值配置只 dump 一次；失败可重试。 */
    private static volatile boolean sSigCfgDumped = false;

    /** v56 只读诊断：已重试次数（读太早会拿到空配置，留几次机会）。 */
    private static volatile int sSigCfgTries = 0;

    /** 最多重试次数。每次采样间隔 2s，所以最多多等 ~10s。 */
    private static final int SIGCFG_MAX_TRIES = 5;

    // ----------------------------------------------------- 电量（标准 BatteryManager）
    static volatile int sBatteryLevel = -1;
    static volatile boolean sBatteryCharging = false;
    static volatile boolean sBatteryQuick = false;
    /** 最近一次电池广播 Intent（缓存供 readQuick 读取充电类型 extra）。 */
    private static volatile android.content.Intent lastBatteryIntent;
    static volatile boolean sBatteryLow = false;
    static volatile boolean sBatteryPowerSave = false;
    static volatile boolean sBatteryPerf = false;

    // ------------------------------------------------- 电池温度（v73，v75 只剩温度）
    /**
     * 电池温度取不到时的哨兵值（{@code Integer.MIN_VALUE}）。
     */
    static final int TEMP_UNKNOWN = Integer.MIN_VALUE;
    /**
     * 电池温度（整摄氏度），或 {@link #TEMP_UNKNOWN}。
     *
     * <p>★ v75：改由 {@link #readBattery} 直接从 ACTION_BATTERY_CHANGED 的
     * {@code EXTRA_TEMPERATURE} 写入 —— 功率读数删除后，不再需要
     * {@code BatteryPresence} 那个独立的取数器（连带它的 3s 轮询一起没）。
     * 温度随电池广播更新：电量/插拔/温度变化时平台都会发，够用。
     */
    static volatile int sBatteryTempC = TEMP_UNKNOWN;

    /**
     * ★ v77：图标色改为<b>按 host 隔离</b>（每处状态栏各一份）。
     *
     * <p>原先是一个 {@code static} 字段，而 {@code TrioState} 本身是每处一个实例
     * （{@code new TrioState(root)}）—— 于是状态栏 / 下拉 / 锁屏<b>共享同一个颜色</b>，
     * 任何一处的取色回调都会写进去，最后一次回调的赢。用户实测"下拉在浅色背景下
     * 仍是深色"，根因就是这里串色。现在改为实例字段，并按来源 View 的
     * {@code rootView} 把回调归属到对应实例（见 {@link #forView}）：
     * <b>状态栏跟状态栏、下拉跟下拉、锁屏跟锁屏</b>，各自跟随所在界面的明暗。
     */
    private volatile int mIconTint = 0;

    /** ★ v77：权威取色路径（onDarkChanged）是否已给过值 —— 实例级。 */
    private volatile boolean mGotDarkTint = false;

    /**
     * ★ v77：归属不明（找不到对应实例）时的兜底色。
     *
     * <p>宁可退回到"可能不准"，也不能让图标取不到颜色（那会变成隐形）。
     */
    private static volatile int sFallbackTint = 0;

    /** ★ v77：host → TrioState 弱映射，用于把取色回调归属到对应状态栏实例。 */
    private static final java.util.Map<View, java.lang.ref.WeakReference<TrioState>> sByHost =
            new java.util.WeakHashMap<View, java.lang.ref.WeakReference<TrioState>>();

    private static volatile Context sContext;
    private static volatile WifiManager sWifiManager;
    /** ★ v77：日志去重改为实例级（三处各自的颜色不再互相覆盖）。 */
    private volatile String mLastTint;
    private static volatile long sSimsDueAt;
    private static final long SIMS_INTERVAL_MS = 2000L;

    final View host;

    // 本 host 的电量状态（每帧从静态量搬运，保持原字段语义）
    int level = -1;
    boolean charging;
    boolean quickCharging;
    boolean low;
    boolean powerSave;
    boolean performanceMode;

    // 本 host 的 tint 状态（三星上不再由 MIUI 字段填充，仅占位以保持 API 不变）
    boolean useTint;
    int tintColor;
    int lightColor;
    int darkColor;
    float darkIntensity;
    int outRingInk;

    // 共享信号等级（每帧从静态量搬运）
    int wifiLevel = -1;
    int mobileLevel = -1;
    boolean wifiPresent = false;
    boolean islandHideBattery;
    String mobileType = "";
    int[] slotLevels = { -1, -1 };
    /** ★ v59：当前上网卡所在槽位，环外读数按它决定画哪张卡（与环内四点同源）。 */
    int dataSlot = -1;
    int sims;

    TrioState(View host) {
        this.host = host;
        synchronized (sByHost) {
            sByHost.put(host, new java.lang.ref.WeakReference<TrioState>(this));
        }
    }

    /**
     * ★ v77：把一次取色回调归属到它真正所属的那套状态栏。
     *
     * <p>三套状态栏位于不同窗口，{@code getRootView()} 各不相同，按它匹配即可。
     * 匹配不到返回 null，由 {@link #sFallbackTint} 兜底。
     */
    private static TrioState forView(View v) {
        if (v == null) {
            return null;
        }
        try {
            final View vr = v.getRootView();
            if (vr == null) {
                return null;
            }
            synchronized (sByHost) {
                for (java.util.Map.Entry<View, java.lang.ref.WeakReference<TrioState>> e
                        : sByHost.entrySet()) {
                    final View h = e.getKey();
                    final TrioState st = (e.getValue() == null) ? null : e.getValue().get();
                    if (st == null || h == null) {
                        continue;
                    }
                    if (h.getRootView() == vr) {
                        return st;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 归属判定失败一律走兜底，绝不影响系统
        }
        return null;
    }

    // ============================================================ 接入 / 上下文

    /**
     * ★ v77：布尔量去抖（消除"每秒轮询 × 广播更新"相位不一致造成的颜色闪烁）。
     *
     * <p>规则：原始值与当前值不同时，要求它**连续 2 次**保持一致才真正切换；
     * 中途变回去则重新计数。切换延迟最多一个轮询周期（1s），对充电/省电/低电量
     * 这类状态完全无感，但能干净地消掉"每秒跳一下"的视觉抖动。
     */
    private boolean settle(int idx, boolean cur, boolean raw) {
        if (raw == cur) {
            settlePending[idx] = cur;
            settleCount[idx] = 0;
            return cur;
        }
        if (raw == settlePending[idx]) {
            settleCount[idx]++;
        } else {
            settlePending[idx] = raw;
            settleCount[idx] = 1;
        }
        if (settleCount[idx] >= 2) {
            settleCount[idx] = 0;
            return settlePending[idx];
        }
        return cur;
    }

    /** {@link #settle} 的待定值与连续计数（按 idx 与调用点一一对应）。 */
    private final boolean[] settlePending = new boolean[4];
    private final int[] settleCount = new int[4];

    // ---------------------------------------------------- 实时取色（v77）

    /**
     * ★ v77：实时从**本实例自己**的 host 树里读一个原生图标的当前颜色。
     *
     * <p>为什么必须实时读：回调归因（{@link #forView}）只对状态栏可靠 ——
     * 系统的暗色图标调度器把 {@code onDarkChanged} 推到状态栏那棵树的图标上，
     * 下拉与锁屏的实例因此永远收不到归属自己的回调（{@code mIconTint} 恒 0），
     * 只能吃全局兜底值，表现为"下拉/锁屏不跟随自己界面的明暗"。
     * 直接读自己树里的原生图标，则三处各自独立、且与原生同帧同值。
     */
    private int liveTintFromHost() {
        try {
            final View probe = findTintProbe();
            if (probe != null) {
                int t = TrioHooks.tintFrom(probe);
                // ★ 9.0 专属修正：OneUI 9.0 状态栏**左侧**的三星时钟/运营商名被染成固定灰
                //   ff6f767d，9.9 的单 DFS 会先撞上它 → 状态栏取色恒灰。
                //   这里**只在读到那个固定灰时**才换用真系统图标类的探针；
                //   8.5 树里根本不会出现这个值 ⇒ 8.5 的取值路径与 9.9 逐行一致。
                // ★ 浅色模式：时钟/运营商名这类非系统图标在 OneUI 浅色下被染成**纯黑**
                //   （0xFF000000），而系统图标类（电池/网速）是**深灰**。DFS 先撞到纯黑
                //   那个时，我们的图标就比状态栏原生图标黑一截 —— 与原生不一致。
                //   和 GRAY_CLOCK_9 一样处理：读到纯黑就改用系统图标类的颜色。
                // ★ 10-22 实修：判定必须**只看 RGB、忽略 alpha**。
                //   实机日志（22:45~23:13）里探针撞到的是 TextView（三星时钟/运营商名），
                //   读回的是 b3000000 / c4939393 / d1bcbcbc 这类**带 alpha** 的黑与灰，
                //   而这里原本写成 `t == GRAY_CLOCK_9(0xff6f767d) || t == PURE_BLACK(0xff000000)`，
                //   alpha 一变就整个失之交臂 ⇒ 黑色被当成有效图标色直接采信，再被
                //   foreground() 的 `| 0xFF000000` 锤成 **ff000000 纯黑**
                //   —— 就是用户报的「深色模式纯黑」（日志 iconTint=edffffff → ff000000）。
                // ★ 10-22 二修（实机 06:44~06:45）：探针几乎必然先撞到 TextView
                //   （三星状态栏左侧的时钟/运营商名），读到的是**文字色** ——
                //   半透明灰 caa7a7a7 / d1bcbcbc，或黑 b3000000 ——
                //   与原生图标（电池/信号）根本不同步，深色模式下就画出 ffa6a6a6 的中灰
                //   （日志 06:44:21 fg=ffffffff → 06:45:05 fg=ffa6a6a6）。
                //   真系统图标类才是与原生图标同步的那一路：**只要探针不是它，就以它为准**；
                //   树里没有它才沿用探针色（最坏情况 = 现状，不会更差）。
                if (!isSystemIconView(probe)) {
                    final View sys = searchSystemIcon(host, 0);
                    if (sys != null) {
                        final int t2 = TrioHooks.tintFrom(sys);
                        if (t2 != 0 && !isUnusableInk(t2)) {
                            t = t2;
                        }
                    }
                }
                // 仍拿到墨色（黑 / 近黑 / OneUI 固定灰）⇒ 不可用作图标色，返回 0
                //   交给 foreground() 的 bg / sLastGoodTint / nightMode 链兜底。
                //   **绝不把黑色交给渲染层**，也绝不让它存进 sLastGoodTint 污染下拉/锁屏。
                if (isUnusableInk(t)) {
                    return 0;
                }
                if (t != 0) {
                    mLastLiveTint = t;
                    sLastGoodTint = t;
                    // 诊断：这次颜色是从**本界面这棵树里的哪个原生图标**读到的。
                    // 下拉读到的若是状态栏图标类，说明它被状态栏调度器刷过，需要换探针。
                    final String note = "probe[" + probe.getClass().getSimpleName()
                            + "]=" + Integer.toHexString(t);
                    if (!note.equals(mLastProbeNote)) {
                        mLastProbeNote = note;
                        TrioHooks.log(TrioHooks.LOG_INFO, note);
                    }
                    return t;
                }
            }
            // 探针暂时拿不到：沿用本实例上一次成功取到的值（他没变就不变），
            // 绝不回退到状态栏那棵树的深色 —— 否则下拉/锁屏会"他没变就变"，来回跳。
            return mLastLiveTint;
        } catch (Throwable ignored) {
            return mLastLiveTint;
        }
    }

    /**
     * 找一个"会被系统染色"的原生图标当探针；命中后缓存，丢失才重搜。
     *
     * <p>搜索<b>限频 500ms</b>：探针命中时每帧只做一次 {@code getImageTintList()}
     * （几乎零成本）；只有探针所在 View 被系统重建时才重新遍历，且不会每帧遍历。
     */
    private View findTintProbe() {
        View c = (tintProbe == null) ? null : tintProbe.get();
        if (c != null && c.getParent() != null) {
            return c;
        }
        // ★ 9.9 原样：单个 DFS —— 先认三个系统图标类；遇到非容器且"已染色"的 View 也直接返回
        //   （这条内联兜底正是 9.9 把下拉取对的关键：下拉那棵树里的图标类/染色路径与状态栏不同）。
        //   ⚠ 不要再拆成"系统图标类优先 + 任意染色兜底"两阶段（那是适配 9.0 时改的），
        //   两阶段会让 DFS 不再按树序先遇到谁就取谁，8.5 下拉因此变回跟状态栏一样。
        final View found = searchTintProbe(host, 0);
        tintProbe = new java.lang.ref.WeakReference<View>(found);
        return found;
    }

    /** 9.9 原样的单 DFS 探针搜索。 */
    private static View searchTintProbe(View v, int depth) {
        if (v == null || depth > 12) {
            return null;
        }
        try {
            final String cn = v.getClass().getName();
            if (cn.equals("com.android.systemui.statusbar.StatusBarIconView")
                    || cn.equals("com.android.systemui.statusbar.policy.NetspeedView")
                    || cn.equals("com.android.systemui.battery.BatteryMeterView")) {
                return v;
            }
            if (!(v instanceof android.view.ViewGroup)) {
                if (TrioHooks.tintFrom(v) != 0) {
                    return v;
                }
            }
            if (v instanceof android.view.ViewGroup) {
                final android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    final View r = searchTintProbe(g.getChildAt(i), depth + 1);
                    if (r != null) {
                        return r;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 阶段一：全树找第一个【真系统图标类】（与系统暗色调度器染色路径同类）。
     * 只认这三类，绝不被其它已染色元素（时钟/运营商名等）抢先 —— 修 9.0 状态栏取色恒灰。
     */
    private static View searchSystemIcon(View v, int depth) {
        if (v == null || depth > 12) {
            return null;
        }
        try {
            final String cn = v.getClass().getName();
            if (cn.equals("com.android.systemui.statusbar.StatusBarIconView")
                    || cn.equals("com.android.systemui.statusbar.policy.NetspeedView")
                    || cn.equals("com.android.systemui.battery.BatteryMeterView")) {
                return v;
            }
            if (v instanceof android.view.ViewGroup) {
                final android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    final View r = searchSystemIcon(g.getChildAt(i), depth + 1);
                    if (r != null) {
                        return r;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 阶段二（兜底）：全树找第一个"非容器且已染色"的 View。
     * 仅当阶段一找不到系统图标类时才走（覆盖三星锁屏可能用不同图标类 / 染色路径，
     * 导致原三类硬编码名单漏命中、探针为 null 而锁屏被强制 nightMode 兜底的现象）。
     */
    private static View searchAnyTinted(View v, int depth) {
        if (v == null || depth > 12) {
            return null;
        }
        try {
            if (!(v instanceof android.view.ViewGroup)) {
                if (TrioHooks.tintFrom(v) != 0) {
                    return v;
                }
            }
            if (v instanceof android.view.ViewGroup) {
                final android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    final View r = searchAnyTinted(g.getChildAt(i), depth + 1);
                    if (r != null) {
                        return r;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private java.lang.ref.WeakReference<View> tintProbe;
    /** ★ v82b：本实例上一次成功从原生探针读到的颜色；探针暂时拿不到时沿用，绝不借其它实例（状态栏）的深色。 */
    private int mLastLiveTint = 0;
    // ★ 已删除 sLastGoodTint（跨实例 static 全局色）：它让"探针读不到"的下拉/锁屏
    //   直接抄状态栏的颜色，造成三处界面被绑在一起。兜底改为本界面自己的背景（hostThemeTint）。

    /**
     * 在最早拿到真实 SystemUI 上下文处调用（见 TrioHooks.onViewCreated）。
     * 安装电量 / Wi‑Fi 标准广播，不再依赖任何 MIUI 私有类。
     */
    static void attachContext(Context context) {
        if (context == null || sContext != null) {
            return;
        }
        try {
            final Context app = context.getApplicationContext();
            sContext = (app != null) ? app : context;
            startBattery(sContext);
            startWifi(sContext);
            // 保留原项目的其它接收器（如配置变更）。若 TrioConfig 内部仍引用
            // MIUI 类，这里会抛异常并被吞掉——三星上电量/Wi‑Fi 的权威来源是本文件。
            TrioConfig.installReceiver(sContext);
        } catch (Throwable ignored) {
            // 任一接收器注册失败都不影响另一路
        }
    }

    /**
     * TrioHooks 截到 {@code StatusBarIconView.setIconColor} 时调用（v58 的兜底取色路径）。
     *
     * <p>★ v77：按来源 View 归属到对应状态栏实例，不再写全局共享值。
     */
    static void noteIconTint(View src, int tint) {
        if (tint == 0) {
            return;
        }
        sFallbackTint = tint;
        final TrioState st = forView(src);
        if (st != null) {
            st.applyFallbackTint(tint);
        }
    }

    /** 兜底路径的写入：本实例还没拿到权威值时才接受（v58 的原意）。 */
    private void applyFallbackTint(int tint) {
        if (mGotDarkTint) {
            return;
        }
        applyTint(tint);
    }

    /**
     * ★ v58：状态栏图标的**真实颜色**，由 {@code onDarkChanged} 回调写入。
     *
     * <p><b>为什么要换源：</b>反汇编三星 SystemUI 实证——浅色/深色背景切换时，
     * 状态栏图标的颜色走的是
     * {@code DarkIconDispatcher.DarkReceiver.onDarkChanged(areas, darkIntensity, tint)}
     * → {@code ImageView.setImageTintList}，<b>完全不经过
     * {@code StatusBarIconView.setIconColor}</b>。
     *
     * <p>而 {@code setIconColor} 在全工程只有<b>一个</b>调用者：
     * {@code NotificationIconContainer$IconState.applyToView} ——
     * 那是<b>通知图标</b>在容器之间搬运时同步颜色的路径。
     * 旧实现抓的就是它，所以拿到的其实是通知图标的颜色，
     * 在浅色背景下根本不会变深，三合一便一直保持浅色（用户实测截图）。
     *
     * <p>{@code onDarkChanged} 是系统下发到每个状态栏图标的正路：
     * 背景一变、或图标刚注册，调度器就会推一次当前状态。
     * 我们只需在回调<b>返回之后</b>读这个 View 已经被涂好的颜色即可
     * （{@code getImageTintList()} / {@code getCurrentTextColor()}），
     * 不必自己去复算调度器的调色公式。
     */
    /** ★ v77：按来源 View 归属后写入，权威路径（onDarkChanged）。 */
    static void noteDarkTint(View src, int tint) {
        if (tint == 0) {
            return;
        }
        sFallbackTint = tint;
        final TrioState st = forView(src);
        if (st != null) {
            st.mGotDarkTint = true;
            st.applyTint(tint);
        }
    }

    /** 颜色真的变了才写，并且立刻让叠加层重绘（背景切换与电量/信号无关，不会自动重绘）。 */
    private void applyTint(int tint) {
        if (tint == 0 || tint == mIconTint) {
            return;
        }
        mIconTint = tint;
        TrioOverlay.invalidateAll();
    }

    // ============================================================ 电量：BatteryManager

    private static void startBattery(Context ctx) {
        // 先读一次粘性广播，避免首帧 -1。
        try {
            final Intent sticky = ctx.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky != null) {
                lastBatteryIntent = sticky;
                readBattery(ctx, sticky);
            }
        } catch (Throwable ignored) {
            // API 33+ 对 null 接收器的粘性读取偶有限制，交给下面的实时接收器兜底
        }
        reg(ctx, new BatteryReceiver(),
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        // ★ v77：省电模式切换**不发电池广播**，此前只在 readBattery 里读
        //   isPowerSaveMode()，于是状态一直滞后到下一次插拔充电器才刷新
        //   （他人实测："省电模式切换，他要擦一下充电器才会切换"）。
        //   这里补挂省电模式广播，切换即刻生效。
        reg(ctx, new PowerSaveReceiver(),
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        refreshPowerSave(ctx);
    }

    /** 读一次系统省电模式；变了才写并立刻重绘。 */
    private static void refreshPowerSave(Context ctx) {
        try {
            final Context c = (ctx != null) ? ctx : sContext;
            if (c == null) {
                return;
            }
            final PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return;
            }
            final boolean ps = pm.isPowerSaveMode();
            if (ps != sBatteryPowerSave) {
                sBatteryPowerSave = ps;
                TrioOverlay.invalidateAll();
            }
        } catch (Throwable ignored) {
            // 读失败保持原值，绝不影响绘制
        }
    }

    /** ★ v77：省电模式开关变化的专用接收器（与电池广播无关的那一路）。 */
    private static final class PowerSaveReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            refreshPowerSave(ctx);
        }
    }

    private static final class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent != null && Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                lastBatteryIntent = intent; // 缓存给 readQuick 读充电类型 extra
                readBattery(ctx, intent);
                // ★ v101：充电状态即时响应 —— 广播一到就重绘，不必等 1s 轮询。
                //   charging 已改为不去抖（见 refresh），这里事件驱动让拔/插当场切换。
                TrioOverlay.invalidateAll();
            }
        }
    }

    private static void readBattery(Context ctx, Intent intent) {
        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            sBatteryLevel = (level * 100) / scale; // 0..100
        } else if (level >= 0) {
            sBatteryLevel = level;
        }
        final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        sBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        sBatteryLow = sBatteryLevel >= 0 && sBatteryLevel <= 15;
        readPowerSave(ctx);
        // ★ v54：先取三星扩展字段，再判定快充。
        //   反编译 SystemUI(classes2.dex) 实证：三星把充电类型塞在
        //   ACTION_BATTERY_CHANGED 的扩展 extra 里（见 readSamsungChargeExtras）。
        readSamsungChargeExtras(intent);
        readQuick(ctx, sBatteryCharging);
        readTemperature(intent);
    }

    /**
     * ★ v75：电池温度（v73 起由 {@code BatteryPresence} 单独取，v75 功率删除后
     * 并入这里 —— 温度本来就在同一份广播的 extras 里，不必另起一套取数器）。
     *
     * <p>平台给的是 0.1°C 单位的整数，取整到整摄氏度；取不到时写哨兵
     * {@link #TEMP_UNKNOWN}（读数整块不显示），而不是画一个 0°C 糊弄。
     * 值真的变了才推重绘：温度是静态量，不靠它驱动每帧。
     */
    private static void readTemperature(Intent intent) {
        final int raw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,
                Integer.MIN_VALUE);
        final int whole = (raw == Integer.MIN_VALUE)
                ? TEMP_UNKNOWN
                : Math.round(raw / 10f);
        if (whole != sBatteryTempC) {
            sBatteryTempC = whole;
            TrioOverlay.invalidateAll();
        }
    }

    private static void readPowerSave(Context ctx) {
        sBatteryPowerSave = false;
        try {
            final PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                sBatteryPowerSave = pm.isPowerSaveMode();
            }
        } catch (Throwable ignored) {
        }
    }

    // ================================================== 充电类型（三星扩展字段）

    /** 三星扩展字段：{@code charger_type}。 -1 = 该 extra 不存在。 */
    private static volatile int sChargerType = -1;
    /** 三星扩展字段：{@code charge_type}（2 = 慢充）。 */
    private static volatile int sChargeType = -1;
    /** 三星扩展字段：{@code hv_charger}（高压充电器）。 */
    private static volatile int sHvCharger = -1;
    /** 三星扩展字段：{@code online}（100 / 200 = 无线快充档）。 */
    private static volatile int sOnline = -1;

    /**
     * ★ v54：读取三星在 {@code ACTION_BATTERY_CHANGED} 里附加的充电字段。
     *
     * <h3>证据（反编译 One UI 8 SystemUI classes2.dex）</h3>
     * <pre>
     * com.android.systemui.power.SecPowerUI$Receiver.onReceive:
     *     const-string v10, "charger_type"
     *     v10 = intent.getIntExtra("charger_type", 0)
     *     this.mSuperFastCharger = v10          // field SecPowerUI.mSuperFastCharger
     * </pre>
     * 即三星自己的"超级快充"标志位就是广播里的 {@code charger_type} ——
     * <b>所以直接读广播即可，不需要反射任何 SystemUI 内部字段</b>
     * （早前版本反射 SecBatterySnapshot / SemBatteryManager 之所以拿不到值，正是走错了门）。
     *
     * <h3>取值量表（由 SecPowerUI$Receiver 的推导分支完整还原）</h3>
     * <pre>
     *   未充电(状态≠充电中) / 插入类型非 AC、USB   → 0
     *   无线：online==100 → 7 ；online==200 → 520 ；其它 → 6
     *   mFullyConnected == false（接触不良）        → 9
     *   charge_type == 2（慢充）                    → 8
     *   charger_type == 3                           → 3
     *   charger_type == 4                           → 4
     *   charger_type == 5                           → 5
     *   hv_charger == true（高压）                  → 2
     *   其它                                        → 1
     * </pre>
     * 可见 <b>超级快充是 3/4/5 三档</b>；3 档以下（1/2）是普通/高压。
     */
    private static void readSamsungChargeExtras(Intent intent) {
        try {
            sChargerType = intent.getIntExtra("charger_type", -1);
            sChargeType = intent.getIntExtra("charge_type", -1);
            sHvCharger = intent.getIntExtra("hv_charger", -1);
            sOnline = intent.getIntExtra("online", -1);
        } catch (Throwable ignored) {
            // 取不到就保持 -1，走下面的兜底路径
        }
    }

    /**
     * 快充判定（v32 重写，v54 补上真正可用的数据源）。
     *
     * <p><b>v54 之前的实现全是错的门路</b>：走三星私有服务
     * {@code getSystemService("sem_battery")} → {@code SemBatteryManager#getStringChargeType()}；
     * 但 One UI 8 的 SystemUI dex 里 {@code sem_battery} / {@code getStringChargeType}
     * <b>零命中</b>，所以快充变色从未生效（日志里一直是 {@code chargeType=? quick=false}）。
     *
     * <p>现在首选路径是广播里的 {@code charger_type}（由 {@link #readSamsungChargeExtras} 读入），
     * 它正是三星自己判定超级快充所用的值。取不到时才退回旧的系统属性/extra 兜底。
     */
    private static void readQuick(Context ctx, boolean charging) {
        sBatteryQuick = false;
        sChargeTypeLabel = "";
        if (!charging) {
            return;
        }
        // ★ v54 首选：三星广播的 charger_type（权威值）。
        //   变量名避开兜底分支里的 ct（那是字符串型的 "charging_type"）。
        final int ctCode = sChargerType;
        if (ctCode >= 0) {
            sChargeTypeLabel = "CT" + ctCode;
            sBatteryQuick = ctCode >= QUICK_CHARGER_TYPE_MIN;
            return; // 已有权威值，不再猜其它路径
        }
        // 兜底 1：系统属性（无 charger_type 的 ROM）
        try {
            final String v = getSystemProperty("sys.boot.charger.type");
            if (v != null && !v.isEmpty()) {
                sChargeTypeLabel = v;
                sBatteryQuick = v.contains("fast") || v.contains("super");
                if (sBatteryQuick) {
                    return;
                }
            }
            final String v2 = getSystemProperty("ro.boot.charging.type");
            if (v2 != null && !v2.isEmpty()) {
                sChargeTypeLabel = v2;
                sBatteryQuick = v2.contains("fast") || v2.contains("super");
                if (sBatteryQuick) {
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        // 兜底 2：旧的字符串型 extra，失败则保持 false
        try {
            final android.content.Intent last = lastBatteryIntent;
            if (last != null) {
                // 部分 ROM 用 "fast_charging" / "charging_type" 之类的 extra
                final int fc = last.getIntExtra("fast_charging", -1);
                if (fc > 0) {
                    sBatteryQuick = true;
                    sChargeTypeLabel = "FAST";
                    return;
                }
                final String ct = last.getStringExtra("charging_type");
                if (ct != null && !ct.isEmpty()) {
                    final String up = ct.toUpperCase();
                    sChargeTypeLabel = up;
                    sBatteryQuick = up.contains("FAST") || up.contains("SUPER");
                    if (sBatteryQuick) {
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 供诊断用：最近一次读到的充电类型标签 + 全部三星扩展字段。 */
    static String chargeTypeDiag() {
        return "chargeType=" + (sChargeTypeLabel.isEmpty() ? "?" : sChargeTypeLabel)
                + " quick=" + sBatteryQuick
                + " [charger_type=" + sChargerType
                + " charge_type=" + sChargeType
                + " hv_charger=" + sHvCharger
                + " online=" + sOnline + "]";
    }

    private static volatile String sChargeTypeLabel = "";

    /**
     * 判定为"快充"的最低 {@code charger_type} 值（v54 暂定）。
     *
     * <p>量表（见 {@link #readSamsungChargeExtras}）：1 = 普通、2 = 高压(AFC 类)、
     * <b>3/4/5 = 超级快充三档</b>、6/7 = 无线(7 快)、8 = 慢充、9 = 接触不良。
     * 暂取 3，即"任一超级快充档位都点黄闪电"。
     *
     * <p><b>待实机确认后精确化</b>：三星的 UI 用 {@code charger_type} 区分
     * 「超级快充」与「超级快充 2.0」，但 SystemUI 里没有对应明文（文案在资源里），
     * 所以 3/4/5 到底哪一档是 2.0 只能实测：插 45W 原装头，看日志里的
     * {@code charger_type=} 数字，再把阈值收窄到那一档。
     */
    private static final int QUICK_CHARGER_TYPE_MIN = 3;

    /** 读系统属性（反射 SystemProperties.get）。 */
    private static String getSystemProperty(String key) {
        try {
            final Class<?> sp = Class.forName("android.os.SystemProperties");
            final java.lang.reflect.Method m = sp.getMethod("get", String.class);
            final Object v = m.invoke(null, key);
            return v instanceof String ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================ Wi‑Fi：WifiManager

    private static void startWifi(Context ctx) {
        try {
            sWifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        } catch (Throwable ignored) {
            sWifiManager = null;
        }
        readWifi(ctx);
        final IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        // CONNECTIVITY_ACTION 在 API 28+ 限制，但 SystemUI 持有相应权限，仍可用作兜底。
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        reg(ctx, new WifiReceiver(), f);
    }

    private static final class WifiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null) {
                return;
            }
            final String a = intent.getAction();
            if (WifiManager.RSSI_CHANGED_ACTION.equals(a)
                    || WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(a)
                    || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(a)
                    || ConnectivityManager.CONNECTIVITY_ACTION.equals(a)) {
                readWifi(ctx);
            }
        }
    }

    private static void readWifi(Context ctx) {
        final WifiManager wm = sWifiManager;
        if (wm == null) {
            sWifiPresent = false;
            sWifiLevel = -1;
            return;
        }
        // 是否真的连上 Wi‑Fi（比 wm.isWifiEnabled 更准，能区分“开但没连”）。
        boolean connected = false;
        try {
            final ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                final NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                connected = ni != null && ni.isConnected();
            }
        } catch (Throwable ignored) {
        }
        if (!connected) {
            // 退而求其次：enabled 且有 supplicant 完成态也算连上。
            try {
                final WifiInfo info = wm.getConnectionInfo();
                connected = info != null
                        && info.getSupplicantState() == SupplicantState.COMPLETED
                        && info.getNetworkId() != -1;
            } catch (Throwable ignored) {
            }
        }

        int level = -1;
        if (connected) {
            try {
                final WifiInfo info = wm.getConnectionInfo();
                if (info != null) {
                    final int rssi = info.getRssi();
                    if (rssi != -127) { // WifiManager.INVALID_RSSI (hidden in api36 stub, value is -127)
                        level = WifiManager.calculateSignalLevel(rssi, 5); // 0..4
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        sWifiPresent = connected;
        sWifiLevel = level;
    }

    // ============================================================ 移动信号：Telephony（原样保留）

    /** 每帧调用，廉价。三星上不再有信号图标喂数，所以这里恒做限频轮询。 */
    void refresh() {
        // 电量 / 充电从标准广播静态量搬运
        level = sBatteryLevel;
        // ★ v77：快充/省电/低电量这三个布尔量**去抖后再用**（它们是颜色细节，
        //   1s 轮询与广播相位不一致时会两档跳一下，连续 2 次一致才切换）。
        //
        // ★ v101：但【充电状态 charging 本身不再去抖】—— 用户实测"拔充电器后
        //   图标延迟一会才切换"。根因：charging 也走了 settle(2连拍)，而 refresh
        //   约 1s/次 ⇒ 拔插后要 ~2s 才翻。充电是广播直接给的可靠离散值、不会抖，
        //   理应即时；且 BatteryReceiver.onReceive 末尾已 invalidateAll() 立刻重绘，
        //   走事件驱动比"轮询+去抖"更跟手。快充/省电/低电量保留去抖。
        charging = sBatteryCharging;
        quickCharging = settle(1, quickCharging, sBatteryQuick);
        low = settle(2, low, sBatteryLow);
        powerSave = settle(3, powerSave, sBatteryPowerSave);
        performanceMode = sBatteryPerf;

        // 移动信号：恒做限频轮询（2s），保证单卡信号也会刷新
        sampleSimsIfDue();

        wifiLevel = sWifiLevel;
        mobileLevel = sMobileLevel;
        wifiPresent = sWifiPresent;
        islandHideBattery = sIslandHideBattery;
        mobileType = sMobileType;

        final int[] slots = sSlotLevels;
        slotLevels = new int[] { slots[0], slots[1] };
        dataSlot = sDataSlot;
        sims = (slots[0] >= 0 ? 1 : 0) + (slots[1] >= 0 ? 1 : 0);

        // 单行且图标准链无应答：回退到“承载数据的那张卡”
        if (mobileLevel < 0 && sDataSlot >= 0 && sDataSlot < SIM_SLOTS
                && slots[sDataSlot] >= 0) {
            mobileLevel = slots[sDataSlot];
        }
    }

    private static void sampleSimsIfDue() {
        final long now = SystemClock.elapsedRealtime();
        if (now < sSimsDueAt) {
            return;
        }
        sSimsDueAt = now + SIMS_INTERVAL_MS;
        sampleSims(sContext);
    }

    static boolean pollSimsNow() {
        if (sContext == null) {
            return false;
        }
        final int[] before = sSlotLevels;
        final int beforeData = sDataSlot;
        sampleSims(sContext);
        final int[] after = sSlotLevels;
        return beforeData != sDataSlot || before[0] != after[0] || before[1] != after[1];
    }

    private static void sampleSims(Context context) {
        if (context == null) {
            return;
        }
        final int[] levels = { -1, -1 };
        int dataSlot = -1;
        String type = "";
        try {
            final SubscriptionManager subs =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            final TelephonyManager tel =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (subs == null || tel == null) {
                return;
            }
            final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            final List<SubscriptionInfo> infos = subs.getActiveSubscriptionInfoList();
            // ★ v100：给每张活动卡绑定信号回调，缓存真实 SignalStrength（9.0 同步轮询为空）。
            bindSignalListeners(tel, infos);
            if (infos != null) {
                for (int i = 0; i < infos.size(); i++) {
                    final SubscriptionInfo info = infos.get(i);
                    if (info == null) {
                        continue;
                    }
                    final int subId = info.getSubscriptionId();
                    final int slot = info.getSimSlotIndex();
                    if (subId == dataSubId) {
                        dataSlot = slot;
                        type = networkTypeLabel(tel, subId);
                    }
                    if (slot < 0 || slot >= SIM_SLOTS) {
                        continue;
                    }
                    levels[slot] = levelOf(tel, subId);
                    // 只记录数据卡的原始读数，避免双卡时被非数据卡覆盖
                    if (subId == dataSubId) {
                        // 由 levelOf 内部写入 sLastRaw*
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        sSlotLevels = levels;
        sDataSlot = dataSlot;
        // ★ 补上缺失的赋值：此前 sMobileLevel 声明后从未被写入，一直停在 -1，
        //   导致 refresh() 只能靠"数据卡回退"取值。
        sMobileLevel = (dataSlot >= 0 && dataSlot < SIM_SLOTS)
                ? levels[dataSlot] : -1;
        if (!type.isEmpty()) {
            sMobileType = type;
        }
        // ★ v56：首次采样成功后，**只读** dump 一次真实阈值配置。
        //   不改任何显示逻辑；跑在独立线程，避免在 UI 线程做上百次文件写。
        //   若这一轮读到的配置不可用（subId 还是 -1 / 配置未加载），
        //   允许稍后重试若干次，避免白跑一轮装机。
        if (!sSigCfgDumped) {
            sSigCfgDumped = true;
            try {
                final Context c = context;
                new Thread(() -> {
                    final boolean ok = dumpSignalConfig(c);
                    if (!ok && sSigCfgTries < SIGCFG_MAX_TRIES) {
                        sSigCfgTries++;
                        sSigCfgDumped = false; // 交给下一次采样重试
                    }
                }, "SamsungDuo-sigcfg").start();
            } catch (Throwable ignored) {
                sSigCfgDumped = false;
            }
        }
    }

    /**
     * ★ v100：给每张活动卡绑定信号回调，把最新 SignalStrength 缓存进 {@link #sSigBySub}。
     *
     * <p><b>为什么不用同步轮询</b>：{@code TelephonyManager.getSignalStrength()} 在
     * One UI 9.0 上返回的是空对象（cells=[]、rssi=-127、level=-1），
     * 而回调 {@code onSignalStrengthsChanged} 拿到的是完整对象。
     * 这是参照 OneUI XP（SDK37 样本）的做法：它用
     * {@code TelephonyCallback$SignalStrengthsListener} / {@code PhoneStateListener}
     * 回调式读取，全程不碰 {@code getVendorLevel}。
     *
     * <p><b>重绑策略</b>：按当前活动 subId 集合与上次绑定集合比对，仅在变化时
     * 反注册旧监听、注册新监听（换卡/拔插卡时刷新）；未变化直接返回，避免每次
     * 2s 轮询都反复注册。
     */
    private static void bindSignalListeners(TelephonyManager tel, List<SubscriptionInfo> infos) {
        if (tel == null) {
            return;
        }
        try {
            // 当前活动卡的 subId，按 slot 归档（与 levelOf/sampleSims 的 slot 口径一致）
            final int[] subs = { -1, -1 };
            if (infos != null) {
                for (int i = 0; i < infos.size(); i++) {
                    final SubscriptionInfo info = infos.get(i);
                    if (info == null) {
                        continue;
                    }
                    final int slot = info.getSimSlotIndex();
                    if (slot >= 0 && slot < SIM_SLOTS) {
                        subs[slot] = info.getSubscriptionId();
                    }
                }
            }
            // 未变化 → 不重绑
            if (subs[0] == sBoundSubs[0] && subs[1] == sBoundSubs[1]) {
                return;
            }
            // 反注册旧监听、清旧缓存
            for (int i = 0; i < SIM_SLOTS; i++) {
                final PhoneStateListener old = sBoundListeners[i];
                if (old != null) {
                    try {
                        tel.listen(old, PhoneStateListener.LISTEN_NONE);
                    } catch (Throwable ignored) {
                        // 反注册失败无害
                    }
                    sBoundListeners[i] = null;
                }
                final int oldSub = sBoundSubs[i];
                if (oldSub >= 0) {
                    synchronized (sSigLock) {
                        sSigBySub.remove(oldSub);
                    }
                }
            }
            // 注册新监听 + 立刻做一次同步读作为种子（不必等首次回调）
            for (int i = 0; i < SIM_SLOTS; i++) {
                sBoundSubs[i] = subs[i];
                final int subId = subs[i];
                if (subId < 0) {
                    continue;
                }
                final TelephonyManager perSim = tel.createForSubscriptionId(subId);
                if (perSim == null) {
                    continue;
                }
                // 种子：先放一次同步读，避免首个回调到达前缓存为空
                try {
                    final SignalStrength seed = perSim.getSignalStrength();
                    if (seed != null) {
                        synchronized (sSigLock) {
                            sSigBySub.put(subId, seed);
                        }
                    }
                } catch (Throwable ignored) {
                    // 种子失败无害，等回调
                }
                final PhoneStateListener l = new PhoneStateListener() {
                    @Override
                    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                        if (signalStrength == null) {
                            return;
                        }
                        synchronized (sSigLock) {
                            sSigBySub.put(subId, signalStrength);
                        }
                    }
                };
                try {
                    perSim.listen(l, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
                    sBoundListeners[i] = l;
                } catch (Throwable ignored) {
                    // 某些系统可能拒绝 per-sub 监听，退回主 TelephonyManager 亦可
                }
            }
        } catch (Throwable ignored) {
            // 绑定失败绝不影响其它读数
        }
    }

    // ============================================================ 信号阈值只读诊断（v56）

    /**
     * ★ v56：把设备上**真实的**信号条阈值配置 dump 出来。
     *
     * <p><b>为什么必须从设备读，而不是查 AOSP：</b>
     * 反编译三星 SystemUI（One UI 8.5 / Android 16，全部 5 个 dex）确认——
     * <b>SystemUI 里一个阈值键都没有</b>（{@code rsrp} 零命中，只有
     * {@code always_show_primary_signal_bar_in_opportunistic_network_boolean}
     * 和 {@code no_signal_retry_int} 两个无关键）。
     * 也就是说信号格数是 telephony 框架用 {@code SignalStrength} +
     * {@code CarrierConfigManager} 算好后才交给 SystemUI 的。
     * AOSP 只提供 {@code AccessNetworkThresholds} 的<b>兜底默认值</b>，
     * 真正生效的是各运营商下发到设备上的 carrier config（中国运营商普遍覆盖）。
     * 所以 AOSP 默认值不可用，只能从设备读。
     *
     * <p><b>已验证的键名（来自 Android 16 的 framework stub + AOSP SignalStrengthController）：</b>
     * <pre>
     *   LTE 4G ：lte_rsrp_thresholds_int_array / lte_rsrq_thresholds_int_array
     *            lte_rssnr_thresholds_int_array
     *   5G  NR ：5g_nr_ssrsrp_thresholds_int_array / ..._ssrsrq_... / ..._sssinr_...
     *            （注意：**不是** nr_rsrp_thresholds_int_array，该键不存在）
     *   选测项 ：parameters_used_for_lte_signal_bar_int  （默认 USE_RSRP）
     *            parameters_use_for_5g_nr_signal_bar_int（默认 USE_SSRSRP）
     * </pre>
     * 本方法还会**穷举** carrier config 的全部键，把任何含
     * rsrp/rsrq/rssnr/sinr/signal/threshold 的键连值一起打出来
     * —— 这样即使三星或运营商用了私有键，也能一次看全。
     *
     * <p><b>安全性</b>：全只读，无任何 set/add/remove/反射写；异常全部吞掉。
     */
    private static boolean dumpSignalConfig(Context ctx) {
        if (ctx == null) {
            return false;
        }
        // 拿到"能用"的配置才算成功；否则由调用方安排重试，
        // 避免因为读得太早（配置还没加载 / subId 还是 -1）而白跑一轮装机。
        boolean usable = false;
        try {
            sigLog("===== BEGIN SIGNAL CONFIG DUMP (try " + (sSigCfgTries + 1) + ") =====");
            final CarrierConfigManager ccm =
                    (CarrierConfigManager) ctx.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            final TelephonyManager tel =
                    (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            sigLog("dataSubId=" + subId + " sdk=" + Build.VERSION.SDK_INT
                    + " device=" + Build.DEVICE + " model=" + Build.MODEL);
            if (ccm == null) {
                sigLog("CarrierConfigManager=null —— 无法读取");
                sigLog("===== END SIGNAL CONFIG DUMP =====");
                return false;
            }

            // ---- 1) 该 subId 的全部信号相关键 + 值 ----
            PersistableBundle b = null;
            try {
                b = ccm.getConfigForSubId(subId);
            } catch (Throwable t) {
                sigLog("getConfigForSubId(" + subId + ") threw: " + t);
            }
            if (b == null) {
                sigLog("carrierConfig=null（该 subId 无配置）");
            } else {
                final Set<String> keys = b.keySet();
                final List<String> hits = new ArrayList<>();
                for (String k : keys) {
                    if (k == null) {
                        continue;
                    }
                    final String lk = k.toLowerCase(Locale.ROOT);
                    if (lk.contains("rsrp") || lk.contains("rsrq") || lk.contains("rssnr")
                            || lk.contains("sinr") || lk.contains("signal")
                            || lk.contains("threshold") || lk.contains("bar_count")) {
                        hits.add(k);
                    }
                }
                Collections.sort(hits);
                sigLog("carrierConfig keys=" + keys.size() + "  signalRelated=" + hits.size());
                usable = hits.size() > 0;
                for (String k : hits) {
                    Object v = null;
                    try {
                        v = b.get(k);
                    } catch (Throwable ignored) {
                    }
                    sigLog("  " + k + " = " + brief(v));
                }
            }

            // ---- 2) 当前实测信号（含框架已算好的 level）----
            if (tel == null) {
                sigLog("TelephonyManager=null");
            } else {
                try {
                    final TelephonyManager per = tel.createForSubscriptionId(subId);
                    final TelephonyManager src = (per != null) ? per : tel;
                    sigLog("dataNetworkType=" + src.getDataNetworkType());
                    final SignalStrength ss = src.getSignalStrength();
                    if (ss == null) {
                        sigLog("SignalStrength=null");
                    } else {
                        sigLog("SignalStrength.getLevel()=" + ss.getLevel()
                                + " isGsm=" + ss.isGsm());
                        final List<CellSignalStrength> cells = ss.getCellSignalStrengths();
                        if (cells == null) {
                            sigLog("cells=null");
                        } else {
                            for (int i = 0; i < cells.size(); i++) {
                                final CellSignalStrength c = cells.get(i);
                                if (c == null) {
                                    continue;
                                }
                                sigLog("  cell[" + i + "] " + c.getClass().getSimpleName()
                                        + " level=" + c.getLevel() + " " + describeCell(c));
                            }
                        }
                    }
                } catch (Throwable t) {
                    sigLog("readSignalStrength threw: " + t);
                }
            }
            sigLog("===== END SIGNAL CONFIG DUMP =====");
        } catch (Throwable t) {
            sigLog("dump failed: " + t);
        }
        return usable;
    }

    /** 只读诊断日志（前缀 sigcfg，已在 isCritical 白名单里豁免限流）。 */
    private static void sigLog(String msg) {
        try {
            TrioHooks.log(TrioHooks.LOG_INFO, "sigcfg " + msg);
        } catch (Throwable ignored) {
        }
    }

    /** 把 carrier config 的值压成一行可读文本（重点看 int[] 的长度）。 */
    private static String brief(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof int[]) {
            final int[] a = (int[]) v;
            final StringBuilder s = new StringBuilder("int[").append(a.length).append("] {");
            for (int i = 0; i < a.length; i++) {
                if (i > 0) {
                    s.append(", ");
                }
                s.append(a[i]);
            }
            return s.append('}').toString();
        }
        if (v instanceof long[]) {
            return "long[" + ((long[]) v).length + "]";
        }
        if (v instanceof String[]) {
            return "String[" + ((String[]) v).length + "]";
        }
        final String s = String.valueOf(v);
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    /** 按制式打印原始测量值——这是真正用来和阈值比较的量。 */
    private static String describeCell(CellSignalStrength c) {
        try {
            if (c instanceof CellSignalStrengthLte) {
                final CellSignalStrengthLte l = (CellSignalStrengthLte) c;
                return "rsrp=" + l.getRsrp() + " rsrq=" + l.getRsrq()
                        + " rssnr=" + l.getRssnr();
            }
            if (c instanceof CellSignalStrengthNr) {
                final CellSignalStrengthNr n = (CellSignalStrengthNr) c;
                return "ssRsrp=" + n.getSsRsrp() + " ssRsrq=" + n.getSsRsrq()
                        + " ssSinr=" + n.getSsSinr()
                        + " csiRsrp=" + n.getCsiRsrp() + " csiSinr=" + n.getCsiSinr();
            }
            if (c instanceof CellSignalStrengthGsm) {
                final CellSignalStrengthGsm g = (CellSignalStrengthGsm) c;
                return "rssi=" + g.getRssi() + " ber=" + g.getBitErrorRate();
            }
            if (c instanceof CellSignalStrengthWcdma) {
                final CellSignalStrengthWcdma w = (CellSignalStrengthWcdma) c;
                return "dbm=" + w.getDbm() + " ecno=" + w.getEcNo();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 诊断用：原始 SignalStrength 读数，供日志核对档位来源。 */
    static String rawSignalDiag() {
        return "src=" + sLastLevelSrc + " vendor=" + sLastVendorLevel
                + " rawLevel=" + sLastRawLevel + " rssi=" + sLastRawRssi
                + " cells=[" + sLastCellDiag + "]"
                + " slots=" + java.util.Arrays.toString(sSlotLevels)
                + " dataSlot=" + sDataSlot
                + " mLevel=" + sMobileLevel
                + (sVendorErr.isEmpty() ? "" : " vendorErr=" + sVendorErr);
    }

    /**
     * 把一张卡的信号换算成 0~4 档（喂给三合一的 4 个点）。
     *
     * <p><b>★ v64：取 {@code SignalStrength.getVendorLevel()}（0~5 六级），
     * 而不是 {@code getLevel()}（0~4 五级）。</b>取数顺序：
     * <ol>
     *   <li>{@code vendor} 0~5 → {@link #vendorToDots} 映射成 0~4 ← **正常都走这条**</li>
     *   <li>dBm 换算（{@link #levelFromRssi}，4G/5G 共表）← 仅在 vendor 取不到时</li>
     *   <li>框架聚合 {@code getLevel()} ← 最后兜底</li>
     * </ol>
     * 走哪条会写进 {@link #sLastLevelSrc}，日志里 `src=vendor|dbm|frame` 一眼可辨。
     *
     * <p><b>为什么之前一直走错接口（留档，别再犯）：</b>公开 SDK 里
     * {@code SignalStrength} 只暴露 {@code getLevel()}，所以前几版都在用它；
     * 可实测同一时刻（5G、NR ssRsrp=-92、用户截图）{@code getLevel()=1}，
     * 原生却画满 5 条。直到把 classes3.dex 完整反汇编才看到：原生喂给图标管线的
     * 是 {@code getVendorLevel()}（见 {@code levelOf} 方法体内注释）。
     * <b>两个接口在同一时刻能差 4 档</b> —— v61/v62 两次反复全是因为这一点。
     *
     * <p>顺带在每个 cell 上记全诊断（制式 / level / 原始测量），
     * 以后要复核"档位对不对"一行日志就够，不必再猜。
     */
    private static int levelOf(TelephonyManager tel, int subId) {
        try {
            // ★ v100：优先读回调缓存里的真实 SignalStrength（9.0 上同步轮询
            //   perSim.getSignalStrength() 返回的是 cells=[] 空对象，见 bindSignalListeners）。
            //   缓存未命中才退回同步轮询。
            SignalStrength strength;
            synchronized (sSigLock) {
                strength = sSigBySub.get(subId);
            }
            if (strength == null) {
                final TelephonyManager perSim = tel.createForSubscriptionId(subId);
                if (perSim == null) {
                    return -1;
                }
                strength = perSim.getSignalStrength();
            }
            if (strength == null) {
                return -1;
            }
            // 只记诊断，不参与判定
            sLastRawLevel = strength.getLevel();
            sLastCellDiag = describeCells(strength);

            // ★★★ v64：原生走的是 getVendorLevel()，**不是** getLevel()（反汇编实证）。
            //
            //   com.android.systemui.statusbar.pipeline.mobile.data.repository.prod
            //   .MobileConnectionRepositoryImpl$special$$inlined$map$9$2.emit:
            //       CallbackEvent$OnSignalStrengthChanged.signalStrength
            //       → invoke-virtual SignalStrength.getVendorLevel:()I     ← ★ 就这一条
            //       → emit(Integer.valueOf(v))                             → 喂进图标管线
            //   （另外两处 getVendorLevel 一个是 MobileInputLogger 打日志、
            //     一个是 DummyMobileConnectionRepository，都不在显示路径上。）
            //
            //   这个值最终进 MobileIconInteractorImpl → getMobileSignalIconGroup(slotId, level)，
            //   再由该方法按运营商条件选图标集（同一张反汇编里）：
            //       IS_USA_SPRINT → MOBILE_SIGNAL_STRENGTH_ICONS_SPR        （6 项 = 5 条）
            //       IS_CHINA      → MOBILE_SIGNAL_STRENGTH_5LEVEL_ICONS     （6 项 = 5 条）★
            //       level == 5    → MOBILE_SIGNAL_STRENGTH_ICONS            （5 项 = 4 条）
            //   旁边还有 CarrierInfraMediator$Values.MAX_SIGNAL_LEVEL 与常量 5 比较
            //   ⇒ **这套 level 的满档是 5，即 0~5 六级**，不是 AOSP getLevel() 的 0~4。
            //
            //   这就是之前所有怪现象的答案：我们一直读 getLevel()（NR 上给 1），
            //   而原生读 getVendorLevel()（同一时刻给 4/5）——**取错了接口**，
            //   不是阈值算错。v61「采信 getLevel」和 v62「回退 dBm 表」两次反复，
            //   都是因为这个接口一直没被找到。
            final int vendor = readVendorLevel(strength);
            sLastVendorLevel = vendor;
            if (vendor >= 0) {
                sLastLevelSrc = "vendor";
                sLastRawRssi = readRssi(strength);
                return vendorToDots(vendor);
            }
            // ★ v100 兜底 1：公开 API —— 取所有 cell 的最高 level（0~4）。
            //   One UI 9.0 上 getVendorLevel() 反射拿不到（vendor=-1），
            //   但回调拿到的真实对象里 getCellSignalStrengths() 有完整 cell，
            //   这是 9.0 的主路径；8.x 上 vendor 命中、永远走不到这里，无副作用。
            final int cellLevel = levelFromCells(strength);
            if (cellLevel >= 0) {
                sLastLevelSrc = "cells";
                sLastRawRssi = readRssi(strength);
                return cellLevel;
            }
            // 兜底 2：既有的 dBm 换算（v62 那版用户认可的行为），仅在拿不到 vendor / cells 时走。
            final int rssi = readRssi(strength);
            sLastRawRssi = rssi;
            if (rssi > -126 && rssi < 200) { // 排除 INVALID(-127) 与异常值
                sLastLevelSrc = "dbm";
                return levelFromRssi(rssi);
            }
            // 兜底 3：框架聚合档位（0~4）。
            final int level = strength.getLevel();
            sLastLevelSrc = "frame";
            return (level < 0 || level > 4) ? -1 : level;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 原生 vendor level 的满档值（v64：0~5 六级，对应三星 5 条图标集的 6 项）。 */
    private static final int VENDOR_MAX_LEVEL = 5;

    /** v64：{@code SignalStrength.getVendorLevel()} 的反射句柄缓存（取到就不再找）。 */
    private static volatile java.lang.reflect.Method sGetVendorLevel;
    private static volatile boolean sVendorLookupTried = false;

    /**
     * ★ v64：反射调用 {@code SignalStrength.getVendorLevel()}。
     *
     * <p>它是 @hide 方法（公开 SDK 的 stub 里只有 {@code getLevel()}，用 javap 实测过），
     * 所以只能反射。任何失败都不抛异常、只把原文记进 {@link #sVendorErr} 然后返回 -1，
     * 交给 {@link #levelOf} 的兜底路径 —— 绝不让"取不到档位"影响到其它读数。
     */
    private static int readVendorLevel(SignalStrength s) {
        if (s == null) {
            return -1;
        }
        try {
            java.lang.reflect.Method m = sGetVendorLevel;
            if (m == null && !sVendorLookupTried) {
                sVendorLookupTried = true;
                m = SignalStrength.class.getMethod("getVendorLevel");
                sGetVendorLevel = m;
                sVendorErr = "";
            }
            if (m == null) {
                return -1;
            }
            final Object r = m.invoke(s);
            if (r instanceof Integer) {
                final int v = (Integer) r;
                if (v >= 0 && v <= VENDOR_MAX_LEVEL) {
                    return v;
                }
                sVendorErr = "out-of-range " + v;
            }
        } catch (Throwable t) {
            sVendorErr = String.valueOf(t);
        }
        return -1;
    }

    /**
     * ★ v64：把原生的 0~5 六级映射到我们的 0~4 四个点。
     *
     * <p>我们只有 4 个点、原生有 5 条，无法一一对应，所以按比例**向上取整**：
     * <pre>
     *   vendor 0 → 0   vendor 1 → 1   vendor 2 → 2
     *   vendor 3 → 3   vendor 4 → 4   vendor 5 → 4
     * </pre>
     * 向上取整是刻意的：宁可略偏饱满，也不要出现"原生 4 条、我们只有 3 个点"
     * 这种一眼就少的情况 —— 用户对"少档"极其敏感（v61 那次 1 个点就是这么炸出来的）。
     */
    private static int vendorToDots(int vendor) {
        if (vendor <= 0) {
            return 0;
        }
        final int dots = (vendor * 4 + VENDOR_MAX_LEVEL - 1) / VENDOR_MAX_LEVEL;
        return Math.min(4, dots);
    }

    /**
     * 每个 cell 的「制式 + level + 原始测量」，一行诊断。
     *
     * <p>记原始测量而不是只记 level，是为了能**反算验证**：拿 dump 出来的真实阈值
     * 比一比就知道框架档位对不对（v61 就是这么纠正 v34 的误判的）。
     */
    private static String describeCells(SignalStrength s) {
        try {
            final List<CellSignalStrength> cells = s.getCellSignalStrengths();
            if (cells == null) {
                return "";
            }
            final StringBuilder b = new StringBuilder();
            for (int i = 0; i < cells.size(); i++) {
                final CellSignalStrength c = cells.get(i);
                if (c == null) {
                    continue;
                }
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(c.getClass().getSimpleName()).append("(lv=").append(c.getLevel());
                if (c instanceof CellSignalStrengthLte) {
                    b.append(" rsrp=").append(((CellSignalStrengthLte) c).getRsrp());
                } else if (c instanceof CellSignalStrengthNr) {
                    b.append(" ssRsrp=").append(((CellSignalStrengthNr) c).getSsRsrp());
                } else if (c instanceof CellSignalStrengthGsm) {
                    b.append(" rssi=").append(((CellSignalStrengthGsm) c).getRssi());
                } else if (c instanceof CellSignalStrengthWcdma) {
                    b.append(" dbm=").append(((CellSignalStrengthWcdma) c).getDbm());
                }
                b.append(')');
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 取当前最强一路信号的 dBm。
     *
     * <p>注意：{@code SignalStrength.getRssi()} 在 API 36/37 的 stub 中已被移除
     * （运行时存在、编译不可见），必须走 {@code getCellSignalStrengths()} 取。
     * 多路（如 CA 载波聚合）时取 dBm 最大者，最接近实际可用信号。
     *
     * <p>★ v61 起它的用途只剩两个：诊断日志里的 {@code rssi=}，
     * 以及框架档位不可用时的兜底换算（见 {@link #levelOf}）。
     * 注意它返回的是 cell 的 dBm —— **LTE 是 RSRP、NR 是 SS-RSRP，两者不同量纲**，
     * 所以不能拿一个固定阈值表同时套两种制式（v34 就是这么错的）。
     */
    private static int readRssi(SignalStrength strength) {
        try {
            final List<CellSignalStrength> bars = strength.getCellSignalStrengths();
            if (bars == null) {
                return -127;
            }
            int best = -127;
            for (int i = 0; i < bars.size(); i++) {
                final CellSignalStrength b = bars.get(i);
                if (b != null && b.getDbm() > best) {
                    best = b.getDbm();
                }
            }
            return best;
        } catch (Throwable t) {
            return -127;
        }
    }

    /**
     * ★ v100：公开 API 兜底 —— 取所有 cell 里最高的 {@code getLevel()}（0~4）。
     *
     * <p>用于 One UI 9.0 上 {@code getVendorLevel()} 反射拿不到（vendor=-1）时：
     * {@code getCellSignalStrengths()} 是公开 API，每个 {@link CellSignalStrength#getLevel()}
     * 给出该制式的 0~4 档。多路（CA 载波聚合等）取最高者，最接近实际可用信号。
     * 拿不到有效 cell（空列表或全 -1）时返回 -1，交给后续 dBm / getLevel 兜底。
     *
     * <p>8.x 上 {@code readVendorLevel} 命中、永远走不到这里，对既有行为无副作用。
     */
    private static int levelFromCells(SignalStrength s) {
        try {
            final List<CellSignalStrength> cells = s.getCellSignalStrengths();
            if (cells == null || cells.isEmpty()) {
                return -1;
            }
            int best = -1;
            for (int i = 0; i < cells.size(); i++) {
                final CellSignalStrength c = cells.get(i);
                if (c == null) {
                    continue;
                }
                final int lv = c.getLevel();
                if (lv > best) {
                    best = lv;
                }
            }
            return (best < 0 || best > 4) ? -1 : best;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 上一次读到的原始值，仅用于诊断日志。 */
    private static volatile int sLastRawLevel = -1;
    private static volatile int sLastRawRssi = -127;
    /** ★ v61：上一次的 per-cell 诊断（制式 + level + 原始测量），用于反算验证档位。 */
    private static volatile String sLastCellDiag = "";

    /**
     * ★ v64：**原生图标真正使用的那个 level** —— {@code SignalStrength.getVendorLevel()}。
     *
     * <p>它是 0~5 六级（三星 5 条图标集就是 6 项），不是 AOSP {@code getLevel()} 的 0~4。
     */
    private static volatile int sLastVendorLevel = -1;

    /** ★ v64：本次档位来自哪条路（vendor / dbm / frame / none），一行日志就能判断。 */
    private static volatile String sLastLevelSrc = "none";

    /** ★ v64：反射取 {@code getVendorLevel()} 失败的原文（成功则留空），用于诊断。 */
    private static volatile String sVendorErr = "";

    /**
     * 按 dBm 换算 0~4 格（**v64 起只是兜底**，主路径见 {@link #levelOf} 的 vendor level）。
     *
     * <p>≥-95 四格、≥-105 三格、≥-115 二格、≥-125 一格、其余零格。
     *
     * <p><b>★ 更正 v62 写在这里的结论（那是一条错的因果，必须改掉免得继续传染）：</b>
     * v62 说"原生观感 = dBm 换算、聚合 level 取错了制式"。真实原因是
     * <b>接口从头到尾就选错了</b> —— 原生用的既不是 dBm 换算、也不是
     * {@code getLevel()}，而是 {@code SignalStrength.getVendorLevel()}（0~5 六级，
     * 反汇编实证，见 {@code levelOf} 方法体内的注释）。同一时刻
     * （5G、NR ssRsrp=-92）：{@code getLevel()=1}、{@code getVendorLevel()=4 或 5}。
     *
     * <p>巧合的是：按 dBm 换算 {@code -92 → ≥-95 → 4}，恰好与 vendor 档位的观感接近，
     * 所以 v62 那版"看起来对"。也就是说它**碰巧对了结果、但解释是错的** ——
     * 一旦信号落进两者分歧的区间就会再次显示错，这也是它只当兜底、不再当主路径的原因。
     *
     * <p>保留它是因为它是一张稳定的单调表，在 vendor 取不到时能给出合理档位。
     */
    private static int levelFromRssi(int rssi) {
        if (rssi >= -95) {
            return 4;
        }
        if (rssi >= -105) {
            return 3;
        }
        if (rssi >= -115) {
            return 2;
        }
        if (rssi >= -125) {
            return 1;
        }
        return 0;
    }

    /** 把数据卡的网络类型映射成状态栏标签："5G"/"4G"/"H+"/"3G"/"E"/"2G"，未知为空。 */
    private static String networkTypeLabel(TelephonyManager tel, int subId) {
        try {
            final TelephonyManager perSim = tel.createForSubscriptionId(subId);
            final int nt = (perSim != null) ? perSim.getDataNetworkType()
                    : tel.getDataNetworkType();
            switch (nt) {
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "H+";
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return "E";
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    return "2G";
                default:
                    return "";
            }
        } catch (Throwable t) {
            return "";
        }
    }

    // ============================================================ 颜色

    /** 状态栏图标应画成的颜色。**只用本实例自己的信息**，绝不借别的界面。 */
    int foreground() {
        // ★ 10-22 三修（黄色壁纸实机日志铁证）：
        //   tint[PhoneStatusBarView]: iconTint=b30c0c0c night=true src=sLastGood -> ff454545
        //   onDarkChanged 回调写进 mIconTint 的 b30c0c0c 就是系统调度器推给原生图标
        //   （网速/电池/信号）的**同一个值**（v58 反汇编实证的权威路径）——
        //   它随壁纸明暗自适应（黄壁纸上暗色、深色模式下白色 edffffff），才是
        //   "原生状态栏图标取色"的正路。但它此前**只出现在诊断日志里**，
        //   取值链实际是 探针→bg→sLastGood→mode：探针撞 TextView/近黑被拒后
        //   回落旧缓存，就是用户实测的「一会黑一会白」「深色模式原生没那么黑」。
        //   新顺序：回调 → 探针 → 本界面背景 → 系统主题兜底。
        //   探针降级给收不到回调的下拉/锁屏用（它们的 mIconTint 恒 0）。
        String src = "fallback";
        int t = 0;
        if (mIconTint != 0) {
            t = mIconTint;
            src = "callback";
        }
        if (t == 0) {
            t = liveTintFromHost();
            if (t != 0) {
                src = "probe";
            }
        }
        if (t == 0) {
            // ★ v99（9.9 原样）：本实例至今没读到过自己的真实色时，用"最近任一实例读到的
            //   真实主题色"兜底，而不是 nightMode() 强制纯黑/纯白。
            // ★ 10-22：本界面自己的背景亮度**优先** —— 下拉/锁屏与状态栏明暗互相独立，
            //   hostThemeTint() 此前写了却从没接进这条链（等于白写），
            //   结果"探针读不到"时只能去抄跨实例的 sLastGoodTint，三处界面被绑在一起。
            final int bg = hostThemeTint();
            if (bg != 0) {
                t = bg;
                src = "bg";
            } else if (sLastGoodTint != 0 && !isUnusableInk(sLastGoodTint)) {
                t = sLastGoodTint;
                src = "sLastGood";
            } else {
                t = nightMode() ? 0xFFFFFFFF : 0xFF000000;
                src = "mode";
            }
        }
        // ★ 10-22 四修（实机对比原生图标）：系统 onDarkChanged 回调下发的 tint
        //   本身**自带 alpha**（如 b3000000=70%黑、edfefefe=93%白、caa7a7a7=79%灰），
        //   原生图标正是带着这个 alpha 渲染的——半透明黑叠在状态栏背景上比纯黑浅一截。
        //   旧 v82b 把 alpha 强制锤成 0xFF 不透明，导致我们的墨色比原生"深"
        //   （用户实机反馈"黑色更深一点，自带图标没这么深的黑"）；胶囊与圆环都走
        //   这条 foreground() 链，所以一起中招。
        //   修法：系统来源（callback / probe / sLastGood，读到的就是原生图标真实 tint
        //   含 alpha）的 alpha 原样保留直接交给渲染层；只有本模块自己合成的兜底色
        //   （bg / mode，hostThemeTint 与 nightMode 返回的就是 0xFF000000/0xFFFFFFFF，
        //   本就不透明）才需要实心——它们已是不透明，无需再处理。
        //   暗轨/非激活格走 withAlpha(fg, a.trackAlpha) 只取 RGB、用设计值做透明度，
        //   与 fg 自身的 alpha 无关，故本改不动暗轨观感。
        if (t != 0) {
            noteTint(t, src);
            return t;
        }
        return 0xFF000000;
    }

    /**
     * ★ 本界面自身的明暗兜底：向上找第一个"不透明纯色/渐变"背景（含 host 自己），
     * 按背景亮度决定图标用深色还是浅色。找不到才退回系统的夜间模式。
     *
     * <p>下拉面板必须走这里 —— OneUI 上它的明暗与状态栏**互相独立**，
     * 探针/回调读到的那棵树在下拉场景里往往已经被状态栏的暗色调度器刷过，
     * 直接采信就会变成"下拉跟着状态栏变"。
     */
    private int hostThemeTint() {
        try {
            View v = host;
            for (int i = 0; v != null && i < 16; i++,
                    v = (v.getParent() instanceof View) ? (View) v.getParent() : null) {
                final int c = solidColorOf(v.getBackground());
                if (c != 0 && ((c >>> 24) & 0xFF) >= 0x80) {
                    // 背景偏亮 → 图标用深色；偏暗 → 用浅色
                    final int out = (luminance(c) >= 0.5) ? 0xFF000000 : 0xFFFFFFFF;
                    if (out != mLastHostTint) {
                        mLastHostTint = out;
                        TrioHooks.log(TrioHooks.LOG_INFO, "bg[" + v.getClass().getSimpleName()
                                + "]=" + Integer.toHexString(c) + " -> host "
                                + (out == 0xFFFFFFFF ? "light" : "dark")
                                + " (depth " + i + ")");
                    }
                    return out;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** 取一个不透明纯色背景：ColorDrawable 直接取；GradientDrawable 反射取首个色标。 */
    private static int solidColorOf(android.graphics.drawable.Drawable bg) {
        if (bg == null) {
            return 0;
        }
        if (bg instanceof android.graphics.drawable.ColorDrawable) {
            return ((android.graphics.drawable.ColorDrawable) bg).getColor();
        }
        try {
            final java.lang.reflect.Method m = bg.getClass().getDeclaredMethod("getColors");
            final Object arr = m.invoke(bg);
            if (arr instanceof int[] && ((int[]) arr).length > 0) {
                final int[] cs = (int[]) arr;
                for (final int c : cs) {
                    if (c != 0 && ((c >>> 24) & 0xFF) >= 0x80) {
                        return c;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int mLastHostTint = 0;
    /** 诊断用：上一次探针的 "类名=颜色"，变化时才打印。 */
    private String mLastProbeNote = "";
    /** ★ v99：最近一次任一实例成功读到的真实色（9.9 原样，本轮按 9.9 保留）。 */
    private static int sLastGoodTint = 0;
    /** ★ OneUI 9.0 专属：状态栏左侧时钟/运营商名的固定灰，命中它才换探针。 */
    private static final int GRAY_CLOCK_9 = 0xff6f767d;

    /** 纯黑：OneUI 浅色下时钟/运营商名的染色，不是状态栏系统图标的颜色。 */
    private static final int PURE_BLACK = 0xff000000;

    /**
     * 这个 View 是不是"会被系统染色"的**真系统图标类** —— 与原生图标同步的那一类。
     * 时钟/运营商名这类 TextView 虽然也会被染色，但拿到的是文字色，不是图标色。
     */
    private static boolean isSystemIconView(View v) {
        if (v == null) {
            return false;
        }
        final String cn = v.getClass().getName();
        return cn.equals("com.android.systemui.statusbar.StatusBarIconView")
                || cn.equals("com.android.systemui.statusbar.policy.NetspeedView")
                || cn.equals("com.android.systemui.battery.BatteryMeterView");
    }

    /**
     * 这个色能不能直接拿去画图标？**只看 RGB，忽略 alpha** ——
     * 三星下发给时钟/运营商名这类文字的是 {@code b3000000}、{@code c4939393} 之类
     * 带 alpha 的黑/灰，与 {@code 0xff000000}、{@code 0xff6f767d} 字面值并不相等，
     * 用 {@code ==} 判等必然漏判（10-22「深色模式纯黑」的根因）。
     *
     * @param t ARGB。<b>0（该 View 未染色）不算不可用</b> —— 那是"探针暂时读不到色"
     *   的正常过渡态，必须留给原来的"沿用本实例上一次值"逻辑，否则颜色会来回跳。
     */
    private static boolean isUnusableInk(int t) {
        if (t == 0) {
            return false;
        }
        final int rgb = t & 0x00FFFFFF;
        if (rgb == (PURE_BLACK & 0x00FFFFFF)            // 纯黑（任意 alpha）
                || rgb == (GRAY_CLOCK_9 & 0x00FFFFFF)) { // OneUI 固定灰（任意 alpha）
            return true;
        }
        // 近乎黑（实机 probe[TextView]=b30c0c0c）同样不是图标色：原生图标最暗也就
        // 浅色下的深灰 ff535353 一档，不可能暗到 0x0c 这个量级。
        return luminance(0xFF000000 | rgb) <= 0.10f;
    }

    /**
     * 本实例是不是下拉面板（{@code com.android.systemui.util.NoRemeasureMotionLayout}
     * id=split_shade_status_bar）。下拉的明暗与状态栏独立，必须单独判。
     */
    private boolean isShadeSurface() {
        if (host == null) {
            return false;
        }
        try {
            final String n = host.getClass().getName();
            return n.contains("MotionLayout") || n.contains("ShadeHeader")
                    || n.contains("NotificationPanelView");
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static double luminance(int color) {        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    private void noteTint(int resolved, String src) {
        final String note = "tint[" + ((host == null) ? "?"
                : host.getClass().getSimpleName()) + "]: iconTint="
                + Integer.toHexString(mIconTint)
                + " night=" + nightMode()
                + " src=" + src
                + " -> " + Integer.toHexString(resolved);
        if (!note.equals(mLastTint)) {
            mLastTint = note;
            TrioHooks.log(TrioHooks.LOG_INFO, note);
        }
    }

    /** 夜间模式以 **host 自己的 context** 为准，拿不到才退回模块级 context。 */
    /** 9.9 原样：夜间模式取模块级 SystemUI context。 */
    private boolean nightMode() {
        final Context context = sContext;
        if (context == null) {
            return false;
        }
        try {
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================ 工具

    /** 统一处理 API 33+ 必须的 RECEIVER_NOT_EXPORTED 标志。 */
    private static void reg(Context ctx, BroadcastReceiver r, IntentFilter f) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(r, f);
            }
        } catch (Throwable ignored) {
        }
    }
}
