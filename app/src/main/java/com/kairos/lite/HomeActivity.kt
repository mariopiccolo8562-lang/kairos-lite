package com.kairos.lite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/** Home in stile Smarters: tre riquadri grandi + «Continua a guardare». Niente altro. */
class HomeActivity : BaseActivity() {
    private lateinit var subLive: TextView
    private lateinit var subFilm: TextView
    private lateinit var subSeries: TextView
    private lateinit var sync: TextView
    private lateinit var recentBox: View
    private val recent = RecentAdapter()
    private var phaseWas = Sync.phase
    private val syncL: () -> Unit = { showSync() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.main.postDelayed({ Parental.firstRun(this) }, 2500) // primo avvio: scelta del PIN del controllo genitori
        setContentView(R.layout.activity_home)
        clock(findViewById(R.id.clock))
        findViewById<TextView>(R.id.date).text = Ui.today()
        subLive = findViewById(R.id.subLive)
        subFilm = findViewById(R.id.subFilm)
        subSeries = findViewById(R.id.subSeries)
        sync = findViewById(R.id.sync)
        recentBox = findViewById(R.id.recentBox)

        val live = findViewById<View>(R.id.tileLive)
        val film = findViewById<View>(R.id.tileFilm)
        val series = findViewById<View>(R.id.tileSeries)
        live.setOnClickListener { open(LiveActivity::class.java) }
        film.setOnClickListener { open(VodActivity::class.java) { putExtra("kind", "vod") } }
        series.setOnClickListener { open(VodActivity::class.java) { putExtra("kind", "series") } }
        listOf(live, film, series).forEach { Ui.zoom(it, 1.025f) }
        findViewById<View>(R.id.account).setOnClickListener { open(SettingsActivity::class.java) }
        findViewById<View>(R.id.settings).setOnClickListener { open(SettingsActivity::class.java) }
        findViewById<View>(R.id.search).setOnClickListener { open(SearchActivity::class.java) }
        vpnShield(findViewById(R.id.vpnShield))

        val rv = findViewById<RecyclerView>(R.id.recent)
        rv.fast(horizontal = true)
        rv.adapter = recent
        live.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        // aggiornamenti: Android chiede al cliente il permesso una volta; lo guidiamo noi (al massimo una volta a settimana)
        if (!isFinishing && !Remote.canInstall() && System.currentTimeMillis() - Store.permAskT > 7 * 86_400_000L) {
            Store.permAskT = System.currentTimeMillis()
            Ui.dialog(this, "Aggiornamenti automatici", "Per ricevere da sola le nuove versioni, Android chiede il tuo permesso una volta sola.\n\nPremi «Apri», attiva «Consenti da questa fonte» e poi torna indietro.", "Apri", "Dopo") { Remote.openInstallPerm() }
        }
        Sync.listen(syncL)
        fillAccount()
        counts()
        fillRecent()
        Sync.start()
        showSync()
        messages()
    }

    override fun onPause() {
        super.onPause()
        Sync.unlisten(syncL)
    }

    /** INDIETRO sulla home: si chiede conferma prima di chiudere l'app */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Ui.dialog(this, "Vuoi uscire?", "Kairos Lite si chiude. Canali e preferiti restano pronti per la prossima volta.", "Esci", "Annulla") {
            finishAffinity()
        }
    }

    override fun onStateChanged() {
        fillAccount()
        counts()
        messages()
    }

    private fun fillAccount() {
        val s = Panel.state ?: return
        val pr = K.profile()
        val name = pr?.name ?: s.name.ifEmpty { "Il mio account" }
        Ui.avatar(findViewById(R.id.avatar), name, pr?.avatar ?: "a1", 17)
        findViewById<TextView>(R.id.accName).text = name
        findViewById<TextView>(R.id.accExp).text = if (s.expires.isEmpty()) "Abbonamento attivo" else "Attivo fino al " + Ui.date(s.expires)
        findViewById<TextView>(R.id.brand).text = s.setting("brand").ifEmpty { "Kairos" }
    }

    private fun counts() {
        val pls = K.activeIds()
        val kids = K.kids
        bg {
            val l = Db.count("live", pls, kids)
            val v = Db.count("vod", pls, kids)
            val se = Db.count("series", pls, kids)
            ui {
                val wait = if (Sync.running) "Caricamento…" else "Nessun contenuto"
                subLive.text = if (l > 0) "${Ui.num(l)} canali · guida TV" else wait
                subFilm.text = if (v > 0) "${Ui.num(v)} titoli" else wait
                subSeries.text = if (se > 0) "${Ui.num(se)} serie" else wait
            }
        }
    }

    private fun showSync() {
        // canali pronti prima di film e serie: i numeri si aggiornano a ogni parte
        if (Sync.phase != phaseWas) { phaseWas = Sync.phase; counts() }
        if (Sync.running) {
            sync.visibility = View.VISIBLE
            sync.text = "Aggiornamento delle liste… ${Sync.pct}%"
            return
        }
        if (Sync.error.isNotEmpty()) {
            sync.visibility = View.VISIBLE
            sync.text = "Liste non aggiornate: ${Sync.error}. Riprova da Impostazioni → Aggiorna liste."
        } else sync.visibility = View.GONE
    }

    private fun fillRecent() {
        val list = Store.recent()
        recent.items = list
        recent.notifyDataSetChanged()
        recentBox.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun messages() {
        val m = K.unseenMessages()
        if (m.isEmpty()) return
        val text = m.joinToString("\n\n") { (if (it.title.isNotEmpty()) it.title + "\n" else "") + it.body }
        Store.seenMsgs = Store.seenMsgs + m.map { it.id }
        Ui.dialog(this, if (m.size == 1) "Messaggio dal tuo fornitore" else "Messaggi dal tuo fornitore", text)
    }

    private inner class RecentAdapter : RecyclerView.Adapter<RecentAdapter.H>() {
        var items: List<JSONObject> = emptyList()

        inner class H(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.img)
            val ph: TextView = v.findViewById(R.id.ph)
            val bar: ProgressBar = v.findViewById(R.id.bar)
            val title: TextView = v.findViewById(R.id.title)
            val left: TextView = v.findViewById(R.id.left)
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(p: ViewGroup, t: Int): H {
            val h = H(LayoutInflater.from(p.context).inflate(R.layout.item_recent, p, false))
            h.itemView.findViewById<View>(R.id.box).clipToOutline = true
            h.itemView.setOnClickListener {
                val i = h.bindingAdapterPosition
                if (i in items.indices) Play.resume(this@HomeActivity, items[i])
            }
            h.itemView.setOnLongClickListener {
                val i = h.bindingAdapterPosition
                if (i in items.indices) { Store.removeRecent(items[i].optString("id")); fillRecent() }
                true
            }
            Ui.zoom(h.itemView, 1.06f)
            return h
        }

        override fun onBindViewHolder(h: H, i: Int) {
            val o = items[i]
            val title = o.optString("title")
            h.title.text = title
            h.ph.text = title
            val dur = o.optLong("dur")
            val pos = o.optLong("pos")
            h.bar.progress = if (dur > 0) (pos * 100 / dur).toInt() else 0
            h.left.text = if (dur > 0) Ui.minutes(dur - pos) else ""
            Ui.img(h.img, o.optString("img"), resources.getDimensionPixelSize(R.dimen.recent_w), resources.getDimensionPixelSize(R.dimen.recent_h))
        }
    }
}
