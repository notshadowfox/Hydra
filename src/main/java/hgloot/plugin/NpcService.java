package hgloot.plugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import hgloot.plugin.util.HydraIcons;

@SuppressWarnings("deprecation")
public final class NpcService implements Listener {
    private static final String CONFIG_ROOT = "npcs";
    private static final String LEGACY_CONFIG_ROOT = "npcs";
    private static final String CLICK_LEFT = "left";
    private static final String CLICK_RIGHT = "right";
    private static final String CLICK_BOTH = "both";
    private static final String HIDDEN_NAME_TEAM = "hydra_npc_hide";
    private static final long CLICK_COOLDOWN_MILLIS = 650L;
    private static final long DIALOGUE_LINE_DELAY_TICKS = 40L;
    private static final double NPC_SHOW_RANGE_SQUARED = 48.0D * 48.0D;
    private static final double NPC_HIDE_RANGE_SQUARED = NPC_SHOW_RANGE_SQUARED;
    private final JavaPlugin plugin;
    private final NpcDialogueService npcDialogueService;
    private final DailyRewardService dailyRewardService;
    private final File npcFile;
    private FileConfiguration npcConfig;
    private final NamespacedKey npcMarkerKey;
    private final NamespacedKey npcLabelKey;
    private final NamespacedKey npcIdKey;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, String> npcIdsByUuid = new HashMap<>();
    private final Map<String, SpawnedNpc> spawnedNpcs = new LinkedHashMap<>();
    private final Map<String, List<SkinPropertyData>> skinPropertyCache = new LinkedHashMap<>();
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> dialogueTasks = new HashMap<>();
    private final Map<UUID, Set<UUID>> visibleNpcEntitiesByViewer = new HashMap<>();
    private final Set<String> pendingSkinLookups = new HashSet<>();
    private final NpcNmsBridge bridge;
    private BukkitTask hiddenNameRefreshTask;
    private BukkitTask leaderboardSkinRefreshTask;
    private BukkitTask validationTask;

    public NpcService(
        JavaPlugin plugin,
        NpcDialogueService npcDialogueService,
        DailyRewardService dailyRewardService
    ) {
        this.plugin = plugin;
        this.npcDialogueService = npcDialogueService;
        this.dailyRewardService = dailyRewardService;
        this.npcFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcMarkerKey = new NamespacedKey(plugin, "hydra_npc_marker");
        this.npcLabelKey = new NamespacedKey(plugin, "hydra_npc_label");
        this.npcIdKey = new NamespacedKey(plugin, "hydra_npc_id");
        this.bridge = NpcNmsBridge.tryCreate(plugin);
    }

    public void start() {
        loadDefinitions();
        purgeExistingNpcEntities();
        respawnAll();
        startHiddenNameRefresh();
        startLeaderboardSkinRefresh();
        startValidationTask();
    }

