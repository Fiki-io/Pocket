package com.pocketsamp

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pocketsamp.data.ProfileRepository
import com.pocketsamp.server.RconClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProfileAndRconTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun multiProfileCrudAndFolderInspection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProfileRepository(context)

        val gmDir = tempFolder.newFolder("roleplay")
        File(gmDir, "samp03svr").writeText("elf")
        File(gmDir, "server.cfg").writeText("port 7777\n")
        File(gmDir, "gamemodes").apply { mkdirs() }.let {
            File(it, "larp.amx").writeText("amx")
        }
        File(gmDir, "plugins").apply { mkdirs() }.let {
            File(it, "mysql.so").writeText("so")
            File(it, "streamer.so").writeText("so")
        }

        val p1 = repo.saveProfile(
            id = null,
            name = "Roleplay 1",
            folderPath = gmDir.absolutePath,
            executableName = "samp03svr",
            lowPriorityCpu = true,
            autoStartMariaDb = true
        )
        val p2 = repo.saveProfile(
            id = null,
            name = "Freeroam 2",
            folderPath = gmDir.absolutePath,
            executableName = "omp-server",
            lowPriorityCpu = false,
            autoStartMariaDb = false
        )

        var state = repo.preferencesFlow.first()
        assertEquals(2, state.profiles.size)
        assertEquals(p2.id, state.activeProfile?.id)

        repo.setActiveProfile(p1.id)
        repo.updateProfileTuning(p1.id, lowPriorityCpu = false, autoStartMariaDb = false)
        state = repo.preferencesFlow.first()
        assertEquals(p1.id, state.activeProfile?.id)
        assertFalse(state.activeProfile!!.lowPriorityCpu)
        assertFalse(state.activeProfile!!.autoStartMariaDb)

        val inspection = repo.inspectFolder(gmDir.absolutePath, "samp03svr")
        assertTrue(inspection.folderExists)
        assertTrue(inspection.executableExists)
        assertTrue(inspection.serverCfgExists)
        assertEquals(listOf("larp"), inspection.gamemodeFiles)
        assertEquals(listOf("mysql.so", "streamer.so"), inspection.pluginFiles)

        repo.deleteProfile(p1.id)
        state = repo.preferencesFlow.first()
        assertEquals(1, state.profiles.size)
        assertEquals(p2.id, state.activeProfile?.id)
    }

    @Test
    fun rconClientRejectsEmptyPasswordOrCommand() = runTest {
        assertTrue(RconClient.sendCommand(7777, "", "gmx").isFailure)
        assertTrue(RconClient.sendCommand(7777, "pass", "   ").isFailure)
    }
}
