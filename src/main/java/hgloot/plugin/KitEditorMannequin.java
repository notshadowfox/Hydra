package hgloot.plugin;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

/** A viewer-scoped, planted loadout preview using Hydra's existing fake-player bridge. */
final class KitEditorMannequin {
    private static final String HIDDEN_NAME_TEAM = "kit_preview";
    private final JavaPlugin plugin;
    private NpcNmsBridge bridge;

    KitEditorMannequin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    Preview spawn(Player owner, Location location) {
        if (bridge == null) bridge = NpcNmsBridge.tryCreate(plugin);
        if (bridge != null) {
            NpcVisual visual = null;
            try {
                List<SkinPropertyData> skin = skinProperties(owner);
                if (skin.isEmpty()) plugin.getLogger().warning("Kit Editor skin textures unavailable for "
                    + owner.getName() + "; preview will use the client fallback skin");
                visual = bridge.spawnNpc(UUID.randomUUID(), location, skin);
                Entity body = bridge.toBukkitEntity(visual.serverPlayer());
                body.setInvulnerable(true);
                body.setGravity(false);
                if (body instanceof LivingEntity living) living.setCollidable(false);
                body.setSilent(true);
                body.setPersistent(false);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(owner.getUniqueId())) other.hideEntity(plugin, body);
                }
                hideInternalName(owner, visual);
                // Give the client time to resolve the copied skin before removing the temporary tab entry.
                bridge.sendSpawn(owner, visual, 40L);
                return new Preview(owner.getUniqueId(), visual, null, spawnName(owner, location),
                    location.getYaw(), owner.getLocation().getYaw());
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Kit Editor player preview unavailable; using stand fallback", exception);
                if (visual != null) remove(new Preview(owner.getUniqueId(), visual, null, null, location.getYaw(), 0));
            }
        }
        ArmorStand stand = null;
        try {
            stand = location.getWorld().spawn(location, ArmorStand.class);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
            return new Preview(owner.getUniqueId(), null, stand, spawnName(owner, location),
                location.getYaw(), owner.getLocation().getYaw());
        } catch (RuntimeException exception) {
            if (stand != null) stand.remove();
            plugin.getLogger().log(Level.WARNING, "Kit Editor preview could not spawn", exception);
            return null;
        }
    }

    void update(Player owner, Preview preview, KitDefinition definition) {
        if (preview == null) return;
        ItemStack[] armor = definition.previewArmor();
        ItemStack main = owner.getInventory().getItemInMainHand();
        ItemStack off = owner.getInventory().getItemInOffHand();
        if (preview.visual != null) {
            try {
                bridge.sendPreviewEquipment(owner, preview.visual, armor, main, off);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Kit Editor preview equipment update failed", exception);
            }
        } else if (preview.stand != null && preview.stand.isValid()) {
            preview.stand.getEquipment().setArmorContents(armor);
            preview.stand.getEquipment().setItemInMainHand(main);
            preview.stand.getEquipment().setItemInOffHand(off);
        }
    }

    void look(Player owner, Preview preview) {
        look(owner, preview, owner.getLocation());
    }

    void look(Player owner, Preview preview, Location view) {
        if (preview == null || preview.visual == null) return;
        float relativeYaw = clampAngle(view.getYaw() - preview.entryYaw, 70.0F);
        float headYaw = preview.bodyYaw + relativeYaw;
        float pitch = Math.max(-55.0F, Math.min(55.0F, view.getPitch()));
        try {
            bridge.sendPreviewLook(owner, preview.visual, preview.bodyYaw, headYaw, pitch);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "Kit Editor preview head update failed", exception);
        }
    }

    void swing(Player owner, Preview preview, boolean offHand) {
        if (preview == null) return;
        if (preview.visual != null) {
            try {
                bridge.sendPreviewSwing(owner, preview.visual, offHand);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.FINE, "Kit Editor preview swing failed", exception);
            }
        } else if (preview.stand != null && preview.stand.isValid()) {
            if (offHand) preview.stand.swingOffHand();
            else preview.stand.swingMainHand();
        }
    }

    void remove(Preview preview) {
        if (preview == null) return;
        if (preview.name != null) preview.name.remove();
        if (preview.stand != null) preview.stand.remove();
        if (preview.visual == null || bridge == null) return;
        Player owner = Bukkit.getPlayer(preview.owner);
        try {
            if (owner != null && owner.isOnline()) {
                Team team = owner.getScoreboard().getTeam(HIDDEN_NAME_TEAM);
                if (team != null) team.removeEntry(preview.visual.profileName());
                bridge.sendRemove(owner, preview.visual);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Kit Editor preview packet cleanup failed", exception);
        } finally {
            try {
                bridge.destroyNpc(preview.visual);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Kit Editor preview entity cleanup failed", exception);
            }
        }
    }

    private static float clampAngle(float angle, float limit) {
        float wrapped = (angle + 180.0F) % 360.0F;
        if (wrapped < 0) wrapped += 360.0F;
        return Math.max(-limit, Math.min(limit, wrapped - 180.0F));
    }

    static Component displayName(Player owner) {
        return Component.text(owner.getName(), NamedTextColor.WHITE);
    }

    static List<SkinPropertyData> skinProperties(Player owner) {
        PlayerProfile profile = owner.getPlayerProfile().clone();
        if (profile.getProperties().stream().noneMatch(property -> "textures".equals(property.getName()))) {
            try {
                profile.completeFromCache(true);
            } catch (RuntimeException ignored) {
                // Online profile data remains usable; remote skin lookup is never required for editor entry.
            }
        }
        List<SkinPropertyData> skin = new ArrayList<>();
        for (ProfileProperty property : profile.getProperties()) {
            if ("textures".equals(property.getName())) {
                skin.add(new SkinPropertyData(property.getName(), property.getValue(), property.getSignature()));
            }
        }
        return skin;
    }

    static void hideInternalName(Player owner, NpcVisual visual) {
        Team team = owner.getScoreboard().getTeam(HIDDEN_NAME_TEAM);
        if (team == null) team = owner.getScoreboard().registerNewTeam(HIDDEN_NAME_TEAM);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(visual.profileName());
    }

    private TextDisplay spawnName(Player owner, Location location) {
        TextDisplay name = location.getWorld().spawn(location.clone().add(0, 2.25, 0), TextDisplay.class);
        try {
            name.text(displayName(owner));
            name.setBillboard(Display.Billboard.CENTER);
            name.setPersistent(false);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(owner.getUniqueId())) other.hideEntity(plugin, name);
            }
            return name;
        } catch (RuntimeException exception) {
            name.remove();
            throw exception;
        }
    }

    static final class Preview {
        final UUID owner;
        final NpcVisual visual;
        final ArmorStand stand;
        final TextDisplay name;
        final float bodyYaw;
        final float entryYaw;

        Preview(UUID owner, NpcVisual visual, ArmorStand stand, TextDisplay name, float bodyYaw, float entryYaw) {
            this.owner = owner;
            this.visual = visual;
            this.stand = stand;
            this.name = name;
            this.bodyYaw = bodyYaw;
            this.entryYaw = entryYaw;
        }
    }
}
