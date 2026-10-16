package com.benim.notdefteri

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Uygulama açılışında Firebase App Check'i kurar.
 *
 * App Check, AI (Gemini) isteklerinin sadece bu uygulamanın gerçek, değiştirilmemiş
 * bir kopyasından geldiğini doğrular. Bu sayede biri APK'yı decompile edip
 * Firebase proje bilgilerini başka bir uygulamada kullanarak senin kotanı/faturanı
 * tüketemez.
 *
 * NOT: Bu sadece istemci tarafı kurulum. Firebase konsolunda da
 * "App Check > APIs" kısmından ilgili API için "Enforce" (zorunlu kıl) seçeneğini
 * açman gerekiyor, yoksa doğrulama sadece izlenir, engellenmez.
 */
class NotdefteriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)

        val appCheck = Firebase.appCheck
        if (BuildConfig.DEBUG) {
            // Debug/emülatör derlemeleri: Logcat'te "DebugAppCheckProviderFactory" satırında
            // çıkan token'ı Firebase Console > App Check > Apps > Manage debug tokens
            // kısmına eklemen gerekir, yoksa debug derlemede AI istekleri reddedilebilir.
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            // Play Store'a yüklenen gerçek (release) derleme: cihazın ve uygulamanın
            // gerçek/değiştirilmemiş olduğunu Google Play Integrity API ile doğrular.
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
