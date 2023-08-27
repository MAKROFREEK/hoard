package smaamc.hoard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Random;

public class Hoard extends JavaPlugin implements Listener {
    private boolean isHoardRunning;
    private BukkitTask taskId;
    private Random random;
    private FileConfiguration config;



    @Override
    public void onEnable() {
        getLogger().info("Hoard has been enabled.");
        getCommand("hoard").setExecutor(new HoardCommand(this));
        config = getConfig();
        random = new Random();
        reloadConfig();
    }

    @Override
    public void onDisable() {
        getLogger().info("Hoard has been disabled.");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public class HoardCommand implements CommandExecutor {
        private final Hoard plugin;

        public HoardCommand(Hoard plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("hoard")) {
                if (args.length > 0) {
                    String subCommand = args[0].toLowerCase();
                    switch (subCommand) {
                        case "start":
                            return startHoard(sender);
                        case "stop":
                            return stopHoard(sender);
                        case "reload":
                            return reloadHoardConfig(sender);
                        case "random":
                            return loadRandomConfig(sender);
                        default:
                            getLogger().info("Invalid subcommand. Use /hoard <start|stop|reload|random>");
                            return true;
                    }
                } else {
                    getLogger().info("Missing subcommand. Use /hoard <start|stop|reload|random>");
                    return true;
                }
            }
            return false;
        }

        private boolean loadRandomConfig(CommandSender sender) {
            File dataFolder = plugin.getDataFolder();
            File[] configFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));

            if (configFiles == null || configFiles.length == 0) {
                getLogger().info("No configuration files found");
                return true;
            }

            File randomConfigFile = configFiles[plugin.random.nextInt(configFiles.length)];
            FileConfiguration randomConfig = YamlConfiguration.loadConfiguration(randomConfigFile);
            plugin.config = randomConfig;

            getLogger().info("Random config '" + randomConfigFile.getName() + "' loaded.");
            return true;
        }

        //  Starts the hoard spawning process.
        //  @param  sender  the command sender
        //  @return         true if the hoard spawning process started successfully, false otherwise

        private boolean startHoard(CommandSender sender) {
            if (isHoardRunning) {
                getLogger().info("Hoard is already running.");
                return true;
            }

            isHoardRunning = true;
            getLogger().info("Hoard spawning started.");

            long spawnInterval = plugin.getConfig().getInt("spawn_interval");
            taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Player[] onlinePlayers = Bukkit.getOnlinePlayers().toArray(new Player[0]);

                if (onlinePlayers.length == 0) {
                    Bukkit.getLogger().warning("No players are online to spawn the hoard.");
                    return;
                }

                for (Player player : onlinePlayers) {
                    World world = player.getWorld();
                    String worldName = world.getName();

                    if (plugin.getConfig().contains("worlds." + worldName)) {
                        List<String> mobNames = plugin.getConfig().getStringList("worlds." + worldName + ".mobs");
                        int minDistance = plugin.getConfig().getInt("min_distance");
                        int maxDistance = plugin.getConfig().getInt("max_distance");
                        int spawnLimit = plugin.getConfig().getInt("spawn_limit");

                        for (int i = 0; i < spawnLimit; i++) {
                            if (!isHoardRunning) {
                                return;
                            }

                            EntityType entityType = EntityType.valueOf(mobNames.get(random.nextInt(mobNames.size())));
                            Location spawnLocation;

                            if (world.getEnvironment() == World.Environment.NETHER) {
                                // For the Nether, adjust min and max distances
                                int netherMinDistance = 5;
                                int netherMaxDistance = 10;
                                spawnLocation = getRandomSafeLocation(player.getLocation(), netherMinDistance, netherMaxDistance);
                            } else {
                                spawnLocation = getRandomSafeLocation(player.getLocation(), minDistance, maxDistance);
                            }

                            if (spawnLocation != null) {
                                // Spawn the mob at the location
                                world.spawnEntity(spawnLocation, entityType);

                                // Teleport the mob to the nearest block on the ground if it's in the air
                                Entity entity = spawnLocation.getWorld().getNearbyEntities(spawnLocation, 0.1, 0.1, 0.1)
                                        .stream()
                                        .filter(e -> e.getType() == entityType && e.getLocation().distanceSquared(spawnLocation) < 0.1)
                                        .findFirst()
                                        .orElse(null);

                                if (entity != null && entity.isValid()) {
                                    entity.teleport(teleportToGround(entity.getLocation()));
                                }
                            }
                        }
                    } else {
                        Bukkit.getLogger().warning("The world '" + worldName + "' is not configured for hoard spawns.");
                    }
                }
            }, 0L, spawnInterval * 20);

            return true;
        }

        private boolean reloadHoardConfig(CommandSender sender) {
            plugin.reloadConfig();
            getLogger().info("Hoard configuration reloaded.");
            sender.sendMessage("Hoard configuration reloaded.");
            return true;
        }

        private boolean stopHoard(CommandSender sender) {
            if (!isHoardRunning) {
                getLogger().info("Hoard is not running.");
                return true;
            }
            isHoardRunning = false;
            if (taskId != null) {
                taskId.cancel();
                taskId = null;
            }
            getLogger().info("Hoard spawning stopped.");
            return true;
        }

        /**
         * Generates a random safe location within a certain distance from a given center location.
         *
         * @param  center        the center location from which to generate the random safe location
         * @param  minDistance   the minimum distance from the center location to the generated location
         * @param  maxDistance   the maximum distance from the center location to the generated location
         * @return               a random safe location within the specified distance from the center location,
         *                       or null if no safe location was found after 10 attempts
         */
        private Location getRandomSafeLocation(Location center, int minDistance, int maxDistance) {
            World world = center.getWorld();
            int x = center.getBlockX();
            int z = center.getBlockZ();
            // int y = center.getBlockY();

            for (int i = 0; i < 10; i++) { // Try 10 times to find a safe location
                x += (int) ((random.nextDouble() * (maxDistance - minDistance) + minDistance) * (random.nextBoolean() ? -1 : 1));
                z += (int) ((random.nextDouble() * (maxDistance - minDistance) + minDistance) * (random.nextBoolean() ? -1 : 1));

                // Limit the x and z coordinates to prevent going outside the world bounds
                x = Math.max(-30000000, Math.min(30000000, x));
                z = Math.max(-30000000, Math.min(30000000, z));

                // Ensure the Y-coordinate is within the specified range for Nether
                int minY = 50; // Minimum Y-coordinate for Nether spawns
                int maxY = 100; // Maximum Y-coordinate for Nether spawns
                int newY = random.nextInt(maxY - minY) + minY;

                Location location = new Location(world, x + 0.5, newY + 0.5, z + 0.5);
                Location bottomBlockLocation = location.clone().subtract(0, 1, 0);

                if (bottomBlockLocation.getBlock().isPassable()) {
                    // Check if the block below the location is passable
                    return location;
                }
            }

            return null;
        }

        private Location teleportToGround(Location location) {
            World world = location.getWorld();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            // Find the nearest solid block vertically from the given location
            while (y >= 0) {
                assert world != null;
                if (!!world.getBlockAt(x, y, z).getType().isSolid()) break;
                y--;
            }

            // Ensure the Y-coordinate is within valid bounds
            if (y < 0) {
                y = 0; // Set Y to 0 if no solid block was found above
            } else if (y > 255) {
                y = 255; // Set Y to 255 if no solid block was found below
            }

            // Teleport the entity to the nearest block on the ground
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }

    }
}
