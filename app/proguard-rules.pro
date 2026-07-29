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

# Compose
-keep class androidx.compose.runtime.** { *; }

# Hilt KSP generated entry points
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep our entity classes (Room references them by name in generated code)
-keep class com.clipvault.manager.data.local.entity.** { *; }