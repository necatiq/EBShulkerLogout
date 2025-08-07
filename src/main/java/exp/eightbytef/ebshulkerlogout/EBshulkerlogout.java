package exp.eightbytef.ebshulkerlogout;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;

public final class EBshulkerlogout extends JavaPlugin implements @NotNull Listener {

    private static EBshulkerlogout instance;
    private final List<Material> SHULKER_BOXES = Arrays.asList(
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX
    );

    private Map<UUID, Integer> playersWithDroppedShulkers = new HashMap<>();
    private Random random = new Random();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!getConfig().getBoolean("action-bar-enabled", true)) {
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (hasShulkerBox(player)) {
                        sendActionBar(player);
                    }
                }
            }
        }.runTaskTimer(this, 20, 20);

        getLogger().info("EBshulkerlogout включен");
    }

    @Override
    public @NotNull Logger getSLF4JLogger() {
        return super.getSLF4JLogger();
    }

    @Override
    public void onDisable() {
        getLogger().info("EBshulkerlogout выключен");
    }

    public static EBshulkerlogout getInstance() {
        return instance;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!getConfig().getBoolean("drop-shulkers-on-logout", true)) {
            return;
        }

        Player player = event.getPlayer();
        int droppedCount = dropAllShulkerBoxes(player);

        if (droppedCount > 0) {
            playersWithDroppedShulkers.put(player.getUniqueId(), droppedCount);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("notification-on-rejoin-enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (playersWithDroppedShulkers.containsKey(uuid)) {
            int count = playersWithDroppedShulkers.get(uuid);
            String message = Format.color(getConfig().getString("messages.shulkers-dropped-notification"));
            message = format(message.replace("%count%", String.valueOf(count)), player);
            player.sendMessage(message);

            playersWithDroppedShulkers.remove(uuid);
        }
    }

    private boolean hasShulkerBox(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();

        for (ItemStack item : contents) {
            if (item != null && SHULKER_BOXES.contains(item.getType())) {
                return true;
            }
        }
        return false;
    }

    private int dropAllShulkerBoxes(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        Location playerLocation = player.getLocation();

        double dropDistance = getConfig().getDouble("drop-distance");
        double dropHeight = getConfig().getDouble("drop-height");

        List<Integer> slotsToUpdate = new ArrayList<>();
        int droppedCount = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && SHULKER_BOXES.contains(item.getType())) {
                Location dropLocation = calculateDropLocation(playerLocation, dropDistance, dropHeight);

                player.getWorld().dropItem(dropLocation, item.clone());
                slotsToUpdate.add(i);
                droppedCount += item.getAmount();
            }
        }

        for (int slot : slotsToUpdate) {
            inventory.setItem(slot, null);
        }

        return droppedCount;
    }

    private Location calculateDropLocation(Location playerLocation, double distance, double height) {
        Location dropLocation = playerLocation.clone();

        // генерируем градусы от 0 до 360
        double angle = random.nextDouble() * 2 * Math.PI;

        // Считаем углы
        double xOffset = distance * Math.cos(angle);
        double zOffset = distance * Math.sin(angle);

        dropLocation.add(xOffset, height, zOffset);

        return dropLocation;
    }

    private void sendActionBar(Player player) {
        String message = format(Format.color(getConfig().getString("messages.actionbar-warning")), player);
        try {
            player.sendActionBar(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', message));
        } catch (NoSuchMethodError e) {
            try {
                Class<?> craftPlayerClass = Bukkit.getServer().getClass().getDeclaredMethod("getHandle").getReturnType();
                Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
                Object packet = Class.forName("net.minecraft.server." + getServerVersion() + ".PacketPlayOutChat")
                        .getConstructor(Class.forName("net.minecraft.server." + getServerVersion() + ".IChatBaseComponent"), byte.class)
                        .newInstance(
                                Class.forName("net.minecraft.server." + getServerVersion() + ".ChatComponentText")
                                        .getConstructor(String.class)
                                        .newInstance(ChatColor.translateAlternateColorCodes('&', message)),
                                (byte) 2
                        );
                Object connection = craftPlayerClass.getField("playerConnection").get(craftPlayer);
                connection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + getServerVersion() + ".Packet"))
                        .invoke(connection, packet);
            } catch (Exception ex) {
                getLogger().warning("Failed to send ActionBar: " + ex.getMessage());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }

    private String getServerVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }

    public String format(String message, Player player) {
        return ChatColor.translateAlternateColorCodes('&', message)
                .replace("%player%", player.getName());
    }
}
