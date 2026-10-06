package hgloot.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

final class NpcNmsBridge {
    private final JavaPlugin plugin;
    private final Method craftServerGetServer;
    private final Method craftWorldGetHandle;
    private final Method craftPlayerGetHandle;
    private final Constructor<?> gameProfileConstructor;
    private final Constructor<?> gameProfileConstructorWithProperties;
    private final Method gameProfileGetProperties;
    private final Constructor<?> propertyMapConstructor;
    private final Method mutablePropertyMultimapCreate;
    private final Constructor<?> propertyConstructorSigned;
    private final Constructor<?> propertyConstructorUnsigned;
    private final Constructor<?> serverPlayerConstructor;
    private final Method clientInformationCreateDefault;
    private final Constructor<?> clientInformationRecordConstructor;
    private final RecordComponent[] clientInformationRecordComponents;
    private final Constructor<?> connectionConstructor;
    private final Object serverboundPacketFlow;
    private final Method commonListenerCookieCreateInitial;
    private final Constructor<?> serverGamePacketListenerConstructor;
    private final Field serverPlayerConnectionField;
    private final Method entitySetPos;
    private final Method entitySetRot;
    private final Method entitySetYHeadRot;
    private final Method entitySetYBodyRot;
    private final Method entityGetBukkitEntity;
    private final Method entityGetId;
    private final Method entityGetEntityData;
    private final Method synchedEntityDataSet;
    private final Method synchedEntityDataSetForced;
    private final Method synchedEntityDataGetNonDefaultValues;
    private final List<Object> playerSkinPartsAccessors;
    private final Method serverLevelAddNewPlayer;
    private final Method serverLevelRemovePlayerImmediately;
    private final Object removalReasonDiscarded;
    private final Constructor<?> serverEntityConstructor;
    private final Class<?> serverEntityCallbackClass;
    private final boolean serverEntityTracksConnections;
    private final Method connectionSendPacket;
    private final Method playerInfoCreatePlayerInitializing;
    private final Constructor<?> addEntityPacketConstructor;
    private final Constructor<?> rotateHeadPacketConstructor;
    private final Constructor<?> setEntityDataPacketConstructor;
    private final Constructor<?> playerInfoRemovePacketConstructor;
    private final Constructor<?> removeEntitiesPacketConstructor;
    private final Constructor<?> intArrayListConstructor;

