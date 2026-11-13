package com.kairos.lite

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** Base di tutte le schermate: schermo intero, stato dell'abbonamento, VPN, «Disconnetti» dal pannello */
abstract class BaseActivity : Activity() {
    /** le schermate dei contenuti richiedono un abbonamento attivo */
    open val needsActive = true

    private var clockView: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() {
            clockView?.text = Ui.hm(System.currentTimeMillis())
            shieldRefresh()
            App.main.postDelayed(this, 20_000)
        }
    }
    private var shieldView: ImageView? = null
    private var dnsView: ImageView? = null

    /**
     * Lucchetto «DNS» accanto allo scudo (13 novembre 2026): c'è quando il fornitore ha acceso il DNS cifrato dal pannello.
     * Colori e frasi in [DnsBadge]. Al tocco dice lo stato; dopo un rifiuto o un errore riprova.
     */
    fun dnsBadge(v: ImageView?) {
        dnsView = v ?: return
        v.setOnClickListener { dnsTap() }
        dnsRefresh()
    }
    /** stato del DNS cifrato da mostrare ("" = il fornitore non lo ha acceso: niente lucchetto) */
    fun dnsState(): String = DnsBadge.state(Remote.sdns && Panel.state?.active == true, KxVpn.state == "on", DnsShield.state)
    fun dnsRefresh() {
        onDnsChanged()
        val v = dnsView ?: return
        val st = dnsState()
        if (st.isEmpty()) { v.visibility = View.GONE; return }
        v.visibility = View.VISIBLE
        v.isFocusable = true; v.isClickable = true
        v.imageTintList = android.content.res.ColorStateList.valueOf(DnsBadge.color(st))
        v.alpha = if (st == "off") 0.7f else 1f
        v.contentDescription = DnsBadge.text(st)
    }
    fun dnsTap() {
        val st = dnsState()
        if (st.isEmpty()) return
        if (!DnsBadge.canRetry(st)) { Ui.toast(DnsBadge.text(st)); return }
        DnsShield.retry()
        Vpns.apply(this)
        Ui.toast(if (st == "denied") "DNS cifrato: riprovo. Conferma la richiesta di Android" else "DNS cifrato: riprovo ad attivarlo…")
        for (ms in longArrayOf(800, 2_000, 4_000, 8_000)) App.main.postDelayed({ if (!isFinishing) dnsRefresh() }, ms)
    }
    /** lo stato del DNS cifrato può essere cambiato: le Home che lo disegnano a modo loro si aggiornano qui */
    open fun onDnsChanged() {}


    /** Scudo VPN nella barra in alto. Compare solo se il fornitore lo ha permesso dal pannello: VPN «Consentita» + permesso
     *  «Accendere/spegnere la VPN». Con «Sempre attiva» è un semplice indicatore (acceso, non si tocca); altrimenti è nascosto. */
    fun vpnShield(v: ImageView?) {
        shieldView = v ?: return
        v.setOnClickListener { vpnToggle() }
        shieldRefresh()
    }
    fun shieldRefresh() {
        dnsRefresh()
        val v = shieldView ?: return
        val s = Panel.state
        val ok = s != null && s.active && s.vpnConf.isNotEmpty()
        val can = ok && s!!.vpnMode == "allow" && s.perm("vpn")
        val forced = ok && s!!.vpnMode == "force"
        // la VPN la gestisce il fornitore (sempre attiva, consentita senza permesso, spenta): lo scudo c'è lo stesso e si può
        // premere, per leggere «VPN gestita dal fornitore» (voce «shield» del pannello)
        val managed = s != null && s.active && s.vpnShield
        if (!can && !forced && !managed) { v.visibility = View.GONE; return }
        v.visibility = View.VISIBLE
        v.isFocusable = true; v.isClickable = true
        val st = KxVpn.state
        val on = st == "on" || (forced && KxVpn.active())
        v.imageTintList = android.content.res.ColorStateList.valueOf(if (on) 0xFF2EE59D.toInt() else if (st == "connecting") 0xFFF5B041.toInt() else 0xCCFFFFFF.toInt())
        v.alpha = if (on || st == "connecting") 1f else 0.7f
        v.contentDescription = if (!can) "VPN gestita dal fornitore" else if (on) "VPN attiva: premi per spegnerla" else "VPN spenta: premi per accenderla"
    }
    private fun vpnToggle() {
        val s = Panel.state ?: return
        if (!(s.active && s.vpnConf.isNotEmpty() && s.vpnMode == "allow" && s.perm("vpn"))) { Ui.toast("VPN gestita dal fornitore"); return }
        Store.vpnUser = !Store.vpnUser
        if (Store.vpnUser) Vpns.retry()
        Ui.toast(if (Store.vpnUser) "Attivo la VPN…" else "VPN spenta")
        Vpns.apply(this)
        shieldRefresh()
        for (ms in longArrayOf(800, 2_000, 4_000, 8_000)) App.main.postDelayed({ if (!isFinishing) shieldRefresh() }, ms)
    }
    /** lo stato dal pannello è cambiato: prima lo scudo, poi la schermata (o la scelta del profilo, se quello in uso non c'è più) */
    fun stateChanged() {
        shieldRefresh()
        if (profileGone()) return
        onStateChanged()
    }

    /**
     * Il profilo in uso è stato tolto dal pannello: si torna a «Chi guarda?» come all'avvio. Mai durante la visione: lì vale al
     * ritorno nei menu (onResume).
     */
    private fun profileGone(): Boolean {
        if (this is ProfilesActivity || this is PlayerActivity || this is ActivationActivity || isFinishing || !K.profileGone()) return false
        startActivity(Intent(this, ProfilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screens.add(this)
        // dopo una chiusura forzata di Android (poca memoria) si riparte dall'ultimo stato salvato
        if (Panel.state == null) Panel.loadCached()
        if (App.lite) overridePendingTransition(0, 0)
    }

    override fun onStart() {
        super.onStart()
        // di ritorno dal lettore: le immagini tolte si ricaricano (dalla cache su disco)
        if (imagesOff) { imagesOff = false; reloadImages() }
    }

    override fun onStop() {
        super.onStop()
        // il lettore è salito sopra: le schermate sotto non tengono locandine e sfondi, il buffer video ha più memoria
        if (this !is PlayerActivity && App.top is PlayerActivity) freeForPlayer()
    }

    override fun onDestroy() {
        screens.remove(this)
        // le immagini della schermata tornano alla cache di Glide e le richieste ancora in corso si annullano
        Ui.unloadAll(window.peekDecorView())
        super.onDestroy()
    }

    /** immagini tolte con [freeImages]: al ritorno [reloadImages] */
    private var imagesOff = false

    /** toglie dalle viste le immagini di Glide (anche dalle righe staccate dei RecyclerView): la schermata non è visibile */
    open fun freeImages() {
        if (imagesOff || isFinishing) return
        imagesOff = true
        Ui.unloadAll(window.peekDecorView())
    }

    /** la schermata torna visibile dopo [freeImages]: rimette le sue immagini (le liste che si rileggono in onResume lo fanno già) */
    open fun reloadImages() {}

    override fun onResume() {
        super.onResume()
        if (needsActive) {
            val s = Panel.state ?: Panel.loadCached()
            if (s == null || !s.active) { goActivation(); return }
            if (profileGone()) return
            Vpns.apply(this)
        }
        if (clockView != null) { App.main.removeCallbacks(clockTick); clockTick.run() }
        shieldRefresh()
        Updater.resume(this) // di ritorno dalla schermata del permesso «Installa app sconosciute»: l'installazione riparte
        Updater.offer(this) // versione nuova trovata mentre si guardava un canale: la si propone qui
        // PIN del controllo genitori reimpostato durante la visione: a lettore chiuso si chiede subito il nuovo PIN
        if (Parental.askPending && this !is PlayerActivity) App.main.postDelayed({ if (!isFinishing && App.top === this && Parental.askPending) Parental.askNow(this) }, 1000)
    }

    override fun onPause() {
        super.onPause()
        App.main.removeCallbacks(clockTick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    fun clock(v: TextView?) {
        clockView = v
        if (v != null) v.text = Ui.hm(System.currentTimeMillis())
    }

    fun goActivation() {
        if (this is ActivationActivity) return
        startActivity(Intent(this, ActivationActivity::class.java).putExtra("stay", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    /** «Disconnetti» dal pannello: nei menu non c'è niente da chiudere (l'anteprima la ferma [LiveActivity]); nessun avviso al cliente */
    open fun onKicked() {}

    /** lo stato dal pannello è cambiato (playlist, permessi, messaggi…) */
    open fun onStateChanged() {}

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Vpns.REQ) Vpns.onConsent(resultCode == RESULT_OK)
        if (requestCode == DnsShield.REQ) DnsShield.onConsent(resultCode == RESULT_OK)
    }

    override fun finish() {
        super.finish()
        if (App.lite) overridePendingTransition(0, 0)
    }

    fun open(c: Class<out Activity>, fill: (Intent.() -> Unit)? = null) {
        val i = Intent(this, c)
        fill?.invoke(i)
        startActivity(i)
    }

    companion object {
        /** schermate aperte, dalla creazione alla chiusura (solo thread principale) */
        private val screens = LinkedHashSet<BaseActivity>()

        /** entrando nel lettore: via le immagini dalle schermate sotto e dalla cache in memoria, così il video ha spazio */
        fun freeForPlayer() {
            for (a in screens.toList()) if (a !is PlayerActivity) try { a.freeImages() } catch (_: Exception) {}
            Ui.clearMemory()
        }
    }
}
