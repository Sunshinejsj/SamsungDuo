# The module entry point is named only from META-INF/xposed/java_init.list and is
# instantiated reflectively by the framework, so it must survive shrinking AND
# must keep its exact class name (java_init.list references it by that name).
# IMPORTANT: keep the PORTED package name com.jsj.samsungduo, not the original
# io.github.yixing233.hyperduo -- a stale rule for the old package silently
# lets R8 shrink the entire hook layer, which makes the module load as a
# ClassNotFoundException (LSPosed shows it active but nothing is hooked).
-keep class com.jsj.samsungduo.SamsungDuoModule { *; }

# The framework resolves hook targets and reads fields on the ROM's classes by
# name at runtime; nothing here is reachable from the entry point statically.
-keep class com.jsj.samsungduo.** { *; }

# The libxposed API is compile-only and supplied by the framework at runtime.
-dontwarn io.github.libxposed.**
