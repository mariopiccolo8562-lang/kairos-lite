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
    private var runWas = Sync.running
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
        dnsBadge(findViewById(R.id.dnsBadge))

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
        loadArt()
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
        // Home dal pannello cambiata (es. adulti nascosti o visibili): «Continua a guardare» si rifà subito
        if (HomeCfg.key() != recentKey) fillRecent()
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

    /* ---------------- locandine nelle tessere (13 novembre 2026) ---------------- */
    // Film e Serie: i titoli di tendenza della settimana che la linea ha davvero (classifica del pannello), altrimenti le
    // ultime arrivate; TV in diretta: l'ultimo canale e il programma in onda. Spente con «Leggerissima» e dal pannello.
    private var artReq = 0
    private val posterIds = mapOf(
        R.id.filmPosters to listOf(R.id.filmP1, R.id.filmP2, R.id.filmP3),
        R.id.seriesPosters to listOf(R.id.seriesP1, R.id.seriesP2, R.id.seriesP3),
    )

    private fun loadArt() {
        val trendFilm = findViewById<TextView>(R.id.trendFilm)
        val trendSeries = findViewById<TextView>(R.id.trendSeries)
        val liveNow = findViewById<TextView>(R.id.liveNow)
        if (App.max || !Remote.homeArt) {
            artReq++
            for ((_, ids) in posterIds) ids.forEach { val v = findViewById<ImageView>(it); Ui.unload(v); v.visibility = View.GONE }
            listOf(trendFilm, trendSeries, liveNow).forEach { it.visibility = View.GONE }
            return
        }
        val pls = K.activeIds()
        val kids = K.kids
        val lastId = Store.lastLiveId
        val token = ++artReq
        bg {
            val trend = Trend.get()
            val tf = trend?.let { Db.posters("vod", it.movies, pls, kids, 3) }.orEmpty()
            val ts = trend?.let { Db.posters("series", it.series, pls, kids, 3) }.orEmpty()
            val films = if (tf.size >= 3) tf else Db.newestPosters("vod", pls, kids, 3)
            val series = if (ts.size >= 3) ts else Db.newestPosters("series", pls, kids, 3)
            val row = if (lastId > 0) Db.one("live", lastId) else null
            val prog = row?.let { try { Epg.now(K.pl(it.s("pl")), it.s("sid"), it.s("epg"), it.s("name")) } catch (_: Exception) { null } }
            ui {
                if (token != artReq || isFinishing) return@ui
                setPosters(R.id.filmPosters, films, 0)
                setPosters(R.id.seriesPosters, series, 0)
                line(trendFilm, films.firstOrNull()?.let { (if (tf.size >= 3) "Di tendenza: " else "Novità: ") + it.name })
                line(trendSeries, series.firstOrNull()?.let { (if (ts.size >= 3) "Di tendenza: " else "Novità: ") + it.name })
                line(liveNow, row?.let { r ->
                    val title = prog?.title.orEmpty()
                    "Ultimo: " + r.s("name") + if (title.isNotBlank()) " · ora $title" else ""
                })
            }
        }
    }

    private fun line(view: TextView, text: String?) {
        view.text = text.orEmpty()
        view.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** tre locandine in fila (100 %, 82 %, 66 %), alte quanto la tessera lascia; [tries]: la tessera non è ancora misurata */
    private fun setPosters(rowId: Int, items: List<Db.Poster>, tries: Int) {
        val row = findViewById<View>(rowId)
        val ids = posterIds[rowId] ?: return
        val h = row.height - row.paddingTop - row.paddingBottom
        if (h <= 0 || row.width <= 0) {
            if (tries < 5) row.postDelayed({ if (!isFinishing) setPosters(rowId, items, tries + 1) }, 120)
            return
        }
        val gap = Ui.dp(this, 6)
        val byWidth = ((row.width - 2 * gap) / (2f / 3f * 2.48f)).toInt()
        val tallest = minOf(h, byWidth, Ui.dp(this, 160))
        val shares = floatArrayOf(1f, 0.82f, 0.66f)
        ids.forEachIndexed { i, id ->
            val v = findViewById<ImageView>(id)
            val item = items.getOrNull(i)
            if (item == null || tallest < Ui.dp(this, 44)) {
                Ui.unload(v)
                v.visibility = View.GONE
                return@forEachIndexed
            }
            val ph = (tallest * shares[i]).toInt()
            val pw = ph * 2 / 3
            val lp = v.layoutParams
            if (lp.width != pw || lp.height != ph) { lp.width = pw; lp.height = ph; v.layoutParams = lp }
            if (v.background == null) {
                v.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Ui.dp(this@HomeActivity, 8).toFloat()
                    setColor(0x33000000)
                }
                v.clipToOutline = true
            }
            v.visibility = View.VISIBLE
            Ui.img(v, item.img, pw, ph)
        }
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
        // canali pronti prima di film e serie: i numeri si aggiornano a ogni parte, e quando l'aggiornamento parte
        // (il controllo è sul thread delle liste: può partire un attimo dopo l'apertura della Home)
        if (Sync.phase != phaseWas || Sync.running != runWas) { phaseWas = Sync.phase; runWas = Sync.running; counts() }
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

    /** configurazione della Home con cui è stato riempito «Continua a guardare», e ultima richiesta (vale solo l'ultima) */
    private var recentKey = ""
    private var recentSeq = 0

    private fun fillRecent() {
        recentKey = HomeCfg.key()
        val my = ++recentSeq
        val list = Store.recent()
        if (list.isEmpty() || !HomeCfg.noAdult()) { showRecent(list); return }
        // Home senza adulti (rc «adult» diverso da "on"): le categorie dei titoli si leggono dall'archivio, fuori dal thread dell'interfaccia
        bg {
            val ok = homeRecent(list)
            ui { if (!isFinishing && my == recentSeq) showRecent(ok) }
        }
    }

    private fun showRecent(list: List<JSONObject>) {
        recent.items = list
        recent.notifyDataSetChanged()
        recentBox.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * «Continua a guardare» senza adulti: fuori i film e le serie delle categorie nascoste o per adulti e quelli con un nome
     * per adulti (vedi [HomeCfg.blocked]). Legge l'archivio: solo fuori dal thread dell'interfaccia.
     */
    private fun homeRecent(all: List<JSONObject>): List<JSONObject> {
        fun tableOf(o: JSONObject) = if (o.optString("kind") == "series") "series" else "vod"
        fun keyOf(o: JSONObject) = if (o.optString("kind") == "series") o.optString("group") else o.optString("id")
        val names = HashMap<String, Map<String, String>>()
        for ((t, items) in all.groupBy { tableOf(it) }) names[t] = Db.catNames(t, items.map { keyOf(it) })
        return all.filter { o -> !HomeCfg.blocked(o.optString("title"), names[tableOf(o)]?.get(keyOf(o))) }
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

        override fun onViewRecycled(h: H) { Ui.unload(h.img) }

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
