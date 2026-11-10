# Keep manifest-declared components
-keep class com.callrecorder.CallRecorderService { *; }
-keep class com.callrecorder.MainActivity { *; }
-keep class com.callrecorder.BootReceiver { *; }
-keep class com.callrecorder.call.CallStateReceiver { *; }

# Android framework classes used via reflection by MediaRecorder
-keep class android.media.** { *; }

# Keep Kotlin metadata for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
