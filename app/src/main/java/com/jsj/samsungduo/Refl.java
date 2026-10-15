package com.jsj.samsungduo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small reflection helper. Never throws: all failures degrade to null / default. */
final class Refl {

    private Refl() {
    }

    static Class<?> cls(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    static Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
                // try superclass
            }
        }
        return null;
    }

    static Method method(Class<?> c, String name, Class<?>... sig) {
        // 必须沿继承链向上找：View/ Fragment 的生命周期方法（onAttachedToWindow、
        // onViewCreated、onDraw…）常声明在父类，只查 getDeclaredMethod 会漏。
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, sig);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                // try superclass
            }
        }
        return null;
    }

    static Object get(Field f, Object o) {
        if (f == null || o == null) {
            return null;
        }
        try {
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    static int getInt(Field f, Object o, int def) {
        Object v = get(f, o);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    static float getFloat(Field f, Object o, float def) {
        Object v = get(f, o);
        return v instanceof Number ? ((Number) v).floatValue() : def;
    }

    static boolean getBool(Field f, Object o, boolean def) {
        Object v = get(f, o);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    static void set(Field f, Object o, Object v) {
        if (f == null || o == null) {
            return;
        }
        try {
            f.set(o, v);
        } catch (Throwable ignored) {
            // best effort
        }
    }

    /** Invokes a method by name with an explicit signature, or returns null. */
    static Object callArgs(Object o, String name, Class<?>[] sig, Object[] args) {
        if (o == null) {
            return null;
        }
        try {
            Method m = o.getClass().getMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(o, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invokes an already-resolved no-arg method, or returns null. */
    static Object invoke(Method m, Object o) {
        if (m == null || o == null) {
            return null;
        }
        try {
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invokes an already-resolved method with arguments, or returns null. */
    static Object invokeArgs(Method m, Object o, Object[] args) {
        if (m == null || o == null) {
            return null;
        }
        try {
            return m.invoke(o, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invokes a public no-arg method by name, or returns null. */
    static Object callByName(Object o, String name) {
        if (o == null) {
            return null;
        }
        try {
            Method m = o.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }
}
