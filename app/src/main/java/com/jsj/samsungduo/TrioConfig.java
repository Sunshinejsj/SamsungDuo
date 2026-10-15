package com.jsj.samsungduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * Hook-side snapshot of the user's settings.
 *
 * <p>The remote {@link SharedPreferences} handed to a hooked process is
 * read-only, but it is live: the framework pushes {@code put}/{@code delete}
 * bundles over Binder and re-fires the change listeners. A single listener is
 * registered once and every change simply re-reads the handful of values into
 * this object, then pokes the rendered hosts so the next frame uses the new
 * values. The listener is invoked on a Binder thread; {@code invalidateHosts}
 * always posts to the main looper, so that is safe.
 *
 * <p>Every getter falls back to the {@link Prefs} default, which keeps the
 * module working when the framework is embedded and cannot serve remote
 * preferences at all.
 */
public final class TrioConfig {

    /** Notified (on whatever thread the framework used) after a value changed. */
    public interface Listener {
        void onConfigChanged();
    }

    private static final Object LOCK = new Object();

    /**
     * Registered listeners, held strongly and on purpose.
     *
     * <p>A weak list is tempting but silently wrong here: the only listeners are
     * anonymous inner classes created inside {@code TrioHooks.install}, so
     * nothing else keeps them reachable. They were collected on the first GC
     * after install, after which a settings change updated the snapshot but
     * repainted nothing - the module appeared unable to update the status bar
     * live. The module lives in the hooked process for its whole lifetime, so a
     * strong reference cannot outlive its usefulness.
     */
    private static final List<Listener> LISTENERS = new ArrayList<>();

    private static volatile SharedPreferences sPrefs;
    private static volatile boolean sDebugLog = Prefs.DEF_DEBUG_LOG;

    /** Guards {@link #installReceiver}: registration must happen exactly once. */
    private static volatile boolean sReceiverInstalled;

    // Values are published together, so a reader never sees a mixed snapshot.
    private static volatile TrioSettings sSnapshot = TrioSettings.defaults();

    private TrioConfig() {
    }

