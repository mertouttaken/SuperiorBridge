package com.bgsoftware.ssbbridge.plugin.listeners;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.Packet;
import com.bgsoftware.ssbbridge.common.messaging.packets.IslandCreationRequestPacket;
import com.bgsoftware.ssbbridge.common.messaging.packets.PrepareIslandCreationPacket;
import com.bgsoftware.ssbbridge.common.messaging.packets.ResponsePacket;
import com.bgsoftware.ssbbridge.plugin.SSBProxyBridgePlugin;
import com.bgsoftware.ssbbridge.plugin.config.BridgeConfig;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.events.PreIslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IslandCreationListener implements Listener {

    private final SSBProxyBridgePlugin plugin;
    private final MessageBroker broker;
    private final BridgeConfig config;

    private final Set<UUID> permittedCreates = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<UUID, String> pendingIncomingCreations = new ConcurrentHashMap<>();

    public IslandCreationListener(SSBProxyBridgePlugin plugin, MessageBroker broker, BridgeConfig config) {
        this.plugin = plugin;
        this.broker = broker;
        this.config = config;
        broker.subscribe("ssb-server-" + config.serverId, this::handleIncomingCreationOrder);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIslandCreateAttempt(PreIslandCreateEvent event) {
        SuperiorPlayer sPlayer = event.getPlayer();
        Player player = sPlayer.asPlayer();
        if (player == null) return;

        if (permittedCreates.remove(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.YELLOW + "En uygun sunucu aranıyor, lütfen bekleyin...");

        // DÜZELTME: PreIslandCreateEvent'te schematic metodu yok.
        // Genellikle oyuncunun bekleyen bir isteği (request) olur.
        // Schematic bilgisi cross-server taşınacağı için boş bırakabilir veya
        // varsayılan schematic'i alabilirsin.
        String schematic = "";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                IslandCreationRequestPacket request = new IslandCreationRequestPacket(
                        config.serverId,
                        player.getUniqueId().toString(),
                        player.getName(),
                        schematic
                );

                Packet responseRaw = broker.sendRequest(request, config.managerChannel, 3000).join();

                if (responseRaw instanceof ResponsePacket) {
                    ResponsePacket response = (ResponsePacket) responseRaw;
                    String targetServer = response.getPayloadJson();

                    Bukkit.getScheduler().runTask(plugin, () -> processManagerDecision(player, targetServer, schematic));
                }

            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Sunucu ağına bağlanılamadı.");
                e.printStackTrace();
            }
        });
    }

    private void processManagerDecision(Player player, String targetServer, String schematic) {
        if (targetServer.equals(config.serverId)) {
            permittedCreates.add(player.getUniqueId());
            // DÜZELTME: API yerine komut tetiklemek daha güvenli ve eventleri doğru fırlatır.
            player.chat("/is create" + (schematic.isEmpty() ? "" : " " + schematic));
        } else {
            player.sendMessage(ChatColor.GREEN + "Ada oluşturmak için yönlendiriliyorsunuz...");
            PrepareIslandCreationPacket packet = new PrepareIslandCreationPacket(config.serverId, player.getUniqueId().toString(), schematic);
            broker.sendPacket(packet, "ssb-server-" + targetServer);
            plugin.getProxyService().sendPlayerToServer(player, targetServer);
        }
    }

    private void handleIncomingCreationOrder(Packet packet) {
        if (packet instanceof PrepareIslandCreationPacket) {
            PrepareIslandCreationPacket order = (PrepareIslandCreationPacket) packet;
            pendingIncomingCreations.put(UUID.fromString(order.getPlayerUuid()), order.getSchematicName());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (pendingIncomingCreations.containsKey(uuid)) {
            String schematic = pendingIncomingCreations.remove(uuid);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                permittedCreates.add(uuid);
                SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayer(uuid);

                // DÜZELTME: createIsland metodu GridManager'da şu parametreleri ister:
                // (SuperiorPlayer, schematicName, BigDecimal bonus, Biome, islandName)
                // En basit haliyle:
                SuperiorSkyblockAPI.getGrid().createIsland(
                        sPlayer,
                        (schematic == null || schematic.isEmpty()) ? "normal" : schematic,
                        BigDecimal.ZERO,
                        null,
                        ""
                );

                event.getPlayer().sendMessage(ChatColor.GREEN + "Adanız oluşturuldu!");
            }, 10L);
        }
    }
}