    private NpcNmsBridge(JavaPlugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;

        Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        Class<?> multimapClass = Class.forName("com.google.common.collect.Multimap");
        Class<?> clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Class<?> packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Class<?> commonListenerCookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
        Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
        Class<?> synchedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
        Class<?> serverEntityClass = Class.forName("net.minecraft.server.level.ServerEntity");
        Class<?> intListClass = Class.forName("it.unimi.dsi.fastutil.ints.IntList");
        Class<?> intArrayListClass = Class.forName("it.unimi.dsi.fastutil.ints.IntArrayList");
        Class<?> removalReasonClass = Class.forName("net.minecraft.world.entity.Entity$RemovalReason");

        this.craftServerGetServer = Bukkit.getServer().getClass().getMethod("getServer");
        this.craftWorldGetHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
        this.craftPlayerGetHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
        this.gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
        this.gameProfileConstructorWithProperties = findOptionalConstructor(gameProfileClass, UUID.class, String.class, propertyMapClass);
        this.gameProfileGetProperties = resolveGameProfilePropertiesAccessor(gameProfileClass);
        this.propertyMapConstructor = gameProfileConstructorWithProperties == null
            ? null
            : propertyMapClass.getConstructor(multimapClass);
        this.mutablePropertyMultimapCreate = gameProfileConstructorWithProperties == null
            ? null
            : resolveMutablePropertyMultimapFactory(multimapClass);
        this.propertyConstructorSigned = propertyClass.getConstructor(String.class, String.class, String.class);
        this.propertyConstructorUnsigned = propertyClass.getConstructor(String.class, String.class);
        this.serverPlayerConstructor = serverPlayerClass.getConstructor(
            minecraftServerClass,
            serverLevelClass,
            gameProfileClass,
            clientInformationClass
        );
        this.clientInformationCreateDefault = clientInformationClass.getMethod("createDefault");
        this.clientInformationRecordComponents = clientInformationClass.getRecordComponents();
        this.clientInformationRecordConstructor = resolveRecordConstructor(clientInformationClass, clientInformationRecordComponents);
        this.connectionConstructor = connectionClass.getConstructor(packetFlowClass);
        this.serverboundPacketFlow = enumConstant(packetFlowClass, "SERVERBOUND");
        this.commonListenerCookieCreateInitial = commonListenerCookieClass.getMethod("createInitial", gameProfileClass, boolean.class);
        this.serverGamePacketListenerConstructor = Class
            .forName("net.minecraft.server.network.ServerGamePacketListenerImpl")
            .getConstructor(minecraftServerClass, connectionClass, serverPlayerClass, commonListenerCookieClass);
        this.serverPlayerConnectionField = serverPlayerClass.getDeclaredField("connection");
        this.serverPlayerConnectionField.setAccessible(true);
        this.entitySetPos = entityClass.getMethod("setPos", double.class, double.class, double.class);
        this.entitySetRot = entityClass.getMethod("setRot", float.class, float.class);
        this.entitySetYHeadRot = serverPlayerClass.getMethod("setYHeadRot", float.class);
        this.entitySetYBodyRot = serverPlayerClass.getMethod("setYBodyRot", float.class);
        this.entityGetBukkitEntity = entityClass.getMethod("getBukkitEntity");
        this.entityGetId = entityClass.getMethod("getId");
        this.entityGetEntityData = entityClass.getMethod("getEntityData");
        this.synchedEntityDataSet = findMethod(synchedEntityDataClass, "set", entityDataAccessorClass, Object.class);
        this.synchedEntityDataSetForced = findOptionalMethod(synchedEntityDataClass, "set", entityDataAccessorClass, Object.class, boolean.class);
        this.synchedEntityDataGetNonDefaultValues = synchedEntityDataClass.getMethod("getNonDefaultValues");
        this.playerSkinPartsAccessors = resolvePlayerSkinPartsAccessors(playerClass, entityDataAccessorClass);
        if (this.playerSkinPartsAccessors.isEmpty()) {
            plugin.getLogger().warning("[NPC] Could not resolve player skin-parts metadata accessor; outer skin layers may stay hidden.");
        } else {
            plugin.getLogger().info("[NPC] Resolved " + this.playerSkinPartsAccessors.size()
                + " player skin-parts metadata accessor(s): " + describeAccessorIds(this.playerSkinPartsAccessors));
        }
        this.serverLevelAddNewPlayer = serverLevelClass.getMethod("addNewPlayer", serverPlayerClass);
        this.serverLevelRemovePlayerImmediately = serverLevelClass.getMethod("removePlayerImmediately", serverPlayerClass, removalReasonClass);
        this.removalReasonDiscarded = enumConstant(removalReasonClass, "DISCARDED");
        ServerEntityBridgeSignature serverEntityBridge = resolveServerEntityBridgeSignature(serverEntityClass, serverLevelClass, entityClass);
        this.serverEntityConstructor = serverEntityBridge.constructor();
        this.serverEntityCallbackClass = serverEntityBridge.callbackClass();
        this.serverEntityTracksConnections = serverEntityBridge.tracksConnections();
        this.connectionSendPacket = findMethod(serverPlayerConnectionField.getType(), "send", packetClass);
        this.playerInfoCreatePlayerInitializing = Class
            .forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
            .getMethod("createPlayerInitializing", Collection.class);
        this.addEntityPacketConstructor = Class
            .forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket")
            .getConstructor(entityClass, serverEntityClass);
        this.rotateHeadPacketConstructor = Class
            .forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket")
            .getConstructor(entityClass, byte.class);
        this.setEntityDataPacketConstructor = Class
            .forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
            .getConstructor(int.class, List.class);
        this.playerInfoRemovePacketConstructor = Class
            .forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
            .getConstructor(List.class);
        this.removeEntitiesPacketConstructor = Class
            .forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
            .getConstructor(intListClass);
        this.intArrayListConstructor = intArrayListClass.getConstructor(int[].class);
    }

