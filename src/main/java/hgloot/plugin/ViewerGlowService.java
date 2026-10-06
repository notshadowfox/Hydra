package hgloot.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Sends the glowing metadata bit to one viewer without changing global entity state. */
final class ViewerGlowService {
    private final JavaPlugin plugin;
    private boolean unavailableLogged;

    ViewerGlowService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void setGlowing(Player viewer, Player target, boolean glowing) {
        if (viewer == null || target == null || viewer.equals(target)
            || !viewer.isOnline() || !target.isOnline()
            || !viewer.getWorld().equals(target.getWorld()) || !viewer.canSee(target)) {
            return;
        }
        try {
            Object handle = target.getClass().getMethod("getHandle").invoke(target);
            Object entityData = invokeNoArg(handle, "getEntityData");
            Field flagsField = findField(handle.getClass(), "DATA_SHARED_FLAGS_ID");
            Object flagsAccessor = flagsField.get(null);
            Method getMethod = findCompatibleMethod(entityData.getClass(), "get", flagsAccessor.getClass());
            byte currentFlags = ((Number) getMethod.invoke(entityData, flagsAccessor)).byteValue();
            byte sentFlags = glowing ? (byte) (currentFlags | 0x40) : (byte) (currentFlags & ~0x40);

            Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            Method create = findStaticCreate(dataValueClass);
            Object dataValue = create.invoke(null, flagsAccessor, sentFlags);
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            Constructor<?> constructor = packetClass.getConstructor(int.class, List.class);
            Object packet = constructor.newInstance(target.getEntityId(), List.of(dataValue));

            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Field connectionField = findField(viewerHandle.getClass(), "connection");
            Object connection = connectionField.get(viewerHandle);
            Method send = findSendMethod(connection.getClass(), packetClass);
            send.invoke(connection, packet);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                plugin.getLogger().warning("Private teammate glow is unavailable: " + exception.getMessage());
            }
        }
    }

    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through the NMS hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> argumentType)
        throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                && method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Method findStaticCreate(Class<?> dataValueClass) throws NoSuchMethodException {
        for (Method method : dataValueClass.getMethods()) {
            if (method.getName().equals("create") && method.getParameterCount() == 2) return method;
        }
        throw new NoSuchMethodException("SynchedEntityData.DataValue.create");
    }

    private static Method findSendMethod(Class<?> type, Class<?> packetClass) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1
                && method.getParameterTypes()[0].isAssignableFrom(packetClass)) {
                return method;
            }
        }
        throw new NoSuchMethodException("connection.send");
    }
}
