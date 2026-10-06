package hgloot.plugin.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HydraModuleManager {
    private final Logger logger;
    private final Map<String, HydraModule> modules = new LinkedHashMap<>();

    public HydraModuleManager(Logger logger) {
        this.logger = logger;
    }

    public void register(HydraModule module) {
        String key = normalizeKey(module.key());
        HydraModule existing = modules.putIfAbsent(key, module);
        if (existing != null) {
            throw new IllegalStateException("Duplicate module key: " + key);
        }
    }

    public HydraModule get(String key) {
        if (key == null) {
            return null;
        }
        return modules.get(normalizeKey(key));
    }

    public List<HydraModule> allModules() {
        return new ArrayList<>(modules.values());
    }

    public void enableAll() {
        for (HydraModule module : modules.values()) {
            enableModule(module);
        }
    }

    public void disableAll() {
        List<HydraModule> ordered = allModules();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            disableModule(ordered.get(i));
        }
    }

    public void enableModule(HydraModule module) {
        if (module.isEnabled()) {
            return;
        }
        try {
            module.enable();
            logger.info("[HydraModules] Enabled module: " + module.key());
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "[HydraModules] Failed to enable module: " + module.key(), throwable);
        }
    }

    public void disableModule(HydraModule module) {
        if (!module.isEnabled()) {
            return;
        }
        try {
            module.disable();
            logger.info("[HydraModules] Disabled module: " + module.key());
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "[HydraModules] Failed to disable module: " + module.key(), throwable);
        }
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
