package com.jsj.samsungduo;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SamsungDuo — 把 Wi‑Fi 信号与移动信号合并进三星状态栏，画成仿 iPhone Duo 的三合一
 * 图标（电量环 + Wi‑Fi 弧 + 信号点），挂在右上角状态栏。
 *
 * <p>完整移植自小米 HyperDuo，但 hook 层（{@link TrioHooks}）、取数层（{@link TrioState}）、
 * 叠加层（{@link TrioOverlay}）已重写为三星 One UI 8 / Android 16 适配版。
 *
 * <p>入口在 {@code META-INF/xposed/java_init.list} 声明；作用域在
 * {@code META-INF/xposed/scope.list}（仅 {@code com.android.systemui}）。
 */
public final class SamsungDuoModule extends XposedModule {

    /** 与原项目一致的日志标签；logcat 过滤用 {@code adb logcat -s SamsungDuo}。 */
    private static final String TAG = "SamsungDuo";
    private static final int LOG_INFO = 4;
    private static final int LOG_WARN = 5;

    /** 只 hook 系统界面；其它包（设置、桌面…）不碰。 */
    private static final String TARGET_PACKAGE = "com.android.systemui";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        final String pkg = param.getPackageName();
        // 关键诊断：模块只要被框架加载，这一行必定写进 /sdcard/SamsungDuo_debug.log。
        // 用户手机/电脑不在一起时，靠这个文件定位“无效”到底卡在哪一步。
        flog("=== SamsungDuoModule loaded, pkg=" + pkg + " (target=" + TARGET_PACKAGE + ") ===");
        if (!TARGET_PACKAGE.equals(pkg)) {
            flog("pkg != target, skip (no hooks for " + pkg + ")");
            return;
        }
        try {
            // 三星移植：用这一行替换原来的 MiuiXxx hook 分发。
            TrioHooks.hookAll(this, param.getClassLoader());
            flog("hookAll finished");
        } catch (Throwable t) {
            log(LOG_WARN, "hookAll failed: " + t);
            flog("hookAll FAILED: " + t);
        }
    }

    private void log(int priority, String msg) {
        try {
            log(priority, TAG, msg);
        } catch (Throwable ignored) {
            // 日志绝不能影响模块加载
        }
        flog(msg);
    }

    /** 上一次写盘的时刻（uptimeMillis），用于日志限流。 */
    private static volatile long sLastLogAt = 0L;
    /** 日志写盘最小间隔（ms）：防止高频回调把 I/O 打满。 */
    private static final long LOG_MIN_INTERVAL_MS = 100L;

    /**
     * 关键生命周期 / 一次性诊断标记：这些行必须无条件落盘。
     *
     * <p>原因：v10 加了 100ms 限流后，{@code statusbar view ready} 与
     * {@code overlay: window attached} 这类关键行被限流误伤丢弃，导致日志看不出
     * 叠加层到底走到哪一步（排查只能靠猜）。限流只该作用于会重复刷屏的行。
     */
    private static boolean isCritical(String msg) {
        return msg.startsWith("=== ")
                || msg.startsWith("Samsung hooks installed")
                || msg.startsWith("hookAll")
                || msg.startsWith("statusbar view ready")
                || msg.startsWith("statusbar root pending")
                || msg.startsWith("overlay:")
                || msg.startsWith("reload receiver registered")
                || msg.startsWith("tint:")
                || msg.startsWith("TREE[")
                // ★ v48：下拉面板结构 dump 必须豁免限流。
                //   实测(v47)：dumpOnce 一次性连打几十行 shade-diag，
                //   而 100ms 限流把这些行全部丢弃 → 日志里只剩
                //   "shade header ready after 1 attempts"，
                //   看不到任何 BEGIN SHADE TREE DUMP，
                //   白等了一轮实机。这里把它列为关键行。
                || msg.startsWith("shade-diag")
                || msg.startsWith("shade ")
                // ★ v56：信号阈值配置 dump 是一口气连打几十行，
                //   不豁免限流就会被 100ms 节流吃掉（v47 踩过一次，见 shade-diag）。
                || msg.startsWith("sigcfg")
                // ★ v65：行内几何 dump 同样是连打十几行，必须豁免限流。
                || msg.startsWith("geometry ");
    }

    /**
     * 同时把日志落盘到 /sdcard/SamsungDuo_debug.log，便于无 adb 时手机端查看。
     *
     * <p>带 100ms 限流：hook 回调（如每个图标 attach）可达毫秒级频率，
     * 不限流会瞬间写满磁盘并拖累 SystemUI。但关键生命周期行豁免限流。
     */
    public static void flog(String msg) {
        try {
            final long now = android.os.SystemClock.uptimeMillis();
            if (!isCritical(msg) && now - sLastLogAt < LOG_MIN_INTERVAL_MS) {
                return; // 限流：丢弃过高频的重复日志
            }
            sLastLogAt = now;
            final String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            final FileWriter fw = new FileWriter("/sdcard/SamsungDuo_debug.log", true);
            fw.write(ts + " " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {
            // 写盘失败绝不能影响模块加载
        }
    }
}
