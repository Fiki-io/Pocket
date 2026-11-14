package com.pocketsamp

import com.pocketsamp.config.ServerCfgRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ServerCfgRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseAndSaveStructuredPreservesCommentsAndCustomKeys() = runTest {
        val dir = tempFolder.newFolder("samp_gm")
        val cfgFile = File(dir, "server.cfg")
        cfgFile.writeText(
            """
            # Konfigurasi server utama
            echo Executing Server Config...
            lanmode 0
            rcon_password rahasia
            maxplayers 50
            port 7777
            hostname Server Lama
            gamemode0 bare 1
            plugins mysql.so streamer.so
            filterscripts gl_realtime
            announce 0
            """.trimIndent() + "\n"
        )

        val repo = ServerCfgRepository()
        val loaded = repo.load(dir.absolutePath).getOrThrow()
        assertEquals("Server Lama", loaded.hostname)
        assertEquals("7777", loaded.port)
        assertEquals("50", loaded.maxPlayers)
        assertEquals("rahasia", loaded.rconPassword)
        assertFalse(loaded.lanMode)

        val updated = loaded.copy(
            hostname = "PocketSamp Indo",
            port = "7788",
            maxPlayers = "100",
            lanMode = true,
            announce = true
        )
        val saved = repo.saveStructured(dir.absolutePath, updated).getOrThrow()
        assertEquals("PocketSamp Indo", saved.hostname)
        assertEquals("7788", saved.port)
        assertEquals("100", saved.maxPlayers)
        assertTrue(saved.lanMode)
        assertTrue(saved.announce)

        val rawText = cfgFile.readText()
        assertTrue(rawText.contains("# Konfigurasi server utama"))
        assertTrue(rawText.contains("filterscripts gl_realtime"))
        assertTrue(rawText.contains("lanmode 1"))
    }
}
