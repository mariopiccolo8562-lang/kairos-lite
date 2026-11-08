package com.kairos.lite

import android.os.Bundle
import android.util.JsonReader
import android.util.JsonToken
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

/** Scheda di un film o di una serie (stagioni ed episodi) */
class DetailActivity : BaseActivity() {
    class Ep(val id: String, val season: Int, val num: Int, val title: String, val ext: String, val url: String, val img: String, val dur: String)

    private lateinit var table: String
    private var row: Db.Row? = null
    private var pl: Playlist? = null
    private var key = ""
    private var title = ""
    private var poster = ""
    private var backdrop = ""

    private lateinit var playBtn: Button
    private lateinit var restartBtn: Button
    private lateinit var favBtn: ImageButton
    private lateinit var loading: TextView

    private var episodes: List<Ep> = emptyList()
    private var seasons: List<Int> = emptyList()
    private var season = 0
    private val seasonAdapter = SeasonAdapter()
    private val epAdapter = EpAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        table = if (intent.getStringExtra("table") == "series") "series" else "vod"
        playBtn = findViewById(R.id.play)
        restartBtn = findViewById(R.id.restart)
        favBtn = findViewById(R.id.fav)
        loading = findViewById(R.id.loading)
        findViewById<View>(R.id.posterBox).clipToOutline = true
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.kind).text = if (table == "series") "Serie TV" else "Film"
        favBtn.setOnClickListener {
            if (key.isEmpty()) return@setOnClickListener
            val on = Store.toggleFav(table, key)
            favBtn.setImageResource(if (on) R.drawable.ic_heart_fill else R.drawable.ic_heart)
        }
        playBtn.setOnClickListener { play(false) }
        restartBtn.setOnClickListener { play(true) }
        val sv = findViewById<RecyclerView>(R.id.seasons)
        sv.fast(horizontal = true)
        sv.adapter = seasonAdapter
        val ev = findViewById<RecyclerView>(R.id.episodes)
        ev.fast(horizontal = true)
        ev.adapter = epAdapter
        Ui.zoom(playBtn, 1.04f)

        val id = intent.getLongExtra("id", 0)
        bg {
            val r = Db.one(table, id)
            ui {
                if (r == null) { Ui.toast("Contenuto non trovato"); finish(); return@ui }
                row = r
                pl = K.pl(r.s("pl"))
                key = r.s("k")
                title = r.s("name")
                poster = r.s("img")
                fillBasic(r)
                loadInfo(r)
            }
        }
        playBtn.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (row != null) refreshButtons()
        epAdapter.notifyDataSetChanged()
    }

    private fun fillBasic(r: Db.Row) {
        findViewById<TextView>(R.id.title).text = title
        findViewById<TextView>(R.id.plot).text = r.s("plot")
        meta(r.s("year"), r.s("rating"), "", "")
        Ui.img(findViewById(R.id.poster), poster, resources.getDimensionPixelSize(R.dimen.poster_w), resources.getDimensionPixelSize(R.dimen.poster_h))
        favBtn.setImageResource(if (Store.isFav(table, key)) R.drawable.ic_heart_fill else R.drawable.ic_heart)
        refreshButtons()
    }

    private fun meta(year: String, rating: String, genre: String, dur: String) {
        val r = rating.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ " + String.format(java.util.Locale.ITALY, "%.1f", if (it > 10) it / 10 else it) }
        findViewById<TextView>(R.id.meta).text = listOfNotNull(year.ifEmpty { null }, genre.ifEmpty { null }, dur.ifEmpty { null }, r).joinToString("  •  ")
    }

    private fun setBackdrop(url: String) {
        if (url.isEmpty() || url == backdrop) return
        backdrop = url
        showBackdrop()
    }

    /** sfondo della scheda in ogni modalità: sulle chiavette solo più leggero (misura ridotta, 16 bit), mai tolto */
    private fun showBackdrop() {
        val w = if (App.max) 480 else if (App.lite) 640 else 1280
        Ui.img(findViewById(R.id.backdrop), backdrop, w, w * 9 / 16)
    }

    /** di ritorno dal lettore: locandina e sfondo tolti per far spazio al video si rimettono (gli episodi in onResume) */
    override fun reloadImages() {
        if (row == null) return
        Ui.img(findViewById(R.id.poster), poster, resources.getDimensionPixelSize(R.dimen.poster_w), resources.getDimensionPixelSize(R.dimen.poster_h))
        if (backdrop.isNotEmpty()) showBackdrop()
    }

    /* ---------------- dati dal server ---------------- */
    private fun loadInfo(r: Db.Row) {
        val p = pl
        if (table == "vod") {
            val url = p?.api("get_vod_info", "&vod_id=" + r.s("sid")) ?: return
            net {
                val info = try { JSONObject(Http.text(url, connectMs = 12_000, readMs = 20_000).text).optJSONObject("info") } catch (_: Exception) { null }
                ui {
                    if (info == null || isFinishing) return@ui
                    val plot = info.optString("plot").ifEmpty { info.optString("description") }
                    if (plot.isNotEmpty()) findViewById<TextView>(R.id.plot).text = plot
                    meta(r.s("year").ifEmpty { info.optString("releasedate").take(4) }, info.optString("rating").ifEmpty { r.s("rating") }, info.optString("genre"), durText(info))
                    setBackdrop(first(info.opt("backdrop_path")).ifEmpty { info.optString("cover_big") })
                }
            }
            return
        }
        findViewById<View>(R.id.epBox).visibility = View.VISIBLE
        if (p != null && p.isXtream && r.s("sid").isNotEmpty()) {
            loading.visibility = View.VISIBLE
            loading.text = "Caricamento episodi…"
            val url = p.api("get_series_info", "&series_id=" + r.s("sid")) ?: return
            net {
                // letta a flusso: anche con migliaia di episodi in memoria resta l'elenco, non tutto il testo della risposta
                val si = try { SeriesJson.load(url) } catch (_: Exception) { null }
                val list = si?.eps ?: emptyList()
                val info = si?.info
                ui {
                    if (isFinishing) return@ui
                    loading.visibility = View.GONE
                    if (info != null) {
                        val plot = info["plot"] ?: ""
                        if (plot.isNotEmpty()) findViewById<TextView>(R.id.plot).text = plot
                        meta(r.s("year").ifEmpty { (info["releaseDate"] ?: "").take(4) }, (info["rating"] ?: "").ifEmpty { r.s("rating") }, info["genre"] ?: "", "")
                        setBackdrop(info["backdrop_path"] ?: "")
                    }
                    setEpisodes(list.sortedWith(compareBy({ it.season }, { it.num })))
                }
            }
        } else {
            bg {
                val list = Db.episodes(r.l("_id")).mapIndexed { i, e -> Ep(Integer.toHexString(Playlist.pathOf(e.url).hashCode()), e.season, e.num, e.title, "", e.url, "", "") }
                ui { setEpisodes(list) }
            }
        }
    }

    private fun durText(o: JSONObject): String = durOf(o.optString("duration_secs"), o.optString("duration"))

    private fun first(v: Any?): String = when (v) {
        is JSONArray -> if (v.length() > 0) v.optString(0) else ""
        is String -> v
        else -> ""
    }

    private fun setEpisodes(list: List<Ep>) {
        episodes = list
        seasons = list.map { it.season }.distinct().sorted()
        if (list.isEmpty()) { loading.visibility = View.VISIBLE; loading.text = "Nessun episodio disponibile."; return }
        val last = lastEpisode()
        season = last?.season ?: seasons.first()
        seasonAdapter.notifyDataSetChanged()
        epAdapter.items = list.filter { it.season == season }
        epAdapter.notifyDataSetChanged()
        refreshButtons()
    }

    /* ---------------- riproduzione ---------------- */
    private fun epKey(e: Ep) = "ep:$key:${e.id}"

    private fun lastEpisode(): Ep? {
        val rec = Store.recent().firstOrNull { it.optString("group") == key } ?: return null
        val id = rec.optString("id")
        return episodes.firstOrNull { epKey(it) == id }
    }

    private fun refreshButtons() {
        if (table == "vod") {
            val pos = Store.position(key)
            playBtn.text = if (pos > 60_000) "Riprendi da " + Ui.clock(pos) else "Guarda"
            restartBtn.visibility = if (pos > 60_000) View.VISIBLE else View.GONE
        } else {
            val e = lastEpisode()
            playBtn.text = if (e != null) "Riprendi S${e.season} E${e.num}" else if (episodes.isNotEmpty()) "Guarda S${episodes[0].season} E${episodes[0].num}" else "Guarda"
            restartBtn.visibility = View.GONE
        }
    }

    private fun play(fromStart: Boolean) {
        val r = row ?: return
        if (table == "vod") {
            val url = Play.movieUrl(r) ?: run { Ui.toast("Indirizzo del film non valido"); return }
            val start = if (fromStart) 0L else Store.position(key)
            val rec = JSONObject().put("id", key).put("kind", "movie").put("title", title).put("img", backdrop.ifEmpty { poster })
                .put("pl", r.s("pl")).put("sid", r.s("sid")).put("ext", r.s("ext")).put("url", r.s("url"))
            Play.vod(this, "movie", key, title, "", url, start, rec, null)
            return
        }
        val e = lastEpisode() ?: episodes.firstOrNull() ?: return
        playEpisode(e)
    }

    private fun playEpisode(e: Ep) {
        val r = row ?: return
        val url = epUrl(e) ?: run { Ui.toast("Indirizzo dell'episodio non valido"); return }
        val all = episodes
        val i = all.indexOf(e)
        val queue = JSONArray()
        for (n in all.drop(i + 1).take(30)) {
            val u = epUrl(n) ?: continue
            queue.put(JSONObject().put("id", epKey(n)).put("title", title).put("sub", "S${n.season} E${n.num} · " + n.title).put("url", u).put("recent", recentOf(r, n)))
        }
        Play.vod(this, "series", epKey(e), title, "S${e.season} E${e.num} · " + e.title, url, Store.position(epKey(e)), recentOf(r, e), queue)
    }

    private fun recentOf(r: Db.Row, e: Ep): JSONObject = JSONObject().put("id", epKey(e)).put("group", key).put("kind", "series")
        .put("title", "$title · S${e.season} E${e.num}").put("img", e.img.ifEmpty { backdrop.ifEmpty { poster } })
        .put("pl", r.s("pl")).put("sid", e.id).put("ext", e.ext).put("url", e.url)

    private fun epUrl(e: Ep): String? {
        val p = pl ?: return null
        return if (e.url.isNotEmpty()) p.fix(e.url) else p.episodeUrl(e.id, e.ext)
    }

    /* ---------------- stagioni ed episodi ---------------- */
    private inner class SeasonAdapter : RecyclerView.Adapter<SeasonAdapter.H>() {
        inner class H(val t: TextView) : RecyclerView.ViewHolder(t)
        override fun getItemCount() = seasons.size
        override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
            val h = H(LayoutInflater.from(p.context).inflate(R.layout.item_pill, p, false) as TextView)
            h.t.setOnClickListener {
                val i = h.bindingAdapterPosition
                if (i !in seasons.indices) return@setOnClickListener
                season = seasons[i]
                notifyDataSetChanged()
                epAdapter.items = episodes.filter { it.season == season }
                epAdapter.notifyDataSetChanged()
            }
            return h
        }
        override fun onBindViewHolder(h: H, i: Int) {
            h.t.text = "Stagione " + seasons[i]
            h.t.isActivated = seasons[i] == season
        }
    }

    private inner class EpAdapter : RecyclerView.Adapter<EpAdapter.H>() {
        var items: List<Ep> = emptyList()
        inner class H(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.img)
            val tag: TextView = v.findViewById(R.id.tag)
            val bar: ProgressBar = v.findViewById(R.id.bar)
            val title: TextView = v.findViewById(R.id.title)
            val meta: TextView = v.findViewById(R.id.meta)
        }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(p: ViewGroup, v: Int): H {
            val h = H(LayoutInflater.from(p.context).inflate(R.layout.item_episode, p, false))
            h.itemView.findViewById<View>(R.id.box).clipToOutline = true
            h.itemView.setOnClickListener {
                val i = h.bindingAdapterPosition
                if (i in items.indices) playEpisode(items[i])
            }
            Ui.zoom(h.itemView, 1.05f)
            return h
        }
        override fun onViewRecycled(h: H) { Ui.unload(h.img) }
        override fun onBindViewHolder(h: H, i: Int) {
            val e = items[i]
            h.tag.text = "E" + e.num
            h.title.text = e.num.toString() + ". " + e.title.ifEmpty { "Episodio " + e.num }
            h.meta.text = e.dur
            val pos = Store.position(epKey(e))
            val dur = Store.duration(epKey(e))
            h.bar.visibility = if (pos > 0 && dur > 0) View.VISIBLE else View.GONE
            h.bar.progress = if (dur > 0) (pos * 100 / dur).toInt() else 0
            val w = resources.getDimensionPixelSize(R.dimen.ep_w)
            Ui.img(h.img, e.img.ifEmpty { backdrop.ifEmpty { poster } }, w, w * 9 / 16)
        }
    }
}

