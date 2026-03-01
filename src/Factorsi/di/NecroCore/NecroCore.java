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
    private final HashMap<UUID, BukkitRunnable> activeRevivalAnimations = new HashMap<>();

    // Настраиваемые параметры
    private int concussionDuration;
    private double concussionStartHealth;
    private int revivalAnimationDelayNormal;
    private int revivalAnimationDelayForce;
    private double revivalHealthCost;
    private int revivalRequestTimeout;
    private double baseFailureChance;
    private double failureMultiplier;
    private double minFailureChance;
    private double healthChangeReviver;
    private double healthChangeTarget;
    private double goldenAppleHealPercent;
    private double enchantedAppleHealPercent;
    private double totemHealPercent;
    private double enchantedAppleHealthBoost;
    private String language;

    // Сообщения
    private Map<String, String> messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        loadMessages();

        getLogger().info("NecroCore v1.3.0 by Factorsi_di|Nicto_55 enabled. Language: " + language);

        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand necro = getCommand("necro");
        if (necro != null) necro.setTabCompleter(this);

        PluginCommand necromode = getCommand("necromode");
        if (necromode != null) necromode.setTabCompleter(this);

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

    private void loadConfig() {
        reloadConfig();
        language = getConfig().getString("language", "en");
        concussionDuration = getConfig().getInt("concussion-duration", 300);
        concussionStartHealth = getConfig().getDouble("concussion-start-health", 0.5);
        revivalAnimationDelayNormal = getConfig().getInt("revival-animation-delay-normal", 4);
        revivalAnimationDelayForce = getConfig().getInt("revival-animation-delay-force", 3);
        revivalHealthCost = getConfig().getDouble("revival-health-cost", 4.0);
        revivalRequestTimeout = getConfig().getInt("revival-request-timeout", 60);
        baseFailureChance = getConfig().getDouble("revival-base-failure-chance", 0.70);
        failureMultiplier = getConfig().getDouble("revival-failure-multiplier", 0.85);
        minFailureChance = getConfig().getDouble("revival-min-failure-chance", 0.10);
        healthChangeReviver = getConfig().getDouble("revival-health-change-reviver", -4.0);
        healthChangeTarget = getConfig().getDouble("revival-health-change-target", 2.0);
        goldenAppleHealPercent = getConfig().getDouble("golden-apple-heal-percent", 0.20);
        enchantedAppleHealPercent = getConfig().getDouble("enchanted-apple-heal-percent", 0.50);
        totemHealPercent = getConfig().getDouble("totem-heal-percent", 1.0);
        enchantedAppleHealthBoost = getConfig().getDouble("enchanted-apple-health-boost", 2.0);
    }

    private void loadMessages() {
        messages = new HashMap<>();
        if (language.equalsIgnoreCase("ru")) {
            // Русские сообщения
            messages.put("only-players", "Только игроки могут использовать эту команду.");
            messages.put("no-permission", "Нет прав.");
            messages.put("necromode-usage", "Использование: /necromode <spectator|ghost>");
            messages.put("necromode-invalid", "Доступные режимы: spectator, ghost");
            messages.put("necromode-set", "Посмертный режим установлен: %s");
            messages.put("necromode-no-permission", "Нет прав на изменение посмертного режима.");

            messages.put("ghostnow-not-concussed", "Вы не контужены!");
            messages.put("ghostnow-success", "Вы добровольно стали призраком, потеряв вещи и опыт.");

            messages.put("necroforce-usage", "Использование: /necroforce <игрок|all>");
            messages.put("necroforce-player-not-found", "Игрок не найден.");
            messages.put("necroforce-not-dead", "Этот игрок не мёртв.");
            messages.put("necroforce-revived", "Принудительно воскрешён %s");
            messages.put("necroforce-revived-count", "Воскрешено игроков: %d");

            messages.put("necro-usage", "Использование: /necro <игрок>");
            messages.put("necro-self", "Вы не можете воскресить себя.");
            messages.put("necro-not-dead", "Этот игрок не мёртв (не в режиме наблюдателя или призрака).");
            messages.put("necro-need-totems", "Вы должны держать тотемы бессмертия в обеих руках.");
            messages.put("necro-need-health", "У вас должно быть не менее %.1f сердец здоровья.");
            messages.put("necro-request-sent", "Ожидание подтверждения от %s (%dс)");
            messages.put("necro-request-received", "Игрок %s хочет воскресить вас. Введите /necro accept в течение %d секунд.");
            messages.put("necro-request-expired-reviver", "Запрос на воскрешение для %s истёк.");
            messages.put("necro-request-expired-target", "Запрос на воскрешение от %s истёк.");
            messages.put("necro-no-request", "Нет активных запросов на воскрешение.");
            messages.put("necro-reviver-left", "Воскрешающий покинул сервер.");
            messages.put("necro-reviver-not-available", "Воскрешающий не может сейчас воскрешать.");
            messages.put("necro-accept-no-permission", "Нет прав на принятие воскрешения.");

            messages.put("revival-failed", "Воскрешение не удалось! Вместо %s появился зомби! (Шанс неудачи: %.1f%%)");
            messages.put("revival-failed-target", "Попытка воскрешения от %s провалилась. Ваша зомби-версия теперь бродит по миру!");
            messages.put("revival-start", "⚡ Начало ритуала воскрешения...");
            messages.put("revival-start-target", "⚡ Игрок %s начинает ритуал вашего воскрешения...");
            messages.put("revival-interrupted-left", "Ритуал прерван: один из игроков вышел.");
            messages.put("revival-interrupted-target-left", "Ритуал воскрешения прерван.");
            messages.put("revival-interrupted-not-dead", "Ритуал прерван: цель больше не мертва.");
            messages.put("revival-success", "Вы воскресили %s (Следующий шанс неудачи: %.1f%%)");
            messages.put("revival-success-target", "Вас воскресил %s");

            messages.put("force-revival-start", "⚡ Начало принудительного ритуала воскрешения...");
            messages.put("force-revival-start-target", "⚡ Начало принудительного воскрешения...");
            messages.put("force-revival-interrupted", "Ритуал прерван: цель вышла.");
            messages.put("force-revival-success-target", "Вас принудительно воскресил администратор.");

            messages.put("concussion-title", "💀 КОНТУЗИЯ");
            messages.put("concussion-subtitle", "%d секунд до смерти! | /ghostnow чтобы стать призраком");
            messages.put("concussion-message1", "✝ ВЫ КОНТУЖЕНЫ! ✝");
            messages.put("concussion-message2", "У вас есть %d секунд, чтобы вас спасли.");
            messages.put("concussion-message3", "Если вас не спасут, вы умрёте и потеряете все вещи!");
            messages.put("concussion-message4", "Используйте %s для добровольного перехода в призрака.");
            messages.put("concussion-death-title", "☠ СМЕРТЬ");
            messages.put("concussion-death-subtitle", "Вы потеряли все вещи и опыт");
            messages.put("concussion-death-message", "✝ Вы умерли! ✝ %s");
            messages.put("concussion-death-drop", "Все вещи и опыт выпали на землю.");
            messages.put("concussion-warning", "⚠ ВНИМАНИЕ!");
            messages.put("concussion-warning-left", "Вы вышли во время контузии!");
            messages.put("concussion-warning-restore", "Состояние контузии восстановится при повторном входе.");
            messages.put("concussion-warning-expire", "Если время контузии истечёт, вы потеряете все вещи и опыт!");
            messages.put("concussion-restored", "Состояние контузии восстановлено. %d секунд осталось.");
            messages.put("concussion-expired-offline", "время контузии истекло, пока вы были офлайн");
            messages.put("concussion-expired", "время контузии истекло");

            messages.put("rescue-golden-apple", "Вы спасли %s золотым яблоком!");
            messages.put("rescue-enchanted-apple", "Вы спасли %s зачарованным яблоком!");
            messages.put("rescue-totem", "Вы спасли %s тотемом!");
            messages.put("rescue-title", "💊 СПАСЁН!");
            messages.put("rescue-subtitle", "Вы сохранили все вещи!");
            messages.put("rescue-message", "Вас спасли %s! Вы сохранили инвентарь и опыт.");
            messages.put("rescue-health", "Здоровье: %.1f♥");
            messages.put("rescue-max-health-increase", "✚ Ваше максимальное здоровье увеличено на %.1f сердец!");
            messages.put("rescue-new-max-health", "Новое максимальное здоровье: %.0f❤");

            messages.put("ghost-mode-enter", "Теперь вы в режиме ПРИЗРАКА. Вы не можете взаимодействовать с миром.");
            messages.put("ghost-mode-effects", "Эффекты: Невидимость, Ночное зрение, Мгновенное здоровье, Насыщение");
            messages.put("ghost-mode-exit", "Вы вышли из режима ПРИЗРАКА.");
            messages.put("ghost-manual-switch", "Вы вручную переключились в режим ПРИЗРАКА.");
            messages.put("ghost-manual-exit", "Вы вышли из режима ПРИЗРАКА.");
            messages.put("ghost-no-permission", "Нет прав на переключение в режим призрака.");
        } else {
            // Английские сообщения (по умолчанию)
            messages.put("only-players", "Only players can use this command.");
            messages.put("no-permission", "No permission.");
            messages.put("necromode-usage", "Usage: /necromode <spectator|ghost>");
            messages.put("necromode-invalid", "Available modes: spectator, ghost");
            messages.put("necromode-set", "Post-death mode set to: %s");
            messages.put("necromode-no-permission", "No permission to change post-death mode.");

            messages.put("ghostnow-not-concussed", "You are not concussed!");
            messages.put("ghostnow-success", "You voluntarily became a ghost, losing items and experience.");

            messages.put("necroforce-usage", "Usage: /necroforce <player|all>");
            messages.put("necroforce-player-not-found", "Player not found.");
            messages.put("necroforce-not-dead", "This player is not dead.");
            messages.put("necroforce-revived", "Force revived %s");
            messages.put("necroforce-revived-count", "Players revived: %d");

            messages.put("necro-usage", "Usage: /necro <player>");
            messages.put("necro-self", "You cannot revive yourself.");
            messages.put("necro-not-dead", "This player is not dead (not in spectator or ghost mode).");
            messages.put("necro-need-totems", "You must hold totems in both hands.");
            messages.put("necro-need-health", "You must have at least %.1f hearts of health.");
            messages.put("necro-request-sent", "Waiting for confirmation from %s (%ds)");
            messages.put("necro-request-received", "Player %s wants to revive you. Type /necro accept within %d seconds.");
            messages.put("necro-request-expired-reviver", "Revival request for %s has expired.");
            messages.put("necro-request-expired-target", "Revival request from %s has expired.");
            messages.put("necro-no-request", "No active revival requests.");
            messages.put("necro-reviver-left", "The reviver left the server.");
            messages.put("necro-reviver-not-available", "The reviver cannot revive right now.");
            messages.put("necro-accept-no-permission", "No permission to accept revivals.");

            messages.put("revival-failed", "Revival failed! Instead of %s, a zombie appeared! (Failure chance: %.1f%%)");
            messages.put("revival-failed-target", "Revival attempt by %s failed. Your zombie version now roams the world!");
            messages.put("revival-start", "⚡ Starting revival ritual...");
            messages.put("revival-start-target", "⚡ Player %s is starting your revival ritual...");
            messages.put("revival-interrupted-left", "Ritual interrupted: one of the players left.");
            messages.put("revival-interrupted-target-left", "Revival ritual interrupted.");
            messages.put("revival-interrupted-not-dead", "Ritual interrupted: target is no longer dead.");
            messages.put("revival-success", "You revived %s (Next failure chance: %.1f%%)");
            messages.put("revival-success-target", "You were revived by %s");

            messages.put("force-revival-start", "⚡ Starting forced revival ritual...");
            messages.put("force-revival-start-target", "⚡ Starting forced revival...");
            messages.put("force-revival-interrupted", "Ritual interrupted: target left.");
            messages.put("force-revival-success-target", "You were force revived by an admin.");

            messages.put("concussion-title", "💀 CONCUSSION");
            messages.put("concussion-subtitle", "%d seconds until death! | /ghostnow to become a ghost");
            messages.put("concussion-message1", "✝ YOU ARE CONCUSSED! ✝");
            messages.put("concussion-message2", "You have %d seconds to be rescued.");
            messages.put("concussion-message3", "If you are not rescued, you will die and lose all items!");
            messages.put("concussion-message4", "Use %s for voluntary transition.");
            messages.put("concussion-death-title", "☠ DEATH");
            messages.put("concussion-death-subtitle", "You lost all items and experience");
            messages.put("concussion-death-message", "✝ You died! ✝ %s");
            messages.put("concussion-death-drop", "All items and experience dropped on the ground.");
            messages.put("concussion-warning", "⚠ WARNING!");
            messages.put("concussion-warning-left", "You left during concussion!");
            messages.put("concussion-warning-restore", "Concussion state will be restored upon rejoin.");
            messages.put("concussion-warning-expire", "If concussion time runs out, you'll lose all items and experience!");
            messages.put("concussion-restored", "Concussion state restored. %d seconds remaining.");
            messages.put("concussion-expired-offline", "concussion time expired while you were offline");
            messages.put("concussion-expired", "concussion time expired");

            messages.put("rescue-golden-apple", "You saved %s with a golden apple!");
            messages.put("rescue-enchanted-apple", "You saved %s with an enchanted apple!");
            messages.put("rescue-totem", "You saved %s with a totem!");
            messages.put("rescue-title", "💊 RESCUED!");
            messages.put("rescue-subtitle", "You kept all your items!");
            messages.put("rescue-message", "You were saved with %s! You kept your inventory and experience.");
            messages.put("rescue-health", "Health: %.1f♥");
            messages.put("rescue-max-health-increase", "✚ Your max health increased by %.1f hearts!");
            messages.put("rescue-new-max-health", "New max health: %.0f❤");

            messages.put("ghost-mode-enter", "You are now in GHOST mode. You cannot interact with the world.");
            messages.put("ghost-mode-effects", "Effects: Invisibility, Night Vision, Instant Health, Saturation");
            messages.put("ghost-mode-exit", "You left GHOST mode.");
            messages.put("ghost-manual-switch", "You manually switched to GHOST mode.");
            messages.put("ghost-manual-exit", "You left GHOST mode.");
            messages.put("ghost-no-permission", "No permission to switch to ghost mode.");
        }
    }

    private String msg(String key, Object... args) {
        String format = messages.get(key);
        if (format == null) format = "Missing message: " + key;
        return String.format(format, args);
    }

    // ========== REVIVAL ANIMATION ==========

    private void startSoulSandAuraAnimation(Player player, int durationSeconds) {
        UUID playerId = player.getUniqueId();

        if (activeRevivalAnimations.containsKey(playerId)) {
            activeRevivalAnimations.get(playerId).cancel();
            activeRevivalAnimations.remove(playerId);
        }

        BukkitRunnable animation = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSeconds * 20;
            double angle = 0;
            final double radius = 1.5;
            final double heightOffset = 0.5;
            final int points = 12;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    this.cancel();
                    activeRevivalAnimations.remove(playerId);
                    return;
                }

                Location center = player.getLocation();

                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < points; j++) {
                        double currentAngle = angle + (j * 2 * Math.PI / points);
                        double x = radius * Math.cos(currentAngle);
                        double z = radius * Math.sin(currentAngle);
                        double y = heightOffset + 0.3 * Math.sin(ticks * 0.1 + j * 0.5);
                        Location particleLoc = center.clone().add(x, y, z);
                        player.getWorld().spawnParticle(Particle.SOUL,
                                particleLoc, 2, 0.1, 0.1, 0.1, 0.05);
                        if (ticks % 5 == 0) {
                            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                                    particleLoc, 1, 0.05, 0.05, 0.05, 0.02);
                        }
                    }
                }

                angle += Math.PI / 40;
                if (angle > 2 * Math.PI) angle = 0;

                ticks++;

                if (ticks % 20 == 0) {
                    spawnRevivalPulse(player);
                }
            }
        };

        animation.runTaskTimer(this, 0L, 1L);
        activeRevivalAnimations.put(playerId, animation);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeRevivalAnimations.containsKey(playerId)) {
                    activeRevivalAnimations.get(playerId).cancel();
                    activeRevivalAnimations.remove(playerId);
                }
            }
        }.runTaskLater(this, durationSeconds * 20L);
    }

    private void spawnRevivalPulse(Player player) {
        Location center = player.getLocation();
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            double x = Math.cos(angle);
            double z = Math.sin(angle);
            for (double r = 0.5; r <= 2.5; r += 0.5) {
                Location pulseLoc = center.clone().add(x * r, 0.5, z * r);
                player.getWorld().spawnParticle(Particle.SOUL, pulseLoc, 1, 0, 0, 0, 0.1);
            }
        }
    }

    private void stopSoulSandAuraAnimation(Player player) {
        UUID playerId = player.getUniqueId();
        if (activeRevivalAnimations.containsKey(playerId)) {
            activeRevivalAnimations.get(playerId).cancel();
            activeRevivalAnimations.remove(playerId);
        }
    }

    // ========== CONCUSSION HANDLING ==========

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
            double safeDamage = Math.max(0, player.getHealth() - concussionStartHealth);
            e.setDamage(safeDamage);

            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline() && player.getHealth() <= concussionStartHealth) {
                    enterConcussion(player);
                }
            });
        }
    }

    private void enterConcussion(Player player) {
        if (concussedPlayers.contains(player.getUniqueId())) return;

        concussedPlayers.add(player.getUniqueId());
        concussionStartTime.put(player.getUniqueId(), System.currentTimeMillis());

        player.setHealth(concussionStartHealth);

        int concussionTicks = concussionDuration * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, concussionTicks, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, concussionTicks, 255, false, false));

        player.sendTitle(
                ChatColor.DARK_RED + msg("concussion-title"),
                ChatColor.RED + msg("concussion-subtitle", concussionDuration),
                10, 60, 10
        );

        player.sendMessage(ChatColor.DARK_RED + msg("concussion-message1"));
        player.sendMessage(ChatColor.YELLOW + msg("concussion-message2", concussionDuration));
        player.sendMessage(ChatColor.YELLOW + msg("concussion-message3"));
        player.sendMessage(ChatColor.YELLOW + msg("concussion-message4", ChatColor.GOLD + "/ghostnow" + ChatColor.YELLOW));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (concussedPlayers.contains(player.getUniqueId())) {
                    triggerDeathWithLootLoss(player, msg("concussion-expired"));
                }
            }
        }.runTaskLater(this, concussionDuration * 20L);
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
                ChatColor.RED + msg("concussion-death-title"),
                ChatColor.RED + msg("concussion-death-subtitle"),
                0, 60, 20
        );

        player.sendMessage(ChatColor.RED + msg("concussion-death-message", reason));
        player.sendMessage(ChatColor.GRAY + msg("concussion-death-drop"));
    }

    private void exitConcussion(Player player) {
        UUID uuid = player.getUniqueId();
        concussedPlayers.remove(uuid);
        concussionStartTime.remove(uuid);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player)) return;

        Player rescuer = e.getPlayer();
        Player target = (Player) e.getRightClicked();

        if (!concussedPlayers.contains(target.getUniqueId())) return;

        ItemStack item = rescuer.getInventory().getItemInMainHand();

        if (item.getType() == Material.GOLDEN_APPLE) {
            healFromConcussion(target, goldenAppleHealPercent, "golden apple", rescuer);
            item.setAmount(item.getAmount() - 1);
            rescuer.sendMessage(ChatColor.GREEN + msg("rescue-golden-apple", target.getName()));

        } else if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            healFromConcussion(target, enchantedAppleHealPercent, "enchanted apple", rescuer);
            item.setAmount(item.getAmount() - 1);
            rescuer.sendMessage(ChatColor.GREEN + msg("rescue-enchanted-apple", target.getName()));

        } else if (item.getType() == Material.TOTEM_OF_UNDYING) {
            healFromConcussion(target, totemHealPercent, "totem", rescuer);
            item.setAmount(item.getAmount() - 1);
            rescuer.sendMessage(ChatColor.GREEN + msg("rescue-totem", target.getName()));
        }
    }

    private void healFromConcussion(Player player, double percent, String method, Player rescuer) {
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double newHealth;

        if (percent >= 1.0) {
            newHealth = Math.max(maxHealth, 20.0);
        } else {
            newHealth = maxHealth * percent;
        }

        exitConcussion(player);
        player.setHealth(Math.min(newHealth, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));

        if (method.equals("enchanted apple")) {
            double currentMaxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            if (currentMaxHealth < 20.0) {
                double newMaxHealth = Math.min(currentMaxHealth + enchantedAppleHealthBoost, 20.0);
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMaxHealth);
                player.setHealth(Math.min(newHealth + enchantedAppleHealthBoost/2, newMaxHealth));

                player.sendMessage(ChatColor.GOLD + msg("rescue-max-health-increase", enchantedAppleHealthBoost/2));
                player.sendMessage(ChatColor.GOLD + msg("rescue-new-max-health", newMaxHealth/2));
            }
        }

        player.sendTitle(
                ChatColor.GREEN + msg("rescue-title"),
                ChatColor.GREEN + msg("rescue-subtitle"),
                0, 60, 20
        );

        player.sendMessage(ChatColor.GREEN + msg("rescue-message", method));
        player.sendMessage(ChatColor.GREEN + msg("rescue-health", newHealth));

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        spawnRevivalParticles(player);
    }

    // ========== STANDARD LOGIC ==========

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (concussedPlayers.contains(player.getUniqueId())) return;

        String mode = deathModes.getOrDefault(player.getUniqueId(), "ghost");
        if (mode.equalsIgnoreCase("spectator")) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) player.setGameMode(GameMode.SPECTATOR);
            }, 1L);
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) enterGhost(player);
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
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEAL, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false));

        player.sendMessage(ChatColor.GRAY + msg("ghost-mode-enter"));
        player.sendMessage(ChatColor.GRAY + msg("ghost-mode-effects"));
        setupGhostTeam(player);
    }

    private void exitGhost(Player player) {
        if (!ghosts.remove(player.getUniqueId())) return;

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.HEAL);
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

    // ========== REJOIN HANDLING ==========

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        pendingRequests.remove(uuid);
        stopSoulSandAuraAnimation(player);

        if (concussedPlayers.contains(uuid)) {
            player.sendMessage(ChatColor.DARK_RED + msg("concussion-warning"));
            player.sendMessage(ChatColor.RED + msg("concussion-warning-left"));
            player.sendMessage(ChatColor.YELLOW + msg("concussion-warning-restore"));
            player.sendMessage(ChatColor.YELLOW + msg("concussion-warning-expire"));
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
                long concussionDurationMs = concussionDuration * 1000L;

                if (elapsed >= concussionDurationMs) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        triggerDeathWithLootLoss(player, msg("concussion-expired-offline"));
                    }, 20L);
                } else {
                    long remainingTime = concussionDurationMs - elapsed;
                    int remainingTicks = (int) (remainingTime / 50);

                    player.setHealth(concussionStartHealth);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, remainingTicks, 0, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, remainingTicks, 255, false, false));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (concussedPlayers.contains(uuid) && player.isOnline()) {
                                triggerDeathWithLootLoss(player, msg("concussion-expired"));
                            }
                        }
                    }.runTaskLater(this, remainingTicks);

                    player.sendMessage(ChatColor.YELLOW + msg("concussion-restored", remainingTime/1000));
                }
            }
        }
    }

    // ========== COMMANDS ==========

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
            sender.sendMessage(msg("only-players"));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("hardcorenecro.mode.change")) {
            player.sendMessage(ChatColor.RED + msg("necromode-no-permission"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + msg("necromode-usage"));
            return true;
        }
        String mode = args[0].toLowerCase();
        if (!mode.equals("spectator") && !mode.equals("ghost")) {
            player.sendMessage(ChatColor.RED + msg("necromode-invalid"));
            return true;
        }
        deathModes.put(player.getUniqueId(), mode);
        player.sendMessage(ChatColor.GREEN + msg("necromode-set", mode));
        return true;
    }

    private boolean handleGhostNow(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(msg("only-players"));
            return true;
        }

        Player player = (Player) sender;
        if (!concussedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + msg("ghostnow-not-concussed"));
            return true;
        }
        if (!player.hasPermission("hardcorenecro.ghostnow")) {
            player.sendMessage(ChatColor.RED + msg("no-permission"));
            return true;
        }

        triggerDeathWithLootLoss(player, msg("ghostnow-success"));
        player.sendMessage(ChatColor.GRAY + msg("ghostnow-success"));
        return true;
    }

    private boolean handleNecroForce(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + msg("necroforce-usage"));
            return true;
        }
        if (!sender.hasPermission("hardcorenecro.force")) {
            sender.sendMessage(ChatColor.RED + msg("no-permission"));
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
            sender.sendMessage(ChatColor.GREEN + msg("necroforce-revived-count", revivedCount));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + msg("necroforce-player-not-found"));
            return true;
        }
        if (target.getGameMode() != GameMode.SPECTATOR && !ghosts.contains(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + msg("necroforce-not-dead"));
            return true;
        }

        forceRevivePlayer(sender, target);
        sender.sendMessage(ChatColor.GREEN + msg("necroforce-revived", target.getName()));
        return true;
    }

    private boolean handleNecro(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(msg("only-players"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("hardcorenecro.revive")) {
            player.sendMessage(ChatColor.RED + msg("no-permission"));
            return true;
        }

        if (args.length >= 1 && args[args.length - 1].equalsIgnoreCase("accept")) {
            return handleAccept(player);
        }

        if (ghosts.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(ChatColor.RED + msg("necro-not-dead"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + msg("necro-usage"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + msg("necroforce-player-not-found")); // Обратите внимание: используется ключ для necroforce
            return true;
        }
        if (target == player) {
            player.sendMessage(ChatColor.RED + msg("necro-self"));
            return true;
        }
        if (!(target.getGameMode() == GameMode.SPECTATOR || ghosts.contains(target.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + msg("necro-not-dead"));
            return true;
        }

        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType() != Material.TOTEM_OF_UNDYING ||
                inv.getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) {
            player.sendMessage(ChatColor.RED + msg("necro-need-totems"));
            return true;
        }
        if (player.getHealth() < revivalHealthCost * 2) {
            player.sendMessage(ChatColor.RED + msg("necro-need-health", revivalHealthCost/2));
            return true;
        }

        pendingRequests.put(target.getUniqueId(), new RevivalRequest(player.getUniqueId()));
        player.sendMessage(ChatColor.YELLOW + msg("necro-request-sent", target.getName(), revivalRequestTimeout));
        target.sendMessage(ChatColor.GOLD + msg("necro-request-received", player.getName(), revivalRequestTimeout));

        Bukkit.getScheduler().runTaskLater(this, () -> {
            RevivalRequest removed = pendingRequests.remove(target.getUniqueId());
            if (removed != null) {
                Player reqOwner = Bukkit.getPlayer(removed.reviver);
                if (reqOwner != null) {
                    reqOwner.sendMessage(ChatColor.RED + msg("necro-request-expired-reviver", target.getName()));
                }
                if (target.isOnline()) {
                    target.sendMessage(ChatColor.RED + msg("necro-request-expired-target", player.getName()));
                }
            }
        }, revivalRequestTimeout * 20L);
        return true;
    }

    private boolean handleAccept(Player player) {
        if (!player.hasPermission("hardcorenecro.ghost.accept")) {
            player.sendMessage(ChatColor.RED + msg("necro-accept-no-permission"));
            return true;
        }
        RevivalRequest req = pendingRequests.remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage(ChatColor.RED + msg("necro-no-request"));
            return true;
        }
        Player reviver = Bukkit.getPlayer(req.reviver);
        if (reviver == null) {
            player.sendMessage(ChatColor.RED + msg("necro-reviver-left"));
            return true;
        }
        if (reviver.getGameMode() == GameMode.SPECTATOR || ghosts.contains(reviver.getUniqueId())) {
            player.sendMessage(ChatColor.RED + msg("necro-reviver-not-available"));
            return true;
        }
        PlayerInventory invRev = reviver.getInventory();
        if (invRev.getItemInMainHand().getType() != Material.TOTEM_OF_UNDYING ||
                invRev.getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) {
            reviver.sendMessage(ChatColor.RED + msg("necro-need-totems"));
            return true;
        }
        if (reviver.getHealth() < revivalHealthCost * 2) {
            reviver.sendMessage(ChatColor.RED + msg("necro-need-health", revivalHealthCost/2));
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

    // ========== REVIVAL SYSTEM WITH ANIMATION ==========

    private String getReviveKey(UUID reviverId, UUID targetId) {
        return reviverId.toString() + "_" + targetId.toString();
    }

    private double getFailureChance(UUID reviverId, UUID targetId) {
        String key = getReviveKey(reviverId, targetId);
        int successCount = successfulRevives.getOrDefault(key, 0);
        double chance = baseFailureChance * Math.pow(failureMultiplier, successCount);
        return Math.max(chance, minFailureChance);
    }

    private void recordSuccessfulRevive(UUID reviverId, UUID targetId) {
        String key = getReviveKey(reviverId, targetId);
        int currentCount = successfulRevives.getOrDefault(key, 0);
        successfulRevives.put(key, currentCount + 1);
    }

    private void performRevive(Player reviver, Player target) {
        PlayerInventory inv = reviver.getInventory();
        inv.getItemInOffHand().setAmount(inv.getItemInOffHand().getAmount() - 1);
        reviver.damage(revivalHealthCost);

        double failureChance = getFailureChance(reviver.getUniqueId(), target.getUniqueId());

        if (Math.random() < failureChance) {
            Location spawnLoc = reviver.getLocation();
            Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            zombie.setCustomName(target.getName());
            zombie.setCustomNameVisible(true);

            reviver.sendMessage(ChatColor.DARK_RED + msg("revival-failed", target.getName(), failureChance * 100));
            target.sendMessage(ChatColor.DARK_RED + msg("revival-failed-target", reviver.getName()));
            return;
        }

        reviver.sendMessage(ChatColor.YELLOW + msg("revival-start"));
        target.sendMessage(ChatColor.YELLOW + msg("revival-start-target", reviver.getName()));

        startSoulSandAuraAnimation(reviver, revivalAnimationDelayNormal);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!reviver.isOnline() || !target.isOnline()) {
                    reviver.sendMessage(ChatColor.RED + msg("revival-interrupted-left"));
                    if (target.isOnline()) {
                        target.sendMessage(ChatColor.RED + msg("revival-interrupted-target-left"));
                    }
                    stopSoulSandAuraAnimation(reviver);
                    return;
                }

                if (!(target.getGameMode() == GameMode.SPECTATOR || ghosts.contains(target.getUniqueId()))) {
                    reviver.sendMessage(ChatColor.RED + msg("revival-interrupted-not-dead"));
                    target.sendMessage(ChatColor.RED + msg("revival-interrupted-target-left"));
                    stopSoulSandAuraAnimation(reviver);
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

                adjustMaxHealth(reviver, healthChangeReviver);
                adjustMaxHealth(target, healthChangeTarget);

                target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 1));

                spawnRevivalParticles(target);
                recordSuccessfulRevive(reviver.getUniqueId(), target.getUniqueId());

                double newChance = getFailureChance(reviver.getUniqueId(), target.getUniqueId());
                reviver.sendMessage(ChatColor.GREEN + msg("revival-success", target.getName(), newChance * 100));
                target.sendMessage(ChatColor.GREEN + msg("revival-success-target", reviver.getName()));

                stopSoulSandAuraAnimation(reviver);
            }
        }.runTaskLater(this, revivalAnimationDelayNormal * 20L);
    }

    private void forceRevivePlayer(CommandSender sender, Player target) {
        Player reviver = sender instanceof Player ? (Player) sender : null;

        if (reviver != null) {
            reviver.sendMessage(ChatColor.YELLOW + msg("force-revival-start"));
            startSoulSandAuraAnimation(reviver, revivalAnimationDelayForce);
        }

        target.sendMessage(ChatColor.YELLOW + msg("force-revival-start-target"));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isOnline()) {
                    if (reviver != null && reviver.isOnline()) {
                        reviver.sendMessage(ChatColor.RED + msg("force-revival-interrupted"));
                        stopSoulSandAuraAnimation(reviver);
                    }
                    return;
                }

                exitGhost(target);
                target.setGameMode(GameMode.SURVIVAL);

                if (reviver != null) {
                    target.teleport(reviver);
                    stopSoulSandAuraAnimation(reviver);
                } else {
                    target.teleport(target.getWorld().getSpawnLocation());
                }

                if (target.getHealth() <= 0.0) {
                    target.setHealth(Math.min(4.0, target.getMaxHealth()));
                }

                target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 1));
                spawnRevivalParticles(target);

                if (reviver == null && sender != null) {
                    sender.sendMessage(ChatColor.GREEN + msg("necroforce-revived", target.getName()));
                }

                target.sendMessage(ChatColor.GREEN + msg("force-revival-success-target"));
            }
        }.runTaskLater(this, revivalAnimationDelayForce * 20L);
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
            player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, x, y, z, 1, 0, 0, 0, 0);
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

    // ========== EVENT HANDLERS ==========

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
            if (!p.hasPermission("hardcorenecro.ghost.gamemode")) {
                p.sendMessage(ChatColor.RED + msg("ghost-no-permission"));
                e.setCancelled(true);
                return;
            }
            if (!ghosts.contains(p.getUniqueId())) {
                enterGhost(p);
                p.sendMessage(ChatColor.GRAY + msg("ghost-manual-switch"));
            } else {
                exitGhost(p);
                p.sendMessage(ChatColor.GREEN + msg("ghost-manual-exit"));
            }
            e.setCancelled(true);
        }
    }
}