package com.mixla.recetas;

import java.util.*;
import org.bukkit.inventory.ItemStack;

/** Snapshots defensivos: nunca expone los objetos de inventarios reales. */
public final class Recipe {
    private final UUID id;
    private final String name;
    private final ItemStack[] ingredients;
    private final ItemStack result;
    public Recipe(UUID id, String name, ItemStack[] ingredients, ItemStack result) {
        this.id = Objects.requireNonNull(id);
        this.name = validateName(name);
        if (ingredients.length != 9) throw new IllegalArgumentException("Se requieren nueve posiciones");
        if (result == null || result.getType().isAir() || result.getAmount() < 1)
            throw new IllegalArgumentException("El resultado es obligatorio");
        this.ingredients = Arrays.stream(ingredients).map(Recipe::copy).toArray(ItemStack[]::new);
        this.result = result.clone();
    }
    public static String validateName(String name) {
        String clean = Objects.requireNonNull(name).strip();
        if (clean.isEmpty() || clean.length() > 64 || clean.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("El nombre debe tener entre 1 y 64 caracteres, sin controles");
        return clean;
    }
    public static ItemStack copy(ItemStack item) { return item == null || item.getType().isAir() ? null : item.clone(); }
    public UUID id() { return id; }
    public String name() { return name; }
    public ItemStack[] ingredients() { return Arrays.stream(ingredients).map(Recipe::copy).toArray(ItemStack[]::new); }
    public ItemStack result() { return result.clone(); }
}
