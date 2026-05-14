# Keep Kotlin metadata for reflection-friendly classes.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.compose.runtime.* *;
}
