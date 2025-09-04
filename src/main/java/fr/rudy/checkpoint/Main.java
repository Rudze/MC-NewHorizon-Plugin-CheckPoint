package fr.rudy.checkpoint;

import fr.rudy.databaseapi.DatabaseAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Main extends JavaPlugin implements TabExecutor, Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitRunnable> pendingTeleports = new HashMap<>();
    private final long COOLDOWN = 30 * 60 * 1000; // 30 minutes en ms
    private final long TELEPORT_DELAY = 5 * 20; // 5 secondes (ticks)

    @Override
    public void onEnable() {
        getCommand("checkpoint").setExecutor(this);
        createTable();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("✅ Plugin Checkpoint actif !");
    }

    private void createTable() {
        Connection conn = DatabaseAPI.get().getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS checkpoints (
                    uuid TEXT PRIMARY KEY,
                    world TEXT,
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    yaw FLOAT,
                    pitch FLOAT
                );
                """)) {
            stmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length < 2) return false;

        String sub = args[0];
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            return true;
        }

        if (sub.equalsIgnoreCase("set")) {
            saveCheckpoint(target);
            return true;
        }

        if (sub.equalsIgnoreCase("tp")) {
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();

            if (cooldowns.containsKey(uuid) && now - cooldowns.get(uuid) < COOLDOWN) {
                long remaining = (COOLDOWN - (now - cooldowns.get(uuid))) / 1000;
                player.sendMessage("<glyph:error> §cVous devez attendre encore " + remaining + " secondes.");
                return true;
            }

            Location loc = getCheckpoint(target);
            if (loc == null) {
                return true;
            }

            player.sendMessage("§eTéléportation dans 5 secondes. Ne bougez pas et ne prenez pas de dégâts.");

            BukkitRunnable countdown = new BukkitRunnable() {
                int secondsLeft = 5;

                @Override
                public void run() {
                    if (secondsLeft <= 0) {
                        player.teleport(loc);
                        cooldowns.put(uuid, now);
                        pendingTeleports.remove(uuid);
                        player.sendMessage("<glyph:info> §bTéléporté au point de retour de " + target.getName());
                        cancel();
                        return;
                    }
                    player.sendActionBar(Component.text("Téléportation dans " + secondsLeft + "s...").color(NamedTextColor.YELLOW));
                    secondsLeft--;
                }
            };

            countdown.runTaskTimer(this, 0L, 20L);
            pendingTeleports.put(uuid, countdown);

            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.getFrom().toVector().equals(event.getTo().toVector())) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (pendingTeleports.containsKey(uuid)) {
                pendingTeleports.get(uuid).cancel();
                pendingTeleports.remove(uuid);
                event.getPlayer().sendActionBar(Component.text("Téléportation annulée: vous avez bougé.").color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (pendingTeleports.containsKey(uuid)) {
                pendingTeleports.get(uuid).cancel();
                pendingTeleports.remove(uuid);
                player.sendActionBar(Component.text("Téléportation annulée: vous avez subi des dégâts.").color(NamedTextColor.RED));
            }
        }
    }

    private void saveCheckpoint(Player player) {
        Location loc = player.getLocation();
        Connection conn = DatabaseAPI.get().getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO checkpoints (uuid, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                world=excluded.world,
                x=excluded.x,
                y=excluded.y,
                z=excluded.z,
                yaw=excluded.yaw,
                pitch=excluded.pitch;
                """)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, loc.getWorld().getName());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setFloat(6, loc.getYaw());
            stmt.setFloat(7, loc.getPitch());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Location getCheckpoint(Player player) {
        Connection conn = DatabaseAPI.get().getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM checkpoints WHERE uuid = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");

                return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("set", "tp");
        if (args.length == 2) return null; // laisser Bukkit proposer les pseudos
        return Collections.emptyList();
    }
}
