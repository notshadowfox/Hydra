package hgloot.plugin.ultraroyales;

import hgloot.plugin.ultraroyales.listeners.BingoTheClownListener;
import hgloot.plugin.ultraroyales.listeners.FrozenChestpieceListener;
import hgloot.plugin.ultraroyales.listeners.HoglinHornListener;
import hgloot.plugin.ultraroyales.listeners.KnightsSaddleListener;
import hgloot.plugin.ultraroyales.listeners.LifeElixirListener;
import hgloot.plugin.ultraroyales.listeners.SonicWrenchListener;
import hgloot.plugin.ultraroyales.listeners.SpongeSaberListener;
import hgloot.plugin.ultraroyales.listeners.SpiderStaffListener;
import dev.zxdzero.ZxdzeroEvents.ItemHelper;
import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import dev.zxdzero.ZxdzeroEvents.registries.ItemActionRegistry;
import dev.zxdzero.ZxdzeroEvents.tooltip.Tooltip;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.util.SafeItemDelivery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class Items {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    public static final NamespacedKey knightsHorse = NamespacedKey.fromString("knightshorse", UltraRoyales.getPlugin());

    public static void registerBehavior() {

        // Knight's Saddle
        ItemActionRegistry.register(knightsSaddle(), (player, item) -> {
            int cooldown = CooldownRegistry.getCooldown(player, UltraRoyales.saddleCooldown);
            if (cooldown != 0) {
                player.sendMessage(HydraMessages.cooldown("Knight's Saddle", cooldown));
                return;
            }

            if (player.getVehicle() != null) {
                player.sendMessage(HydraMessages.error("Dismount to use Knight's Saddle"));
                return;
            }

            ItemStack horseArmor = new ItemStack(Material.IRON_HORSE_ARMOR);
            ItemMeta horseArmorMeta = horseArmor.getItemMeta();
            EquippableComponent horseArmorEquippableComponent = horseArmorMeta.getEquippable();
            horseArmorEquippableComponent.setSlot(EquipmentSlot.BODY);
            horseArmorEquippableComponent.setModel(new NamespacedKey("ultraroyales", "knight_horse"));
            horseArmorMeta.setEquippable(horseArmorEquippableComponent);
            horseArmor.setItemMeta(horseArmorMeta);

            Horse horse = (Horse) player.getWorld().spawnEntity(player.getLocation(), EntityType.HORSE);
            horse.setTamed(true);
            horse.setOwner(player);
            horse.setAdult();
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            horse.getInventory().setArmor(horseArmor);
            horse.setColor(Horse.Color.GRAY);
            horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.5);
            horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
            horse.setHealth(40);
            horse.setJumpStrength(1.0);
            horse.setInvulnerable(true);
            final Vector forward = lookChargeDirection(player);
            horse.getPersistentDataContainer().set(knightsHorse, PersistentDataType.BOOLEAN, true);
            KnightsSaddleListener.beginCharge(horse, forward);
            horse.setRotation(yawForDirection(forward), 0.0F);
            horse.addPassenger(player);

            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.25, 0), 42, 0.55, 0.12, 0.55, 0.04);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.9f, 0.75f);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || player.isDead() || !horse.isValid() || horse.isDead()) {
                        endKnightsSaddleCharge(player, horse);
                        cancel();
                        return;
                    }

                    if (!horse.getPassengers().contains(player)) {
                        endKnightsSaddleCharge(player, horse);
                        cancel();
                        return;
                    }

                    if (ticks++ >= KnightsSaddleListener.getChargeDurationTicks(horse)) {
                        endKnightsSaddleCharge(player, horse);
                        cancel();
                        return;
                    }
                    if (KnightsSaddleListener.triggerTerrainSmashIfBlocked(horse, player, forward)) {
                        endKnightsSaddleCharge(player, horse);
                        cancel();
                        return;
                    }

                    double ramp = ticks <= KnightsSaddleListener.PREPARE_TICKS
                        ? 0.34D + (0.035D * ticks)
                        : 1.08D;
                    if (ticks > KnightsSaddleListener.PREPARE_TICKS && ticks % 4 == 0) {
                        Location hoof = horse.getLocation().clone().add(0.0D, 0.12D, 0.0D);
                        horse.getWorld().spawnParticle(Particle.CLOUD, hoof, 8, 0.35D, 0.08D, 0.35D, 0.01D);
                        horse.getWorld().playSound(hoof, Sound.ENTITY_HORSE_GALLOP, 0.55f, 0.65f);
                    }
                    Vector dash = KnightsSaddleListener.getChargeDirection(horse).multiply(ramp);
                    horse.setVelocity(new Vector(dash.getX(), horse.getVelocity().getY(), dash.getZ()));
                    horse.setRotation(yawForDirection(dash), 0.0F);
                    KnightsSaddleListener.applyChargeDamage(horse, player);
                }
            }.runTaskTimer(UltraRoyales.getPlugin(), 0L, 1L);
        });

        // Spider Staff
        ItemActionRegistry.register(spiderStaff(), (player, item) -> {
            if (CooldownRegistry.getCooldown(player, UltraRoyales.staffCooldown) == 0) {
                SpiderStaffListener.castVenomWave(player);
                CooldownRegistry.setCooldown(player, UltraRoyales.staffCooldown, 45);
            }
        });

        // Heart item
        ItemActionRegistry.register(heartItem(), (player, item) -> {
            if (!player.isSneaking()) return;
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth == null) return;
            double currentMax = maxHealth.getBaseValue();
            if (currentMax >= 40.0D) {
                player.sendMessage(HydraMessages.warning("Heart Bundle cap reached"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
                return;
            }
            maxHealth.setBaseValue(Math.min(40.0D, currentMax + 2.0D));
            item.setAmount(item.getAmount() - 1);
        });

        // Bingo Spawn Egg
        ItemActionRegistry.register(bingoSpawnEgg(), (player, item) -> {
            RayTraceResult rayTraceResult = player.rayTraceBlocks(5);
            if (rayTraceResult == null) return;

            Skeleton skeleton = (Skeleton) player.getWorld().spawn(rayTraceResult.getHitPosition().toLocation(player.getWorld()), EntityType.SKELETON.getEntityClass());
            skeleton.getEquipment().setItemInMainHand(null);
            EntityEquipment equipment = skeleton.getEquipment();

            ItemStack bingoHelmet = new ItemStack(Material.DIRT);
            ItemMeta bingoHelmetMeta = bingoHelmet.getItemMeta();
            CustomModelDataComponent customModelData = bingoHelmetMeta.getCustomModelDataComponent();
            customModelData.setStrings(List.of("ultraroyales:bingo_head"));
            bingoHelmetMeta.setCustomModelDataComponent(customModelData);
            bingoHelmet.setItemMeta(bingoHelmetMeta);

            ItemStack bingoChestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemMeta bingoChestplateMeta = bingoChestplate.getItemMeta();
            EquippableComponent bingoChestplateEquippableComponent = bingoChestplateMeta.getEquippable();
            bingoChestplateEquippableComponent.setSlot(EquipmentSlot.CHEST);
            bingoChestplateEquippableComponent.setModel(new NamespacedKey("ultraroyales", "bingo"));
            bingoChestplateMeta.setEquippable(bingoChestplateEquippableComponent);
            bingoChestplate.setItemMeta(bingoChestplateMeta);

            ItemStack bingoLeggings = new ItemStack(Material.DIAMOND_LEGGINGS);
            ItemMeta bingoLeggingsMeta = bingoLeggings.getItemMeta();
            EquippableComponent bingoLeggingsEquippableComponent = bingoLeggingsMeta.getEquippable();
            bingoLeggingsEquippableComponent.setSlot(EquipmentSlot.LEGS);
            bingoLeggingsEquippableComponent.setModel(new NamespacedKey("ultraroyales", "bingo"));
            bingoLeggingsMeta.setEquippable(bingoLeggingsEquippableComponent);
            bingoLeggings.setItemMeta(bingoLeggingsMeta);

            ItemStack bingoBoots = new ItemStack(Material.DIAMOND_BOOTS);
            ItemMeta bingoBootsMeta = bingoBoots.getItemMeta();
            EquippableComponent bingoBootsEquippableComponent = bingoBootsMeta.getEquippable();
            bingoBootsEquippableComponent.setSlot(EquipmentSlot.FEET);
            bingoBootsEquippableComponent.setModel(new NamespacedKey("ultraroyales", "bingo"));
            bingoBootsMeta.setEquippable(bingoBootsEquippableComponent);
            bingoBoots.setItemMeta(bingoBootsMeta);

            equipment.setHelmet(bingoHelmet);
            equipment.setChestplate(bingoChestplate);
            equipment.setLeggings(bingoLeggings);
            equipment.setBoots(bingoBoots);
            skeleton.setCanPickupItems(false);

            BingoTheClownListener.startMove(skeleton);
            BingoTheClownListener.startFriendly(skeleton);
        });

        // Royal Roulette
        ItemActionRegistry.register(royalRoulette(), (player, item) -> {
            ItemStack reward = randomLegendaryReward(player);
            if (reward == null) {
                return;
            }
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else if (player.getInventory().getItemInMainHand().isSimilar(item)) {
                player.getInventory().setItemInMainHand(null);
            } else if (player.getInventory().getItemInOffHand().isSimilar(item)) {
                player.getInventory().setItemInOffHand(null);
            }
            SafeItemDelivery.Destination destination = SafeItemDelivery.deliver(player, reward);
            if (destination == SafeItemDelivery.Destination.ENDER_CHEST) {
                player.sendMessage(HydraMessages.warning("Your inventory was full, so the legendary was placed in your Ender Chest."));
            } else if (destination == SafeItemDelivery.Destination.OWNED_DROP) {
                player.sendMessage(HydraMessages.warning("Your inventory and Ender Chest were full, so the legendary was dropped for you."));
            }
            player.sendMessage(HydraMessages.success("Royal Roulette granted: ")
                .append(reward.getItemMeta().displayName() == null
                    ? Component.text(reward.getType().name(), HydraMessages.HYDRA_NEON_CYAN)
                    : reward.getItemMeta().displayName()));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.25f);
        });

        // Sonic Wrench
        ItemActionRegistry.register(sonicWrench(), (player, item) -> SonicWrenchListener.cast(player));

        // Frozen Chestpiece
        ItemActionRegistry.register(frozenChestpiece(), (player, item) -> FrozenChestpieceListener.activate(player));

        // Hoglin Horn
        ItemActionRegistry.register(hoglinHorn(), (player, item) -> HoglinHornListener.use(player));
    }

    public static ItemStack heartItem() {
        ItemStack heart = new ItemStack(Material.RESIN_BRICK);
        ItemMeta meta = heart.getItemMeta();
        meta.lore(List.of(Tooltip.SHIFT_RIGHT_CLICK.toComponent("to use")));
        meta.displayName(Component.text("Heart").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:heart"));
        meta.setCustomModelDataComponent(customModelData);

        heart.setItemMeta(meta);

        return heart;
    }

    public static ItemStack knightsSaddle() {
        ItemStack saddle = new ItemStack(Material.SADDLE);
        ItemMeta meta = saddle.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(loreLine("Summons a warhorse built for devastating charges.", NamedTextColor.GRAY));
        lore.add(loreLine("RIGHT CLICK to summon your warhorse and begin charging.", NamedTextColor.GREEN));
        lore.add(loreLine("Trample enemies to damage, knock back, and slow them.", NamedTextColor.RED));
        lore.add(loreLine("Hitting enemies extends the charge. • 45s Cooldown", NamedTextColor.GRAY));
        meta.lore(lore);
        meta.displayName(Component.text("Knight's Saddle").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:knightssaddle"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);

        saddle.setItemMeta(meta);

        return saddle;
    }

    public static ItemStack dwarvenBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(loreLine("A heavy bow with slower, more powerful arrows.", NamedTextColor.GRAY));
        lore.add(loreLine("Repeated hits build Fracture.", NamedTextColor.RED));
        lore.add(loreLine("3 Fractures SHATTER the target, dealing bonus damage and a brief debuff.", NamedTextColor.RED));
        meta.lore(lore);
        meta.displayName(Component.text("Dwarf Bow").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:dwarvenbow"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);

        bow.setItemMeta(meta);
        bow.addUnsafeEnchantment(Enchantment.POWER, 3);

        return bow;
    }

    public static ItemStack fractialDwarvenBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.displayName(Component.text("Fractial's Dwarven Bow").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:fractial_dwarvenbow"));
        meta.setCustomModelDataComponent(customModelData);

        bow.setItemMeta(meta);

        return bow;
    }

    public static ItemStack ghlochester() {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(loreLine("A powerful hook built to drag enemies out of position.", NamedTextColor.GRAY));
        lore.add(loreLine("Hook enemies to pull and slow them.", NamedTextColor.RED));
        lore.add(loreLine("Hook the same target again within 20s to trigger DEEP HOOK.", NamedTextColor.RED));
        lore.add(loreLine("Dealing bonus damage with a stronger pull and reveal.", NamedTextColor.RED));
        meta.lore(lore);
        meta.displayName(Component.text("Ghlochester").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:ghlochester"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);

        rod.setItemMeta(meta);

        return rod;
    }

    public static ItemStack spiderStaff() {
        ItemStack saber = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = saber.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(loreLine("A venomous staff that marks enemies for your spiders.", NamedTextColor.GRAY));
        lore.add(loreLine("RIGHT CLICK to release a Venom Wave, poisoning and slowing enemies.", NamedTextColor.RED));
        lore.add(loreLine("Marked or poisoned enemies are swarmed by Cave Spiders.", NamedTextColor.RED));
        lore.add(loreLine("Duration: 12s", NamedTextColor.GRAY));
        meta.lore(lore);
        meta.displayName(Component.text("Spider Staff", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:spiderstaff"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);

        saber.setItemMeta(meta);

        return saber;
    }

    public static ItemStack memoryBreaker() {
        ItemStack breaker = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = breaker.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A weapon that breaks the mind"));
        lore.add(info("before the body."));
        lore.add(blank());
        lore.add(info("Hit to scramble the target's hotbar"));
        lore.add(info("for 4 seconds."));
        lore.add(blank());
        lore.add(info("Items return to their original slots"));
        lore.add(info("after the effect ends."));
        meta.lore(null);
        meta.displayName(Component.text("Mnemosyne").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:memory_breaker"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        breaker.setItemMeta(meta);
        return breaker;
    }

    public static ItemStack sonicWrench() {
        ItemStack wrench = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = wrench.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A tool that unloads a full spread"));
        lore.add(info("of sonic-charged wrenches."));
        lore.add(blank());
        lore.add(info("Right-click to fire 5 mini wrenches."));
        lore.add(blank());
        lore.add(info("Each wrench detonates for 4 damage"));
        lore.add(info("and heavy knockback."));
        lore.add(blank());
        lore.add(info("Effective range is about 30 blocks."));
        lore.add(blank());
        lore.add(info("Cooldown is 6 seconds."));
        meta.lore(null);
        meta.displayName(Component.text("Sculk Wrench").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:sonic_wrench"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        wrench.setItemMeta(meta);
        return wrench;
    }

    public static ItemStack aegis() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemMeta meta = shield.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A living shield built to turn"));
        lore.add(info("enemy pressure back on them."));
        lore.add(blank());
        lore.add(info("Right-click to raise a brief block."));
        lore.add(blank());
        lore.add(info("Blocked attacks reflect back"));
        lore.add(info("for double damage."));
        lore.add(blank());
        lore.add(info("Cooldown is 2 minutes."));
        meta.lore(null);
        meta.displayName(Component.text("Aegis").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:aegis"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        shield.setItemMeta(meta);
        return shield;
    }

    public static ItemStack frozenChestpiece() {
        ItemStack chestpiece = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestpiece.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A relic that turns the wearer"));
        lore.add(info("into a crushing snowball."));
        lore.add(blank());
        lore.add(info("Shift right-click while worn to activate."));
        lore.add(blank());
        lore.add(info("Roll for 8 seconds at high speed."));
        lore.add(blank());
        lore.add(info("Targets in the path are frozen"));
        lore.add(info("and take crush damage."));
        lore.add(blank());
        lore.add(info("Cooldown is 2 minutes."));
        meta.lore(null);
        meta.displayName(Component.text("Frozen Chestpiece").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:frozen_chestpiece"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        chestpiece.setItemMeta(meta);
        return chestpiece;
    }

    public static ItemStack meltedScythe() {
        ItemStack scythe = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = scythe.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A weapon that melts through armor"));
        lore.add(info("with relentless heat."));
        lore.add(blank());
        lore.add(info("Base damage is 5."));
        lore.add(blank());
        lore.add(info("Deals +1 damage for each armor point"));
        lore.add(info("on the target up to +8."));
        lore.add(blank());
        lore.add(info("All hits apply Fire Aspect V."));
        meta.lore(null);
        meta.displayName(Component.text("Melted Scythe").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:melted_scythe"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);
        scythe.setItemMeta(meta);
        return scythe;
    }

    public static ItemStack spongeSaber() {
        return spongeSaber(null);
    }

    public static ItemStack spongeSaber(ItemMeta meta) {
        ItemStack saber = new ItemStack(Material.DIAMOND_SWORD);
        if (meta == null) {
            meta = saber.getItemMeta();
        }
        meta = ItemHelper.weaponBuilder(meta, 8.5, 1.6);
        List<Component> statLore = meta.lore();
        if (statLore == null) {
            statLore = new ArrayList<>();
        }

        List<Component> lore = new ArrayList<>();
        addSpongeSaberDescription(lore);
        lore.addAll(statLore);
        meta.lore(lore);
        meta.displayName(Component.text("Sponge Saber").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:sponge_saber"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setUnbreakable(true);

        saber.setItemMeta(meta);

        return saber;
    }

    public static ItemStack wetSpongeSaber(int power, ItemMeta meta) {
        ItemStack saber = new ItemStack(Material.DIAMOND_SWORD);
        if (meta == null) {
            meta = saber.getItemMeta();
        }
        meta = ItemHelper.weaponBuilder(meta, 8.5, 1.6);
        List<Component> statLore = meta.lore();
        if (statLore == null) {
            statLore = new ArrayList<>();
        }

        List<Component> lore = new ArrayList<>();
        addSpongeSaberDescription(lore);
        lore.addAll(statLore);
        meta.lore(lore);
        meta.displayName(Component.text("Wet Sponge Saber").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:wet_sponge_saber"));
        meta.setCustomModelDataComponent(customModelData);
        meta.getPersistentDataContainer().set(SpongeSaberListener.SPONGE_POWER, PersistentDataType.INTEGER, power);
        meta.setUnbreakable(true);

        saber.setItemMeta(meta);

        return saber;
    }

    public static ItemStack wetSpongeSaber() {
        return wetSpongeSaber(3, null);
    }

    public static ItemStack wetSpongeSaber(ItemMeta meta) {
        return wetSpongeSaber(3, meta);
    }

    public static ItemStack bingoSpawnEgg() {
        ItemStack bingoSpawn = new ItemStack(Material.SNIFFER_SPAWN_EGG);
        ItemMeta meta = bingoSpawn.getItemMeta();
        meta.displayName(Component.text("Bingo the Clown").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:bingo_spawn_egg"));
        meta.setCustomModelDataComponent(customModelData);

        bingoSpawn.setItemMeta(meta);

        return bingoSpawn;
    }

    public static ItemStack skeletalBarber() {
        ItemStack staff = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = staff.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(loreLine("A vicious blade that hunts down a single marked target.", NamedTextColor.GRAY));
        lore.add(loreLine("Melee hits apply BONE MARK.", NamedTextColor.RED));
        lore.add(loreLine("Marked enemies take increased damage and can be EXECUTED at low health.", NamedTextColor.RED));
        lore.add(loreLine("Killing your marked target heals you.", NamedTextColor.GREEN));
        lore.add(loreLine("Bone Mark expires after 5s without pressure.", NamedTextColor.GRAY));
        meta.lore(lore);
        meta.displayName(Component.text("Skeletal Barber").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:skeletalbarber"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setItemModel(new NamespacedKey("ultraroyales", "skeletalbarber"));
        meta.setUnbreakable(true);

        staff.setItemMeta(meta);

        return staff;
    }

    public static ItemStack electricConch() {
        ItemStack conch = new ItemStack(Material.TRIDENT);
        ItemMeta meta = conch.getItemMeta();
        meta = ItemHelper.weaponBuilder(meta, 0, 4);
        List<Component> statLore = meta.lore();
        if (statLore == null) {
            statLore = new ArrayList<>();
        }
        List<Component> lore = new ArrayList<>();
        lore.add(loreLine("A storm-powered conch that shocks nearby enemies.", NamedTextColor.GRAY));
        lore.add(loreLine("RIGHT CLICK to activate Storm Aura around yourself.", NamedTextColor.YELLOW));
        lore.add(loreLine("Nearby enemies are struck, slowed, revealed, and knocked away.", NamedTextColor.RED));
        lore.add(loreLine("2 Charges • 4s Delay • 30s Recharge", NamedTextColor.GRAY));
        lore.addAll(statLore);
        meta.lore(lore);
        meta.displayName(Component.text("Electric Conch").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:electricconch"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setItemModel(new NamespacedKey("ultraroyales", "electricconch"));
        meta.removeEnchant(Enchantment.RIPTIDE);

        conch.setItemMeta(meta);

        return conch;
    }

    public static ItemStack ghostKnife() {
        ItemStack knife = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = knife.getItemMeta();
        if (meta == null) {
            return knife;
        }

        meta = ItemHelper.weaponBuilder(meta, 0, 4);
        meta.displayName(Component.text("Ghost Knife")
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(info("A blade that lunges through the space"));
        lore.add(info("between you and your target."));
        lore.add(blank());
        lore.add(info("Right-click to Lunge II."));
        lore.add(blank());
        lore.add(info("Short self debuffs after use."));
        meta.lore(lore);
        meta.setUnbreakable(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:ghostknife"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setItemModel(new NamespacedKey("ultraroyales", "ghostknife"));
        knife.setItemMeta(meta);
        knife.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
        return knife;
    }


    // COMMON CRAFTS
    public static ItemStack lifeElixir(int power) {
        ItemStack elixir = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) elixir.getItemMeta();
        meta.getPersistentDataContainer().set(LifeElixirListener.ELIXIR_POWER, PersistentDataType.INTEGER, power);

        PotionEffect effect = new PotionEffect(
                PotionEffectType.ABSORPTION,
                power >= 1 ? 200 : 120,
                power >= 1 ? 2 : 1,
                true,
                true
        );
        meta.addCustomEffect(effect, true);
        meta.addCustomEffect(new PotionEffect(
            PotionEffectType.REGENERATION,
            power >= 1 ? 80 : 40,
            power >= 1 ? 1 : 0,
            true,
            true
        ), true);
        meta.setColor(Color.fromRGB(255, 200, 50)); // CAN REMOVE IF CUSTOM MODEL BEING USED

        meta.displayName(Component.text(power == 1 ? "Life Elixir" : "Lesser Life Elixir").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("A light absorption burst, not a full reset.")
            .color(TextColor.color(0x0099db))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Single use.")
            .color(TextColor.color(0x0099db))
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:lifeelixir"));
        meta.setCustomModelDataComponent(customModelData);

        elixir.setItemMeta(meta);
        return elixir;
    }

    public static ItemStack emptyLifeElixirBottle() {
        ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
        ItemMeta meta = bottle.getItemMeta();
        if (meta == null) {
            return bottle;
        }
        meta.displayName(Component.text("Empty Life Elixir")
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:empty_lifeelixir"));
        meta.setCustomModelDataComponent(customModelData);
        bottle.setItemMeta(meta);
        return bottle;
    }

    public static ItemStack gildedSword(Material baseSword) {
        if (baseSword == null || !baseSword.name().endsWith("_SWORD")) {
            baseSword = Material.IRON_SWORD;
        }
        ItemStack sword = new ItemStack(baseSword);
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) {
            return sword;
        }
        String materialName = titleCase(baseSword.name().replace("_SWORD", "").replace('_', ' '));
        meta.displayName(Component.text("Gilded " + materialName + " Sword")
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        String modelId = "quartz_" + baseSword.name().toLowerCase(Locale.ROOT);
        customModelData.setStrings(List.of("hydra:" + modelId));
        meta.setCustomModelDataComponent(customModelData);
        meta.setItemModel(new NamespacedKey("hydra", modelId));
        // Re-apply the base sword's vanilla attribute modifiers first. Calling
        // addAttributeModifier wipes an item's implicit default modifiers, which is why
        // gilded swords were losing their vanilla attack speed/damage. Re-adding the
        // defaults keeps the sword identical to its base, then we stack the gilded bonus.
        for (var entry : baseSword.getDefaultAttributeModifiers(EquipmentSlot.HAND).entries()) {
            meta.addAttributeModifier(entry.getKey(), entry.getValue());
        }
        meta.addAttributeModifier(
            Attribute.ATTACK_DAMAGE,
            new AttributeModifier(
                new NamespacedKey(UltraRoyales.getPlugin(), "gilded_" + baseSword.name().toLowerCase(Locale.ROOT) + "_damage"),
                0.15D,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            )
        );
        sword.setItemMeta(meta);
        return sword;
    }

    public static boolean isGildedSword(ItemStack item) {
        if (item == null || !RecipeBookCatalog.GILDED_SWORD_MATERIALS.contains(item.getType())) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasItemModel() || !meta.hasCustomModelDataComponent()) {
            return false;
        }
        String modelId = "quartz_" + item.getType().name().toLowerCase(Locale.ROOT);
        return new NamespacedKey("hydra", modelId).equals(meta.getItemModel())
            && meta.getCustomModelDataComponent().getStrings().contains("hydra:" + modelId);
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public static ItemStack bookOfWelding() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A lost forge manual bound in heat.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Component.text("Right-click an anvil while holding").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("a pickaxe in your other hand.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Component.text("That pickaxe will instantly smelt").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("smeltable ore drops when mined.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Single use.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.displayName(Component.text("Book of Welding").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:book_of_welding"));
        meta.setCustomModelDataComponent(customModelData);
        meta.setEnchantmentGlintOverride(true);
        book.setItemMeta(meta);
        return book;
    }

    public static ItemStack royalRoulette() {
        ItemStack roulette = new ItemStack(Material.CLOCK);
        ItemMeta meta = roulette.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A relic of pure chance, meant for those").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("who trust fate over control.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Tooltip.RIGHT_CLICK.toComponent("to receive a random legendary."));
        lore.add(Component.text("Can roll legendaries normally").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("not in the active pool.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Single use.").color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.displayName(Component.text("Royal Roulette").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:royal_roulette"));
        meta.setCustomModelDataComponent(customModelData);
        roulette.setItemMeta(meta);
        return roulette;
    }

    public static ItemStack goblinMask() {
        ItemStack mask = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = mask.getItemMeta();
        meta.lore(null);
        meta.displayName(Component.text("Goblin Mask").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:goblin_mask"));
        meta.setCustomModelDataComponent(customModelData);
        mask.setItemMeta(meta);
        return mask;
    }

    public static ItemStack hookedGrappler() {
        ItemStack grappler = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = grappler.getItemMeta();
        meta.lore(null);
        meta.displayName(Component.text("Hooked Grappler").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:hooked_grappler"));
        meta.setCustomModelDataComponent(customModelData);
        grappler.setItemMeta(meta);
        return grappler;
    }

    public static ItemStack diamondTotem() {
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = totem.getItemMeta();
        meta.lore(null);
        meta.displayName(Component.text("Diamond Totem").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setEnchantmentGlintOverride(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:diamond_totem"));
        meta.setCustomModelDataComponent(customModelData);
        totem.setItemMeta(meta);
        return totem;
    }

    public static ItemStack wonderPickaxe() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        meta.lore(null);
        meta.displayName(Component.text("Wonder Pickaxe").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:wonder_pickaxe"));
        meta.setCustomModelDataComponent(customModelData);
        pickaxe.setItemMeta(meta);
        return pickaxe;
    }

    public static ItemStack lokiScepter() {
        ItemStack scepter = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = scepter.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("Sneak right-click to switch Pearl/Spread."));
        lore.add(blank());
        lore.add(info("Pearls burst around impact."));
        lore.add(info("92% curse blast, 8% blessing."));
        lore.add(info("Spread fires three random projectiles."));
        meta.lore(null);
        meta.displayName(Component.text("Loki's Scepter").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setEnchantmentGlintOverride(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:loki_scepter"));
        meta.setCustomModelDataComponent(customModelData);
        scepter.setItemMeta(meta);
        return scepter;
    }

    public static ItemStack druidsDaggers() {
        ItemStack daggers = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = daggers.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("Twin daggers built for rapid double hits."));
        lore.add(blank());
        lore.add(info("Each swing lands with both blades."));
        lore.add(blank());
        lore.add(info("Positive potion effects on targets"));
        lore.add(info("are twisted into negative ones."));
        meta.lore(null);
        meta.displayName(Component.text("Druid's Daggers").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.setItemModel(new NamespacedKey("ultraroyales", "druids_daggers"));
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:druids_daggers"));
        meta.setCustomModelDataComponent(customModelData);
        daggers.setItemMeta(meta);
        return daggers;
    }

    public static ItemStack architectsDrill() {
        ItemStack drill = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = drill.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A siege weapon that ramps into"));
        lore.add(info("a relentless drill combo."));
        lore.add(blank());
        lore.add(info("Right-click to activate Drill Mode."));
        lore.add(blank());
        lore.add(info("Drill Mode lasts for 5 seconds."));
        lore.add(blank());
        lore.add(info("Auto-hits for 0.5 damage while active."));
        lore.add(blank());
        lore.add(info("Speed ramps up to 3x normal"));
        lore.add(info("and gains spear reach."));
        lore.add(blank());
        lore.add(info("Overheats for 60 seconds after use."));
        meta.lore(null);
        meta.displayName(Component.text("Architect's Drill").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:architects_drill"));
        meta.setCustomModelDataComponent(customModelData);
        drill.setItemMeta(meta);
        return drill;
    }

    public static ItemStack seventhSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A duelist's blade that scales"));
        lore.add(info("directly with your health."));
        lore.add(blank());
        lore.add(info("1 heart gives 1 damage."));
        lore.add(blank());
        lore.add(info("7 hearts gives 7 damage."));
        lore.add(blank());
        lore.add(info("Full health gives 10 damage."));
        meta.lore(null);
        meta.displayName(Component.text("Seventh Sword").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:seventh_sword"));
        meta.setCustomModelDataComponent(customModelData);
        sword.setItemMeta(meta);
        return sword;
    }

    public static ItemStack monarchWings() {
        ItemStack wings = new ItemStack(Material.ELYTRA);
        ItemMeta meta = wings.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A royal set of wings made for"));
        lore.add(info("unlimited aerial momentum."));
        lore.add(blank());
        lore.add(info("Protection III while worn."));
        lore.add(blank());
        lore.add(info("Gliding momentum has no cap."));
        lore.add(blank());
        lore.add(info("Shift while gliding to ascend 20 blocks."));
        meta.lore(null);
        meta.displayName(Component.text("Monarch Wings").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.PROTECTION, 3, true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:monarch_wings"));
        meta.setCustomModelDataComponent(customModelData);
        wings.setItemMeta(meta);
        return wings;
    }

    public static ItemStack hoglinHorn() {
        ItemStack horn = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = horn.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("Right-click to summon a primary"));
        lore.add(info("war hog and four hunting hogs."));
        lore.add(blank());
        lore.add(info("Right-click again to dash."));
        lore.add(blank());
        lore.add(info("Sneak right-click to stomp."));
        lore.add(blank());
        lore.add(info("Summon cooldown is 20 seconds."));
        meta.lore(null);
        meta.displayName(Component.text("Hoglin Horn").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:hoglin_horn"));
        meta.setCustomModelDataComponent(customModelData);
        horn.setItemMeta(meta);
        return horn;
    }

    public static ItemStack enderious() {
        ItemStack hammer = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = hammer.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A plasma weapon that distorts"));
        lore.add(info("space through unstable impact."));
        lore.add(blank());
        lore.add(info("Right-click to fire a plasma bolt."));
        lore.add(blank());
        lore.add(info("Impact damages and disrupts enemies."));
        lore.add(blank());
        lore.add(info("Cooldown is 8 seconds."));
        meta.lore(null);
        meta.displayName(Component.text("Plasma Distorter").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(UltraRoyales.enderiousItemKey, PersistentDataType.BOOLEAN, true);
        hammer.setItemMeta(meta);
        return hammer;
    }

    public static ItemStack scarabsWrath() {
        ItemStack sword = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = sword.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(info("A sun-cursed blade that infects prey"));
        lore.add(info("with desert rot and rising pressure."));
        lore.add(blank());
        lore.add(info("Hit to apply Rot for 6 seconds."));
        lore.add(blank());
        lore.add(info("Rot damage increases the longer"));
        lore.add(info("the target remains in combat."));
        lore.add(blank());
        lore.add(info("If the target survives the full duration,"));
        lore.add(info("Rot bursts for bonus damage."));
        meta.lore(null);
        meta.displayName(Component.text("Scarab's Wrath").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
        customModelData.setStrings(List.of("ultraroyales:scarabs_wrath"));
        meta.setCustomModelDataComponent(customModelData);
        sword.setItemMeta(meta);
        return sword;
    }

    public static ItemStack withModeLabel(ItemStack item, boolean hungerGames) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemStack labeled = item.clone();
        ItemMeta meta = labeled.getItemMeta();
        if (meta == null) {
            return labeled;
        }
        List<Component> lore = meta.lore();
        List<Component> updatedLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        updatedLore.removeIf(line -> {
            String text = PLAIN_TEXT.serialize(line).trim();
            return text.equalsIgnoreCase("HG") || text.equalsIgnoreCase("BR");
        });
        if (!updatedLore.isEmpty()) {
            Component lastLine = updatedLore.get(updatedLore.size() - 1);
            if (!PLAIN_TEXT.serialize(lastLine).trim().isEmpty()) {
                updatedLore.add(Component.empty());
            }
        }
        updatedLore.add(
            Component.text(hungerGames ? "HG" : "BR")
                .color(hungerGames ? NamedTextColor.AQUA : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
        );
        meta.lore(updatedLore);
        labeled.setItemMeta(meta);
        return labeled;
    }

    public static ItemStack withHungerGamesVariant(String weaponId, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemStack updated = item.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) {
            return updated;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            return updated;
        }
        List<Component> updatedLore = switch ((weaponId == null ? "" : weaponId.toLowerCase())) {
            case "dwarven_bow" -> buildDwarvenBowHgLore(lore);
            case "ghlochester" -> buildGhlochesterHgLore(lore);
            case "spider_staff" -> buildSpiderStaffHgLore(lore);
            case "sponge_saber" -> buildSpongeSaberHgLore(lore);
            case "skeletal_barber" -> buildSkeletalBarberHgLore(lore);
            case "electric_conch" -> buildElectricConchHgLore(lore);
            case "royal_roulette" -> buildRoyalRouletteHgLore(lore);
            default -> new ArrayList<>(lore);
        };
        meta.lore(updatedLore);
        updated.setItemMeta(meta);
        return updated;
    }

    private static List<Component> buildDwarvenBowHgLore(List<Component> original) {
        return new ArrayList<>(original);
    }

    private static List<Component> buildGhlochesterHgLore(List<Component> original) {
        return new ArrayList<>(original);
    }

    private static List<Component> buildSpiderStaffHgLore(List<Component> original) {
        return new ArrayList<>(original);
    }

    private static List<Component> buildSpongeSaberHgLore(List<Component> original) {
        List<Component> lore = new ArrayList<>(original);
        replaceLine(lore, "8.5 Attack Damage", darkGreen(" 7 Attack Damage"));
        replaceLine(lore, "8.5 Attack Damage", darkGreen(" 7 Attack Damage"), 10);
        lore.add(blank());
        lore.add(info("Cannot be enchanted in HG."));
        return lore;
    }

    private static List<Component> buildSkeletalBarberHgLore(List<Component> original) {
        return new ArrayList<>(original);
    }

    private static List<Component> buildElectricConchHgLore(List<Component> original) {
        List<Component> lore = new ArrayList<>(original);
        lore.add(blank());
        lore.add(info("Melee hits deal +1 heart true damage in HG."));
        return lore;
    }

    private static List<Component> buildRoyalRouletteHgLore(List<Component> original) {
        List<Component> lore = new ArrayList<>(original);
        replaceLine(lore, "Can roll legendaries normally", info("Rolls from the HG legendary pool."));
        replaceLine(lore, "not in the active pool.", info("BR-only legendaries are excluded."));
        return lore;
    }

    private static List<Component> copyRange(List<Component> original, int fromInclusive, int toExclusive) {
        int safeFrom = Math.max(0, fromInclusive);
        int safeTo = Math.max(safeFrom, Math.min(original.size(), toExclusive));
        return new ArrayList<>(original.subList(safeFrom, safeTo));
    }

    private static void replaceLine(List<Component> lore, String plainText, Component replacement) {
        replaceLine(lore, plainText, replacement, -1);
    }

    private static void replaceLine(List<Component> lore, String plainText, Component replacement, int preferredIndex) {
        if (preferredIndex >= 0 && preferredIndex < lore.size()) {
            String current = normalizedPlain(lore.get(preferredIndex));
            if (current.equals(normalized(plainText))) {
                lore.set(preferredIndex, replacement);
                return;
            }
        }
        for (int i = 0; i < lore.size(); i++) {
            if (normalizedPlain(lore.get(i)).equals(normalized(plainText))) {
                lore.set(i, replacement);
                return;
            }
        }
    }

    private static String normalizedPlain(Component component) {
        return normalized(PLAIN_TEXT.serialize(component));
    }

    private static String normalized(String text) {
        return text == null ? "" : text.trim();
    }

    private static Component blank() {
        return Component.text("");
    }

    private static Component info(String text) {
        return Component.text(text).color(TextColor.color(0x0099db)).decoration(TextDecoration.ITALIC, false);
    }

    private static Component loreLine(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static void addSpongeSaberDescription(List<Component> lore) {
        lore.add(loreLine("A water-powered saber that grows stronger near water.", NamedTextColor.GRAY));
        lore.add(loreLine("Hold near water to build your Water Meter.", NamedTextColor.AQUA));
        lore.add(loreLine("RIGHT CLICK to release Water Slash, scaling with stored water.", NamedTextColor.AQUA));
        lore.add(loreLine("Water Slash: 6s Cooldown", NamedTextColor.GRAY));
        lore.add(loreLine("SNEAK + RIGHT CLICK to absorb nearby water faster.", NamedTextColor.GREEN));
        lore.add(loreLine("4s Cooldown", NamedTextColor.GRAY));
    }

    private static Component darkGreen(String text) {
        return Component.text(text, NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false);
    }

    private static void endKnightsSaddleCharge(Player player, Horse horse) {
        KnightsSaddleListener.endCharge(horse);

        if (horse != null && horse.isValid() && horse.getPassengers().contains(player)) {
            player.leaveVehicle();
        }

        if (horse != null && horse.isValid() && !horse.isDead()) {
            horse.remove();
        }

        if (player != null && player.isOnline()) {
            KnightsSaddleListener.applyRecovery(player);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, -1, 0), 40, 0.5, 0.5, 0.5, 0.02);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_HORSE_BREATHE, 1.0f, 0.95f);
            CooldownRegistry.setCooldown(player, UltraRoyales.saddleCooldown, KnightsSaddleListener.SADDLE_COOLDOWN_SECONDS);
        }
    }

    private static Vector lookChargeDirection(Player player) {
        Vector direction = player.getLocation().getDirection().clone().setY(0.0D);
        if (direction.lengthSquared() <= 0.0001D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    private static float yawForDirection(Vector direction) {
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            return direction.getX() > 0.0D ? -90.0F : 90.0F;
        }
        return direction.getZ() > 0.0D ? 0.0F : 180.0F;
    }

    private static ItemStack randomLegendaryReward(Player player) {
        boolean hungerGames = player != null && LegendaryModeRules.isHungerGamesWorld(player.getWorld());
        if (hungerGames) {
            return OriginalLegendaryRecipes.randomProductionReward(ThreadLocalRandom.current());
        }
        List<String> pool = hungerGames
            ? List.of()
            : List.of(
                "knights_saddle",
                "dwarven_bow",
                "ghlochester",
                "spider_staff",
                "sponge_saber",
                "skeletal_barber",
                "electric_conch",
                "memory_breaker",
                "sonic_wrench",
                "aegis",
                "frozen_chestpiece",
                "melted_scythe"
            );
        if (pool.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(pool.size());
        String rewardId = pool.get(index);
        ItemStack reward = switch (rewardId) {
            case "knights_saddle" -> knightsSaddle();
            case "dwarven_bow" -> dwarvenBow();
            case "ghlochester" -> ghlochester();
            case "spider_staff" -> spiderStaff();
            case "sponge_saber" -> spongeSaber();
            case "skeletal_barber" -> skeletalBarber();
            case "electric_conch" -> electricConch();
            case "memory_breaker" -> memoryBreaker();
            case "sonic_wrench" -> sonicWrench();
            case "aegis" -> aegis();
            case "frozen_chestpiece" -> frozenChestpiece();
            case "melted_scythe" -> meltedScythe();
            default -> null;
        };
        if (reward == null) {
            return null;
        }
        if (hungerGames) {
            reward = withHungerGamesVariant(rewardId, reward);
        }
        return withModeLabel(reward, hungerGames);
    }
}
