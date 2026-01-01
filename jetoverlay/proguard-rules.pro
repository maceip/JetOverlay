# Keep the API and Service (Same as consumer rules)
-keep class com.yazan.jetoverlay.api.** { *; }
-keep class com.yazan.jetoverlay.service.OverlayService { *; }

# Standard Jetpack Compose Rules (Usually handled by dependencies, but good to have)
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable