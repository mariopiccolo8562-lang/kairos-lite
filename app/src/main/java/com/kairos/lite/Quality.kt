package com.kairos.lite

import java.util.Locale

/**
 * Versioni dello stesso canale nella playlist (4K / FHD / HD / SD), come in KAIROS TV:
 * «Sport 1 FHD», «IT: Sport 1 HD» e «Sport 1 SD» hanno la stessa chiave «sport1».
 * Serve alla qualità adattiva: se la linea non regge si passa alla versione più leggera.
 */
object Quality {
    private val PREFIX = Regex("^\\s*[a-z]{2,3}\\s*[:|]\\s*")
    private val TOKENS = Regex("\\b(4k|uhd|2160p?|fhd|full\\s?hd|1080[pi]?|hd|720p?|sd|576[pi]?|480p?|hevc|h\\.?265|x265|h\\.?264|x264|50fps|60fps|backup|bk|alt|raw|vip)\\b")
    private val HEVC = Regex("(?i)\\b(hevc|h\\.?265|x265)\\b")
    private val NON = Regex("[^a-z0-9]+")

    /** chiave comune alle versioni dello stesso canale ("" = nessuna) */
    fun key(name: String): String {
        var n = name.lowercase(Locale.ROOT)
        n = PREFIX.replace(n, "")
        n = TOKENS.replace(n, " ")
        n = NON.replace(n, "")
        return if (n.length >= 2) n.take(60) else ""
    }

    /** 4 = 4K, 3 = FHD, 2 = HD (anche quando non è indicato), 1 = SD */
    fun rank(name: String): Int {
        val n = name.lowercase(Locale.ROOT)
        return when {
            Regex("\\b(4k|uhd|2160p?)\\b").containsMatchIn(n) -> 4
            Regex("\\b(fhd|full\\s?hd|1080[pi]?)\\b").containsMatchIn(n) -> 3
            Regex("\\b(sd|576[pi]?|480p?)\\b").containsMatchIn(n) -> 1
            else -> 2
        }
    }

    fun hevc(name: String): Boolean = HEVC.containsMatchIn(name)

    fun label(rank: Int): String = when (rank) { 4 -> "4K"; 3 -> "FHD"; 1 -> "SD"; else -> "HD" }
}
