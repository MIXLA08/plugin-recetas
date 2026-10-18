package com.mixla.recetas;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;

public final class Menu implements InventoryHolder {
    public enum Kind { PUBLIC, ADMIN, VIEWER, EDITOR, DELETE }
    final Kind kind;
    final UUID owner;
    final Inventory inventory;
    final Map<Integer, UUID> entries = new HashMap<>();
    final int page;
    UUID recipeId;
    String name;
    boolean finishing;
    public Menu(Kind kind, UUID owner, int size, String title, int page) {
        this.kind = kind; this.owner = owner; this.page = page;
        inventory = Bukkit.createInventory(this, size, Component.text(title));
    }
    @Override public Inventory getInventory() { return inventory; }
}