    static NpcNmsBridge tryCreate(JavaPlugin plugin) {
        try {
            return new NpcNmsBridge(plugin);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize full-body NPC bridge. NPCs will fall back to legacy stands.", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E enumConstant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<E>) enumClass.asSubclass(Enum.class), name);
    }

    NpcVisual spawnNpc(UUID npcId, Location location, List<SkinPropertyData> properties) throws ReflectiveOperationException {
        Object minecraftServer = craftServerGetServer.invoke(Bukkit.getServer());
        Object serverLevel = craftWorldGetHandle.invoke(location.getWorld());
        UUID entityUuid = UUID.nameUUIDFromBytes(("hydra:npc:" + npcId).getBytes(StandardCharsets.UTF_8));
        String profileName = "npc" + npcId.toString().replace("-", "").substring(0, 13);

        Object gameProfile = createGameProfile(entityUuid, profileName, properties);

        Object clientInformation = createNpcClientInformation();
        Object serverPlayer = serverPlayerConstructor.newInstance(minecraftServer, serverLevel, gameProfile, clientInformation);
        Object connection = connectionConstructor.newInstance(serverboundPacketFlow);
        Object cookie = commonListenerCookieCreateInitial.invoke(null, gameProfile, false);
        Object packetListener = serverGamePacketListenerConstructor.newInstance(minecraftServer, connection, serverPlayer, cookie);
        serverPlayerConnectionField.set(serverPlayer, packetListener);

        entitySetPos.invoke(serverPlayer, location.getX(), location.getY(), location.getZ());
        entitySetRot.invoke(serverPlayer, location.getYaw(), location.getPitch());
        entitySetYHeadRot.invoke(serverPlayer, location.getYaw());
        entitySetYBodyRot.invoke(serverPlayer, location.getYaw());
        enableAllSkinLayers(serverPlayer);
        serverLevelAddNewPlayer.invoke(serverLevel, serverPlayer);

        return new NpcVisual(npcId, entityUuid, profileName, location.clone(), location.getWorld(), serverLevel, serverPlayer);
    }

    Entity toBukkitEntity(Object serverPlayer) throws ReflectiveOperationException {
        return (Entity) entityGetBukkitEntity.invoke(serverPlayer);
    }

    void sendSpawn(Player viewer, NpcVisual visual) throws ReflectiveOperationException {
        sendSpawn(viewer, visual, 2L);
    }

    void sendSpawn(Player viewer, NpcVisual visual, long tabLifetimeTicks) throws ReflectiveOperationException {
        Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
        Object viewerConnection = serverPlayerConnectionField.get(viewerHandle);
        Object serverPlayer = visual.serverPlayer();

        Object infoPacket = playerInfoCreatePlayerInitializing.invoke(null, List.of(serverPlayer));
        connectionSendPacket.invoke(viewerConnection, infoPacket);

        Object listenerProxy = Proxy.newProxyInstance(
            serverEntityCallbackClass.getClassLoader(),
            new Class<?>[] { serverEntityCallbackClass },
            (proxy, method, args) -> null
        );
        Object serverEntity = serverEntityTracksConnections
            ? serverEntityConstructor.newInstance(visual.serverLevel(), serverPlayer, 0, false, listenerProxy, Set.of())
            : serverEntityConstructor.newInstance(visual.serverLevel(), serverPlayer, 0, false, listenerProxy);
        Object addEntityPacket = addEntityPacketConstructor.newInstance(serverPlayer, serverEntity);
        connectionSendPacket.invoke(viewerConnection, addEntityPacket);

        enableAllSkinLayers(serverPlayer);
        @SuppressWarnings("unchecked")
        List<Object> packedData = (List<Object>) synchedEntityDataGetNonDefaultValues.invoke(entityGetEntityData.invoke(serverPlayer));
        if (packedData != null && !packedData.isEmpty()) {
            Object dataPacket = setEntityDataPacketConstructor.newInstance(entityGetId.invoke(serverPlayer), packedData);
            connectionSendPacket.invoke(viewerConnection, dataPacket);
        }

        Entity body = toBukkitEntity(serverPlayer);
        byte headYaw = angleToByte(body == null ? 0.0F : body.getLocation().getYaw());
        Object rotateHeadPacket = rotateHeadPacketConstructor.newInstance(serverPlayer, headYaw);
        connectionSendPacket.invoke(viewerConnection, rotateHeadPacket);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            try {
                resendSkinLayerMetadata(viewer, visual);
                Object refreshedViewerHandle = craftPlayerGetHandle.invoke(viewer);
                Object refreshedConnection = serverPlayerConnectionField.get(refreshedViewerHandle);
                Object removeInfoPacket = playerInfoRemovePacketConstructor.newInstance(List.of(visual.entityUuid()));
                connectionSendPacket.invoke(refreshedConnection, removeInfoPacket);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.FINE, "Failed to clear NPC tab entry for " + viewer.getName(), ex);
            }
        }, tabLifetimeTicks);
    }

    void sendRemove(Player viewer, NpcVisual visual) throws ReflectiveOperationException {
        Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
        Object viewerConnection = serverPlayerConnectionField.get(viewerHandle);
        Object intList = intArrayListConstructor.newInstance((Object) new int[] { (int) entityGetId.invoke(visual.serverPlayer()) });
        Object removeEntitiesPacket = removeEntitiesPacketConstructor.newInstance(intList);
        Object removeInfoPacket = playerInfoRemovePacketConstructor.newInstance(List.of(visual.entityUuid()));
        connectionSendPacket.invoke(viewerConnection, removeEntitiesPacket);
        connectionSendPacket.invoke(viewerConnection, removeInfoPacket);
    }

    void destroyNpc(NpcVisual visual) throws ReflectiveOperationException {
        serverLevelRemovePlayerImmediately.invoke(visual.serverLevel(), visual.serverPlayer(), removalReasonDiscarded);
    }

    /** Packet-only preview updates; the fake player never owns combat or inventory state. */
    void sendPreviewEquipment(Player viewer, NpcVisual visual, ItemStack[] armor, ItemStack mainHand,
                              ItemStack offHand) throws ReflectiveOperationException {
        Class<?> nmsSlot = Class.forName("net.minecraft.world.entity.EquipmentSlot");
        Class<?> pair = Class.forName("com.mojang.datafixers.util.Pair");
        Method pairOf = pair.getMethod("of", Object.class, Object.class);
        Method convert = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
            .getMethod("asNMSCopy", ItemStack.class);
        List<Object> slots = new ArrayList<>();
        String[] names = {"FEET", "LEGS", "CHEST", "HEAD", "MAINHAND", "OFFHAND"};
        ItemStack[] items = {armor[0], armor[1], armor[2], armor[3], mainHand, offHand};
        for (int index = 0; index < names.length; index++) {
            ItemStack item = items[index] == null ? new ItemStack(Material.AIR) : items[index];
            slots.add(pairOf.invoke(null, enumConstant(nmsSlot, names[index]), convert.invoke(null, item)));
        }
        Object packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket")
            .getConstructor(int.class, List.class).newInstance(entityGetId.invoke(visual.serverPlayer()), slots);
        sendPreviewPacket(viewer, packet);
    }

    void sendPreviewLook(Player viewer, NpcVisual visual, float bodyYaw, float headYaw, float pitch)
        throws ReflectiveOperationException {
        int entityId = (int) entityGetId.invoke(visual.serverPlayer());
        Object rotation = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot")
            .getConstructor(int.class, byte.class, byte.class, boolean.class)
            .newInstance(entityId, angleToByte(bodyYaw), angleToByte(pitch), true);
        Object head = rotateHeadPacketConstructor.newInstance(visual.serverPlayer(), angleToByte(headYaw));
        sendPreviewPacket(viewer, rotation);
        sendPreviewPacket(viewer, head);
    }

    void sendPreviewSwing(Player viewer, NpcVisual visual, boolean offHand) throws ReflectiveOperationException {
        Object packet = Class.forName("net.minecraft.network.protocol.game.ClientboundAnimatePacket")
            .getConstructor(Class.forName("net.minecraft.world.entity.Entity"), int.class)
            .newInstance(visual.serverPlayer(), offHand ? 3 : 0);
        sendPreviewPacket(viewer, packet);
    }

    private void sendPreviewPacket(Player viewer, Object packet) throws ReflectiveOperationException {
        Object connection = serverPlayerConnectionField.get(craftPlayerGetHandle.invoke(viewer));
        connectionSendPacket.invoke(connection, packet);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "/" + parameterCount);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }

    private static Method findOptionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return findMethod(type, name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Constructor<?> resolveRecordConstructor(Class<?> type, RecordComponent[] components) {
        if (components == null || components.length == 0) {
            return null;
        }
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Constructor<?> findOptionalConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = type.getConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveGameProfilePropertiesAccessor(Class<?> gameProfileClass) throws ReflectiveOperationException {
        try {
            Method method = gameProfileClass.getMethod("getProperties");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            Method method = gameProfileClass.getMethod("properties");
            method.setAccessible(true);
            return method;
        }
    }

    private static Method resolveMutablePropertyMultimapFactory(Class<?> multimapClass) throws ReflectiveOperationException {
        for (String multimapImplClassName : List.of(
            "com.google.common.collect.LinkedHashMultimap",
            "com.google.common.collect.ArrayListMultimap",
            "com.google.common.collect.HashMultimap"
        )) {
            try {
                Class<?> multimapImplClass = Class.forName(multimapImplClassName);
                Method createMethod = findMethod(multimapImplClass, "create", 0);
                if (multimapClass.isAssignableFrom(createMethod.getReturnType())) {
                    return createMethod;
                }
            } catch (ClassNotFoundException ignored) {
                // Try the next mutable multimap implementation available on the runtime.
            }
        }
        throw new NoSuchMethodException("No mutable Guava Multimap factory available for GameProfile properties.");
    }

    private Object createGameProfile(UUID entityUuid, String profileName, List<SkinPropertyData> properties)
        throws ReflectiveOperationException {
        if (gameProfileConstructorWithProperties != null
            && propertyMapConstructor != null
            && mutablePropertyMultimapCreate != null) {
            Object mutablePropertyBacking = mutablePropertyMultimapCreate.invoke(null);
            Method backingPut = findMethod(mutablePropertyBacking.getClass(), "put", 2);
            for (SkinPropertyData property : properties) {
                backingPut.invoke(
                    mutablePropertyBacking,
                    property.name(),
                    createAuthlibProperty(property)
                );
            }
            Object propertyMap = propertyMapConstructor.newInstance(mutablePropertyBacking);
            return gameProfileConstructorWithProperties.newInstance(entityUuid, profileName, propertyMap);
        }

        Object gameProfile = gameProfileConstructor.newInstance(entityUuid, profileName);
        Object propertyMap = gameProfileGetProperties.invoke(gameProfile);
        Method propertyMapPut = findMethod(propertyMap.getClass(), "put", 2);
        for (SkinPropertyData property : properties) {
            propertyMapPut.invoke(propertyMap, property.name(), createAuthlibProperty(property));
        }
        return gameProfile;
    }

    private Object createAuthlibProperty(SkinPropertyData property) throws ReflectiveOperationException {
        return property.signature() == null || property.signature().isBlank()
            ? propertyConstructorUnsigned.newInstance(property.name(), property.value())
            : propertyConstructorSigned.newInstance(property.name(), property.value(), property.signature());
    }

    private Object createNpcClientInformation() {
        try {
            Object base = clientInformationCreateDefault.invoke(null);
            if (clientInformationRecordConstructor == null || clientInformationRecordComponents == null) {
                return base;
            }
            Object[] values = new Object[clientInformationRecordComponents.length];
            for (int i = 0; i < clientInformationRecordComponents.length; i++) {
                RecordComponent component = clientInformationRecordComponents[i];
                Object value = component.getAccessor().invoke(base);
                String name = component.getName().toLowerCase(Locale.ROOT);
                Class<?> type = component.getType();
                if ((type == int.class || type == Integer.class) && isSkinPartsComponentName(name)) {
                    value = 0x7F;
                } else if ((type == byte.class || type == Byte.class) && isSkinPartsComponentName(name)) {
                    value = Byte.valueOf((byte) 0x7F);
                }
                values[i] = value;
            }
            return clientInformationRecordConstructor.newInstance(values);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Failed to create all-skin-layer NPC client information.", ex);
            try {
                return clientInformationCreateDefault.invoke(null);
            } catch (Exception fallback) {
                throw new IllegalStateException("Unable to create ClientInformation for NPC.", fallback);
            }
        }
    }

    private static boolean isSkinPartsComponentName(String name) {
        return name.contains("modelcustomisation")
            || name.contains("modelcustomization")
            || name.contains("skin")
            || name.contains("part");
    }

    private void enableAllSkinLayers(Object serverPlayer) {
        if (playerSkinPartsAccessors.isEmpty()) {
            return;
        }
        try {
            Object entityData = entityGetEntityData.invoke(serverPlayer);
            Byte allLayers = Byte.valueOf((byte) 0x7F);
            for (Object accessor : playerSkinPartsAccessors) {
                synchedEntityDataSet.invoke(entityData, accessor, allLayers);
                if (synchedEntityDataSetForced != null) {
                    synchedEntityDataSetForced.invoke(entityData, accessor, allLayers, true);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Failed to force NPC skin outer layers on.", ex);
        }
    }

    private void resendSkinLayerMetadata(Player viewer, NpcVisual visual) {
        if (viewer == null || !viewer.isOnline() || visual == null) {
            return;
        }
        try {
            Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
            Object viewerConnection = serverPlayerConnectionField.get(viewerHandle);
            Object serverPlayer = visual.serverPlayer();
            enableAllSkinLayers(serverPlayer);
            @SuppressWarnings("unchecked")
            List<Object> packedData = (List<Object>) synchedEntityDataGetNonDefaultValues.invoke(entityGetEntityData.invoke(serverPlayer));
            if (packedData != null && !packedData.isEmpty()) {
                Object dataPacket = setEntityDataPacketConstructor.newInstance(entityGetId.invoke(serverPlayer), packedData);
                connectionSendPacket.invoke(viewerConnection, dataPacket);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Failed to resend NPC skin layer metadata to " + viewer.getName(), ex);
        }
    }

    private static List<Object> resolvePlayerSkinPartsAccessors(Class<?> ownerClass, Class<?> fieldType) {
        Object named = resolveOptionalFieldValue(
            ownerClass,
            fieldType,
            List.of("DATA_PLAYER_MODE_CUSTOMISATION", "DATA_PLAYER_MODE_CUSTOMIZATION")
        );
        if (named != null) {
            return List.of(named);
        }
        List<Object> byteAccessors = new ArrayList<>();
        for (Field field : ownerClass.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !fieldType.isAssignableFrom(field.getType())) {
                continue;
            }
            Type genericType = field.getGenericType();
            String genericName = genericType instanceof ParameterizedType ? genericType.getTypeName() : "";
            if (!genericName.contains("Byte")) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (fieldType.isInstance(value)) {
                    byteAccessors.add(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Continue probing other metadata fields.
            }
        }
        if (byteAccessors.isEmpty()) {
            return List.of();
        }
        List<Object> exactSkinPartIndex = new ArrayList<>();
        for (Object accessor : byteAccessors) {
            int id = entityDataAccessorId(accessor);
            if (id == 17) {
                exactSkinPartIndex.add(accessor);
            }
        }
        if (!exactSkinPartIndex.isEmpty()) {
            return exactSkinPartIndex;
        }
        return List.of(byteAccessors.get(0));
    }

    private static int entityDataAccessorId(Object accessor) {
        if (accessor == null) {
            return -1;
        }
        for (String methodName : List.of("id", "getId")) {
            try {
                Method method = accessor.getClass().getMethod(methodName);
                Object value = method.invoke(accessor);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next known accessor id method name.
            }
        }
        return -1;
    }

    private static String describeAccessorIds(List<Object> accessors) {
        List<String> ids = new ArrayList<>();
        for (Object accessor : accessors) {
            ids.add(String.valueOf(entityDataAccessorId(accessor)));
        }
        return String.join(",", ids);
    }

    private static Object resolveOptionalFieldValue(Class<?> ownerClass, Class<?> fieldType, List<String> candidateNames) {
        for (String name : candidateNames) {
            try {
                Field field = ownerClass.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(null);
                if (fieldType.isInstance(value)) {
                    return value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next known runtime field name.
            }
        }
        return null;
    }

    private static ServerEntityBridgeSignature resolveServerEntityBridgeSignature(
        Class<?> serverEntityClass,
        Class<?> serverLevelClass,
        Class<?> entityClass
    ) throws ReflectiveOperationException {
        for (String callbackClassName : List.of(
            "net.minecraft.server.level.ServerEntity$Synchronizer",
            "net.minecraft.server.level.ServerEntity$Listener"
        )) {
            try {
                Class<?> callbackClass = Class.forName(callbackClassName);
                ServerEntityBridgeSignature bridge = tryResolveServerEntityBridgeSignature(serverEntityClass, serverLevelClass, entityClass, callbackClass);
                if (bridge != null) {
                    return bridge;
                }
            } catch (ClassNotFoundException ignored) {
                // Ignore missing variants and continue probing the runtime constructor.
            }
        }

        for (Constructor<?> constructor : serverEntityClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 5 && parameterTypes.length != 6) {
                continue;
            }
            if (!parameterTypes[0].equals(serverLevelClass)
                || !parameterTypes[1].equals(entityClass)
                || parameterTypes[2] != int.class
                || parameterTypes[3] != boolean.class
                || !parameterTypes[4].isInterface()) {
                continue;
            }
            if (parameterTypes.length == 6 && !parameterTypes[5].isInstance(Set.of())) {
                continue;
            }
            constructor.setAccessible(true);
            return new ServerEntityBridgeSignature(constructor, parameterTypes[4], parameterTypes.length == 6);
        }

        StringBuilder availableConstructors = new StringBuilder();
        for (Constructor<?> constructor : serverEntityClass.getDeclaredConstructors()) {
            if (availableConstructors.length() > 0) {
                availableConstructors.append("; ");
            }
            availableConstructors.append(constructor);
        }
        throw new NoSuchMethodException(
            "No compatible ServerEntity constructor found. Available constructors: " + availableConstructors
        );
    }

    private static ServerEntityBridgeSignature tryResolveServerEntityBridgeSignature(
        Class<?> serverEntityClass,
        Class<?> serverLevelClass,
        Class<?> entityClass,
        Class<?> callbackClass
    ) throws ReflectiveOperationException {
        if (!callbackClass.isInterface()) {
            return null;
        }
        for (Constructor<?> constructor : serverEntityClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 5 && parameterTypes.length != 6) {
                continue;
            }
            if (!parameterTypes[0].equals(serverLevelClass)
                || !parameterTypes[1].equals(entityClass)
                || parameterTypes[2] != int.class
                || parameterTypes[3] != boolean.class
                || !parameterTypes[4].equals(callbackClass)) {
                continue;
            }
            if (parameterTypes.length == 6 && !parameterTypes[5].isInstance(Set.of())) {
                continue;
            }
            constructor.setAccessible(true);
            return new ServerEntityBridgeSignature(constructor, callbackClass, parameterTypes.length == 6);
        }
        return null;
    }

    private static byte angleToByte(float angle) {
        return (byte) (int) (angle * 256.0F / 360.0F);
    }
}

record SkinPropertyData(
    String name,
    String value,
    String signature
) {
}

record NpcVisual(
    UUID npcId,
    UUID entityUuid,
    String profileName,
    Location location,
    World world,
    Object serverLevel,
    Object serverPlayer
) {
}

record ServerEntityBridgeSignature(
    Constructor<?> constructor,
    Class<?> callbackClass,
    boolean tracksConnections
) {
}
