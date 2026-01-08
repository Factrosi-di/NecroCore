package factorsi.di.NecroCore;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.ExperienceOrb;

public class NecroCore extends JavaPlugin implements Listener, TabCompleter {

    private final HashMap<UUID, RevivalRequest> pendingRequests = new HashMap<>();
    private final Set<UUID> ghosts = Collections.synchronizedSet(new HashSet<>());
    private final HashMap<UUID, String> deathModes = new HashMap<>();
    private final HashMap<String, Integer> successfulRevives = new HashMap<>();
    private final Set<UUID> concussedPlayers = Collections.synchronizedSet(new HashSet<>());
    private final HashMap<UUID, Long> concussionStartTime = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("NecroCore v1.2.0 by Factorsi_di|Nicto_55");
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand necro = getCommand("necro");
        if (necro != null) {
            necro.setTabCompleter(this);
        }

        PluginCommand necromode = getCommand("necromode");
        if (necromode != null) {
            necromode.setTabCompleter(this);
        }

        PluginCommand necroforce = getCommand("necroforce");
        if (necroforce != null) {
            necroforce.setExecutor(this);
            necroforce.setTabCompleter(this);
        }

        PluginCommand ghostnow = getCommand("ghostnow");
        if (ghostnow != null) {
            ghostnow.setExecutor(this);
            ghostnow.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        Team team = getGhostTeam();
        for (UUID id : new HashSet<>(ghosts)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                exitGhost(p);
            }
        }
    }

    // ========== ОБРАБОТКА КОНТУЗИИ ==========

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        Player player = (Player) e.getEntity();

        if (concussedPlayers.contains(player.getUniqueId()) || ghosts.contains(player.getUniqueId())) {
            e.setDamage(0);
            return;
        }

        double finalHealth = player.getHealth() - e.getFinalDamage();

        if (finalHealth <= 0) {
            double safeDamage = Math.max(0, player.getHealth() - 0.5);
            e.setDamage(safeDamage);

            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline() && player.getHealth() <= 0.5) {
                    enterConcussion(player);
                }
            });
        }
    }

    private void enterConcussion(Player player) {
        if (concussedPlayers.contains(player.getUniqueId())) return;

        concussedPlayers.add(player.getUniqueId());
        concussionStartTime.put(player.getUniqueId(), System.currentTimeMillis());

        player.setHealth(0.5);

        // API 1.21 использует SLOWNESS
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*60*5, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20*60*5, 255, false, false));

        player.sendTitle(
                ChatColor.DARK_RED + "💀 CONCUSSION",
                ChatColor.RED + "5 minutes until death! | /ghostnow to become ghost",
                10, 60, 10
        );

        player.sendMessage(ChatColor.DARK_RED + "✝ YOU ARE CONCUSSED! ✝");
        player.sendMessage(ChatColor.YELLOW + "You have 5 minutes to be rescued.");
        player.sendMessage(ChatColor.YELLOW + "If not rescued, you will die and lose all items!");
        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.GOLD + "/ghostnow" +
                ChatColor.YELLOW + " for voluntary transition.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (concussedPlayers.contains(player.getUniqueId())) {
                    triggerDeathWithLootLoss(player, "Time's up!");
                }
            }
        }.runTaskLater(this, 20L * 60 * 5);
    }

    private void triggerDeathWithLootLoss(Player player, String reason) {
        if (!player.isOnline()) return;

        Location loc = player.getLocation();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().equals(Material.AIR)) {
                player.getWorld().dropItemNaturally(loc, item.clone());
            }
        }

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().equals(Material.AIR)) {
                player.getWorld().dropItemNaturally(loc, armor.clone());
            }
        }

        int exp = player.getTotalExperience();
        if (exp > 0) {
            int expPerOrb = 50;
            while (exp > 0) {
                int drop = Math.min(exp, expPerOrb);
                ExperienceOrb orb = (ExperienceOrb) player.getWorld().spawnEntity(loc, EntityType.EXPERIENCE_ORB);
                orb.setExperience(drop);
                exp -= drop;
            }
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);

        exitConcussion(player);
        enterGhost(player);

        player.sendTitle(
                ChatColor.RED + "☠ DEATH",
                ChatColor.RED + "You lost all items and experience",
                0, 60, 20
        );

        player.sendMessage(ChatColor.RED + "✝ You died! ✝ " + reason);
        player.sendMessage(ChatColor.GRAY + "All items and experience dropped on the ground.");
    }

    private void exitConcussion(Player player) {
        UUID uuid = player.getUniqueId();
        concussedPlayers.remove(uuid);
        concussionStartTime.remove(uuid);

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS); // API 1.21
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player)) return;

        Player rescuer = e.getPlayer();
        Player target = (Player) e.getRightClicked();

        if (!concussedPlayers.contains(target.getUniqueId())) return;

        ItemStack item = rescuer.getInventory().getItemInMainHand();

        if (item.getType() == Material.GOLDEN_APPLE) {
            healFromConcussion(target, 0.20, "golden apple");
            item.setAmount(item.getAmount() - 1);
            rescuer.sendMessage(ChatColor.GREEN + "You saved " + target.getName() + " with a golden apple!");

        } else if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            healFromConcussion(target, 0.50, "enchanted apple");
            item.setAmount(item.getAmount() - 1);
            rescuer.sendMessage(ChatColor.GREEN + "You saved " + target.getName() + " with an enchanted apple!");

        } else if (item.getType() == Material.TOTEM_OF_UNDYING) {
            healFromConcussion(target, 1.0, "totem of undying");
            item.setAmount(item.getAmount() - 1);
            rescuer.sendMessage(ChatColor.GREEN + "You saved " + target.getName() + " with a totem!");
        }
    }

    private void healFromConcussion(Player player, double percent, String method) {
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double newHealth;

        if (percent == 1.0) {
            newHealth = Math.max(maxHealth, 20.0);
        } else {
            newHealth = maxHealth * percent;
        }

        exitConcussion(player);
        player.setHealth(Math.min(newHealth, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));

        if (method.equals("enchanted apple")) {
            double currentMaxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            if (currentMaxHealth < 20.0) {
                double newMaxHealth = Math.min(currentMaxHealth + 2.0, 20.0);
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMaxHealth);
                player.setHealth(Math.min(newHealth + 1.0, newMaxHealth));

                player.sendMessage(ChatColor.GOLD + "✚ Your max health increased by 1 heart!");
                player.sendMessage(ChatColor.GOLD + "New max health: " + String.format("%.0f", newMaxHealth/2) + "❤");
            }
        }

        player.sendTitle(
                ChatColor.GREEN + "💊 SAVED!",
                ChatColor.GREEN + "You kept all items!",
                0, 60, 20
        );

        player.sendMessage(ChatColor.GREEN + "You were saved with a " + method + "! You kept inventory and experience.");
        player.sendMessage(ChatColor.GREEN + "Health: " + String.format("%.1f", newHealth) + "♥");

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        spawnRevivalParticles(player);
    }

    // ========== СТАНДАРТНАЯ ЛОГИКА ==========

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (concussedPlayers.contains(player.getUniqueId())) return;

        String mode = deathModes.getOrDefault(player.getUniqueId(), "ghost");
        if (mode.equalsIgnoreCase("spectator")) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }, 1L);
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    enterGhost(player);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (ghosts.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> enterGhost(p), 1L);
        }
    }

    private void enterGhost(Player player) {
        ghosts.add(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));

        // API 1.21 использует INSTANT_HEALTH и SATURATION
        player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false));

        player.sendMessage(ChatColor.GRAY + "You are now in GHOST mode. You cannot interact with the world.");
        player.sendMessage(ChatColor.GRAY + "Effects applied: Invisibility, Night Vision, Instant Health, Saturation");
        setupGhostTeam(player);
    }

    private void exitGhost(Player player) {
        if (!ghosts.remove(player.getUniqueId())) return;

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.INSTANT_HEALTH);
        player.removePotionEffect(PotionEffectType.SATURATION);
        player.setAllowFlight(false);
        player.setFlying(false);

        Team t = getGhostTeam();
        if (t != null) {
            String name = player.getName();
            if (name != null) t.removeEntry(name);
        }
    }

    private void setupGhostTeam(Player player) {
        Team team = getGhostTeam();
        if (team != null) {
            String name = player.getName();
            if (name != null && !team.hasEntry(name)) {
                team.addEntry(name);
            }
        }
    }

    private Team getGhostTeam() {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return null;

        Scoreboard sb = sm.getMainScoreboard();
        Team team = sb.getTeam("ghosts");
        if (team == null) {
            team = sb.registerNewTeam("ghosts");
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
            team.setPrefix(ChatColor.DARK_GRAY + "[GHOST] " + ChatColor.RESET);
        }
        return team;
    }

    // ========== ОБРАБОТКА ПЕРЕЗАХОДА ==========

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        pendingRequests.remove(uuid);

        if (concussedPlayers.contains(uuid)) {
            player.sendMessage(ChatColor.DARK_RED + "⚠ WARNING!");
            player.sendMessage(ChatColor.RED + "You left while concussed!");
            player.sendMessage(ChatColor.YELLOW + "Concussion state will be restored on rejoin.");
            player.sendMessage(ChatColor.YELLOW + "If concussion time runs out, you'll lose all items and XP!");
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (concussedPlayers.contains(uuid)) {
            Long startTime = concussionStartTime.get(uuid);
            if (startTime != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                long concussionDuration = 5 * 60 * 1000;

                if (elapsed >= concussionDuration) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        triggerDeathWithLootLoss(player, "concussion time expired while offline");
                    }, 20L);
                } else {
                    long remainingTime = concussionDuration - elapsed;
                    int remainingTicks = (int) (remainingTime / 50);

                    player.setHealth(0.5);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, remainingTicks, 0, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, remainingTicks, 255, false, false));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (concussedPlayers.contains(uuid) && player.isOnline()) {
                                triggerDeathWithLootLoss(player, "concussion time expired");
                            }
                        }
                    }.runTaskLater(this, remainingTicks);

                    player.sendMessage(ChatColor.YELLOW + "Concussion state restored. " + (remainingTime/1000) + " seconds left.");
                }
            }
        }
    }

    // ========== КОМАНДЫ ==========

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("necromode")) {
            return handleNecroMode(sender, args);
        }
        if (cmd.getName().equalsIgnoreCase("ghostnow")) {
            return handleGhostNow(sender);
        }
        if (cmd.getName().equalsIgnoreCase("necroforce")) {
            return handleNecroForce(sender, args);
        }
        if (cmd.getName().equalsIgnoreCase("necro")) {
            return handleNecro(sender, args);
        }
        return true;
    }

    private boolean handleNecroMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can change death mode.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("necrocore.mode.change")) {
            player.sendMessage(ChatColor.RED + "No permission to change death mode.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /necromode <spectator|ghost>");
            return true;
        }
        String mode = args[0].toLowerCase();
        if (!mode.equals("spectator") && !mode.equals("ghost")) {
            player.sendMessage(ChatColor.RED + "Available modes: spectator, ghost");
            return true;
        }
        deathModes.put(player.getUniqueId(), mode);
        player.sendMessage(ChatColor.GREEN + "Death mode set: " + mode);
        return true;
    }

    private boolean handleGhostNow(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (!concussedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are not concussed!");
            return true;
        }
        if (!player.hasPermission("necrocore.ghostnow")) {
            player.sendMessage(ChatColor.RED + "No permission to use this command.");
            return true;
        }

        triggerDeathWithLootLoss(player, "voluntarily became ghost");
        player.sendMessage(ChatColor.GRAY + "You voluntarily became a ghost, losing items and XP.");
        return true;
    }

    private boolean handleNecroForce(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /necroforce <player|all>");
            return true;
        }
        if (!sender.hasPermission("necrocore.force")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args[0].equalsIgnoreCase("all") || args[0].equalsIgnoreCase("@a")) {
            int revivedCount = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getGameMode() == GameMode.SPECTATOR || ghosts.contains(target.getUniqueId())) {
                    forceRevivePlayer(sender, target);
                    revivedCount++;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Revived " + revivedCount + " players.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        if (target.getGameMode() != GameMode.SPECTATOR && !ghosts.contains(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Player is not dead.");
            return true;
        }

        forceRevivePlayer(sender, target);
        sender.sendMessage(ChatColor.GREEN + "Force revived " + target.getName());
        return true;
    }

    private boolean handleNecro(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("necrocore.revive")) {
            player.sendMessage(ChatColor.RED + "No permission to revive players.");
            return true;
        }

        if (args.length >= 1 && args[args.length - 1].equalsIgnoreCase("accept")) {
            return handleAccept(player);
        }

        if (ghosts.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(ChatColor.RED + "You cannot revive while dead.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /necro <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        if (target == player) {
            player.sendMessage(ChatColor.RED + "You cannot revive yourself.");
            return true;
        }
        if (!(target.getGameMode() == GameMode.SPECTATOR || ghosts.contains(target.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "This player is not dead (not spectator or ghost).");
            return true;
        }

        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType() != Material.TOTEM_OF_UNDYING ||
                inv.getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) {
            player.sendMessage(ChatColor.RED + "You must hold totems in both hands.");
            return true;
        }
        if (player.getHealth() < 8d) {
            player.sendMessage(ChatColor.RED + "You must have at least 4 hearts of health.");
            return true;
        }

        pendingRequests.put(target.getUniqueId(), new RevivalRequest(player.getUniqueId()));
        player.sendMessage(ChatColor.YELLOW + "Waiting for confirmation from " +
                target.getName() + ChatColor.GRAY + " (60s)");
        target.sendMessage(ChatColor.GOLD + "Player " + player.getName() +
                " wants to revive you. Type /necro accept within 60 seconds.");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            RevivalRequest removed = pendingRequests.remove(target.getUniqueId());
            if (removed != null) {
                Player reqOwner = Bukkit.getPlayer(removed.reviver);
                if (reqOwner != null) {
                    reqOwner.sendMessage(ChatColor.RED + "Revival request for " +
                            target.getName() + " expired.");
                }
                if (target.isOnline()) {
                    target.sendMessage(ChatColor.RED + "Revival request from " +
                            player.getName() + " expired.");
                }
            }
        }, 20L * 60);
        return true;
    }

    private boolean handleAccept(Player player) {
        if (!player.hasPermission("necrocore.ghost.accept")) {
            player.sendMessage(ChatColor.RED + "No permission to accept revivals.");
            return true;
        }
        RevivalRequest req = pendingRequests.remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage(ChatColor.RED + "No active revival request.");
            return true;
        }
        Player reviver = Bukkit.getPlayer(req.reviver);
        if (reviver == null) {
            player.sendMessage(ChatColor.RED + "Reviver left the server.");
            return true;
        }
        if (reviver.getGameMode() == GameMode.SPECTATOR || ghosts.contains(reviver.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Reviver cannot revive right now.");
            return true;
        }
        PlayerInventory invRev = reviver.getInventory();
        if (invRev.getItemInMainHand().getType() != Material.TOTEM_OF_UNDYING ||
                invRev.getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) {
            reviver.sendMessage(ChatColor.RED + "You must hold totems in both hands.");
            return true;
        }
        if (reviver.getHealth() < 8d) {
            reviver.sendMessage(ChatColor.RED + "You must have at least 4 hearts of health.");
            return true;
        }
        performRevive(reviver, player);
        return true;
    }

    private static class RevivalRequest {
        public final UUID reviver;
        public RevivalRequest(UUID reviver) {
            this.reviver = reviver;
        }
    }

    // ========== СИСТЕМА ВОСКРЕШЕНИЯ ==========

    private String getReviveKey(UUID reviverId, UUID targetId) {
        return reviverId.toString() + "_" + targetId.toString();
    }

    private double getFailureChance(UUID reviverId, UUID targetId) {
        String key = getReviveKey(reviverId, targetId);
        int successCount = successfulRevives.getOrDefault(key, 0);
        double chance = 0.70 * Math.pow(0.85, successCount);
        return Math.max(chance, 0.10);
    }

    private void recordSuccessfulRevive(UUID reviverId, UUID targetId) {
        String key = getReviveKey(reviverId, targetId);
        int currentCount = successfulRevives.getOrDefault(key, 0);
        successfulRevives.put(key, currentCount + 1);
    }

    private void performRevive(Player reviver, Player target) {
        PlayerInventory inv = reviver.getInventory();
        inv.getItemInOffHand().setAmount(inv.getItemInOffHand().getAmount() - 1);
        reviver.damage(4.0);

        double failureChance = getFailureChance(reviver.getUniqueId(), target.getUniqueId());

        if (Math.random() < failureChance) {
            Location spawnLoc = reviver.getLocation();
            Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            zombie.setCustomName(target.getName());
            zombie.setCustomNameVisible(true);

            reviver.sendMessage(ChatColor.DARK_RED + "Revival failed! Instead of " + target.getName() +
                    " a zombie appeared! (Failure chance: " + String.format("%.1f", failureChance * 100) + "%)");
            target.sendMessage(ChatColor.DARK_RED + "Revival attempt by " + reviver.getName() +
                    " failed. Your zombie version now roams the world!");
            return;
        }

        exitGhost(target);
        target.setGameMode(GameMode.SURVIVAL);
        target.teleport(reviver);

        if (target.getHealth() <= 0.0) {
            target.setHealth(Math.min(1.0, target.getMaxHealth()));
        } else {
            target.setHealth(Math.min(2.0, target.getMaxHealth()));
        }

        adjustMaxHealth(reviver, -4);
        adjustMaxHealth(target, +2);

        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 1));

        spawnRevivalParticles(target);
        recordSuccessfulRevive(reviver.getUniqueId(), target.getUniqueId());

        double newChance = getFailureChance(reviver.getUniqueId(), target.getUniqueId());
        reviver.sendMessage(ChatColor.GREEN + "You revived " + target.getName() +
                ChatColor.GRAY + " (Next failure chance: " + String.format("%.1f", newChance * 100) + "%)");
        target.sendMessage(ChatColor.GREEN + "You were revived by " + reviver.getName());
    }

    private void forceRevivePlayer(CommandSender sender, Player target) {
        Player reviver = sender instanceof Player ? (Player) sender : null;

        exitGhost(target);
        target.setGameMode(GameMode.SURVIVAL);

        if (reviver != null) {
            target.teleport(reviver);
        } else {
            target.teleport(target.getWorld().getSpawnLocation());
        }

        if (target.getHealth() <= 0.0) {
            target.setHealth(Math.min(4.0, target.getMaxHealth()));
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 1));
        spawnRevivalParticles(target);

        if (reviver == null && sender != null) {
            sender.sendMessage(ChatColor.GREEN + "Force revived " + target.getName());
        }

        target.sendMessage(ChatColor.GREEN + "You were force revived by an administrator.");
    }

    private void spawnRevivalParticles(Player player) {
        for (int i = 0; i < 30; i++) {
            double x = player.getLocation().getX() + (Math.random() - 0.5) * 3;
            double y = player.getLocation().getY() + Math.random() * 3;
            double z = player.getLocation().getZ() + (Math.random() - 0.5) * 3;
            player.getWorld().spawnParticle(Particle.PORTAL, x, y, z, 2, 0, 0, 0, 0.1);
        }

        for (int i = 0; i < 15; i++) {
            double x = player.getLocation().getX() + (Math.random() - 0.5) * 2;
            double y = player.getLocation().getY() + Math.random() * 2;
            double z = player.getLocation().getZ() + (Math.random() - 0.5) * 2;
            player.getWorld().spawnParticle(Particle.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private void adjustMaxHealth(Player p, double delta) {
        AttributeInstance inst = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (inst == null) return;

        double current = inst.getBaseValue();
        double newVal = current + delta;
        if (delta > 0) {
            newVal = Math.min(20.0, newVal);
        }
        if (delta < 0) {
            newVal = Math.max(2.0, newVal);
        }
        inst.setBaseValue(newVal);
        if (p.getHealth() > newVal) {
            p.setHealth(newVal);
        }
    }

    // ========== TAB-COMPLETE ==========

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("necromode")) {
            if (args.length == 1) {
                return filter(Arrays.asList("spectator", "ghost"), args[0]);
            }
        } else if (command.getName().equalsIgnoreCase("necro")) {
            if (args.length == 1) {
                if (pendingRequests.containsKey(player.getUniqueId())) {
                    return filter(Arrays.asList("accept"), args[0]);
                }
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p != player && (p.getGameMode() == GameMode.SPECTATOR ||
                            ghosts.contains(p.getUniqueId()))) {
                        list.add(p.getName());
                    }
                }
                return filter(list, args[0]);
            } else if (args.length == 2) {
                return filter(Arrays.asList("accept"), args[1]);
            }
        } else if (command.getName().equalsIgnoreCase("necroforce")) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                list.add("all");
                list.add("@a");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR || ghosts.contains(p.getUniqueId())) {
                        list.add(p.getName());
                    }
                }
                return filter(list, args[0]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> base, String current) {
        String low = current.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : base) {
            if (s.toLowerCase().startsWith(low)) {
                out.add(s);
            }
        }
        return out;
    }

    // ========== ОБРАБОТЧИКИ СОБЫТИЙ ==========

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (ghosts.contains(player.getUniqueId()) || concussedPlayers.contains(player.getUniqueId())) {
            e.setExpToDrop(0);
            e.setDropItems(false);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (ghosts.contains(e.getPlayer().getUniqueId()) ||
                concussedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setBuild(false);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();
            if (ghosts.contains(player.getUniqueId()) ||
                    concussedPlayers.contains(player.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (ghosts.contains(e.getPlayer().getUniqueId()) ||
                concussedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();
            if (ghosts.contains(player.getUniqueId())) {
                e.setDamage(0);
            }
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player) {
            Player player = (Player) e.getTarget();
            if (ghosts.contains(player.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            Player player = (Player) e.getDamager();
            if (ghosts.contains(player.getUniqueId())) {
                e.setDamage(0);
            }
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.equals("/gamemode ghost") || msg.equals("/gm ghost")) {
            Player p = e.getPlayer();
            if (!p.hasPermission("necrocore.ghost.gamemode")) {
                p.sendMessage(ChatColor.RED + "No permission to switch to ghost.");
                e.setCancelled(true);
                return;
            }
            if (!ghosts.contains(p.getUniqueId())) {
                enterGhost(p);
                p.sendMessage(ChatColor.GRAY + "You manually switched to GHOST mode.");
            } else {
                exitGhost(p);
                p.sendMessage(ChatColor.GREEN + "You exited GHOST mode.");
            }
            e.setCancelled(true);
        }
    }
}