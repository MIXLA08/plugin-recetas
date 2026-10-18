package com.mixla.recetas;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Se usa exclusivamente desde el hilo del servidor; escritura en disco serializada por Store. */
public final class RecipeManager {
    private final Map<UUID, Recipe> recipes = new LinkedHashMap<>();
    private final Map<UUID, UUID> locks = new HashMap<>();
    private final Map<UUID, String> reservations = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final RecipeStore store;
    public RecipeManager(RecipeStore store) throws IOException {
        this.store = store;
        for (Recipe recipe : store.load()) {
            if (!available(recipe.name(), recipe.id())) throw new IOException("Nombre duplicado: " + recipe.name());
            recipes.put(recipe.id(), recipe);
        }
    }
    public List<Recipe> all() { return recipes.values().stream().sorted(Comparator.comparing(Recipe::name, String.CASE_INSENSITIVE_ORDER)).toList(); }
    public Recipe get(UUID id) { return recipes.get(id); }
    public boolean available(String name, UUID ownId) {
        return recipes.values().stream().noneMatch(r -> !r.id().equals(ownId) && r.name().equalsIgnoreCase(name))
            && reservations.entrySet().stream().noneMatch(e -> !e.getKey().equals(ownId) && e.getValue().equalsIgnoreCase(name));
    }
    public boolean acquire(UUID id, UUID player) { return !pending.contains(id) && locks.putIfAbsent(id, player) == null; }
    public void release(UUID id, UUID player) { if (!pending.contains(id)) locks.remove(id, player); }
    public boolean owned(UUID id, UUID player) { return player.equals(locks.get(id)); }
    public CompletableFuture<Void> save(Recipe recipe, UUID player) {
        if (!owned(recipe.id(), player) || pending.contains(recipe.id())) return CompletableFuture.failedFuture(new IllegalStateException("Receta ocupada"));
        if (!available(recipe.name(), recipe.id())) return CompletableFuture.failedFuture(new IllegalArgumentException("Nombre ya utilizado"));
        pending.add(recipe.id()); reservations.put(recipe.id(), recipe.name());
        return store.save(recipe);
    }
    public CompletableFuture<Void> delete(UUID id, UUID player) {
        if (!owned(id, player) || pending.contains(id)) return CompletableFuture.failedFuture(new IllegalStateException("Receta ocupada"));
        pending.add(id); return store.delete(id);
    }
    public void complete(UUID id, UUID player, Recipe saved, Throwable error, boolean deletion) {
        if (error == null) { if (deletion) recipes.remove(id); else recipes.put(id, saved); }
        pending.remove(id); reservations.remove(id); locks.remove(id, player);
    }
}
