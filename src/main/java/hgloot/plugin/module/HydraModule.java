package hgloot.plugin.module;

public interface HydraModule {
    String key();

    void enable();

    void disable();

    boolean isEnabled();
}