    public void shutdown() {
        for (BukkitTask task : dialogueTasks.values()) {
            task.cancel();
        }
        dialogueTasks.clear();
        visibleNpcEntitiesByViewer.clear();
        if (hiddenNameRefreshTask != null) {
            hiddenNameRefreshTask.cancel();
            hiddenNameRefreshTask = null;
        }
        if (leaderboardSkinRefreshTask != null) {
            leaderboardSkinRefreshTask.cancel();
            leaderboardSkinRefreshTask = null;
        }
        if (validationTask != null) {
            validationTask.cancel();
            validationTask = null;
        }
        removeSpawnedEntities();
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> handleHelp(sender, args);
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "teleport", "tp" -> handleTeleport(sender, args);
            case "movehere", "move" -> handleMove(sender, args);
            case "set" -> handleSet(sender, args);
            case "action" -> handleAction(sender, args);
            case "dialogue" -> handleDialogueCommand(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);

            // Hidden compatibility aliases for one release.
            case "skin" -> handleSkin(sender, args);
            case "title" -> handleTitle(sender, args);
            case "subtitle" -> handleSubtitle(sender, args);
            case "prompt" -> handlePrompt(sender, args);
            case "linktext" -> handleLinkText(sender, args);
            case "command" -> handleCommandEdit(sender, args);
            case "leaderboard" -> handleLeaderboardSkin(sender, args);
            case "refreshall" -> handleRefreshAll(sender);
            case "lookat" -> handleLookAt(sender, args);
            case "name" -> handleLegacyName(sender, args);
            default -> isLegacyPositionalCreate(args) ? handleLegacyCreate(sender, args) : handleUnknownSubcommand(sender, args[0]);
        };
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return completeToken(args[0], List.of(
                "create", "delete", "list", "info", "teleport", "movehere", "set", "action", "dialogue", "refresh", "reload", "help"
            ));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("dialogue")) {
            return npcDialogueService.complete(java.util.Arrays.copyOfRange(args, 1, args.length), npcIdSuggestions());
        }
        if (args.length == 2 && List.of("delete", "info", "teleport", "movehere", "set", "action", "refresh").contains(sub)) {
            return completeToken(args[1], npcIdSuggestions());
        }
        if (args.length == 2 && sub.equals("help")) {
            return completeToken(args[1], List.of("action"));
        }
        if (args.length == 3 && sub.equals("delete")) {
            return completeToken(args[2], List.of("confirm"));
        }
        if (args.length == 3 && sub.equals("set")) {
            return completeToken(args[2], List.of("title", "subtitle", "prompt", "skin", "lookat", "leaderboard-skin", "daily"));
        }
        if (args.length == 4 && sub.equals("set") && args[2].equalsIgnoreCase("daily")) {
            return completeToken(args[3], List.of("add", "remove", "clear"));
        }
        if (args.length == 5 && sub.equals("set") && args[2].equalsIgnoreCase("daily")
            && (args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove"))) {
            return completeToken(args[4], List.of("shards", "fragments", "xp", "coins"));
        }
        if (args.length == 4 && sub.equals("set") && args[2].equalsIgnoreCase("lookat")) {
            return completeToken(args[3], List.of("on", "off"));
        }
        if (args.length == 4 && sub.equals("set") && args[2].equalsIgnoreCase("leaderboard-skin")) {
            return completeToken(args[3], List.of("wins-total", "wins-hg", "wins-br", "kills", "level", "coins", "off"));
        }
        if (args.length == 3 && sub.equals("action")) {
            return completeToken(args[2], List.of(CLICK_LEFT, CLICK_RIGHT, CLICK_BOTH));
        }
        if (args.length == 4 && sub.equals("action")) {
            return completeToken(args[3], List.of("player", "console", "link", "dialogue", "clear"));
        }
        if (args.length == 4 && sub.equals("set") && args[2].equalsIgnoreCase("skin")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return completeToken(args[3], names);
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isNpcBodyEntity(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        NpcDefinition definition = definitionForEntity(event.getEntity());
        if (definition != null && clickAllowed(player)) {
            executeNpcCommand(player, definition, definition.commandFor(CLICK_LEFT));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcRightClick(PlayerInteractEntityEvent event) {
        if (!isNpcBodyEntity(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        NpcDefinition definition = definitionForEntity(event.getRightClicked());
        if (definition != null && clickAllowed(event.getPlayer())) {
            executeNpcCommand(event.getPlayer(), definition, definition.commandFor(CLICK_RIGHT));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        visibleNpcEntitiesByViewer.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            syncNpcsForViewer(event.getPlayer());
        }, 2L);
        // Retry after login initialization. Per-viewer tracking makes this a no-op
        // unless the first packet sync did not complete.
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncNpcsForViewer(event.getPlayer()), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncNpcsForViewer(event.getPlayer()), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelNpcDialogue(event.getPlayer().getUniqueId());
        clickCooldowns.remove(event.getPlayer().getUniqueId());
        visibleNpcEntitiesByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.AQUA + "Hydra NPC Commands");
        sender.sendMessage(ChatColor.GRAY + "/npc create <id> <name>");
        sender.sendMessage(ChatColor.GRAY + "/npc delete <id> confirm");
        sender.sendMessage(ChatColor.GRAY + "/npc list " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "/npc info <id>");
        sender.sendMessage(ChatColor.GRAY + "/npc teleport <id> " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "/npc movehere <id>");
        sender.sendMessage(ChatColor.GRAY + "/npc set <id> <property> <value>");
        sender.sendMessage(ChatColor.GRAY + "/npc set <id> daily <add|remove|clear> [shards|fragments|xp|coins] [amount]");
        sender.sendMessage(ChatColor.GRAY + "/npc action <id> <click> <type> <value>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue <create|delete|title|text|button|assign|preview|list> ...");
        sender.sendMessage(ChatColor.GRAY + "/npc refresh <id> " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "/npc reload");
        sender.sendMessage(ChatColor.DARK_GRAY + "Use /npc help action for action examples.");
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("action")) {
            sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.AQUA + "NPC Actions");
            sender.sendMessage(ChatColor.GRAY + "/npc action <id> <left|right|both> player <command>");
            sender.sendMessage(ChatColor.GRAY + "/npc action <id> <left|right|both> console <command>");
            sender.sendMessage(ChatColor.GRAY + "/npc action <id> <left|right|both> link <url> [label]");
            sender.sendMessage(ChatColor.GRAY + "/npc action <id> <left|right|both> dialogue <line one | line two>");
            sender.sendMessage(ChatColor.GRAY + "/npc action <id> <left|right|both> clear");
            return true;
        }
        sendUsage(sender);
        return true;
    }

    private boolean handleUnknownSubcommand(CommandSender sender, String subcommand) {
        sender.sendMessage(ChatColor.RED + "Unknown NPC command '" + subcommand + "'. Use /npc help.");
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create NPCs.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc create <id> <name>");
            return true;
        }
        String id = normalizeNpcId(args[1]);
        if (id.isBlank()) {
            sender.sendMessage(ChatColor.RED + "NPC id must contain letters, numbers, underscores, or dashes.");
            return true;
        }
        if (definitions.containsKey(id)) {
            sender.sendMessage(ChatColor.RED + "An NPC with that id already exists.");
            return true;
        }

        Location location = player.getLocation().clone();
        String title = color(joinText(args, 2));
        NpcDefinition definition = new NpcDefinition(
            id,
            UUID.randomUUID(),
            location,
            player.getName(),
            title,
            "",
            "",
            "",
            "",
            "",
            "",
            false
        );
        definitions.put(id, definition);
        npcIdsByUuid.put(definition.uuid(), id);
        saveDefinitions();
        spawnNpc(definition);
        sender.sendMessage(ChatColor.GREEN + "Created NPC " + ChatColor.WHITE + id + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleLegacyCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create NPCs.");
            return true;
        }
        if (args.length < 5) {
            sendUsage(sender);
            return true;
        }
        List<String> parsed = parseQuotedArguments(args, 3);
        if (parsed.size() < 2) {
            sender.sendMessage(ChatColor.RED + "Legacy usage: /npc <x> <y> <z> \"<name>\" \"<command>\"");
            return true;
        }

        Location base = player.getLocation();
        Double x = resolveCoordinate(player, args[0], base.getX());
        Double y = resolveCoordinate(player, args[1], base.getY());
        Double z = resolveCoordinate(player, args[2], base.getZ());
        if (x == null || y == null || z == null) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates.");
            return true;
        }

        String title = color(parsed.get(0));
        String id = uniqueNpcId(slugFromTitle(title));
        String command = normalizeCommandMode(parsed.get(1));
        Location location = new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), 0.0F);
        NpcDefinition definition = new NpcDefinition(
            id,
            UUID.randomUUID(),
            location,
            player.getName(),
            title,
            "",
            "",
            command,
            command,
            "",
            "",
            false
        );
        definitions.put(id, definition);
        npcIdsByUuid.put(definition.uuid(), id);
        saveDefinitions();
        spawnNpc(definition);
        sender.sendMessage(ChatColor.YELLOW + "Created legacy NPC as " + ChatColor.WHITE + id + ChatColor.YELLOW + ".");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (definitions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No NPCs are configured.");
            return true;
        }
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.AQUA + "Hydra NPCs");
        for (NpcDefinition definition : definitions.values()) {
            Location location = definition.location();
            sender.sendMessage(ChatColor.WHITE + definition.id()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + stripColor(definition.title())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + location.getWorld().getName()
                + ChatColor.GRAY + " @ "
                + ChatColor.WHITE + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ());
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            String id = args.length >= 2 ? args[1] : "<id>";
            sender.sendMessage(ChatColor.RED + "Deletion is permanent. Confirm with /npc delete " + id + " confirm");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        definitions.remove(definition.id());
        npcIdsByUuid.remove(definition.uuid());
        removeNpc(definition.id());
        saveDefinitions();
        sender.sendMessage(ChatColor.GREEN + "Removed NPC " + ChatColor.WHITE + definition.id() + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can teleport to NPCs.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc tp <id>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        Runnable travel = () -> {
            if (player.teleport(definition.location())) {
                sender.sendMessage(ChatColor.GREEN + "Teleported to NPC " + ChatColor.WHITE + definition.id() + ChatColor.GREEN + ".");
            }
        };
        if (plugin instanceof HGLootPlugin hydra) hydra.transitionTravel(player, travel);
        else travel.run();
        return true;
    }

    private boolean handleMove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can move NPCs.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc move <id>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        NpcDefinition updated = definition.withLocation(player.getLocation().clone());
        definitions.put(updated.id(), updated);
        saveDefinitions();
        spawnNpc(updated);
        sender.sendMessage(ChatColor.GREEN + "Moved NPC " + ChatColor.WHITE + updated.id() + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc set <id> <title|subtitle|prompt|skin|lookat|leaderboard-skin|daily> <value>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        String property = args[2].toLowerCase(Locale.ROOT);
        String value = joinText(args, 3);
        return switch (property) {
            case "title" -> updateText(sender, definition.id(), DefinitionTextField.TITLE, value, "title");
            case "subtitle" -> updateText(sender, definition.id(), DefinitionTextField.SUBTITLE,
                value.equalsIgnoreCase("clear") ? "" : value, "subtitle");
            case "prompt" -> updateText(sender, definition.id(), DefinitionTextField.PROMPT,
                value.equalsIgnoreCase("clear") ? "" : value, "prompt");
            case "skin" -> updateSkin(sender, definition, args[3]);
            case "lookat" -> updateLookAt(sender, definition, value);
            case "leaderboard-skin" -> updateLeaderboardSkin(sender, definition, value);
            case "daily" -> updateDailyReward(sender, definition, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown NPC property. Use title, subtitle, prompt, skin, lookat, leaderboard-skin, or daily.");
                yield true;
            }
        };
    }

    private boolean updateDailyReward(CommandSender sender, NpcDefinition definition, String[] args) {
        if (args.length == 4 && args[3].equalsIgnoreCase("clear")) {
            saveDailyReward(definition, DailyRewardService.RewardBundle.empty());
            sender.sendMessage(ChatColor.GREEN + "Cleared daily rewards for NPC " + ChatColor.WHITE + definition.id() + ChatColor.GREEN + ".");
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc set <id> daily <add|remove|clear> [shards|fragments|xp|coins] [amount]");
            return true;
        }

        DailyRewardService.RewardBundle current;
        try {
            current = dailyRewardBundle(definition);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.RED + "The existing Daily Reward configuration is invalid. Clear it before editing.");
            return true;
        }

        String operation = args[3].toLowerCase(Locale.ROOT);
        if (operation.equals("remove")) {
            try {
                DailyRewardService.RewardBundle updated = dailyRewardService.remove(current, args[4]);
                saveDailyReward(definition, updated);
                sender.sendMessage(ChatColor.GREEN + "Removed " + ChatColor.WHITE + args[4]
                    + ChatColor.GREEN + " from NPC " + ChatColor.WHITE + definition.id() + ChatColor.GREEN + ".");
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(ChatColor.RED + "Daily reward must be shards, fragments, xp, or Coins.");
            }
            return true;
        }

        boolean explicitAdd = operation.equals("add");
        String rewardType = explicitAdd ? args[4] : args[3];
        int amountIndex = explicitAdd ? 5 : 4;
        if (args.length <= amountIndex) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc set <id> daily add <shards|fragments|xp|coins> <amount>");
            return true;
        }
        try {
            int amount = Integer.parseInt(args[amountIndex]);
            DailyRewardService.RewardBundle updated = dailyRewardService.addOrReplace(current, rewardType, amount);
            saveDailyReward(definition, updated);
            sender.sendMessage(ChatColor.GREEN + "Set NPC " + ChatColor.WHITE + definition.id() + ChatColor.GREEN
                + " Daily Rewards to " + ChatColor.WHITE + updated.describe() + ChatColor.GREEN + ".");
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Daily reward amount must be a whole number.");
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.RED + "Daily reward must be shards, fragments, xp, or Coins with an amount between 1 and "
                + DailyRewardService.MAX_REWARD_AMOUNT + ".");
        }
        return true;
    }

    private DailyRewardService.RewardBundle dailyRewardBundle(NpcDefinition definition) {
        String action = definition.leftCommand().regionMatches(true, 0, "daily:", 0, "daily:".length())
            ? definition.leftCommand()
            : definition.rightCommand();
        if (action == null || !action.regionMatches(true, 0, "daily:", 0, "daily:".length())) {
            return DailyRewardService.RewardBundle.empty();
        }
        return dailyRewardService.parse(action);
    }

    private void saveDailyReward(NpcDefinition definition, DailyRewardService.RewardBundle bundle) {
        String action = bundle == null || bundle.isEmpty() ? "" : "daily:" + bundle.encode();
        NpcDefinition updated = definition.withCommands(action, action);
        definitions.put(updated.id(), updated);
        saveDefinitions();
    }

    private boolean updateSkin(CommandSender sender, NpcDefinition definition, String skinOwner) {
        NpcDefinition updated = definition.withSkinOwner(skinOwner);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        spawnNpc(updated);
        sender.sendMessage(ChatColor.GREEN + "Updated NPC skin to " + ChatColor.WHITE + skinOwner + ChatColor.GREEN + ".");
        return true;
    }

    private boolean updateLookAt(CommandSender sender, NpcDefinition definition, String rawValue) {
        boolean enabled;
        if (rawValue.equalsIgnoreCase("on") || rawValue.equalsIgnoreCase("true")) {
            enabled = true;
        } else if (rawValue.equalsIgnoreCase("off") || rawValue.equalsIgnoreCase("false")) {
            enabled = false;
        } else {
            sender.sendMessage(ChatColor.RED + "Look-at must be on or off.");
            return true;
        }
        NpcDefinition updated = definition.withLookAt(enabled);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        sender.sendMessage(ChatColor.GREEN + "Updated NPC look-at behavior to " + ChatColor.WHITE
            + (enabled ? "on" : "off") + ChatColor.GREEN + ".");
        return true;
    }

    private boolean updateLeaderboardSkin(CommandSender sender, NpcDefinition definition, String rawType) {
        String typeKey = switch (rawType.trim().toLowerCase(Locale.ROOT)) {
            case "off", "none", "clear" -> "";
            case "wins-total", "total-wins", "wins" -> QueueService.DebugLeaderboardType.WINS_TOTAL.key();
            case "wins-hg", "hg-wins" -> QueueService.DebugLeaderboardType.WINS_HG.key();
            case "wins-br", "br-wins" -> QueueService.DebugLeaderboardType.WINS_BR.key();
            case "kills" -> QueueService.DebugLeaderboardType.KILLS.key();
            case "level", "levels", "xp" -> QueueService.DebugLeaderboardType.LEVEL.key();
            case "coins", "coin", "gold", "silver", "money", "balance" -> QueueService.DebugLeaderboardType.MONEY.key();
            default -> null;
        };
        if (typeKey == null) {
            sender.sendMessage(ChatColor.RED + "Leaderboard skin must be wins-total, wins-hg, wins-br, kills, level, Coins, or off.");
            return true;
        }
        NpcDefinition updated = definition.withLeaderboardSkinType(typeKey);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        if (typeKey.isBlank()) {
            sender.sendMessage(ChatColor.GREEN + "Disabled leaderboard skin syncing for NPC " + ChatColor.WHITE + updated.id() + ChatColor.GREEN + ".");
        } else {
            refreshLeaderboardSkin(updated.id());
            sender.sendMessage(ChatColor.GREEN + "Updated NPC leaderboard skin to " + ChatColor.WHITE + rawType + ChatColor.GREEN + ".");
        }
        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc action <id> <left|right|both> <player|console|link|dialogue|clear> [value]");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        String click = args[2].toLowerCase(Locale.ROOT);
        if (!click.equals(CLICK_LEFT) && !click.equals(CLICK_RIGHT) && !click.equals(CLICK_BOTH)) {
            sender.sendMessage(ChatColor.RED + "Click must be left, right, or both.");
            return true;
        }
        String actionType = args[3].toLowerCase(Locale.ROOT);
        String storedAction;
        NpcDefinition updated = definition;
        switch (actionType) {
            case "clear" -> storedAction = "";
            case "player", "console", "dialogue" -> {
                if (args.length < 5 || joinText(args, 4).isBlank()) {
                    sender.sendMessage(ChatColor.RED + "This action requires a value. Use /npc help action.");
                    return true;
                }
                String prefix = actionType.equals("dialogue") ? "say:" : actionType + ":";
                storedAction = prefix + joinText(args, 4).trim();
            }
            case "link" -> {
                if (args.length < 5 || args[4].isBlank()) {
                    sender.sendMessage(ChatColor.RED + "Link actions require a URL.");
                    return true;
                }
                storedAction = "url:" + args[4].trim();
                if (args.length > 5) {
                    updated = updated.withLinkText(color(joinText(args, 5)));
                }
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Action type must be player, console, link, dialogue, or clear.");
                return true;
            }
        }
        updated = withNpcAction(updated, click, storedAction);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        sender.sendMessage(ChatColor.GREEN + (actionType.equals("clear") ? "Cleared" : "Updated")
            + " NPC " + click + " click action.");
        return true;
    }

    private NpcDefinition withNpcAction(NpcDefinition definition, String click, String action) {
        return switch (click) {
            case CLICK_LEFT -> definition.withLeftCommand(action);
            case CLICK_RIGHT -> definition.withRightCommand(action);
            default -> definition.withCommands(action, action);
        };
    }

    private boolean handleSkin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc skin <id> <player>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }

        return updateSkin(sender, definition, args[2]);
    }

    private boolean handleTitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc title <id> <text>");
            return true;
        }
        return updateText(sender, args[1], DefinitionTextField.TITLE, joinText(args, 2), "title");
    }

    private boolean handleSubtitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc subtitle <id> <text>");
            return true;
        }
        return updateText(sender, args[1], DefinitionTextField.SUBTITLE, joinText(args, 2), "subtitle");
    }

    private boolean handlePrompt(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc prompt <id> <text>");
            return true;
        }
        return updateText(sender, args[1], DefinitionTextField.PROMPT, joinText(args, 2), "prompt");
    }

    private boolean handleLinkText(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc linktext <id> <text>");
            return true;
        }
        return updateText(sender, args[1], DefinitionTextField.LINK_TEXT, joinText(args, 2), "link text");
    }

    private boolean handleLegacyName(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc title <id> <text>");
            return true;
        }
        return updateText(sender, args[1], DefinitionTextField.TITLE, joinText(args, 2), "title");
    }

    private boolean updateText(CommandSender sender, String idToken, DefinitionTextField field, String value, String label) {
        NpcDefinition definition = resolveNpc(idToken);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        NpcDefinition updated = switch (field) {
            case TITLE -> definition.withTitle(color(value));
            case SUBTITLE -> definition.withSubtitle(color(value));
            case PROMPT -> definition.withPrompt(color(value));
            case LINK_TEXT -> definition.withLinkText(color(value));
        };
        definitions.put(updated.id(), updated);
        saveDefinitions();
        spawnNpc(updated);
        sender.sendMessage(ChatColor.GREEN + "Updated NPC " + label + ".");
        return true;
    }

    private boolean handleCommandEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc command <id> <left|right|both> <player:|console:|url:|say:><action>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        String click = args[2].toLowerCase(Locale.ROOT);
        if (!click.equals(CLICK_LEFT) && !click.equals(CLICK_RIGHT) && !click.equals(CLICK_BOTH)) {
            sender.sendMessage(ChatColor.RED + "Click must be left, right, or both.");
            return true;
        }
        String command = normalizeCommandMode(joinText(args, 3));
        if (command.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Command cannot be blank.");
            return true;
        }

        NpcDefinition updated = switch (click) {
            case CLICK_LEFT -> definition.withLeftCommand(command);
            case CLICK_RIGHT -> definition.withRightCommand(command);
            default -> definition.withCommands(command, command);
        };
        definitions.put(updated.id(), updated);
        saveDefinitions();
        sender.sendMessage(ChatColor.GREEN + "Updated NPC " + click + " click command.");
        return true;
    }

    private boolean handleLeaderboardSkin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc leaderboard <id> <wins total|wins hg|wins br|kills|level|coins|off>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        String typeKey = resolveLeaderboardSkinType(args, 2);
        if (typeKey == null) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc leaderboard <id> <wins total|wins hg|wins br|kills|level|coins|off>");
            return true;
        }
        NpcDefinition updated = definition.withLeaderboardSkinType(typeKey);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        if (typeKey.isBlank()) {
            sender.sendMessage(ChatColor.GREEN + "Disabled leaderboard skin syncing for NPC " + ChatColor.WHITE + updated.id() + ChatColor.GREEN + ".");
            return true;
        }
        refreshLeaderboardSkin(updated.id());
        sender.sendMessage(ChatColor.GREEN + "NPC " + ChatColor.WHITE + updated.id()
            + ChatColor.GREEN + " will use the current #1 " + ChatColor.WHITE + typeKey + ChatColor.GREEN + " skin.");
        return true;
    }

    private boolean handleRefresh(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc refresh <id>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        refreshNpcDisplay(definition.id(), true);
        sender.sendMessage(ChatColor.GREEN + "Refreshed NPC " + ChatColor.WHITE + definition.id() + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleRefreshAll(CommandSender sender) {
        refreshAllNpcDisplays(true);
        sender.sendMessage(ChatColor.GREEN + "Refreshed all NPC displays.");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc info <id>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        Location location = definition.location();
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.WHITE + definition.id());
        sender.sendMessage(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + definition.uuid());
        sender.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.WHITE + location.getWorld().getName()
            + " " + format(location.getX()) + " " + format(location.getY()) + " " + format(location.getZ())
            + " yaw=" + format(location.getYaw()) + " pitch=" + format(location.getPitch()));
        sender.sendMessage(ChatColor.GRAY + "Skin: " + ChatColor.WHITE + definition.skinOwner());
        sender.sendMessage(ChatColor.GRAY + "Title: " + ChatColor.WHITE + definition.title());
        sender.sendMessage(ChatColor.GRAY + "Subtitle: " + ChatColor.WHITE + definition.subtitle());
        sender.sendMessage(ChatColor.GRAY + "Prompt: " + ChatColor.WHITE + definition.prompt());
        sender.sendMessage(ChatColor.GRAY + "Link text: " + ChatColor.WHITE
            + (definition.linkText().isBlank() ? "default" : definition.linkText()));
        sender.sendMessage(ChatColor.GRAY + "Left: " + ChatColor.WHITE + definition.leftCommand());
        sender.sendMessage(ChatColor.GRAY + "Right: " + ChatColor.WHITE + definition.rightCommand());
        sender.sendMessage(ChatColor.GRAY + "Leaderboard skin: " + ChatColor.WHITE
            + (definition.leaderboardSkinType().isBlank() ? "off" : definition.leaderboardSkinType()));
        sender.sendMessage(ChatColor.GRAY + "Look-at: " + ChatColor.WHITE + definition.lookAt());
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        loadDefinitions();
        purgeExistingNpcEntities();
        respawnAll();
        sender.sendMessage(ChatColor.GREEN + "Reloaded NPCs from npcs.yml.");
        return true;
    }

    private boolean handleLookAt(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc lookat <id> <true|false>");
            return true;
        }
        NpcDefinition definition = resolveNpc(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        boolean value = Boolean.parseBoolean(args[2]);
        NpcDefinition updated = definition.withLookAt(value);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        sender.sendMessage(ChatColor.GREEN + "Updated NPC look-at behavior to " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
        return true;
    }

    private void executeNpcCommand(Player player, NpcDefinition definition, String command) {
        if (player == null || !player.isOnline() || command == null || command.isBlank()) {
            return;
        }
        String resolved = command.replace("{player}", player.getName()).trim();
        CommandMode mode = CommandMode.PLAYER;
        if (resolved.regionMatches(true, 0, "console:", 0, "console:".length())) {
            mode = CommandMode.CONSOLE;
            resolved = resolved.substring("console:".length()).trim();
        } else if (resolved.regionMatches(true, 0, "url:", 0, "url:".length())) {
            sendNpcLink(player, resolved.substring("url:".length()).trim(), definition == null ? "" : definition.linkText());
            return;
        } else if (resolved.regionMatches(true, 0, "say:", 0, "say:".length())) {
            sendNpcDialogue(player, definition, resolved.substring("say:".length()).trim());
            return;
        } else if (resolved.regionMatches(true, 0, "open-dialogue:", 0, "open-dialogue:".length())) {
            npcDialogueService.open(player, resolved.substring("open-dialogue:".length()).trim());
            return;
        } else if (resolved.regionMatches(true, 0, "daily:", 0, "daily:".length())) {
            dailyRewardService.claim(player, definition == null ? "unknown" : definition.id(),
                resolved.substring("daily:".length()).trim());
            return;
        } else if (resolved.regionMatches(true, 0, "player:", 0, "player:".length())) {
            resolved = resolved.substring("player:".length()).trim();
        }
        if (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }
        if (resolved.isBlank()) {
            return;
        }
        if (mode == CommandMode.CONSOLE) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        } else {
            Bukkit.dispatchCommand(player, resolved);
        }
    }

    private boolean assignDialogue(CommandSender sender, String npcId, String dialogueId) {
        NpcDefinition definition = resolveNpc(npcId);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "Unknown NPC id.");
            return true;
        }
        if (!npcDialogueService.exists(dialogueId)) {
            sender.sendMessage(ChatColor.RED + "Unknown dialogue id.");
            return true;
        }
        String action = "open-dialogue:" + dialogueId;
        NpcDefinition updated = definition.withCommands(action, action);
        definitions.put(updated.id(), updated);
        saveDefinitions();
        sender.sendMessage(ChatColor.GREEN + "Assigned dialogue " + ChatColor.WHITE + dialogueId
            + ChatColor.GREEN + " to both click actions for NPC " + ChatColor.WHITE + updated.id() + ChatColor.GREEN + ".");
        return true;
    }

    private boolean handleDialogueCommand(CommandSender sender, String[] args) {
        return npcDialogueService.handleAdminCommand(sender, java.util.Arrays.copyOfRange(args, 1, args.length),
            new NpcDialogueService.NpcDialogueAssignment() {
                @Override
                public boolean assign(CommandSender commandSender, String npcId, String dialogueId) {
                    return assignDialogue(commandSender, npcId, dialogueId);
                }

                @Override
                public void clearAssignments(String dialogueId) {
                    clearDialogueAssignments(dialogueId);
                }
            });
    }

    private void clearDialogueAssignments(String dialogueId) {
        String action = "open-dialogue:" + dialogueId;
        boolean changed = false;
        for (NpcDefinition definition : new ArrayList<>(definitions.values())) {
            String left = action.equalsIgnoreCase(definition.leftCommand()) ? "" : definition.leftCommand();
            String right = action.equalsIgnoreCase(definition.rightCommand()) ? "" : definition.rightCommand();
            if (!left.equals(definition.leftCommand()) || !right.equals(definition.rightCommand())) {
                definitions.put(definition.id(), definition.withCommands(left, right));
                changed = true;
            }
        }
        if (changed) saveDefinitions();
    }

    private void sendNpcLink(Player player, String rawUrl, String linkText) {
        if (player == null || rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        String url = rawUrl.trim();
        if (!url.regionMatches(true, 0, "http://", 0, "http://".length())
            && !url.regionMatches(true, 0, "https://", 0, "https://".length())) {
            url = "https://" + url;
        }
        String label = linkText == null || linkText.isBlank() ? color("&bOpen Link") : linkText;
        String message = HydraIcons.EXCLAMATION + " " + label + " " + HydraIcons.EXCLAMATION;
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY))));
    }

    private void sendNpcDialogue(Player player, NpcDefinition definition, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : message.split("\\|")) {
            String line = rawLine.trim();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        cancelNpcDialogue(playerId);
        String speaker = definition == null || isBlank(definition.title())
            ? ChatColor.AQUA + "NPC"
            : definition.title();
        sendNpcDialogueLine(player, speaker, lines.get(0));
        if (lines.size() == 1) {
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            private int lineIndex = 1;

            @Override
            public void run() {
                if (!player.isOnline() || lineIndex >= lines.size()) {
                    dialogueTasks.remove(playerId);
                    cancel();
                    return;
                }
                sendNpcDialogueLine(player, speaker, lines.get(lineIndex++));
                if (lineIndex >= lines.size()) {
                    dialogueTasks.remove(playerId);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, DIALOGUE_LINE_DELAY_TICKS, DIALOGUE_LINE_DELAY_TICKS);
        dialogueTasks.put(playerId, task);
    }

    private void sendNpcDialogueLine(Player player, String speaker, String line) {
        player.sendMessage(speaker + ChatColor.DARK_GRAY + " > " + ChatColor.WHITE + color(line));
    }

    private void cancelNpcDialogue(UUID playerId) {
        BukkitTask task = dialogueTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean clickAllowed(Player player) {
        long now = System.currentTimeMillis();
        Long last = clickCooldowns.get(player.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MILLIS) {
            return false;
        }
        clickCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private void loadDefinitions() {
        definitions.clear();
        npcIdsByUuid.clear();
        ensureNpcFile();
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);
        loadDefinitionsFromNpcConfig();
        migrateLegacyDefinitions();
    }

    private void ensureNpcFile() {
        if (npcFile.exists()) {
            return;
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            npcFile.createNewFile();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create npcs.yml.", ex);
        }
    }

    private void loadDefinitionsFromNpcConfig() {
        ConfigurationSection section = npcConfig.getConfigurationSection(CONFIG_ROOT);
        if (section == null) {
            return;
        }
        for (String idKey : section.getKeys(false)) {
            ConfigurationSection npcSection = section.getConfigurationSection(idKey);
            if (npcSection == null) {
                continue;
            }
            NpcDefinition definition = readDefinition(idKey, npcSection);
            if (definition == null) {
                continue;
            }
            definitions.put(definition.id(), definition);
            npcIdsByUuid.put(definition.uuid(), definition.id());
            plugin.getLogger().info("[NPC] Loaded NPC " + definition.id()
                + " world=" + definition.location().getWorld().getName()
                + " leaderboardSkin=" + (definition.leaderboardSkinType().isBlank() ? "off" : definition.leaderboardSkinType()));
        }
    }

    private void migrateLegacyDefinitions() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(LEGACY_CONFIG_ROOT);
        if (section == null) {
            return;
        }
        boolean migratedAny = false;
        for (String key : section.getKeys(false)) {
            try {
                UUID npcUuid = UUID.fromString(key);
                if (npcIdsByUuid.containsKey(npcUuid)) {
                    continue;
                }
                ConfigurationSection npcSection = section.getConfigurationSection(key);
                if (npcSection == null) {
                    continue;
                }
                String worldName = npcSection.getString("world");
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) {
                    continue;
                }
                Location location = new Location(
                    world,
                    npcSection.getDouble("x"),
                    npcSection.getDouble("y"),
                    npcSection.getDouble("z"),
                    (float) npcSection.getDouble("yaw", 0.0D),
                    (float) npcSection.getDouble("pitch", 0.0D)
                );
                String oldName = npcSection.getString("name", color("&b&lNPC"));
                String id = uniqueNpcId(slugFromTitle(oldName));
                String leftCommand = normalizeCommandMode(npcSection.getString("left_command", ""));
                String rightCommand = normalizeCommandMode(npcSection.getString("right_command", leftCommand));
                NpcDefinition definition = new NpcDefinition(
                    id,
                    npcUuid,
                    location,
                    npcSection.getString("skin", ""),
                    oldName,
                    npcSection.getString("subtitle", ""),
                    npcSection.getString("prompt", ""),
                    leftCommand,
                    rightCommand,
                    "",
                    "",
                    npcSection.getBoolean("look_at", false)
                );
                definitions.put(definition.id(), definition);
                npcIdsByUuid.put(definition.uuid(), definition.id());
                migratedAny = true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (migratedAny) {
            saveDefinitions();
            plugin.getLogger().info("[NPC] Migrated legacy main-config NPC entries into npcs.yml. Legacy data was left intact.");
        }
    }

    private NpcDefinition readDefinition(String idKey, ConfigurationSection section) {
        String id = normalizeNpcId(section.getString("id", idKey));
        if (id.isBlank()) {
            return null;
        }
        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(section.getString("uuid", UUID.randomUUID().toString()));
        } catch (IllegalArgumentException ex) {
            uuid = UUID.randomUUID();
        }
        Location location = new Location(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw", 0.0D),
            (float) section.getDouble("pitch", 0.0D)
        );
        ConfigurationSection commands = section.getConfigurationSection("commands");
        String leftCommand = commands == null ? section.getString("left_command", "") : commands.getString(CLICK_LEFT, "");
        String rightCommand = commands == null ? section.getString("right_command", leftCommand) : commands.getString(CLICK_RIGHT, leftCommand);
        return new NpcDefinition(
            id,
            uuid,
            location,
            section.getString("skin_owner", section.getString("skin", "")),
            color(section.getString("title", section.getString("name", "&b&lNPC"))),
            color(section.getString("subtitle", "")),
            color(section.getString("prompt", "")),
            normalizeCommandMode(leftCommand),
            normalizeCommandMode(rightCommand),
            section.getString("leaderboard_skin", ""),
            color(section.getString("link_text", "")),
            section.getBoolean("look_at", false)
        );
    }

    private void saveDefinitions() {
        if (npcConfig == null) {
            npcConfig = new YamlConfiguration();
        }
        npcConfig.set(CONFIG_ROOT, null);
        for (NpcDefinition definition : definitions.values()) {
            String base = CONFIG_ROOT + "." + definition.id();
            Location location = definition.location();
            npcConfig.set(base + ".id", definition.id());
            npcConfig.set(base + ".uuid", definition.uuid().toString());
            npcConfig.set(base + ".world", location.getWorld().getName());
            npcConfig.set(base + ".x", location.getX());
            npcConfig.set(base + ".y", location.getY());
            npcConfig.set(base + ".z", location.getZ());
            npcConfig.set(base + ".yaw", location.getYaw());
            npcConfig.set(base + ".pitch", location.getPitch());
            npcConfig.set(base + ".skin_owner", definition.skinOwner());
            npcConfig.set(base + ".title", definition.title());
            npcConfig.set(base + ".subtitle", definition.subtitle());
            npcConfig.set(base + ".prompt", definition.prompt());
            npcConfig.set(base + ".link_text", definition.linkText());
            npcConfig.set(base + ".commands.left", definition.leftCommand());
            npcConfig.set(base + ".commands.right", definition.rightCommand());
            npcConfig.set(base + ".leaderboard_skin", definition.leaderboardSkinType());
            npcConfig.set(base + ".look_at", definition.lookAt());
            if (!npcConfig.isConfigurationSection(base + ".metadata")) {
                npcConfig.createSection(base + ".metadata");
            }
        }
        try {
            npcConfig.save(npcFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save npcs.yml.", ex);
        }
    }

    private void respawnAll() {
        removeSpawnedEntities();
        for (NpcDefinition definition : definitions.values()) {
            spawnNpc(definition);
        }
    }

    private void refreshAllNpcDisplays(boolean refreshLeaderboardSkins) {
        for (NpcDefinition definition : new ArrayList<>(definitions.values())) {
            refreshNpcDisplay(definition.id(), refreshLeaderboardSkins);
        }
    }

    private void refreshNpcDisplay(String npcId, boolean refreshLeaderboardSkin) {
        NpcDefinition definition = definitions.get(npcId);
        if (definition == null) {
            return;
        }
        if (refreshLeaderboardSkin && !definition.leaderboardSkinType().isBlank()) {
            refreshLeaderboardSkin(definition.id(), true);
            return;
        }
        plugin.getLogger().info("[NPC] Refreshing display for NPC " + definition.id() + ".");
        spawnNpc(definition);
    }

    private void spawnNpc(NpcDefinition definition) {
        removeNpc(definition.id());
        if (bridge == null) {
            spawnFallbackNpc(definition);
            return;
        }

        try {
            ensureHiddenNameForAllViewers(profileNameFor(definition.uuid()));
            NpcVisual visual = bridge.spawnNpc(definition.uuid(), definition.location().clone(), resolveSkinProperties(definition.skinOwner()));
            Entity bodyEntity = bridge.toBukkitEntity(visual.serverPlayer());
            if (bodyEntity == null) {
                throw new IllegalStateException("NPC body entity was not created.");
            }

            bodyEntity.setInvulnerable(true);
            bodyEntity.setGravity(false);
            bodyEntity.setSilent(true);
            bodyEntity.setPersistent(false);
            if (bodyEntity instanceof Player npcBody) {
                npcBody.setCollidable(false);
            }
            markNpcBody(bodyEntity, definition);

            List<UUID> labelIds = spawnHologramLines(definition);
            spawnedNpcs.put(definition.id(), new SpawnedNpc(definition.id(), bodyEntity.getUniqueId(), labelIds, visual));
            plugin.getLogger().info("[NPC] Body spawned for " + definition.id() + " using full-body fake player.");
            plugin.getLogger().info("[NPC] Display rebuilt for " + definition.id() + " lines=" + labelIds.size() + ".");

            refreshHiddenNameTeams();
            for (Player viewer : viewersIn(definition.location().getWorld())) {
                showNpcToViewer(viewer, visual);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                "Failed to spawn full-body NPC " + definition.id() + "; falling back to lightweight stand display.",
                ex);
            spawnFallbackNpc(definition);
        }
    }

    private void spawnFallbackNpc(NpcDefinition definition) {
        Location location = definition.location().clone();
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCollidable(false);
        stand.setCustomNameVisible(false);
        markNpcBody(stand, definition);
        if (stand.getEquipment() != null) {
            ItemStack head = createNpcHead(definition.skinOwner());
            if (head != null) {
                stand.getEquipment().setHelmet(head);
            }
            stand.getEquipment().setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE));
            stand.getEquipment().setLeggings(dyedLeather(Material.LEATHER_LEGGINGS));
            stand.getEquipment().setBoots(dyedLeather(Material.LEATHER_BOOTS));
        }
        List<UUID> labelIds = spawnHologramLines(definition);
        spawnedNpcs.put(definition.id(), new SpawnedNpc(definition.id(), stand.getUniqueId(), labelIds, null));
        plugin.getLogger().info("[NPC] Body spawned for " + definition.id() + " using fallback display.");
        plugin.getLogger().info("[NPC] Display rebuilt for " + definition.id() + " lines=" + labelIds.size() + ".");
    }

    private void markNpcBody(Entity entity, NpcDefinition definition) {
        entity.getPersistentDataContainer().set(npcMarkerKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, definition.uuid().toString());
    }

    private List<UUID> spawnHologramLines(NpcDefinition definition) {
        List<String> lines = displayLines(definition);
        List<UUID> ids = new ArrayList<>();
        double baseHeight = 2.05D + ((Math.max(1, lines.size()) - 1) * 0.24D);
        for (int i = 0; i < lines.size(); i++) {
            try {
                Location lineLocation = definition.location().clone().add(0.0D, baseHeight - (i * 0.28D), 0.0D);
                TextDisplay display = (TextDisplay) lineLocation.getWorld().spawnEntity(lineLocation, EntityType.TEXT_DISPLAY);
                display.setText(lines.get(i));
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setShadowed(true);
                display.setSeeThrough(false);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.getPersistentDataContainer().set(npcLabelKey, PersistentDataType.BYTE, (byte) 1);
                display.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, definition.uuid().toString());
                ids.add(display.getUniqueId());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                    "[NPC] NPC hologram/text line failed to spawn for " + definition.id() + " line=" + i,
                    ex);
            }
        }
        return ids;
    }

    private List<String> displayLines(NpcDefinition definition) {
        List<String> lines = new ArrayList<>();
        if (!isBlank(definition.title())) {
            lines.add(definition.title());
        }
        if (!isBlank(definition.subtitle())) {
            lines.add(definition.subtitle());
        }
        if (!isBlank(definition.prompt())) {
            lines.add(definition.prompt());
        }
        return lines;
    }

    private ItemStack createNpcHead(String skinOwner) {
        if (skinOwner == null || skinOwner.isBlank()) {
            return null;
        }
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(skinOwner));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack dyedLeather(Material material) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(0, 188, 212));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<SkinPropertyData> resolveSkinProperties(String skinOwner) {
        if (skinOwner == null || skinOwner.isBlank()) {
            return List.of();
        }
        String cacheKey = normalizeSkinOwner(skinOwner);
        List<SkinPropertyData> cached = skinPropertyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        PlayerProfile profile = sourceProfileFor(skinOwner);
        List<SkinPropertyData> properties = extractProperties(profile);
        if (!properties.isEmpty()) {
            skinPropertyCache.put(cacheKey, properties);
            return properties;
        }

        requestSkinRefresh(cacheKey, skinOwner);
        return List.of();
    }

    private PlayerProfile sourceProfileFor(String skinOwner) {
        Player online = Bukkit.getPlayerExact(skinOwner);
        if (online != null) {
            PlayerProfile profile = online.getPlayerProfile().clone();
            profile.completeFromCache(true);
            return profile;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(skinOwner);
        PlayerProfile profile = offline.getPlayerProfile().clone();
        profile.completeFromCache(true);
        return profile;
    }

    private List<SkinPropertyData> extractProperties(PlayerProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<SkinPropertyData> properties = new ArrayList<>();
        for (ProfileProperty property : profile.getProperties()) {
            properties.add(new SkinPropertyData(property.getName(), property.getValue(), property.getSignature()));
        }
        return properties;
    }

    private void requestSkinRefresh(String cacheKey, String skinOwner) {
        if (!pendingSkinLookups.add(cacheKey)) {
            return;
        }
        PlayerProfile lookup = Bukkit.createProfile(skinOwner);
        lookup.update().whenComplete((updated, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingSkinLookups.remove(cacheKey);
            if (error != null) {
                plugin.getLogger().log(Level.FINE, "Failed to refresh NPC skin for " + skinOwner, error);
                return;
            }
            List<SkinPropertyData> properties = extractProperties(updated);
            if (properties.isEmpty()) {
                return;
            }
            skinPropertyCache.put(cacheKey, properties);
            refreshNpcsUsingSkinOwner(skinOwner);
        }));
    }

    private void refreshNpcsUsingSkinOwner(String skinOwner) {
        String cacheKey = normalizeSkinOwner(skinOwner);
        for (NpcDefinition definition : new ArrayList<>(definitions.values())) {
            if (normalizeSkinOwner(definition.skinOwner()).equals(cacheKey)) {
                spawnNpc(definition);
            }
        }
    }

    private void removeSpawnedEntities() {
        for (String npcId : new ArrayList<>(spawnedNpcs.keySet())) {
            removeNpc(npcId);
        }
    }

    private void removeNpc(String npcId) {
        SpawnedNpc spawnedNpc = spawnedNpcs.remove(npcId);
        if (spawnedNpc == null) {
            return;
        }

        if (spawnedNpc.visual() != null && bridge != null) {
            UUID visualId = spawnedNpc.visual().entityUuid();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                Set<UUID> visible = visibleNpcEntitiesByViewer.get(viewer.getUniqueId());
                if (visible == null || !visible.remove(visualId)) {
                    continue;
                }
                try {
                    bridge.sendRemove(viewer, spawnedNpc.visual());
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.FINE, "Failed to hide NPC " + npcId + " from " + viewer.getName(), ex);
                }
                if (visible.isEmpty()) {
                    visibleNpcEntitiesByViewer.remove(viewer.getUniqueId());
                }
            }
            removeHiddenNameEntry(spawnedNpc.visual().profileName());
            try {
                bridge.destroyNpc(spawnedNpc.visual());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.FINE, "Failed to destroy NPC " + npcId, ex);
            }
        }

        if (spawnedNpc.bodyEntityId() != null) {
            Entity body = Bukkit.getEntity(spawnedNpc.bodyEntityId());
            if (body != null) {
                body.remove();
            }
        }
        for (UUID labelId : spawnedNpc.labelEntityIds()) {
            Entity label = Bukkit.getEntity(labelId);
            if (label != null) {
                label.remove();
            }
        }
    }

    private void purgeExistingNpcEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (hasNpcKey(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private boolean hasNpcKey(Entity entity) {
        Byte marker = entity.getPersistentDataContainer().get(npcMarkerKey, PersistentDataType.BYTE);
        Byte label = entity.getPersistentDataContainer().get(npcLabelKey, PersistentDataType.BYTE);
        String npcId = entity.getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
        return (marker != null && marker == (byte) 1)
            || (label != null && label == (byte) 1)
            || (npcId != null && !npcId.isBlank());
    }

    public boolean isNpcEntity(Entity entity) {
        return entity != null && hasNpcKey(entity);
    }

    private boolean isNpcBodyEntity(Entity entity) {
        Byte marker = entity.getPersistentDataContainer().get(npcMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private NpcDefinition definitionForEntity(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(npcIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(raw);
            String id = npcIdsByUuid.get(uuid);
            return id == null ? null : definitions.get(id);
        } catch (IllegalArgumentException ignored) {
            return definitions.get(raw);
        }
    }

    private void syncNpcsForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || bridge == null) {
            return;
        }
        refreshHiddenNames(viewer);
        Set<UUID> visible = visibleNpcEntitiesByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>());
        Map<UUID, NpcVisual> currentVisuals = new HashMap<>();
        for (SpawnedNpc spawnedNpc : spawnedNpcs.values()) {
            NpcVisual visual = spawnedNpc.visual();
            if (visual == null) {
                continue;
            }
            currentVisuals.put(visual.entityUuid(), visual);
            if (viewer.getWorld().equals(visual.world())) {
                boolean currentlyVisible = visible.contains(visual.entityUuid());
                double rangeSquared = currentlyVisible ? NPC_HIDE_RANGE_SQUARED : NPC_SHOW_RANGE_SQUARED;
                if (viewer.getLocation().distanceSquared(visual.location()) <= rangeSquared) {
                    showNpcToViewer(viewer, visual);
                    continue;
                }
            }
        }
        for (UUID visibleId : new HashSet<>(visible)) {
            NpcVisual visual = currentVisuals.get(visibleId);
            if (visual != null && viewer.getWorld().equals(visual.world())
                && viewer.getLocation().distanceSquared(visual.location()) <= NPC_HIDE_RANGE_SQUARED) {
                continue;
            }
            visible.remove(visibleId);
            if (visual != null) {
                try {
                    bridge.sendRemove(viewer, visual);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.FINE, "Failed to clear stale NPC from " + viewer.getName(), ex);
                }
            }
        }
        if (visible.isEmpty()) {
            visibleNpcEntitiesByViewer.remove(viewer.getUniqueId());
        }
    }

    private void showNpcToViewer(Player viewer, NpcVisual visual) {
        if (viewer == null || !viewer.isOnline() || visual == null || bridge == null) {
            return;
        }
        Set<UUID> visible = visibleNpcEntitiesByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>());
        if (!visible.add(visual.entityUuid())) {
            return;
        }
        try {
            ensureHiddenNameEntry(viewer, visual.profileName());
            bridge.sendSpawn(viewer, visual);
        } catch (Exception ex) {
            visible.remove(visual.entityUuid());
            plugin.getLogger().log(Level.FINE, "Failed to show NPC " + visual.profileName() + " to " + viewer.getName(), ex);
        }
    }

    private List<Player> viewersIn(World world) {
        if (world == null) {
            return List.of();
        }
        List<Player> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (world.equals(player.getWorld())) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private void startHiddenNameRefresh() {
        if (hiddenNameRefreshTask != null) {
            hiddenNameRefreshTask.cancel();
        }
        hiddenNameRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshHiddenNameTeams, 20L, 20L);
    }

    private void startLeaderboardSkinRefresh() {
        if (leaderboardSkinRefreshTask != null) {
            leaderboardSkinRefreshTask.cancel();
        }
        leaderboardSkinRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboardSkins, 60L, 20L * 30L);
    }

    private void startValidationTask() {
        if (validationTask != null) {
            validationTask.cancel();
        }
        validationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::validateNpcDisplays, 20L, 20L);
    }

    private void validateNpcDisplays() {
        for (World world : Bukkit.getWorlds()) {
            validateWorldNpcs(world, true);
        }
    }

    private void validateWorldNpcs(World world, boolean forceViewerSync) {
        if (world == null || viewersIn(world).isEmpty()) {
            return;
        }
        for (NpcDefinition definition : new ArrayList<>(definitions.values())) {
            if (!world.equals(definition.location().getWorld())) {
                continue;
            }
            if (isSpawnedNpcComplete(definition)) {
                continue;
            }
            plugin.getLogger().info("[NPC] Validation rebuilt missing display layer for " + definition.id() + ".");
            spawnNpc(definition);
        }
        if (forceViewerSync) {
            for (Player viewer : viewersIn(world)) {
                syncNpcsForViewer(viewer);
            }
        }
    }

    private boolean isSpawnedNpcComplete(NpcDefinition definition) {
        if (definition == null) {
            return false;
        }
        SpawnedNpc spawnedNpc = spawnedNpcs.get(definition.id());
        if (spawnedNpc == null) {
            return false;
        }
        if (spawnedNpc.bodyEntityId() != null) {
            Entity body = Bukkit.getEntity(spawnedNpc.bodyEntityId());
            if (body == null || !body.isValid()) {
                return false;
            }
        }
        int expectedLabels = displayLines(definition).size();
        if (spawnedNpc.labelEntityIds().size() != expectedLabels) {
            return false;
        }
        for (UUID labelId : spawnedNpc.labelEntityIds()) {
            Entity label = Bukkit.getEntity(labelId);
            if (!(label instanceof TextDisplay) || !label.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void refreshLeaderboardSkins() {
        for (NpcDefinition definition : new ArrayList<>(definitions.values())) {
            if (!definition.leaderboardSkinType().isBlank()) {
                refreshLeaderboardSkin(definition.id(), false);
            }
        }
    }

    private boolean refreshLeaderboardSkin(String npcId) {
        return refreshLeaderboardSkin(npcId, false);
    }

    private boolean refreshLeaderboardSkin(String npcId, boolean forceRespawn) {
        NpcDefinition definition = definitions.get(npcId);
        if (definition == null || definition.leaderboardSkinType().isBlank() || !(plugin instanceof HGLootPlugin hydra)) {
            return false;
        }
        QueueService queueService = hydra.queueService();
        if (queueService == null) {
            return false;
        }
        QueueService.DebugLeaderboardType type = QueueService.DebugLeaderboardType.fromKey(definition.leaderboardSkinType());
        if (type == null) {
            return false;
        }
        UUID topPlayerId = queueService.topLeaderboardPlayerId(type);
        if (topPlayerId == null) {
            return false;
        }
        String skinOwner = leaderboardSkinOwner(queueService, topPlayerId);
        if (skinOwner.isBlank()) {
            return false;
        }
        if (!forceRespawn && skinOwner.equalsIgnoreCase(definition.skinOwner())) {
            return false;
        }
        NpcDefinition updated = skinOwner.equalsIgnoreCase(definition.skinOwner())
            ? definition
            : definition.withSkinOwner(skinOwner);
        definitions.put(updated.id(), updated);
        if (!skinOwner.equalsIgnoreCase(definition.skinOwner())) {
            saveDefinitions();
        }
        plugin.getLogger().info("[NPC] Leaderboard display refreshed for " + updated.id()
            + " type=" + updated.leaderboardSkinType() + " skin=" + skinOwner + ".");
        spawnNpc(updated);
        return true;
    }

    private String leaderboardSkinOwner(QueueService queueService, UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return queueService.leaderboardDisplayName(playerId);
    }

    private void refreshHiddenNameTeams() {
        if (spawnedNpcs.isEmpty()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            refreshHiddenNames(viewer);
        }
    }

    private void refreshHiddenNames(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        applyHiddenNameTeam(viewer.getScoreboard());
    }

    public void applyHiddenNameTeam(Player viewer) {
        refreshHiddenNames(viewer);
    }

    public void applyHiddenNameTeam(Scoreboard scoreboard) {
        Team team = ensureHiddenNameTeam(scoreboard);
        if (team == null) {
            return;
        }
        for (NpcDefinition definition : definitions.values()) {
            team.addEntry(profileNameFor(definition.uuid()));
        }
        for (SpawnedNpc spawnedNpc : spawnedNpcs.values()) {
            if (spawnedNpc.visual() != null) {
                team.addEntry(spawnedNpc.visual().profileName());
            }
        }
    }

    private void ensureHiddenNameForAllViewers(String profileName) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ensureHiddenNameEntry(viewer, profileName);
        }
    }

    private void ensureHiddenNameEntry(Player viewer, String profileName) {
        Team team = ensureHiddenNameTeam(viewer == null ? null : viewer.getScoreboard());
        if (team != null) {
            team.addEntry(profileName);
        }
    }

    private void removeHiddenNameEntry(String profileName) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = viewer.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            Team team = scoreboard.getTeam(HIDDEN_NAME_TEAM);
            if (team != null) {
                team.removeEntry(profileName);
            }
        }
    }

    private Team ensureHiddenNameTeam(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return null;
        }
        Team team = scoreboard.getTeam(HIDDEN_NAME_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(HIDDEN_NAME_TEAM);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }

    private String profileNameFor(UUID npcUuid) {
        return "npc" + npcUuid.toString().replace("-", "").substring(0, 13);
    }

    private NpcDefinition resolveNpc(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = normalizeNpcId(token);
        NpcDefinition direct = definitions.get(normalized);
        if (direct != null) {
            return direct;
        }
        for (NpcDefinition definition : definitions.values()) {
            if (definition.uuid().toString().equalsIgnoreCase(token) || shortId(definition.uuid()).equalsIgnoreCase(token)) {
                return definition;
            }
        }
        return null;
    }

    private String shortId(UUID npcId) {
        return npcId.toString().substring(0, 8);
    }

    private List<String> npcIdSuggestions() {
        return new ArrayList<>(definitions.keySet());
    }

    private List<String> completeToken(String token, List<String> candidates) {
        String lowered = token == null ? "" : token.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                matches.add(candidate);
            }
        }
        return new ArrayList<>(matches);
    }

    private List<String> parseQuotedArguments(String[] args, int fromIndex) {
        StringBuilder raw = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) {
                raw.append(' ');
            }
            raw.append(args[i]);
        }
        return parseQuotedSegments(raw.toString());
    }

    private List<String> parseQuotedSegments(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"') {
                if (quoted) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                current.append(ch);
            }
        }
        return out;
    }

    private String joinText(String[] args, int fromIndex) {
        StringBuilder out = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        String text = out.toString().trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private Double resolveCoordinate(Player player, String token, double base) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.equals("~")) {
            return base;
        }
        if (token.startsWith("~")) {
            try {
                return base + Double.parseDouble(token.substring(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isLegacyPositionalCreate(String[] args) {
        return args != null && args.length >= 5
            && isCoordinateToken(args[0])
            && isCoordinateToken(args[1])
            && isCoordinateToken(args[2]);
    }

    private boolean isCoordinateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String numeric = token.startsWith("~") ? token.substring(1) : token;
        if (numeric.isBlank()) {
            return true;
        }
        try {
            Double.parseDouble(numeric);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String normalizeCommandMode(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.regionMatches(true, 0, "player:", 0, "player:".length())
            || trimmed.regionMatches(true, 0, "console:", 0, "console:".length())
            || trimmed.regionMatches(true, 0, "url:", 0, "url:".length())
            || trimmed.regionMatches(true, 0, "say:", 0, "say:".length())
            || trimmed.regionMatches(true, 0, "open-dialogue:", 0, "open-dialogue:".length())
            || trimmed.regionMatches(true, 0, "daily:", 0, "daily:".length())) {
            return trimmed;
        }
        return "player:" + trimmed;
    }

    private String resolveLeaderboardSkinType(String[] args, int index) {
        if (args == null || args.length <= index) {
            return null;
        }
        String stat = args[index].trim().toLowerCase(Locale.ROOT);
        if (stat.equals("off") || stat.equals("none") || stat.equals("disable")) {
            return "";
        }
        if (stat.equals("kills")) {
            return QueueService.DebugLeaderboardType.KILLS.key();
        }
        if (stat.equals("level") || stat.equals("levels") || stat.equals("xp")) {
            return QueueService.DebugLeaderboardType.LEVEL.key();
        }
        if (stat.equals("money") || stat.equals("coins") || stat.equals("coin") || stat.equals("gold") || stat.equals("silver") || stat.equals("balance")) {
            return QueueService.DebugLeaderboardType.MONEY.key();
        }
        if (stat.equals("wins")) {
            String scope = args.length > index + 1 ? args[index + 1].trim().toLowerCase(Locale.ROOT) : "total";
            return switch (scope) {
                case "total", "all", "overall" -> QueueService.DebugLeaderboardType.WINS_TOTAL.key();
                case "hg", "hungergames", "hunger_games" -> QueueService.DebugLeaderboardType.WINS_HG.key();
                case "br", "battleroyale", "battle_royale" -> QueueService.DebugLeaderboardType.WINS_BR.key();
                default -> null;
            };
        }
        QueueService.DebugLeaderboardType type = QueueService.DebugLeaderboardType.fromKey(stat);
        return type == null ? null : type.key();
    }

    private String normalizeSkinOwner(String skinOwner) {
        return skinOwner == null ? "" : skinOwner.trim().toLowerCase(Locale.ROOT);
    }

    private String stripColor(String text) {
        return ChatColor.stripColor(text == null ? "" : text);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private boolean isBlank(String text) {
        return text == null || ChatColor.stripColor(text).isBlank();
    }

    private String normalizeNpcId(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lowered.length(); i++) {
            char ch = lowered.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') {
                out.append(ch);
            } else if (Character.isWhitespace(ch)) {
                out.append('_');
            }
        }
        return out.toString();
    }

    private String slugFromTitle(String title) {
        String stripped = stripColor(title).toLowerCase(Locale.ROOT).trim();
        if (stripped.isBlank()) {
            return "npc";
        }
        return normalizeNpcId(stripped.replace(' ', '_'));
    }

    private String uniqueNpcId(String base) {
        String id = normalizeNpcId(base);
        if (id.isBlank()) {
            id = "npc";
        }
        if (!definitions.containsKey(id)) {
            return id;
        }
        int suffix = 2;
        while (definitions.containsKey(id + "_" + suffix)) {
            suffix++;
        }
        return id + "_" + suffix;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private enum DefinitionTextField {
        TITLE,
        SUBTITLE,
        PROMPT,
        LINK_TEXT
    }

    private enum CommandMode {
        PLAYER,
        CONSOLE
    }

    private record NpcDefinition(
        String id,
        UUID uuid,
        Location location,
        String skinOwner,
        String title,
        String subtitle,
        String prompt,
        String leftCommand,
        String rightCommand,
        String leaderboardSkinType,
        String linkText,
        boolean lookAt
    ) {
        private NpcDefinition withLocation(Location value) {
            return new NpcDefinition(id, uuid, value.clone(), skinOwner, title, subtitle, prompt, leftCommand, rightCommand, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withSkinOwner(String value) {
            return new NpcDefinition(id, uuid, location.clone(), value, title, subtitle, prompt, leftCommand, rightCommand, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withTitle(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, value, subtitle, prompt, leftCommand, rightCommand, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withSubtitle(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, value, prompt, leftCommand, rightCommand, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withPrompt(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, value, leftCommand, rightCommand, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withLinkText(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, prompt, leftCommand, rightCommand,
                leaderboardSkinType, value == null ? "" : value, lookAt);
        }

        private NpcDefinition withLeftCommand(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, prompt, value, rightCommand, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withRightCommand(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, prompt, leftCommand, value, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withCommands(String left, String right) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, prompt, left, right, leaderboardSkinType, linkText, lookAt);
        }

        private NpcDefinition withLeaderboardSkinType(String value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, prompt, leftCommand, rightCommand,
                value == null ? "" : value.trim().toLowerCase(Locale.ROOT), linkText, lookAt);
        }

        private NpcDefinition withLookAt(boolean value) {
            return new NpcDefinition(id, uuid, location.clone(), skinOwner, title, subtitle, prompt, leftCommand, rightCommand, leaderboardSkinType, linkText, value);
        }

        private String commandFor(String click) {
            return CLICK_RIGHT.equalsIgnoreCase(click) ? rightCommand : leftCommand;
        }
    }

    private record SpawnedNpc(
        String npcId,
        UUID bodyEntityId,
        List<UUID> labelEntityIds,
        NpcVisual visual
    ) {
    }
}