/** durata di un film o di un episodio: dai secondi, altrimenti da «hh:mm:ss» */
private fun durOf(secsText: String, d: String): String {
    val secs = secsText.toLongOrNull() ?: 0
    if (secs > 0) return Ui.minutes(secs * 1000)
    return if (d.length >= 5 && d[2] == ':') { val h = d.substring(0, 2).toIntOrNull() ?: 0; val m = d.substring(3, 5).toIntOrNull() ?: 0; Ui.minutes((h * 60L + m) * 60_000) } else ""
}

/**
 * get_series_info letto a flusso (JsonReader): le serie con migliaia di episodi non passano più da tutto il testo della
 * risposta e dall'albero JSONObject (decine di MB per le soap più lunghe, troppi per le chiavette da 1 GB). Si tengono solo i
 * campi usati, con gli stessi valori di optString / optJSONObject sul JSON intero: null diventa «null», una chiave ripetuta
 * vale con l'ultimo valore, una risposta che non è un oggetto JSON valido non dà né scheda né episodi.
 */
private object SeriesJson {
    class Result(val info: Map<String, String>?, val eps: List<DetailActivity.Ep>)

    fun load(url: String): Result {
        val c = Http.open(url, connectMs = 12_000, readMs = 30_000)
        try {
            val r = JsonReader(InputStreamReader(Http.stream(c), Charsets.UTF_8))
            r.isLenient = true
            try { return read(r) } finally { try { r.close() } catch (_: Exception) {} }
        } finally {
            c.disconnect()
        }
    }

