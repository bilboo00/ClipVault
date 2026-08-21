# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.AndroidEntryPoint class *

# Keep generated Hilt classes
-keep class **_HiltModules** { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class hilt_aggregated_deps.** { *; }

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.flow.**

# Hilt KSP generated entry points
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep our entity classes (Room references them by name in generated code)
-keep class com.clipvault.manager.data.local.entity.** { *; }

# jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.internal.**

# Glance widget + protobuf
-keep class androidx.glance.appwidget.proto.** { *; }
-keep class androidx.glance.appwidget.protobuf.** { *; }
-keepclassmembers class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keepclassmembers class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Hilt entry points
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Hilt transitive jsr305
-dontwarn javax.annotation.**
-dontwarn javax.inject.**