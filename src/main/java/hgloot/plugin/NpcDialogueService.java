package hgloot.plugin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Persisted, server-authored Paper dialogs that can be assigned to Hydra NPCs. */
public final class NpcDialogueService {
    private static final String ROOT = "dialogues";
    private static final String DEFAULT_PAGE = "1";
    private static final int MAX_BUTTONS = 3;
    private static final int BODY_WIDTH = 420;
    private static final int BUTTON_WIDTH = 150;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofHours(12))
        .build();

    private final HGLootPlugin plugin;
    private final File file;
    private final Map<String, DialogueDefinition> dialogues = new LinkedHashMap<>();
    private final LegacyComponentSerializer textSerializer = LegacyComponentSerializer.legacyAmpersand();

    public NpcDialogueService(HGLootPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "dialogues.yml");
        load();
        if (!dialogues.containsKey("guide")) {
            dialogues.put("guide", defaultGuide());
            save();
        }
    }

    public boolean exists(String dialogueId) {
        return dialogues.containsKey(normalizeId(dialogueId));
    }

    public void open(Player player, String dialogueId) {
        open(player, dialogueId, null);
    }

    public void open(Player player, String dialogueId, String pageId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        DialogueDefinition dialogue = dialogues.get(normalizeId(dialogueId));
        if (dialogue == null) {
            player.sendMessage(ChatColor.RED + "That NPC dialogue is unavailable.");
            return;
        }
        String resolvedPageId = pageId == null || pageId.isBlank() ? dialogue.startPage() : normalizeId(pageId);
        DialoguePage page = dialogue.pages().get(resolvedPageId);
        if (page == null) {
            player.sendMessage(ChatColor.RED + "Dialogue page '" + resolvedPageId + "' is missing.");
            plugin.getLogger().warning("[NPC][Dialogue] event=missing_page dialogue=" + dialogue.id()
                + " page=" + resolvedPageId + " player=" + player.getName());
            return;
        }
        player.showDialog(createDialog(dialogue, page));
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args, NpcDialogueAssignment assignment) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args, assignment);
            case "list" -> list(sender);
            case "title" -> title(sender, args);
            case "text" -> text(sender, args);
            case "button" -> button(sender, args);
            case "nextpage" -> nextPage(sender, args);
            case "navtext" -> navigationText(sender, args);
            case "columns" -> columns(sender, args);
            case "assign" -> assign(sender, args, assignment);
            case "preview" -> preview(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown dialogue command. Use /npc dialogue help.");
                yield true;
            }
        };
    }

    public List<String> complete(String[] args, List<String> npcIds) {
        if (args.length == 0) {
            return List.of("create", "delete", "list", "title", "text", "button", "nextpage", "navtext",
                "columns", "assign", "preview", "reload");
        }
        if (args.length == 1) {
            return completeToken(args[0], List.of("create", "delete", "list", "title", "text", "button",
                "nextpage", "navtext", "columns", "assign", "preview", "reload"));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("delete", "title", "text", "button", "nextpage", "navtext", "columns", "preview")
            .contains(sub)) {
            return completeToken(args[1], new ArrayList<>(dialogues.keySet()));
        }
        if (args.length == 2 && sub.equals("assign")) {
            return completeToken(args[1], npcIds);
        }
        if (args.length == 3 && sub.equals("assign")) {
            return completeToken(args[2], new ArrayList<>(dialogues.keySet()));
        }
        if (args.length == 3 && List.of("text", "button", "nextpage", "navtext", "columns").contains(sub)) {
            DialogueDefinition dialogue = dialogues.get(normalizeId(args[1]));
            return dialogue == null ? List.of() : completeToken(args[2], new ArrayList<>(dialogue.pages().keySet()));
        }
        if (args.length == 4 && sub.equals("button")) {
            return completeToken(args[3], List.of("1", "2", "3"));
        }
        if (args.length == 5 && sub.equals("button")) {
            return completeToken(args[4], List.of("text", "action", "clear"));
        }
        if (args.length == 4 && sub.equals("columns")) {
            return completeToken(args[3], List.of("1", "2", "3"));
        }
        return List.of();
    }

    private Dialog createDialog(DialogueDefinition dialogue, DialoguePage page) {
        List<ActionButton> buttons = new ArrayList<>();
        page.buttons().values().stream()
            .limit(MAX_BUTTONS)
            .forEach(button -> buttons.add(actionButton(dialogue, page, button)));
        ActionButton next = page.nextPage() == null || page.nextPage().isBlank()
            ? null
            : ActionButton.create(component(page.navigationText()), null, BUTTON_WIDTH,
                callback(dialogue.id(), "page:" + page.nextPage()));

        List<DialogBody> body = List.of(DialogBody.plainMessage(component(page.text()), BODY_WIDTH));
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(component(dialogue.title()))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build())
            .type(DialogType.multiAction(buttons, next, Math.max(1, Math.min(3, page.columns())))));
    }

    private ActionButton actionButton(DialogueDefinition dialogue, DialoguePage page, DialogueButton button) {
        Component tooltip = button.action().startsWith("page:")
            ? Component.text("Open another Guide page", NamedTextColor.GRAY)
            : null;
        return ActionButton.create(component(button.text()), tooltip, BUTTON_WIDTH,
            callback(dialogue.id(), button.action()));
    }

    private DialogAction callback(String dialogueId, String action) {
        return DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> execute(player, dialogueId, action));
        }, CALLBACK_OPTIONS);
    }

    private void execute(Player player, String dialogueId, String action) {
        if (!player.isOnline()) return;
        DialogueAction parsed = DialogueAction.parse(action);
        if (parsed == null) {
            player.sendMessage(ChatColor.RED + "That dialogue button is not configured correctly.");
            return;
        }
        switch (parsed.type()) {
            case PAGE -> open(player, dialogueId, parsed.value());
            case CLOSE -> player.closeDialog();
            case PLAYER_COMMAND -> Bukkit.dispatchCommand(player, stripSlash(parsed.value()));
            case CONSOLE_COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                stripSlash(parsed.value()).replace("{player}", player.getName()));
        }
    }

    private boolean create(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue create <id>");
            return true;
        }
        String id = normalizeId(args[1]);
        if (id.isBlank() || dialogues.containsKey(id)) {
            sender.sendMessage(ChatColor.RED + "Dialogue id is invalid or already exists.");
            return true;
        }
        Map<String, DialoguePage> pages = new LinkedHashMap<>();
        pages.put(DEFAULT_PAGE, new DialoguePage(DEFAULT_PAGE, "Configure this page with /npc dialogue text.", 1,
            new LinkedHashMap<>(), "", "Next"));
        dialogues.put(id, new DialogueDefinition(id, id, DEFAULT_PAGE, pages));
        save();
        sender.sendMessage(ChatColor.GREEN + "Created dialogue " + ChatColor.WHITE + id + ChatColor.GREEN + ".");
        return true;
    }

    private boolean delete(CommandSender sender, String[] args, NpcDialogueAssignment assignment) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue delete <id>");
            return true;
        }
        String id = normalizeId(args[1]);
        if (dialogues.remove(id) == null) {
            sender.sendMessage(ChatColor.RED + "Unknown dialogue id.");
            return true;
        }
        assignment.clearAssignments(id);
        save();
        sender.sendMessage(ChatColor.GREEN + "Deleted dialogue " + ChatColor.WHITE + id + ChatColor.GREEN + ".");
        return true;
    }

    private boolean list(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "NPC Dialogues: " + ChatColor.WHITE
            + (dialogues.isEmpty() ? "none" : String.join(", ", dialogues.keySet())));
        return true;
    }

    private boolean title(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue title <id> <text>");
            return true;
        }
        DialogueDefinition current = resolve(sender, args[1]);
        if (current == null) return true;
        dialogues.put(current.id(), current.withTitle(join(args, 2)));
        save();
        sender.sendMessage(ChatColor.GREEN + "Updated dialogue title.");
        return true;
    }

    private boolean text(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue text <id> <page> <text>");
            return true;
        }
        DialogueDefinition current = resolve(sender, args[1]);
        if (current == null) return true;
        String pageId = normalizeId(args[2]);
        if (pageId.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Invalid page id.");
            return true;
        }
        DialoguePage page = current.pages().getOrDefault(pageId,
            new DialoguePage(pageId, "", 1, new LinkedHashMap<>(), "", "Next"));
        updatePage(current, page.withText(multiline(join(args, 3))));
        sender.sendMessage(ChatColor.GREEN + "Updated dialogue page text.");
        return true;
    }

    private boolean button(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue button <id> <page> <1|2|3> <text|action|clear> [value]");
            return true;
        }
        DialogueDefinition current = resolve(sender, args[1]);
        if (current == null) return true;
        DialoguePage page = current.pages().get(normalizeId(args[2]));
        if (page == null) {
            sender.sendMessage(ChatColor.RED + "Create the page first with /npc dialogue text.");
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            index = -1;
        }
        if (index < 1 || index > MAX_BUTTONS) {
            sender.sendMessage(ChatColor.RED + "Button must be 1, 2, or 3.");
            return true;
        }
        String field = args[4].toLowerCase(Locale.ROOT);
        Map<Integer, DialogueButton> buttons = new LinkedHashMap<>(page.buttons());
        if (field.equals("clear")) {
            buttons.remove(index);
        } else {
            if (args.length < 6) {
                sender.sendMessage(ChatColor.RED + "Button text/action requires a value.");
                return true;
            }
            DialogueButton existing = buttons.getOrDefault(index, new DialogueButton("Button " + index, "close"));
            String value = join(args, 5);
            if (field.equals("text")) {
                buttons.put(index, existing.withText(value));
            } else if (field.equals("action")) {
                if (!validAction(current, value)) {
                    sender.sendMessage(ChatColor.RED + "Action must be close, page:<existingPage>, player:<command>, or console:<command>.");
                    return true;
                }
                buttons.put(index, existing.withAction(value.trim()));
            } else {
                sender.sendMessage(ChatColor.RED + "Button field must be text, action, or clear.");
                return true;
            }
        }
        updatePage(current, page.withButtons(buttons));
        sender.sendMessage(ChatColor.GREEN + "Updated dialogue button " + index + ".");
        return true;
    }

    private boolean nextPage(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue nextpage <id> <page> <nextPage|none>");
            return true;
        }
        DialogueDefinition current = resolve(sender, args[1]);
        if (current == null) return true;
        DialoguePage page = current.pages().get(normalizeId(args[2]));
        if (page == null) {
            sender.sendMessage(ChatColor.RED + "Unknown page id.");
            return true;
        }
        String next = args[3].equalsIgnoreCase("none") ? "" : normalizeId(args[3]);
        if (!next.isBlank() && !current.pages().containsKey(next)) {
            sender.sendMessage(ChatColor.RED + "The next page does not exist.");
            return true;
        }
        updatePage(current, page.withNextPage(next));
        sender.sendMessage(ChatColor.GREEN + "Updated standard Next navigation.");
        return true;
    }

    private boolean columns(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue columns <id> <page> <1|2|3>");
            return true;
        }
        DialogueDefinition current = resolve(sender, args[1]);
        if (current == null) return true;
        DialoguePage page = current.pages().get(normalizeId(args[2]));
        if (page == null) {
            sender.sendMessage(ChatColor.RED + "Unknown page id.");
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            value = 0;
        }
        if (value < 1 || value > 3) {
            sender.sendMessage(ChatColor.RED + "Columns must be 1, 2, or 3.");
            return true;
        }
        updatePage(current, page.withColumns(value));
        sender.sendMessage(ChatColor.GREEN + "Updated dialogue button columns.");
        return true;
    }

    private boolean navigationText(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue navtext <id> <page> <label>");
            return true;
        }
        DialogueDefinition current = resolve(sender, args[1]);
        if (current == null) return true;
        DialoguePage page = current.pages().get(normalizeId(args[2]));
        if (page == null) {
            sender.sendMessage(ChatColor.RED + "Unknown page id.");
            return true;
        }
        updatePage(current, page.withNavigationText(join(args, 3)));
        sender.sendMessage(ChatColor.GREEN + "Updated standard navigation label.");
        return true;
    }

    private boolean assign(CommandSender sender, String[] args, NpcDialogueAssignment assignment) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue assign <npc> <dialogueId>");
            return true;
        }
        String dialogueId = normalizeId(args[2]);
        if (!dialogues.containsKey(dialogueId)) {
            sender.sendMessage(ChatColor.RED + "Unknown dialogue id.");
            return true;
        }
        return assignment.assign(sender, args[1], dialogueId);
    }

    private boolean preview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can preview dialogs.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc dialogue preview <id>");
            return true;
        }
        open(player, args[1]);
        return true;
    }

    private boolean reload(CommandSender sender) {
        load();
        sender.sendMessage(ChatColor.GREEN + "Reloaded NPC dialogues from dialogues.yml.");
        return true;
    }

    private void updatePage(DialogueDefinition dialogue, DialoguePage page) {
        Map<String, DialoguePage> pages = new LinkedHashMap<>(dialogue.pages());
        pages.put(page.id(), page);
        dialogues.put(dialogue.id(), dialogue.withPages(pages));
        save();
    }

    private DialogueDefinition resolve(CommandSender sender, String id) {
        DialogueDefinition dialogue = dialogues.get(normalizeId(id));
        if (dialogue == null) sender.sendMessage(ChatColor.RED + "Unknown dialogue id.");
        return dialogue;
    }

    private boolean validAction(DialogueDefinition dialogue, String raw) {
        DialogueAction action = DialogueAction.parse(raw);
        return action != null && (action.type() != DialogueActionType.PAGE
            || dialogue.pages().containsKey(normalizeId(action.value())));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Hydra NPC Dialogues");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue create|delete|list <id>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue title <id> <text>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue text <id> <page> <text>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue button <id> <page> <1|2|3> <text|action|clear> [value]");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue nextpage|columns <id> <page> <value>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue navtext <id> <page> <label>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue assign <npc> <dialogueId>");
        sender.sendMessage(ChatColor.GRAY + "/npc dialogue preview <id>");
    }

    private void load() {
        dialogues.clear();
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String id = normalizeId(rawId);
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (id.isBlank() || section == null) continue;
            Map<String, DialoguePage> pages = new LinkedHashMap<>();
            ConfigurationSection pageRoot = section.getConfigurationSection("pages");
            if (pageRoot != null) {
                for (String rawPageId : pageRoot.getKeys(false)) {
                    String pageId = normalizeId(rawPageId);
                    ConfigurationSection pageSection = pageRoot.getConfigurationSection(rawPageId);
                    if (pageId.isBlank() || pageSection == null) continue;
                    Map<Integer, DialogueButton> buttons = new LinkedHashMap<>();
                    ConfigurationSection buttonRoot = pageSection.getConfigurationSection("buttons");
                    if (buttonRoot != null) {
                        for (int index = 1; index <= MAX_BUTTONS; index++) {
                            ConfigurationSection buttonSection = buttonRoot.getConfigurationSection(String.valueOf(index));
                            if (buttonSection == null) continue;
                            String label = buttonSection.getString("text", "Button " + index);
                            String action = buttonSection.getString("action", "close");
                            buttons.put(index, new DialogueButton(label, action));
                        }
                    }
                    pages.put(pageId, new DialoguePage(pageId, pageSection.getString("text", ""),
                        Math.max(1, Math.min(3, pageSection.getInt("columns", 1))), buttons,
                        normalizeId(pageSection.getString("next_page", "")),
                        pageSection.getString("navigation_text", "Next")));
                }
            }
            if (pages.isEmpty()) continue;
            String startPage = normalizeId(section.getString("start_page", DEFAULT_PAGE));
            if (!pages.containsKey(startPage)) startPage = pages.keySet().iterator().next();
            dialogues.put(id, new DialogueDefinition(id, section.getString("title", id), startPage, pages));
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (DialogueDefinition dialogue : dialogues.values()) {
            String base = ROOT + "." + dialogue.id();
            config.set(base + ".title", dialogue.title());
            config.set(base + ".start_page", dialogue.startPage());
            for (DialoguePage page : dialogue.pages().values()) {
                String pageBase = base + ".pages." + page.id();
                config.set(pageBase + ".text", page.text());
                config.set(pageBase + ".columns", page.columns());
                config.set(pageBase + ".next_page", page.nextPage());
                config.set(pageBase + ".navigation_text", page.navigationText());
                for (Map.Entry<Integer, DialogueButton> entry : page.buttons().entrySet()) {
                    String buttonBase = pageBase + ".buttons." + entry.getKey();
                    config.set(buttonBase + ".text", entry.getValue().text());
                    config.set(buttonBase + ".action", entry.getValue().action());
                }
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save dialogues.yml.", ex);
        }
    }

    private DialogueDefinition defaultGuide() {
        Map<String, DialoguePage> pages = new LinkedHashMap<>();
        pages.put("main", page("main",
            "Hey, welcome to Hydra!\n\nI'm here to help you figure out how everything works. What do you want to know about?",
            3, button("GAMES", "page:games"), button("SOCIALS", "page:socials"), button("HYDRA", "page:hydra")));
        pages.put("games", page("games", "What do you want to know about games?", 1,
            button("How do I join a game?", "page:games_join"),
            button("What is Minigame Roulette?", "page:games_roulette"),
            button("How does Hunger Games work?", "page:games_hg")).withNavigation("main", "BACK"));
        pages.put("games_join", answer("games_join", "How do I join a game?",
            "You can join games using the Navigator in the Hub. Select the game you want to play and join its queue.", "games"));
        pages.put("games_roulette", answer("games_roulette", "What is Minigame Roulette?",
            "Minigame Roulette is a three-round tournament. Your locked team earns points from placement and credited eliminations across each round.", "games"));
        pages.put("games_hg", answer("games_hg", "How does Hunger Games work?",
            "Search the map for loot, craft powerful items, and eliminate every opponent. The final surviving player or team wins.", "games"));

        pages.put("socials", page("socials", "What do you want to know about Socials?", 1,
            button("How do Friends work?", "page:socials_friends"),
            button("How do Parties work?", "page:socials_parties"),
            button("What are Public Parties?", "page:socials_public")).withNavigation("main", "BACK"));
        pages.put("socials_friends", answer("socials_friends", "How do Friends work?",
            "Open Socials in the Hub to view friends, requests, and blocked players.", "socials"));
        pages.put("socials_parties", answer("socials_parties", "How do Parties work?",
            "Parties keep up to four players together when joining supported queues. The leader controls invites and party visibility.", "socials"));
        pages.put("socials_public", answer("socials_public", "What are Public Parties?",
            "Public parties can be joined without an invite through Hydra's available party join paths. Private parties remain invite-only.", "socials"));

        pages.put("hydra", page("hydra", "What do you want to know about Hydra?", 1,
            button("What is Hydra?", "page:hydra_about"),
            button("How does progression work?", "page:hydra_progress"),
            button("What are Shard Fragments?", "page:hydra_shards")).withNavigation("main", "BACK"));
        pages.put("hydra_about", answer("hydra_about", "What is Hydra?",
            "Hydra is a competitive Minecraft network built around Hunger Games, Minigame Roulette, custom progression, and social play.", "hydra"));
        pages.put("hydra_progress", answer("hydra_progress", "How does progression work?",
            "Playing Hydra modes earns account progression and rewards. Your menus and sidebar show the authoritative values.", "hydra"));
        pages.put("hydra_shards", answer("hydra_shards", "What are Shard Fragments?",
            "Collect " + ProgressionService.FRAGMENTS_PER_SHARD
                + " Shard Fragments to become eligible to open the existing Shard Claim interface.", "hydra"));
        return new DialogueDefinition("guide", "&b&lGUIDE", "main", pages);
    }

    private DialoguePage answer(String id, String question, String answer, String category) {
        return page(id, "&b" + question + "\n\n&7" + answer, 2,
            button("OTHER QUESTIONS", "page:" + category), button("CLOSE", "close"));
    }

    private DialoguePage page(String id, String text, int columns, DialogueButton... buttons) {
        Map<Integer, DialogueButton> mapped = new LinkedHashMap<>();
        for (int index = 0; index < buttons.length && index < MAX_BUTTONS; index++) mapped.put(index + 1, buttons[index]);
        return new DialoguePage(id, text, columns, mapped, "", "Next");
    }

    private DialogueButton button(String text, String action) {
        return new DialogueButton(text, action);
    }

    private Component component(String text) {
        return textSerializer.deserialize(text == null ? "" : text);
    }

    private String normalizeId(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    private String multiline(String value) {
        return value.replace("\\n", "\n").replace(" | ", "\n");
    }

    private String stripSlash(String value) {
        String command = value == null ? "" : value.trim();
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private String join(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }

    private List<String> completeToken(String token, List<String> candidates) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    public interface NpcDialogueAssignment {
        boolean assign(CommandSender sender, String npcId, String dialogueId);
        void clearAssignments(String dialogueId);
    }

    record DialogueDefinition(String id, String title, String startPage, Map<String, DialoguePage> pages) {
        DialogueDefinition {
            pages = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pages));
        }
        DialogueDefinition withTitle(String value) { return new DialogueDefinition(id, value, startPage, pages); }
        DialogueDefinition withPages(Map<String, DialoguePage> value) { return new DialogueDefinition(id, title, startPage, value); }
    }

    record DialoguePage(String id, String text, int columns, Map<Integer, DialogueButton> buttons, String nextPage,
                        String navigationText) {
        DialoguePage {
            buttons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(buttons));
        }
        DialoguePage withText(String value) { return new DialoguePage(id, value, columns, buttons, nextPage, navigationText); }
        DialoguePage withColumns(int value) { return new DialoguePage(id, text, value, buttons, nextPage, navigationText); }
        DialoguePage withButtons(Map<Integer, DialogueButton> value) { return new DialoguePage(id, text, columns, value, nextPage, navigationText); }
        DialoguePage withNextPage(String value) { return new DialoguePage(id, text, columns, buttons, value, navigationText); }
        DialoguePage withNavigationText(String value) { return new DialoguePage(id, text, columns, buttons, nextPage, value); }
        DialoguePage withNavigation(String target, String label) { return new DialoguePage(id, text, columns, buttons, target, label); }
    }

    record DialogueButton(String text, String action) {
        DialogueButton withText(String value) { return new DialogueButton(value, action); }
        DialogueButton withAction(String value) { return new DialogueButton(text, value); }
    }

    enum DialogueActionType { PAGE, CLOSE, PLAYER_COMMAND, CONSOLE_COMMAND }

    record DialogueAction(DialogueActionType type, String value) {
        static DialogueAction parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String value = raw.trim();
            if (value.equalsIgnoreCase("close")) return new DialogueAction(DialogueActionType.CLOSE, "");
            int separator = value.indexOf(':');
            if (separator <= 0 || separator == value.length() - 1) return null;
            String type = value.substring(0, separator).toLowerCase(Locale.ROOT);
            String argument = value.substring(separator + 1).trim();
            if (argument.isBlank()) return null;
            return switch (type) {
                case "page" -> new DialogueAction(DialogueActionType.PAGE, argument);
                case "player" -> new DialogueAction(DialogueActionType.PLAYER_COMMAND, argument);
                case "console" -> new DialogueAction(DialogueActionType.CONSOLE_COMMAND, argument);
                default -> null;
            };
        }
    }
}