    private fun read(r: JsonReader): Result {
        if (r.peek() != JsonToken.BEGIN_OBJECT) throw IllegalStateException("risposta non valida")
        var info: Map<String, String>? = null
        var seasons: LinkedHashMap<String, List<DetailActivity.Ep>>? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "info" -> info = if (r.peek() == JsonToken.BEGIN_OBJECT) readInfo(r) else { r.skipValue(); null }
                "episodes" -> seasons = if (r.peek() == JsonToken.BEGIN_OBJECT) readSeasons(r) else { r.skipValue(); null }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Result(info, seasons?.values?.flatten() ?: emptyList())
    }

    /** scheda della serie: trama, data, voto, genere e primo sfondo */
    private fun readInfo(r: JsonReader): Map<String, String> {
        val m = HashMap<String, String>()
        r.beginObject()
        while (r.hasNext()) {
            when (val k = r.nextName()) {
                "plot", "releaseDate", "rating", "genre" -> m[k] = str(r)
                "backdrop_path" -> m[k] = first(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        return m
    }

    /** stagioni nell'ordine della risposta; come JSONObject una chiave ripetuta resta al primo posto con l'ultimo valore */
    private fun readSeasons(r: JsonReader): LinkedHashMap<String, List<DetailActivity.Ep>> {
        val out = LinkedHashMap<String, List<DetailActivity.Ep>>()
        r.beginObject()
        while (r.hasNext()) {
            val sk = r.nextName()
            out[sk] = if (r.peek() == JsonToken.BEGIN_ARRAY) readEpisodes(r, sk) else { r.skipValue(); emptyList() }
        }
        r.endObject()
        return out
    }

    private fun readEpisodes(r: JsonReader, sk: String): List<DetailActivity.Ep> {
        val out = ArrayList<DetailActivity.Ep>()
        var i = 0 // posizione nella lista (anche degli elementi che non sono episodi): numero di riserva dell'episodio
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() == JsonToken.BEGIN_OBJECT) out.add(readEpisode(r, sk, i)) else r.skipValue()
            i++
        }
        r.endArray()
        return out
    }

    private fun readEpisode(r: JsonReader, sk: String, i: Int): DetailActivity.Ep {
        var id = ""
        var season: String? = null
        var num = ""
        var title = ""
        var ext = ""
        var img = ""
        var dur = ""
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = str(r)
                "season" -> season = str(r)
                "episode_num" -> num = str(r)
                "title" -> title = str(r)
                "container_extension" -> ext = str(r)
                // di «info» servono immagine e durata: trama, codec audio e video e il resto si saltano
                "info" -> if (r.peek() == JsonToken.BEGIN_OBJECT) {
                    var im = ""
                    var secs = ""
                    var d = ""
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "movie_image" -> im = str(r)
                            "duration_secs" -> secs = str(r)
                            "duration" -> d = str(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    img = im
                    dur = durOf(secs, d)
                } else {
                    r.skipValue()
                    img = ""
                    dur = ""
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return DetailActivity.Ep(id, (season ?: sk).toIntOrNull() ?: sk.toIntOrNull() ?: 1, num.toIntOrNull() ?: (i + 1), title, ext, "", img, dur)
    }

    /** come optString: testo e numeri come sono scritti, vero / falso, null diventa «null»; liste e oggetti "" */
    private fun str(r: JsonReader): String = when (r.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        JsonToken.NULL -> { r.nextNull(); "null" }
        else -> { r.skipValue(); "" }
    }

    /** come first(opt(…)): di una lista il primo elemento (come optString(0)), un testo così com'è, altro "" */
    private fun first(r: JsonReader): String = when (r.peek()) {
        JsonToken.STRING -> r.nextString()
        JsonToken.BEGIN_ARRAY -> {
            r.beginArray()
            val v = if (r.hasNext()) str(r) else ""
            while (r.hasNext()) r.skipValue()
            r.endArray()
            v
        }
        else -> { r.skipValue(); "" }
    }
}
