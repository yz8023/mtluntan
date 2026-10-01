# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Rhino needs reflection-based calls
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.**

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keep class com.google.gson.** { *; }
-keep class io.mtluntan.app.data.db.entity.** { *; }
-keep class io.mtluntan.app.domain.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class io.mtluntan.app.data.db.entity.** { *; }

# Keep our models referenced from Gson/Room
-keepnames class io.mtluntan.app.** { *; }