    /**
     * Binds to the framework's remote preferences. Safe to call repeatedly; only
     * the first successful call registers a listener.
     */
    static void install(XposedInterface xposed) {
        if (sPrefs != null) {
            return;
        }
        SharedPreferences prefs;
        try {
            prefs = xposed.getRemotePreferences(Prefs.NAME);
        } catch (Throwable t) {
            // Embedded framework (or a very old one): run on defaults.
            TrioHooks.log(TrioHooks.LOG_WARN, "remote preferences unavailable: " + t);
            return;
        }
        if (prefs == null) {
            return;
        }
        sPrefs = prefs;
        reload();
        try {
            prefs.registerOnSharedPreferenceChangeListener(LISTENER);
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot watch preferences: " + t);
        }
        // Re-read once the framework has had time to serve the user's values.
        //
        // install() runs inside the first hook callback of a booting SystemUI,
        // and on a cold start the remote-preferences map can still hold
        // defaults for a few seconds: the daemon has not pushed the user's
        // file across yet. The first reload() then publishes an all-defaults
        // snapshot - signal mode in-ring, arcs on, type off - so the early
        // layout passes fold nothing, reserve nothing, and the native icons
        // share the bar with whatever the module has mounted by then (issue
        // #9: a stray native "5G" after every reboot until a toggle forces a
        // re-read). The change listener cannot heal this: it only fires on
        // writes, and nobody writes at boot.
        //
        // One delayed re-read closes the window. The change listener stays
        // registered the whole time, so a user write in between simply wins
        // twice; reloadIfChanged() publishes only when the file really moved,
        // so the second read is usually a no-op.
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                reloadIfChanged();
            }
        }, RESYNC_DELAY_MS);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                reloadIfChanged();
            }
        }, RESYNC_DELAY_MS * 4);
    }

    /** How long after install to re-read the remote map, in milliseconds. */
    private static final long RESYNC_DELAY_MS = 5_000L;

    /**
     * Re-reads the whole configuration and notifies the listeners only when
     * something actually moved - the boot-time backstop for the early read
     * having seen defaults. Notified through the normal path so the hosts
     * re-fold and re-sync exactly as they would for a settings change.
     */
    private static void reloadIfChanged() {
        final TrioSettings previous = sSnapshot;
        reload();
        final TrioSettings now = sSnapshot;
        if (previous == now || previous.equals(now)) {
            return;
        }
        TrioHooks.log(TrioHooks.LOG_INFO, "delayed settings re-read applied");
        notifyChanged();
    }

    /** Immutable view of every setting the renderer cares about. */
    static TrioSettings get() {
        return sSnapshot;
    }

    /**
     * The same snapshot resolved into the drawing and suppression rules.
     *
     * <p>Convenience for the hook side, which reads it far more often than it
     * reads a raw setting: {@code TrioAppearance.of(get())} at every call site
     * was noisy enough that the rules started being re-derived by hand again.
     */
    static TrioAppearance appearance() {
        return TrioAppearance.of(sSnapshot);
    }

    static boolean debugLog() {
        return sDebugLog;
    }

    static void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LOCK) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Listens for the settings app's explicit reload broadcast, the
     * callback-independent second path for a changed setting.
     *
     * <p>The framework's remote-preference callback is the intended mechanism and
     * stays in place, but it has been observed to stop reaching the hooked process
     * on device: the daemon database held the new {@code enabled} value while
     * SystemUI kept drawing the trio glyph from the old snapshot. The broadcast
     * carries the whole snapshot, so it cannot half-apply.
     *
     * <p>Idempotent: whichever hooked view calls it first wins; later calls are
     * no-ops, so registering from every relevant lifecycle callback is safe.
     *
     * @param context a SystemUI context, which is only reachable from inside a
     *                hook callback - not from {@code install}.
     */
    static void installReceiver(Context context) {
        if (context == null || sReceiverInstalled) {
            return;
        }
        // The view this is called from can live in a window context that SystemUI
        // tears down and rebuilds (status bar, keyguard, a re-inflated bar), and a
        // receiver registered on one of those dies with it. The application context
        // lives as long as the process, which is what the one-shot registration
        // below assumes; fall back to what we were handed if it is unavailable.
        final Context app = context.getApplicationContext();
        final Context target = (app != null) ? app : context;
        sReceiverInstalled = true;
        try {
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    onReloadBroadcast(intent);
                }
            };
            final IntentFilter filter = new IntentFilter(Prefs.ACTION_RELOAD);
            if (Build.VERSION.SDK_INT >= 33) {
                // The sender is a different app, so the receiver has to be
                // exported. That does mean any app can forge this intent; the
                // worst it can do is change how this user's own status bar is
                // drawn, which is the same thing the settings app is allowed to
                // do, so no further guard is warranted here.
                target.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                target.registerReceiver(receiver, filter);
            }
            TrioHooks.log(TrioHooks.LOG_INFO, "reload receiver registered");
        } catch (Throwable t) {
            sReceiverInstalled = false;
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot register reload receiver: " + t);
        }
    }

    /**
     * Applies a snapshot delivered by broadcast.
     *
     * <p>Deliberately does not touch {@link #sPrefs}: the local cache is not
     * writable and cannot be updated from here. When the framework's own callback
     * does work it delivers the same values, so the two paths agree.
     *
     * <p>The two paths cannot fight over the result. Ordering them is not enough
     * on its own - the framework callback applies only the key it reports,
     * precisely because its map can be stale for every other key - so this
     * broadcast is what carries a key that callback never delivered. A later
     * framework callback for an unrelated key therefore leaves this snapshot
     * intact instead of reverting it.
     */
    private static void onReloadBroadcast(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            final Bundle extras = intent.getExtras();
            if (extras == null || !extras.containsKey(Prefs.KEY_ENABLED)) {
                // A bundle without the master switch is not one this module sent
                // and would move state on a stranger's behalf. Checked with
                // containsKey rather than Bundle.isEmpty(), which is API 31 while
                // this module declares minSdk 29.
                TrioHooks.log(TrioHooks.LOG_WARN, "reload broadcast without extras, ignored");
                return;
            }
            // Seeded from the current snapshot: a key the sender omitted keeps the
            // value it already had instead of snapping back to a shared default.
            final TrioSettings previous = sSnapshot;
            final TrioSettings incoming = TrioSettings.fromBundle(extras, previous);
            sDebugLog = incoming.debugLog;
            sSnapshot = incoming;
            if (sDebugLog) {
                TrioHooks.log(TrioHooks.LOG_INFO,
                        "reload broadcast: enabled=" + incoming.enabled
                                + " ring=" + incoming.ringStroke
                                + " arc=" + incoming.arcStroke
                                + " size=" + incoming.valueSize
                                + " (was enabled=" + previous.enabled + ")");
            }
            notifyChanged();
        } catch (Throwable t) {
            TrioHooks.log(TrioHooks.LOG_WARN, "bad reload broadcast: " + t);
        }
    }

    /**
     * The framework fires once per changed key. Only that key's value is
     * guaranteed fresh - see {@link TrioSettings#applyKeyFrom} - so it is applied
     * on its own rather than by re-reading the whole snapshot, which would
     * re-publish stale values for every key whose diff never arrived.
     */
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (!applyKey(prefs, key)) {
                        return;
                    }
                    if (sDebugLog) {
                        TrioHooks.log(TrioHooks.LOG_INFO,
                                "remote pref changed: " + key
                                        + " -> enabled=" + sSnapshot.enabled
                                        + " ring=" + sSnapshot.ringStroke
                                        + " arc=" + sSnapshot.arcStroke
                                        + " size=" + sSnapshot.valueSize);
                    }
                    notifyChanged();
                }
            };

    /**
     * Copies one key's fresh value into the published snapshot.
     *
     * @return true when the snapshot changed, so listeners need notifying
     */
    private static boolean applyKey(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) {
            return false;
        }
        try {
            final TrioSettings next = sSnapshot.copy();
            if (!next.applyKeyFrom(TrioSettings.from(prefs), key)) {
                // Not a key this module renders with; nothing to repaint.
                return false;
            }
            sDebugLog = next.debugLog;
            sSnapshot = next;
            return true;
        } catch (Throwable t) {
            // A type mismatch in the framework's cast must never break drawing.
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot read preferences: " + t);
            return false;
        }
    }

    /**
     * Reads the whole configuration once, when the framework connects. A full
     * read is right here and only here: the map is authoritative at install time
     * and nothing has been published yet that it could be stale against.
     */
    private static void reload() {
        SharedPreferences prefs = sPrefs;
        if (prefs == null) {
            return;
        }
        try {
            sSnapshot = TrioSettings.from(prefs);
            sDebugLog = prefs.getBoolean(Prefs.KEY_DEBUG_LOG, Prefs.DEF_DEBUG_LOG);
        } catch (Throwable t) {
            // A type mismatch in the framework's cast must never break drawing.
            TrioHooks.log(TrioHooks.LOG_WARN, "cannot read preferences: " + t);
        }
    }

    private static void notifyChanged() {
        List<Listener> alive;
        synchronized (LOCK) {
            alive = new ArrayList<>(LISTENERS);
        }
        for (Listener l : alive) {
            try {
                l.onConfigChanged();
            } catch (Throwable t) {
                // One bad listener must not stop the others.
                TrioHooks.log(TrioHooks.LOG_WARN, "config listener failed: " + t);
            }
        }
    }

}
