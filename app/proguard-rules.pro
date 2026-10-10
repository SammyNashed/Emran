# Emran ProGuard / R8 rules.
#
# Release builds run with isMinifyEnabled = true and isShrinkResources = true, so
# anything reached only by reflection has to be kept explicitly. Everything below
# is required for a working release build -- removing a section will produce an
# APK that installs fine and then crashes at runtime.

# Keep line numbers so release crash reports are readable, but hide the original
# source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Moshi
# ---------------------------------------------------------------------------
# Emran uses KotlinJsonAdapterFactory (reflection based) in AdhkarRepository,
# QuranRepository and EmranWidgetStateSerializer. Reflection needs the Kotlin
# metadata, the generic signatures and the model constructors intact.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Generated adapters from moshi-kotlin-codegen.
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *

# Emran's own JSON models are deserialised reflectively -- keep their shape.
#
# The whole `data` package, not selected sub-packages. QuranChapter and
# QuranVerse sit directly in `io.github.sammynashed.emran.data`, which
# `data.assets.**` and `data.repository.**` do not match - so R8 renamed their
# fields, reflection could no longer find them, and the Quran tab failed with
# "Failed to fetch data: null" in release while working perfectly in debug.
-keep class io.github.sammynashed.emran.data.** { *; }
-keep class io.github.sammynashed.emran.glance.state.** { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Jetpack Glance / AppWidget
# ---------------------------------------------------------------------------
# The widget receiver and worker are instantiated by the framework by name.
-keep class io.github.sammynashed.emran.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# ---------------------------------------------------------------------------
# WorkManager / BroadcastReceivers
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class io.github.sammynashed.emran.service.** { *; }

# ---------------------------------------------------------------------------
# Kotlin coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# WorkManager builds its InputMerger by reflection for every job. R8 strips the
# no-arg constructor otherwise, and every worker (post-boot alarm restore, widget
# refresh) then fails with "Could not create Input Merger" - release builds only.
-keep class * extends androidx.work.InputMerger { <init>(); }

# Moshi (reflective enum adapter) and persisted state look enum constants up by
# NAME. Enums outside data.** (e.g. the theme/app-icon enum stored in widget
# state) were being renamed or pruned in release: "NoSuchFieldException: EXPRESSIVE".
-keepclassmembers enum io.github.sammynashed.emran.** { *; }

# ONNX Runtime's native library (libonnxruntime4j_jni.so) finds its Java classes
# by exact name through JNI (TensorInfo, OrtException, ...). Nothing in Kotlin
# references them, so R8 stripped/renamed them and the first OrtSession.run()
# aborted the process: "ClassNotFoundException: ai.onnxruntime.TensorInfo".
# RATL is the engine that drives it; keep both whole.
-keep class ai.onnxruntime.** { *; }
-keep class ai.ratl.** { *; }
-dontwarn ai.onnxruntime.**
