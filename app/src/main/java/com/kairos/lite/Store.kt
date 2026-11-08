package com.kairos.lite

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** Impostazioni e dati locali (SharedPreferences, nessuna libreria in più) */
object Store {
    private lateinit var p: SharedPreferences
    private lateinit var pos: SharedPreferences

    fun init(c: Context) {
        p = c.getSharedPreferences("lite", Context.MODE_PRIVATE)
        pos = c.getSharedPreferences("lite_pos", Context.MODE_PRIVATE)
        if ((p.getString("code", "") ?: "").isEmpty() || (p.getString("secret", "") ?: "").isEmpty()) newIdentity()
    }

    /** codice di attivazione (stesso formato di KAIROS TV: KXAB-7Q2M) + segreto legato al dispositivo */
    private fun newIdentity() {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        fun ch() = a[r.nextInt(a.length)]
        val code = "KX" + ch() + ch() + "-" + ch() + ch() + ch() + ch()
        val b = ByteArray(32).also { r.nextBytes(it) }
        val secret = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        p.edit().putString("code", code).putString("secret", secret).apply()
    }

    val code: String get() = p.getString("code", "") ?: ""
    val secret: String get() = p.getString("secret", "") ?: ""

    private fun s(k: String, d: String = ""): String = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()

    /** indirizzo del pannello: quello della build, oppure quello nuovo indicato dal pannello stesso */
    var server: String
        get() = s("server").takeIf { it.startsWith("https://") } ?: BuildConfig.SERVER_URL
        set(v) = put("server", v)

    var stateJson: String
        get() = s("state")
        set(v) = put("state", v)
    var profileId: String
        get() = s("profile")
        set(v) = put("profile", v)
    /** auto | on | off */
    /** configurazione imposta dal pannello (controllo remoto) e sua versione */
    var rcJson: String
        get() = s("rc")
        set(v) = put("rc", v)
    var rcv: String
        get() = s("rcv")
        set(v) = put("rcv", v)
    /** auto | on | max | off (quella imposta dal pannello vince) */
    /** controllo genitori (attivo di serie), PIN scelto dal cliente (vuoto = 0000) e ultimo rinvio della scelta del PIN */
    var parental: Boolean
        get() = p.getBoolean("parental", true)
        set(v) = p.edit().putBoolean("parental", v).apply()
    var pin: String
        get() = s("pin", "")
        set(v) = put("pin", v)
    var pinLater: Long
        get() = p.getLong("pinLater", 0L)
        set(v) = p.edit().putLong("pinLater", v).apply()
    /** senza il permesso «lite» del cliente vale quella imposta dal pannello, altrimenti «auto» (la scelta salvata resta per dopo) */
    var liteMode: String
        get() = Remote.lite ?: (if (K.liteAllowed()) s("liteMode", "auto") else "auto")
        set(v) = put("liteMode", v)
    /** aggiornamento in background: la versione nuova si scarica da sola (quella imposta dal pannello vince) */
    var bgUpdate: Boolean
        get() = Remote.bgUpdate ?: p.getBoolean("bgUpdate", true)
        set(v) = p.edit().putBoolean("bgUpdate", v).apply()
    /** primo avviso al cliente della versione [updFirstCode] (da qui contano i giorni dell'aggiornamento obbligatorio) */
    var updFirstCode: Int
        get() = p.getInt("updFirstCode", 0)
        set(v) = p.edit().putInt("updFirstCode", v).apply()
    var updFirstAt: Long
        get() = p.getLong("updFirstAt", 0)
        set(v) = p.edit().putLong("updFirstAt", v).apply()
    /** ultima richiesta «Invia aggiornamento» del pannello già gestita */
    var updSeen: Long
        get() = p.getLong("updSeen", 0)
        set(v) = p.edit().putLong("updSeen", v).apply()

