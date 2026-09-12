package org.bmp.cph

import net.neoforged.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.bmp.cph.client.ClientBootstrap
import org.bmp.cph.config.ConfigManager
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

@Mod(Cph.ID)
object Cph {
    const val ID = "cph"
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        ConfigManager.load()

        runForDist(
            clientTarget = {
                ClientBootstrap.register()
            },
            serverTarget = {
                LOGGER.info("CoolPackHelper loaded on a dedicated server; the client menu is disabled.")
            },
        )
    }
}
