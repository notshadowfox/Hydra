package hgloot.plugin.hunger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class LocatorBarBridge {
    private final Plugin plugin;
    private final Method getHandle;
    private final Field connection;
    private final Method send;
    private final Object icon;
    private final java.lang.reflect.Constructor<?> vec3i;
    private final Method add;
    private final Method update;
    private final Method remove;

    LocatorBarBridge(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        getHandle = craftPlayer.getMethod("getHandle");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        connection = Arrays.stream(serverPlayer.getFields())
            .filter(field -> field.getType().getName().contains("ServerGamePacketListener"))
            .findFirst().orElseThrow();
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
        send = Arrays.stream(connection.getType().getMethods())
            .filter(method -> method.getName().equals("send") && method.getParameterCount() == 1
                && method.getParameterTypes()[0].isAssignableFrom(packet))
            .findFirst().orElseThrow();
        Class<?> iconClass = Class.forName("net.minecraft.world.waypoints.Waypoint$Icon");
        icon = iconClass.getConstructor().newInstance();
        Class<?> vecClass = Class.forName("net.minecraft.core.Vec3i");
        vec3i = vecClass.getConstructor(int.class, int.class, int.class);
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket");
        add = packetClass.getMethod("addWaypointPosition", UUID.class, iconClass, vecClass);
        update = packetClass.getMethod("updateWaypointPosition", UUID.class, iconClass, vecClass);
        remove = packetClass.getMethod("removeWaypoint", UUID.class);
    }

    void add(Player viewer, UUID waypointId, Location target) { sendPosition(viewer, waypointId, target, add); }
    void update(Player viewer, UUID waypointId, Location target) { sendPosition(viewer, waypointId, target, update); }
    void remove(Player viewer, UUID waypointId) { invokeSend(viewer, invoke(remove, waypointId)); }

    private void sendPosition(Player viewer, UUID id, Location location, Method factory) {
        if (viewer == null || location == null) return;
        Object position;
        try {
            position = vec3i.newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        } catch (ReflectiveOperationException exception) {
            log(exception);
            return;
        }
        invokeSend(viewer, invoke(factory, id, icon, position));
    }

    private Object invoke(Method method, Object... args) {
        try { return method.invoke(null, args); }
        catch (ReflectiveOperationException exception) { log(exception); return null; }
    }

    private void invokeSend(Player viewer, Object packet) {
        if (viewer == null || packet == null) return;
        try { send.invoke(connection.get(getHandle.invoke(viewer)), packet); }
        catch (ReflectiveOperationException exception) { log(exception); }
    }

    private void log(Exception exception) {
        plugin.getLogger().log(Level.WARNING, "[HG Utility] Locator Bar packet failed", exception);
    }
}