    /* versione nuova trovata dall'ultimo controllo (resta anche chiudendo l'app): codice (0 = nessuna), versione, indirizzo
       dell'APK; e l'APK già scaricato e verificato: percorso, misura e data del file (se il file cambia non vale più) */
    val updCode: Int get() = p.getInt("updCode", 0)
    val updVer: String get() = s("updVer")
    val updUrl: String get() = s("updUrl")
    val updFile: String get() = s("updFile")
    val updLen: Long get() = p.getLong("updLen", 0)
    val updMtime: Long get() = p.getLong("updMtime", 0)
    fun saveUpd(code: Int, ver: String, url: String) {
        p.edit().putInt("updCode", code).putString("updVer", ver).putString("updUrl", url).apply()
    }
    fun saveUpdFile(path: String, len: Long, mtime: Long) {
        p.edit().putString("updFile", path).putLong("updLen", len).putLong("updMtime", mtime).apply()
    }
    fun clearUpd() {
        p.edit().remove("updCode").remove("updVer").remove("updUrl").remove("updFile").remove("updLen").remove("updMtime").apply()
    }
    /** anteprima nella lista canali: click (primo OK = riquadro, secondo OK = schermo intero) | off */
    var preview: String
        get() = Remote.preview ?: s("previewMode", "click")
        set(v) = put("previewMode", v)
    var plChoice: String
        get() = s("plChoice")
        set(v) = put("plChoice", v)
    var syncSig: String
        get() = s("syncSig")
        set(v) = put("syncSig", v)
    var syncAt: Long
        get() = p.getLong("syncAt", 0)
        set(v) = p.edit().putLong("syncAt", v).apply()
    var kickSeen: Long
        get() = p.getLong("kickSeen", 0)
        set(v) = p.edit().putLong("kickSeen", v).apply()
    var vpnUser: Boolean
        get() = p.getBoolean("vpnUser", false)
        set(v) = p.edit().putBoolean("vpnUser", v).apply()
    /** ultima volta che l'app ha chiesto il permesso per installare gli aggiornamenti */
    var permAskT: Long
        get() = p.getLong("permAskT", 0L)
        set(v) = p.edit().putLong("permAskT", v).apply()
    var privacyOk: Boolean
        get() = p.getBoolean("privacyOk", false)
        set(v) = p.edit().putBoolean("privacyOk", v).apply()
    var lastLiveCat: String
        get() = s("liveCat")
        set(v) = put("liveCat", v)
    var lastLiveId: Long
        get() = p.getLong("liveId", 0)
        set(v) = p.edit().putLong("liveId", v).apply()

    /* ---------------- Kairos Adaptive (vedi Line) ---------------- */
    /** classe del dispositivo D / N / O, calcolata una volta */
    var devCls: String
        get() = s("devCls")
        set(v) = put("devCls", v)
    /** classe della linea (weak / normal / good), data del giudizio e banda misurata in bit/s */
    val lineCls: String get() = s("lineCls")
    val lineAt: Long get() = p.getLong("lineAt", 0)
    val bw: Long get() = p.getLong("bw", 0)
    fun saveLine(cls: String, at: Long, bw: Long) {
        p.edit().putString("lineCls", cls).putLong("lineAt", at).putLong("bw", bw).apply()
    }
    /** versione «a scatti» imparata sul dispositivo (QualityPolicy.Heavy, vedi Line.heavy) */
    var heavy: String
        get() = s("heavyQ")
        set(v) = put("heavyQ", v)

    var seenMsgs: Set<String>
        get() = p.getStringSet("seenMsgs", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("seenMsgs", HashSet(v)).apply()

    /** server di una playlist M3U al momento del caricamento: se il pannello cambia DNS si sostituisce al volo */
    fun plOrigin(id: String): String = s("plo_$id")
    fun setPlOrigin(id: String, o: String) = put("plo_$id", o)

    private fun prof(): String = profileId.ifEmpty { "_" }

    /* ---------------- preferiti (per profilo) ---------------- */
    fun favs(kind: String): Set<String> = p.getStringSet("fav_${kind}_${prof()}", emptySet()) ?: emptySet()
    fun isFav(kind: String, k: String): Boolean = favs(kind).contains(k)
    fun toggleFav(kind: String, k: String): Boolean {
        val set = HashSet(favs(kind))
        val on = if (set.contains(k)) { set.remove(k); false } else { set.add(k); true }
        p.edit().putStringSet("fav_${kind}_${prof()}", set).apply()
        return on
    }

    /* ---------------- «Continua a guardare» (per profilo) ---------------- */
    fun recent(): List<JSONObject> {
        val t = s("recent_${prof()}")
        if (t.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(t)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun saveRecent(o: JSONObject) {
        val id = o.optString("id")
        val group = o.optString("group")
        val list = recent().filter { it.optString("id") != id && (group.isEmpty() || it.optString("group") != group) }.toMutableList()
        list.add(0, o)
        val a = JSONArray()
        list.take(20).forEach { a.put(it) }
        put("recent_${prof()}", a.toString())
    }

    fun removeRecent(id: String) {
        val a = JSONArray()
        recent().filter { it.optString("id") != id }.forEach { a.put(it) }
        put("recent_${prof()}", a.toString())
    }

    /* ---------------- posizione di visione di film ed episodi ---------------- */
    fun position(id: String): Long = pos.getLong(id, 0)
    fun duration(id: String): Long = pos.getLong("$id#d", 0)
    fun setPosition(id: String, v: Long, dur: Long = 0) {
        if (v <= 0) pos.edit().remove(id).remove("$id#d").apply()
        else pos.edit().putLong(id, v).putLong("$id#d", dur).apply()
    }
}
