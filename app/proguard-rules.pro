# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Not Defterim: release (minify) için ek güvenlik/kararlılık kuralları ---

# Room entity'lerini (Note, ActivityLog, ChatMessage) ve DAO'ları koru.
# Room, KSP ile kod üretiyor ama R8 alan adlarını değiştirirse veritabanı
# okuma/yazma sırasında eşleşme hataları çıkabilir.
-keep class com.benim.notdefteri.data.** { *; }

# Firebase AI (Gemini) SDK'sının kullandığı kotlinx.serialization tabanlı
# modellerin R8 tarafından bozulmaması için tüm paketi ve serileştirme
# ile ilgili sınıfları koru (küçük bir uygulamada boyut kaybı önemsiz,
# önemli olan çalışma zamanında bozulmaması).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class com.google.firebase.ai.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Glance widget (androidx.glance) reflection ile RemoteViews üretiyor.
-keep class androidx.glance.appwidget.** { *; }

# App Check sağlayıcı sınıflarının adları koru (yansıma ile aranıyor olabilir).
-keep class com.google.firebase.appcheck.** { *; }

# Firestore, geri bildirim verilerini (HashMap) yansıma ile serileştiriyor.
-keep class com.google.firebase.firestore.** { *; }
-keepattributes Signature