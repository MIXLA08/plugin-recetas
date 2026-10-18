package com.mixla.recetas;

import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public class RecetasPlugin extends JavaPlugin {
    private RecipeStore store;
    private RecipeManager manager;
    RecipeManager manager() { return manager; }
    private MenuListener listener;
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            store = new RecipeStore(getDataFolder().toPath().resolve("recipes"), getLogger());
            manager = new RecipeManager(store);
            Menus menus = new Menus(manager, getConfig().getBoolean("decorar-menus", true));
            listener = new MenuListener(this, manager, menus);
            getServer().getPluginManager().registerEvents(listener, this);
            RecipeCommands commands = new RecipeCommands(listener, menus);
            for (String name : new String[]{"recetas", "editrecetas"}) {
                var command = Objects.requireNonNull(getCommand(name));
                command.setExecutor(commands); command.setTabCompleter(commands);
            }
            getLogger().info("Cargadas " + manager.all().size() + " recetas informativas. No se registran crafteos.");
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "No se pudieron cargar las recetas; datos conservados. Plugin deshabilitado.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (listener != null) listener.shutdown();
        if (store != null) store.close();
    }
}
