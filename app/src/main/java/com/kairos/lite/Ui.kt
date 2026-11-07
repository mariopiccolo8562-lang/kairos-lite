package com.kairos.lite

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    /** leggero ingrandimento sull'elemento selezionato col telecomando (spento in modalità leggera) */
    fun zoom(v: View, scale: Float = 1.05f) {
        if (App.lite) return
        val prev = v.onFocusChangeListener
        v.setOnFocusChangeListener { x, has ->
            prev?.onFocusChange(x, has)
            x.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(110).start()
        }
    }

    fun toast(msg: String) {
        Toast.makeText(App.I, msg, Toast.LENGTH_LONG).show()
    }

    fun date(ymd: String): String =
        if (ymd.length == 10 && ymd[4] == '-') ymd.substring(8, 10) + "/" + ymd.substring(5, 7) + "/" + ymd.substring(0, 4) else ymd

    private val HM = SimpleDateFormat("HH:mm", Locale.ITALY)
    private val DAY = SimpleDateFormat("EEEE d MMMM", Locale.ITALY)
    fun hm(ms: Long): String = synchronized(HM) { HM.format(Date(ms)) }
    fun today(): String = synchronized(DAY) { DAY.format(Date()) }

    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ITALY, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ITALY, "%d:%02d", m, sec)
    }

    fun minutes(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    fun num(n: Int): String = String.format(Locale.ITALY, "%,d", n).replace(',', '.')

    private val AV = intArrayOf(
        0xFFFFB547.toInt(), 0xFF4FD1C5.toInt(), 0xFF7C9CFF.toInt(), 0xFFFF7A8A.toInt(), 0xFF8BD17C.toInt(), 0xFFC08CFF.toInt(),
        0xFFFF9F5A.toInt(), 0xFF5AC8FF.toInt(), 0xFFE8D25A.toInt(), 0xFFFF6FB0.toInt(), 0xFF6FE0A8.toInt(), 0xFFB0B8C8.toInt(),
    )
    fun avatarColor(av: String): Int {
        val n = av.removePrefix("a").toIntOrNull() ?: 1
        return AV[(n - 1).mod(AV.size)]
    }
    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.substring(0, 1).uppercase(Locale.ITALY) }.ifEmpty { "?" }

    /** cerchio colorato con le iniziali (profili, account) */
    fun avatar(t: TextView, name: String, av: String, radiusDp: Int) {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(t.context, radiusDp).toFloat()
        g.setColor(avatarColor(av))
        t.background = g
        t.text = initials(name)
    }

    /** dispositivo debole: modalità leggera oppure classe D di Kairos Adaptive (immagini a 16 bit, misure TMDB più piccole) */
    fun weak(): Boolean = App.lite || Line.device() == "D"

    private val TMDB = Regex("^(https?://image\\.tmdb\\.org/t/p/)(original|w(\\d+)[^/]*)(/.+)$")

    /**
     * Locandine e sfondi di TMDB (image.tmdb.org/t/p/<misura>/…): si chiede la misura più piccola adatta alla vista invece
     * dell'originale (locandine w185 / w342 / w500, sfondi w780 / w1280; sui dispositivi deboli locandine w185 e sfondi w780),
     * mai più grande di quella indicata nell'indirizzo. Gli altri indirizzi restano come sono.
     */
    fun tmdb(url: String, w: Int, h: Int): String {
        val m = TMDB.find(url) ?: return url
        val cur = m.groupValues[3].toIntOrNull() ?: Int.MAX_VALUE // «original»
        val weak = weak()
        val want = if (w > h) (if (weak || w <= 780) 780 else 1280) else (if (weak || w <= 185) 185 else if (w <= 342) 342 else 500)
        if (cur <= want) return url
        return m.groupValues[1] + "w" + want + m.groupValues[4]
    }

    /**
     * Immagine decodificata alla misura mostrata (mai all'originale), a 16 bit sui dispositivi deboli (metà memoria; Glide tiene
     * i 32 bit solo per le immagini trasparenti). Su disco resta anche la copia già ridotta: rivederla costa la decodifica di un
     * file piccolo, non quella dell'originale. TMDB: misura ridotta e, se quella non arriva, l'indirizzo originale.
     */
    fun img(v: ImageView, url: String?, w: Int, h: Int) {
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            Glide.with(v).clear(v)
            v.setImageDrawable(null)
            return
        }
        val small = tmdb(url, w, h)
        val rq = request(v, small, w, h)
        if (small == url) rq.into(v) else rq.error(fit(v, request(v, url, w, h))).into(v)
    }

    private fun request(v: ImageView, url: String, w: Int, h: Int): RequestBuilder<Drawable> {
        var rq = Glide.with(v).load(url).override(w, h).dontAnimate().diskCacheStrategy(DiskCacheStrategy.ALL)
        if (weak()) rq = rq.format(DecodeFormat.PREFER_RGB_565)
        return rq
    }

    /**
     * Svuota la cache delle immagini in memoria (thread principale). Le bitmap tolte dalla cache tornano nel pool di Glide
     * solo al giro successivo del thread principale: il pool si svuota di nuovo subito dopo, altrimenti resterebbero lì.
     */
    fun clearMemory() {
        try { Glide.get(App.I).clearMemory() } catch (_: Throwable) {}
        App.main.post { try { Glide.get(App.I).bitmapPool.clearMemory() } catch (_: Throwable) {} }
    }

    /** la richiesta di riserva riceve lo stesso adattamento che Glide dà da solo a quella principale (dallo scaleType) */
    private fun fit(v: ImageView, rq: RequestBuilder<Drawable>): RequestBuilder<Drawable> = when (v.scaleType) {
        ImageView.ScaleType.CENTER_CROP -> rq.optionalCenterCrop()
        ImageView.ScaleType.CENTER_INSIDE, ImageView.ScaleType.FIT_XY -> rq.optionalCenterInside()
        ImageView.ScaleType.FIT_CENTER, ImageView.ScaleType.FIT_START, ImageView.ScaleType.FIT_END -> rq.optionalFitCenter()
        else -> rq
    }

    fun dialog(a: Activity, title: String, msg: String, ok: String = "OK", cancel: String? = null, onOk: (() -> Unit)? = null): AlertDialog? {
        if (a.isFinishing) return null
        val b = AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton(ok) { _, _ -> onOk?.invoke() }
        if (cancel != null) b.setNegativeButton(cancel, null)
        return try { b.show() } catch (_: Exception) { null }
    }

    fun input(a: Activity, title: String, hint: String, numeric: Boolean, initial: String = "", onOk: (String) -> Unit) {
        if (a.isFinishing) return
        val e = EditText(a)
        e.hint = hint
        e.setText(initial)
        e.setSingleLine()
        e.setTextColor(androidx.core.content.ContextCompat.getColor(a, R.color.text))
        e.setHintTextColor(androidx.core.content.ContextCompat.getColor(a, R.color.dim))
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER
        val box = LinearLayout(a)
        val pad = dp(a, 20)
        box.setPadding(pad, dp(a, 8), pad, 0)
        box.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        try {
            AlertDialog.Builder(a).setTitle(title).setView(box)
                .setPositiveButton("OK") { _, _ -> onOk(e.text.toString().trim()) }
                .setNegativeButton("Annulla", null).show()
            e.requestFocus()
        } catch (_: Exception) {}
    }

    fun choose(a: Activity, title: String, items: Array<String>, checked: Int, onPick: (Int) -> Unit) {
        if (a.isFinishing) return
        try {
            AlertDialog.Builder(a).setTitle(title)
                .setSingleChoiceItems(items, checked) { d, i -> d.dismiss(); onPick(i) }
                .setNegativeButton("Annulla", null).show()
        } catch (_: Exception) {}
    }

    /** codice grande al centro (collegamento di un'altra TV) */
    fun bigCode(a: Activity, title: String, code: String, note: String) {
        if (a.isFinishing) return
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val pad = dp(a, 24)
        box.setPadding(pad, dp(a, 8), pad, 0)
        val t = TextView(a)
        t.text = code
        t.textSize = 44f
        t.letterSpacing = 0.12f
        t.setTextColor(androidx.core.content.ContextCompat.getColor(a, R.color.text))
        t.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val n = TextView(a)
        n.text = note
        n.setTextColor(androidx.core.content.ContextCompat.getColor(a, R.color.muted))
        n.textSize = 15f
        n.gravity = Gravity.CENTER
        box.addView(t)
        box.addView(n)
        try { AlertDialog.Builder(a).setTitle(title).setView(box).setPositiveButton("Fatto", null).show() } catch (_: Exception) {}
    }
}
