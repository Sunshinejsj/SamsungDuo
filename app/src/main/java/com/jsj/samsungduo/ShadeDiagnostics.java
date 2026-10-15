package com.jsj.samsungduo;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * 下拉面板（shade）结构诊断器—— **只读，绝不修改系统 View 树**。
 *
 * <h3>为什么需要它</h3>
 * v39/v41 两次尝试把自绘 View 挂进下拉面板的 {@code StatusIconContainer}，
 * 均导致 SystemUI 崩溃。事后复盘：我一直是<b>读 dex 推断</b>容器结构，
 * 而没有<b>实机 View 树 dump</b>，导致连续猜错。
 *
 * <p>本类只做一件事：把目标容器的真实结构完整打出来，供离线分析：
 * <ul>
 *   <li>每一层的<b>类名</b>（含完整包名，判断是否自定义容器）</li>
 *   <li><b>资源 id 名</b>（如 system_icons / statusIcons）</li>
 *   <li><b>尺寸与可见性</b>（判断布局是否就绪）</li>
 *   <li><b>LayoutParams 的真实类型</b>（挂载时必须用同类型，否则 ClassCastException）</li>
 *   <li>是否实现 {@code StatusIconDisplayable}（系统只测量这类子 View）</li>
 *   <li>父容器类名（addView 的直接接收者，决定用哪种 LayoutParams）</li>
 * </ul>
 *
 * <p><b>安全保证</b>：全程只有读取与日志，绝不调用 addView/removeView/
 * setVisibility/setLayoutParams —— 因此<b>不可能导致 SystemUI 崩溃</b>。
 */
final class ShadeDiagnostics {

    private static final String TAG = "shade-diag";
    private static boolean dumped = false;
    /** v45：允许 dump 两次（attach 时 + 布局就绪后）。 */
    private static int dumpCount = 0;

    private ShadeDiagnostics() {
    }

    /**
     * 一次性 dump 下拉面板图标区的完整结构。
     *
     * <p><b>v45</b>：允许 dump 两次（首次 + 布局就绪后各一次），
     * 因为下拉面板 header 首次 attach 时尺寸可能为 0x0，
     * 布局完成后的那一次才能拿到真实尺寸。
     */
    static void dumpOnce(final View root) {
        if (root == null) {
            return;
        }
        if (dumpCount >= 8) {
            return;
        }
        dumpCount++;
        dumped = true;
        try {
            TrioHooks.log(TrioHooks.LOG_INFO, TAG + " ===== BEGIN SHADE TREE DUMP =====");
            final StringBuilder sb = new StringBuilder();
            walk(root, 0, sb, 0);
            // 分条写入，避免单条日志过长被截断
            final String[] lines = sb.toString().split("\n");
            for (String line : lines) {
                if (line != null && line.trim().length() > 0) {
                    TrioHooks.log(TrioHooks.LOG_INFO, TAG + " " + line);
                }
            }
            TrioHooks.log(TrioHooks.LOG_INFO, TAG + " ===== END SHADE TREE DUMP =====");
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, TAG + " dump failed: " + t);
        }
    }

    /**
     * ★ v48：dump目标 View 的<b>祖先链</b>（只读）。
     *
     * <p>为什么需要：{@code header} 拿到的 {@code split_shade_status_bar}
     * 只是下拉面板里的一个子节点，只有知道它上面还套了几层、
     * 每层是什么类/什么 id，才能确定"下拉真正的单栏根View"是谁。
     * 反方向（向下）dump 只能看到子树，看不到宿主。
     */
    static void dumpChain(final View v) {
        try {
            if (v == null) {
                return;
            }
            TrioHooks.log(TrioHooks.LOG_INFO, TAG + " ===== BEGIN SHADE ANCESTOR CHAIN =====");
            final StringBuilder sb = new StringBuilder();
            View cur = v;
            int up = 0;
            while (cur != null && up < 25) {
                sb.append("  UP[").append(up).append("] ")
                        .append(cur.getClass().getName())
                        .append(" id=").append(idName(cur))
                        .append(" ").append(cur.getWidth()).append("x").append(cur.getHeight())
                        .append(" vis=").append(visName(cur.getVisibility()));
                final ViewGroup.LayoutParams lp = cur.getLayoutParams();
                sb.append(" lp=").append(lp == null ? "null" : lp.getClass().getName());
                if (cur instanceof ViewGroup) {
                    sb.append(" children=").append(((ViewGroup) cur).getChildCount());
                }
                sb.append("\n");
                cur = (cur.getParent() instanceof View) ? (View) cur.getParent() : null;
                up++;
            }
            for (String line : sb.toString().split("\n")) {
                if (line != null && line.trim().length() > 0) {
                    TrioHooks.log(TrioHooks.LOG_INFO, TAG + " " + line);
                }
            }
            TrioHooks.log(TrioHooks.LOG_INFO, TAG + " ===== END SHADE ANCESTOR CHAIN =====");
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, TAG + " chain dump failed: " + t);
        }
    }

    private static void walk(View v, int depth, StringBuilder sb, int guard) {
        if (v == null || depth > 8 || guard > 200) {
            return;
        }
        final StringBuilder line = new StringBuilder();
        line.append("  ");
        for (int i = 0; i < depth; i++) {
            line.append("  ");
        }
        line.append(v.getClass().getName())
                .append(" id=").append(idName(v))
                .append(" ").append(v.getWidth()).append("x").append(v.getHeight())
                .append(" vis=").append(visName(v.getVisibility()));
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        line.append(" lp=").append(lp == null ? "null" : lp.getClass().getName());
        line.append(" displayable=").append(isDisplayable(v));
        if (v instanceof ViewGroup) {
            line.append(" children=").append(((ViewGroup) v).getChildCount());
        }
        sb.append(line).append("\n");

        if (v instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) v;
            final int n = vg.getChildCount();
            for (int i = 0; i < n; i++) {
                walk(vg.getChildAt(i), depth + 1, sb, guard + 1);
            }
        }
    }

    private static String idName(View v) {
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

    private static String visName(int v) {
        switch (v) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return String.valueOf(v);
        }
    }

    /** 是否实现 StatusIconDisplayable（用反射查接口，避免编译期依赖）。 */
    private static boolean isDisplayable(View v) {
        try {
            for (Class<?> k = v.getClass(); k != null; k = k.getSuperclass()) {
                for (Class<?> i : k.getInterfaces()) {
                    if ("com.android.systemui.statusbar.StatusIconDisplayable"
                            .equals(i.getName())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return false;
    }
}