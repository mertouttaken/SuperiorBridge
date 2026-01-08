package com.bgsoftware.ssbbridge.plugin.listeners;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.Packet;
import com.bgsoftware.ssbbridge.common.messaging.packets.*;
import com.bgsoftware.ssbbridge.plugin.SSBProxyBridgePlugin;
import com.bgsoftware.ssbbridge.plugin.config.BridgeConfig;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.events.PreIslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.api.schematic.SchematicOptions;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
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
        broker.subscribe("ssb-server-" + config.serverId, this::onPrivatePacketReceived);
    }

    private void onPrivatePacketReceived(Packet packet) {
        if (packet instanceof PrepareIslandCreationPacket) {
            handleIncomingCreationOrder((PrepareIslandCreationPacket) packet);
        } else if (packet instanceof IslandInvitePacket) {
            handleIncomingInvite((IslandInvitePacket) packet);
        }
    }

    private void handleIncomingInvite(IslandInvitePacket invite) {
        Player target = Bukkit.getPlayer(invite.getTargetPlayerName());
        if (target != null && target.isOnline()) {
            target.sendMessage("");
            target.sendMessage(ChatColor.GREEN + "★ " + ChatColor.AQUA + invite.getInviterName() +
                    ChatColor.WHITE + " seni adasına davet etti!");
            target.sendMessage(ChatColor.YELLOW + "Katılmak için: " + ChatColor.WHITE + "/is accept " + invite.getInviterName());
            target.sendMessage("");
        }
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
                    String targetServer = ((ResponsePacket) responseRaw).getPayloadJson();

                    // Karar mekanizması buraya taşındı
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetServer.equals(config.serverId)) {
                            permittedCreates.add(player.getUniqueId());
                            player.chat("/is create" + (schematic.isEmpty() ? "" : " " + schematic));
                        } else {
                            player.sendMessage(ChatColor.GREEN + "Ada oluşturmak için yönlendiriliyorsunuz...");
                            PrepareIslandCreationPacket packet = new PrepareIslandCreationPacket(config.serverId, player.getUniqueId().toString(), schematic, null);
                            broker.sendPacket(packet, "ssb-server-" + targetServer);
                            plugin.getProxyService().sendPlayerToServer(player, targetServer);
                        }
                    });
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Sunucu ağına bağlanılamadı.");
                e.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onLobbyCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();

        if (msg.startsWith("/is go")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.YELLOW + "Adanız hazırlanıyor, sunucu tahsis ediliyor...");

            DataRequestPacket request = new DataRequestPacket(
                    "lobby",
                    "LOAD_ISLAND_AND_TELEPORT",
                    player.getUniqueId().toString()
            );

            broker.sendRequest(request, "ssb-manager", 8000).thenAccept(responseRaw -> {
                if (responseRaw instanceof ResponsePacket) {
                    String targetServer = ((ResponsePacket) responseRaw).getPayloadJson();

                    // Karar mekanizması buraya taşındı
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetServer.equals(config.serverId)) {
                            // Eğer zaten bu sunucudaysa direkt komutu işlet
                            player.chat("/is go");
                        } else {
                            player.sendMessage(ChatColor.GREEN + "Adanıza yönlendiriliyorsunuz...");
                            plugin.getProxyService().sendPlayerToServer(player, targetServer);
                        }
                    });
                }
            });
        }
    }

    private void handleIncomingCreationOrder(PrepareIslandCreationPacket order) {
        if (order.getSchematicData() != null) {
            File tempFile = new File(plugin.getDataFolder(), "imports/" + order.getPlayerUuid() + ".schem");
            try {
                java.nio.file.Files.write(tempFile.toPath(), order.getSchematicData());
                pendingIncomingCreations.put(UUID.fromString(order.getPlayerUuid()), tempFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- Şematik İşlemleri ---

    public void saveAreaAsSchematic(Location loc1, Location loc2, String schemName) {
        SchematicOptions options = SchematicOptions.newBuilder(schemName).build();
        SuperiorSkyblockAPI.getSchematics().saveSchematic(loc1, loc2, options, () -> {
            System.out.println("Şematik başarıyla kaydedildi: " + schemName);
        });
    }

    public void pasteMySchematic(String schemName, Location targetLoc) {
        Schematic schematic = SuperiorSkyblockAPI.getSchematics().getSchematic(schemName);
        Island island = SuperiorSkyblockAPI.getIslandAt(targetLoc);

        if (schematic != null && island != null) {
            schematic.pasteSchematic(island, targetLoc, () -> {
                System.out.println(schemName + " adaya başarıyla yapıştırıldı.");
            });
        } else {
            if (schematic == null) System.out.println("Hata: " + schemName + " şematiği bulunamadı!");
            if (island == null) System.out.println("Hata: Lokasyonda ada bulunamadı!");
        }
    }

    public void printAllSchematics() {
        List<String> allSchems = SuperiorSkyblockAPI.getSchematics().getSchematics();
        for (String name : allSchems) {
            System.out.println("Yüklü Şematik: " + name);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Island island = SuperiorSkyblockAPI.getPlayer(player.getUniqueId()).getIsland();

        if (island == null) return;

        String schemName = "baslangic_sematigi";
        Schematic schematic = SuperiorSkyblockAPI.getSchematics().getSchematic(schemName);

        if (schematic != null) {
            Location center = island.getCenter(Dimension.getByName("normal")).getBlock().getLocation();
            schematic.pasteSchematic(island, center, () -> {
                player.sendMessage("§aAdan başarıyla yüklendi!");
            });
        }
    }
}