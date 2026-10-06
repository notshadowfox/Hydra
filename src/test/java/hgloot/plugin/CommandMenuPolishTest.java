package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hgloot.plugin.announcement.AnnouncementService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class CommandMenuPolishTest {
    private HGLootPlugin plugin;
    private MenuService menus;
    private QueueService queues;
    private FriendService friends;
    private PartyService parties;
    private Player player;

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(HGLootPlugin.class);
        menus = mock(MenuService.class);
        queues = mock(QueueService.class);
        friends = mock(FriendService.class);
        parties = mock(PartyService.class);
        player = mock(Player.class);
        when(player.hasPermission("hgloot.queue")).thenReturn(true);
        when(player.hasPermission("hgloot.friends")).thenReturn(true);
        when(player.hasPermission("hgloot.party")).thenReturn(true);
        when(player.hasPermission("hgloot.playermenu")).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        setField("menuService", menus);
        setField("queueService", queues);
        setField("friendService", friends);
        setField("partyService", parties);
        setField("announcementService", mock(AnnouncementService.class));
    }

    @Test
    void hungerGamesRootOpensExistingMenuWithoutJoining() throws Exception {
        assertTrue(call("handleHungerGames", player, new String[0]));

        verify(menus).openPlayerHungerGamesMenu(player);
        verify(queues, never()).joinHungerGamesSolo(any());
        verify(queues, never()).joinHungerGamesDuos(any());
    }

    @Test
    void friendsRootAndPluralAliasOpenExistingMenu() throws Exception {
        assertTrue(call("handleFriend", player, new String[0]));
        verify(menus).openPlayerFriendsMenu(player);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/plugin.yml").toFile());
        assertTrue(yaml.getStringList("commands.friend.aliases").contains("friends"));
    }

    @Test
    void partyRootOpensPartySubmenuButListRemainsTextCommand() throws Exception {
        assertTrue(call("handleParty", player, new String[0]));
        verify(menus).openPlayerPartyMenu(player);

        assertTrue(call("handleParty", player, new String[] {"list"}));
        verify(parties).isInParty(player.getUniqueId());
        verify(menus).openPlayerPartyMenu(player);
    }

    @Test
    void singleWordPartyAndTeamChatCommandsUseExistingToggles() throws Exception {
        HydraPlusService chat = mock(HydraPlusService.class);
        setField("hydraPlusService", chat);
        when(player.hasPermission("hgloot.party")).thenReturn(false);

        Method toggle = HGLootPlugin.class.getDeclaredMethod("handleChatToggle", CommandSender.class, boolean.class);
        toggle.setAccessible(true);
        assertTrue((boolean) toggle.invoke(plugin, player, true));
        assertTrue((boolean) toggle.invoke(plugin, player, false));
        verify(chat).togglePartyChat(player);
        verify(chat).toggleTeamChat(player);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/plugin.yml").toFile());
        assertTrue(yaml.contains("commands.pc"));
        assertTrue(yaml.contains("commands.tc"));
        assertTrue(yaml.contains("commands.partychat"));
        assertTrue(yaml.contains("commands.teamchat"));
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        assertTrue(source.contains("commandRoutes.put(\"partychat\", (sender, args) -> handleChatToggle(sender, true))"));
        assertTrue(source.contains("commandRoutes.put(\"teamchat\", (sender, args) -> handleChatToggle(sender, false))"));
        assertFalse(source.contains("args[0].equalsIgnoreCase(\"chat\")"));
    }

    @Test
    void socialRootOpensExistingParentMenu() throws Exception {
        Method method = HGLootPlugin.class.getDeclaredMethod("handleSocial", CommandSender.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(plugin, player));
        verify(menus).openPlayerSocialMenu(player);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/plugin.yml").toFile());
        assertTrue(yaml.contains("commands.social"));
    }

    @Test
    void kitEditorIsReachedFromNavigatorAndNotPersonalSettings() throws Exception {
        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"))
            .replace("\r\n", "\n");
        String navigator = menu.substring(menu.indexOf("public void openPlayerMenu("),
            menu.indexOf("private void fillNavigatorCard("));
        String settings = menu.substring(menu.indexOf("public void openPlayerSettingsMenu("),
            menu.indexOf("public void openPlayerKitEditorMenu("));
        String selector = menu.substring(menu.indexOf("public void openPlayerKitEditorMenu("),
            menu.indexOf("public void openPlayerCosmeticsMenu("));
        assertTrue(navigator.contains("\"open_player_kit_editor\""));
        assertTrue(navigator.contains("button(Material.IRON_SWORD, ChatColor.AQUA + \"Kit Editor\", \"open_player_kit_editor\")"));
        assertFalse(navigator.contains("Arrange your Bridge starting items."));
        assertFalse(settings.contains("\"open_player_kit_editor\""));
        assertTrue(selector.contains("themedMenuInventory(54, MAIN_TITLE_COLOR + \"Kit Editor\", \"kit_editor\")"));
        assertTrue(selector.contains("inv.setItem(19, button(Material.IRON_SWORD, ChatColor.AQUA + \"Bridge Kit\", \"player_edit_bridge_kit\""));
        assertTrue(selector.contains("Edit your Bridge item layout."));
        assertFalse(selector.contains("\"back_player_menu\""));
        assertFalse(selector.contains("Material.RED_CONCRETE"));
        assertFalse(selector.contains("guiBackButton("));
        assertTrue(settings.contains("selectedPage == 1 ? \"settings_page_1\" : \"settings_page_2\""));
        assertTrue(menu.contains("PlayerSettingsService.Toggle.NIGHT_VISION"));
        assertTrue(settings.contains("GUI_MODEL_TOGGLE_ON : GUI_MODEL_TOGGLE_OFF"));
        assertFalse(settings.contains("guiBackButton("));
        assertTrue(menu.contains("if (action.equals(\"back_player_menu\")) {\n            player.closeInventory();"));
    }

    @Test
    void existingSubcommandsRemainOnTheirCommandPaths() throws Exception {
        assertTrue(call("handleHungerGames", player, new String[] {"status"}));
        verify(queues).sendStatus(eq(player), any());
        verify(menus, never()).openPlayerHungerGamesMenu(player);

        when(friends.getFriends(player.getUniqueId())).thenReturn(List.of());
        assertTrue(call("handleFriend", player, new String[] {"list"}));
        verify(friends).getFriends(player.getUniqueId());
        verify(menus, never()).openPlayerFriendsMenu(player);
    }

    @Test
    void unavailableBattleRoyaleRootDoesNotExposeQueueMenu() throws Exception {
        assertTrue(call("handleBattleRoyale", player, new String[0]));
        verify(menus, never()).openPlayerPlayMenu(player);

        String menu = Files.readString(Path.of("src/main/java/hgloot/plugin/MenuService.java"));
        assertTrue(menu.contains("ChatColor.RED + \"Currently Unavailable\""));
        assertTrue(menu.contains("Battle Royale is coming soon."));
    }

    @Test
    void consoleKeepsPlayerOnlyFeedbackAndNeverOpensMenus() throws Exception {
        CommandSender console = mock(CommandSender.class);

        assertTrue(call("handleHungerGames", console, new String[0]));
        assertTrue(call("handleFriend", console, new String[0]));

        verify(console).sendMessage("§cOnly players can use this.");
        verify(console).sendMessage("§cOnly players can run this.");
        verify(menus, never()).openPlayerHungerGamesMenu(any());
        verify(menus, never()).openPlayerFriendsMenu(any());
    }

    @Test
    void existingMenuCommandsAndLegitimateRootActionsRemainIntact() throws Exception {
        String pluginSource = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));
        String progression = Files.readString(Path.of("src/main/java/hgloot/plugin/ProgressionService.java"));
        String roulette = Files.readString(Path.of("src/main/java/hgloot/plugin/MinigameRouletteService.java"));

        assertTrue(pluginSource.contains("menuService.openPlayerCosmeticsMenu(player)"));
        assertTrue(pluginSource.contains("menuService.openPlayerSettingsMenu(player)"));
        assertTrue(pluginSource.contains("legendaryRecipeBookService.openIndex(player)"));
        assertTrue(pluginSource.contains("commandRoutes.put(\"battlepass\""));
        assertTrue(pluginSource.contains("menuService.openPlayerPartyMenu(player)"));
        assertTrue(roulette.contains("String sub = args.length == 0 ? \"status\""));
        assertTrue(progression.contains("if (args.length == 0)"));
        assertTrue(progression.contains("openShardMenu(player);"));
        assertFalse(progression.contains("if (args.length == 0 || (!args[0].equalsIgnoreCase(\"open\")"));
    }

    private boolean call(String methodName, CommandSender sender, String[] args) throws Exception {
        Method method = HGLootPlugin.class.getDeclaredMethod(methodName, CommandSender.class, String[].class);
        method.setAccessible(true);
        return (boolean) method.invoke(plugin, sender, args);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = HGLootPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
