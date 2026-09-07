# MediaPipe rules
-keep class com.google.mediapipe.** { *; }
-keep class com.google.android.libraries.mediapipe.** { *; }
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# TFLite rules
-keep class org.tensorflow.lite.** { *; }
-keepattributes *Annotation*
-keep class com.google.android.gms.tflite.** { *; }
