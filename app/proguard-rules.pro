# ZERO PLAYER - R8 rules
# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }

# Room generated implementations
-keep class com.zeroplayer.app.data.db.** { *; }

# Kotlin metadata used by reflection in a few platform paths
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations

# Keep MediaItem extras readable across the session boundary
-keepclassmembers class android.os.Bundle { *; }
