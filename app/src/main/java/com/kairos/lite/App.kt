package com.kairos.lite

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.request.RequestOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Kairos Lite: app nativa leggera (viste Android classiche, nessuna WebView).
 * Su dispositivi con poca memoria parte da sola in «modalità leggera»: niente animazioni,
 * niente anteprima video nella lista canali, immagini a 16 bit e buffer più piccoli.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        I = this
        Store.init(this)
        Db.init(this)
        lite = detectLite()
        max = Store.liteMode == "max"
        // immagini: memoria limitata per classe (le chiavette hanno 1 GB di RAM); su disco un tetto per classe, così locandine e
        // loghi non si riscaricano: cartella cache dell'app, che Android svuota da sola se manca spazio
        try {
            val weakDev = Line.device() == "D"
            var mem = if (max) 4L shl 20 else if (lite) 6L shl 20 else 20L shl 20
            // classe D (Kairos Adaptive): al massimo 1/8 della memoria concessa all'app, anche senza modalità leggera
            if (weakDev) mem = minOf(mem, ((getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass.toLong() shl 20) / 8)
            Glide.init(this, GlideBuilder()
                .setMemoryCache(LruResourceCache(mem))
                .setBitmapPool(LruBitmapPool(mem / 2))
                .setDiskCache(InternalCacheDiskCacheFactory(this, if (weakDev) 150L shl 20 else 250L shl 20))
                .setDefaultRequestOptions(RequestOptions().format(if (lite || weakDev) DecodeFormat.PREFER_RGB_565 else DecodeFormat.PREFER_ARGB_8888))
                .setLogLevel(android.util.Log.ERROR))
        } catch (_: Throwable) {}
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {
                started++
                if (started == 1) Pulse.foreground(true)
            }
            override fun onActivityResumed(a: Activity) { top = a }
            override fun onActivityPaused(a: Activity) { if (top === a) top = null }
            override fun onActivityStopped(a: Activity) {
                started--
                if (started <= 0) { started = 0; Pulse.foreground(false) }
            }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // memoria che scarseggia (o app in sfondo): via tutta la cache delle immagini in memoria, poi si rilegge dal disco
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Ui.clearMemory()
            // pagine dell'archivio che SQLite tiene in memoria (fino a qualche MB): si rileggono dal disco quando servono
            try { SQLiteDatabase.releaseMemory() } catch (_: Throwable) {}
        } else try { Glide.get(this).trimMemory(level) } catch (_: Throwable) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Ui.clearMemory()
    }

    fun detectLite(): Boolean = when (Store.liteMode) {
        "on", "max" -> true
        "off" -> false
        else -> autoLite()
    }

    /** chiavette e box con meno di ~1,8 GB di RAM o Android vecchio (Fire OS 5/6) */
    fun autoLite(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.isLowRamDevice) return true
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.totalMem in 1 until 1_800_000_000L) return true
        return Build.VERSION.SDK_INT < 24
    }

    companion object {
        lateinit var I: App
        @Volatile var lite = false
        /** «Leggerissima»: per le chiavette più vecchie (liste senza loghi, immagini più piccole, canali in HD) */
        @Volatile var max = false
        val main = Handler(Looper.getMainLooper())
        val io: ExecutorService = Executors.newFixedThreadPool(3) { r -> Thread(r, "kl-io").apply { isDaemon = true } }
        var started = 0
        var top: Activity? = null
    }
}

fun bg(block: () -> Unit) {
    App.io.execute { try { block() } catch (t: Throwable) { Log.w("KairosLite", "bg", t) } }
}

/** rete lenta (guida TV, schede) su thread propri: non blocca le letture dell'archivio */
private val netPool = Executors.newFixedThreadPool(2) { r -> Thread(r, "kl-net").apply { isDaemon = true } }
fun net(block: () -> Unit) {
    netPool.execute { try { block() } catch (t: Throwable) { Log.w("KairosLite", "net", t) } }
}

fun ui(block: () -> Unit) {
    App.main.post { try { block() } catch (t: Throwable) { Log.w("KairosLite", "ui", t) } }
}
