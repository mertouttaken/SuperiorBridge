package com.bgsoftware.ssbbridge.plugin.listeners;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.packets.IslandSavePacket;
import com.bgsoftware.ssbbridge.manager.db.DatabaseHandler;
import com.bgsoftware.ssbbridge.manager.network.ManagerPacketHandler;
import com.bgsoftware.ssbbridge.plugin.SSBProxyBridgePlugin;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

public class IslandUnloadListener implements Listener {

    private final SSBProxyBridgePlugin plugin;
    private final MessageBroker broker;
    private final ManagerPacketHandler mph;
    public IslandUnloadListener(SSBProxyBridgePlugin plugin, MessageBroker broker, ManagerPacketHandler mph) {
        this.plugin = plugin;
        this.broker = broker;
        this.mph = mph;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayer(event.getPlayer().getUniqueId());
        Island island = sPlayer.getIsland();

        if (island == null) return;

        // Adada başka kimse var mı? (Takım arkadaşı veya ziyaretçi)
        // Eğer içerideki oyuncu sayısı 1 ise (o da çıkan oyuncu), adayı unload et.
        if (island.getAllPlayersInside().size() <= 1) {
            saveAndUnload(island);
        }
    }

    private void saveAndUnload(Island island) {
        UUID islandId = island.getUniqueId();
        File tempFile = new File(plugin.getDataFolder(), "temp_archived_" + islandId + ".schem");
        mph.handleIslandUnload(island, tempFile);
    }
}