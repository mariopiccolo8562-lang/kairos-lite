package com.kairos.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Aggiornamenti: prima l'indirizzo dei download (oggi il pannello), GitHub solo come riserva se il pannello fallisce */
class UpdFeedTest {
    private val gh = "https://github.com/mariopiccolo8562-lang/kairos-lite/releases/latest/download/kairos-lite.json"
    private val panel = "https://panel-kairos.com/get/lite.json"

    @Test
    fun oggiPannelloPoiGitHub() {
        // l'indirizzo dei download oggi è il pannello: il secondo tentativo, uguale al primo, si salta
        assertEquals(listOf(panel, gh), UpdFeed.urls("https://panel-kairos.com", "lite", gh))
        assertEquals(listOf(panel, gh), UpdFeed.urls("https://panel-kairos.com/", "lite", gh))
    }

    @Test
    fun dominioDedicatoPrimaDelPannello() {
        assertEquals(listOf("https://dl.example.com/get/lite.json", panel, gh), UpdFeed.urls("https://dl.example.com/", "lite", gh))
    }

    @Test
    fun indirizziNonValidiSiSaltano() {
        assertEquals(listOf(panel), UpdFeed.urls("", "lite", ""))
        assertEquals(listOf(panel, gh), UpdFeed.urls("http://dl.example.com", "lite", gh))
    }

    @Test
    fun pannelloCheRispondeGitHubNonSiChiede() {
        val asked = ArrayList<String>()
        val r = UpdFeed.first(UpdFeed.urls("https://panel-kairos.com", "lite", gh)) { asked.add(it); "pannello" }
        assertEquals("pannello", r)
        assertEquals(listOf(panel), asked) // nessun controllo in più del solito
    }

    @Test
    fun pannelloConErroreRispostaNonValidaOMutoSiUsaGitHub() {
        // null = errore HTTP (es. 502) o file non valido; eccezione = il pannello non risponde (rete, tempo scaduto)
        val fails = listOf<(String) -> String?>({ null }, { throw IOException("tempo scaduto") })
        for (fail in fails) {
            val asked = ArrayList<String>()
            val r = UpdFeed.first(UpdFeed.urls("https://panel-kairos.com", "lite", gh)) { u -> asked.add(u); if (u == gh) "github" else fail(u) }
            assertEquals("github", r)
            assertEquals(listOf(panel, gh), asked)
        }
    }

    @Test
    fun dominioDedicatoGiuRispondeIlPannello() {
        val asked = ArrayList<String>()
        val r = UpdFeed.first(UpdFeed.urls("https://dl.example.com", "lite", gh)) { u -> asked.add(u); if (u == panel) "pannello" else null }
        assertEquals("pannello", r)
        assertEquals(listOf("https://dl.example.com/get/lite.json", panel), asked)
    }

    @Test
    fun tuttiGiuNessunaVersione() {
        // pannello e GitHub non rispondono (o repository privato): come una rete giù, si riprova più tardi
        assertNull(UpdFeed.first(UpdFeed.urls("https://panel-kairos.com", "lite", gh)) { null })
    }

    @Test
    fun numeroDellaVersione() {
        assertEquals(10502, UpdFeed.code("1.5.2", 0))
        assertEquals(10502, UpdFeed.code("1.5.2", 99))
        assertEquals(10502, UpdFeed.code("", 10502))
        assertEquals(7, UpdFeed.code("1.5", 7))
    }

    @Test
    fun fileValido() {
        assertTrue(UpdFeed.valid(10502, "https://panel-kairos.com/get/lite.apk"))
        assertFalse(UpdFeed.valid(0, "https://panel-kairos.com/get/lite.apk"))
        assertFalse(UpdFeed.valid(10502, "http://panel-kairos.com/get/lite.apk"))
        assertFalse(UpdFeed.valid(10502, ""))
    }
}
