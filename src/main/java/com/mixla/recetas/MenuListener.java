package com.mixla.recetas;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/** Toda interacción es cancelada; la edición sólo cambia copias en el inventario superior. */
public final class MenuListener implements Listener {
    private record NameRequest(Menu editor) {}
    private final RecetasPlugin plugin;
    private final RecipeManager manager;
    private final Menus menus;
    private final Map<UUID, ItemStack> brushes = new HashMap<>();
    private final Map<UUID, NameRequest> names = new HashMap<>();
    private final Map<UUID, Menu> confirmations = new HashMap<>();
    private final Map<UUID, Menu> recovery = new HashMap<>();
    public MenuListener(RecetasPlugin plugin, RecipeManager manager, Menus menus) {
        this.plugin = plugin; this.manager = manager; this.menus = menus;
    }
    private Menu menu(Player player) {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof Menu menu ? menu : null;
    }
    private boolean authorized(Player player, Menu menu) {
        return menu.owner.equals(player.getUniqueId()) &&
            (menu.kind == Menu.Kind.PUBLIC || menu.kind == Menu.Kind.VIEWER ? Permissions.view(player) : Permissions.admin(player));
    }
    private void later(Runnable action) { Bukkit.getScheduler().runTask(plugin, action); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!authorized(player, menu)) { later(player::closeInventory); return; }
        if (menu.finishing) return;
        // No shift, números, doble clic, drop, offhand ni clic creativo de clonado.
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        if (menu.kind == Menu.Kind.EDITOR && slot >= menu.inventory.getSize() && event.getClickedInventory() == player.getInventory()) {
            ItemStack copy = Recipe.copy(player.getInventory().getItem(event.getSlot()));
            if (copy == null) brushes.remove(player.getUniqueId()); else brushes.put(player.getUniqueId(), copy);
            player.sendMessage(copy == null ? "Selección vacía: clic derecho borra un espacio." : "Copia seleccionada. Clic izquierdo en un espacio del editor para colocarla.");
            return;
        }
        if (slot < 0 || slot >= menu.inventory.getSize()) return;
        switch (menu.kind) {
            case PUBLIC, ADMIN -> {
                boolean admin = menu.kind == Menu.Kind.ADMIN;
                if (slot == 45 && menu.page > 0) later(() -> menus.catalog(player, admin, menu.page - 1));
                else if (slot == 53 && menu.page + 1 < RecipeLayout.pages(manager.all().size())) later(() -> menus.catalog(player, admin, menu.page + 1));
                else if (admin && slot == 47) later(() -> requestName(player, null));
                else if (menu.entries.containsKey(slot)) {
                    UUID id = menu.entries.get(slot);
                    later(() -> {
                        if (!authorized(player, menu)) return;
                        Recipe recipe = manager.get(id);
                        if (recipe == null) { menus.catalog(player, admin, menu.page); return; }
                        if (admin) menus.editor(player, id, recipe.name(), menu.page); else menus.viewer(player, recipe, menu.page);
                    });
                }
            }
            case VIEWER -> { if (slot == 26) later(() -> menus.catalog(player, false, menu.page)); }
            case EDITOR -> {
                if (RecipeLayout.editable(slot)) {
                    ItemStack source = brushes.get(player.getUniqueId());
                    if (source == null) source = event.getCursor();
                    menu.inventory.setItem(slot, event.getClick() == ClickType.RIGHT ? null : Recipe.copy(source));
                } else if (slot == 6) later(() -> requestName(player, menu));
                else if (slot == 7) later(() -> {
                    if (!authorized(player, menu) || menu.finishing) return;
                    confirmations.put(player.getUniqueId(), menu); menus.confirmDelete(player, menu);
                });
                else if (slot == 8 || slot == 26) later(() -> finish(player, menu, true));
            }
            case DELETE -> {
                if (slot == 15) later(() -> restoreConfirmation(player, menu));
                else if (slot == 11) later(() -> delete(player, menu));
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drop(PlayerDropItemEvent event) { if (menu(event.getPlayer()) != null) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void swap(PlayerSwapHandItemsEvent event) { if (menu(event.getPlayer()) != null) event.setCancelled(true); }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof Menu menu)) return;
        brushes.remove(player.getUniqueId());
        if (menu.finishing) return;
        if (menu.kind == Menu.Kind.EDITOR) finish(player, menu, false);
        if (menu.kind == Menu.Kind.DELETE) {
            Menu editor = confirmations.remove(player.getUniqueId());
            if (editor != null) { editor.finishing = false; finish(player, editor, false); }
            else manager.release(menu.recipeId, player.getUniqueId());
        }
    }
    public void requestName(Player player, Menu editor) {
        if (!Permissions.admin(player) || (editor != null && editor.finishing)) return;
        cancelName(player);
        names.put(player.getUniqueId(), new NameRequest(editor));
        if (editor != null) editor.finishing = true;
        player.closeInventory();
        player.sendMessage("Escribí /editrecetas nombre <nombre único> (1 a 64 caracteres). /editrecetas cancelar para cancelar.");
    }
    public void acceptName(Player player, String input) {
        if (!Permissions.admin(player)) { cancelName(player); return; }
        NameRequest request = names.get(player.getUniqueId());
        if (request == null) { player.sendMessage("Primero seleccioná Crear receta o Cambiar nombre en el menú."); return; }
        final String name;
        try { name = Recipe.validateName(input); }
        catch (IllegalArgumentException ex) { player.sendMessage(ex.getMessage()); return; }
        Menu editor = request.editor;
        UUID id = editor == null ? UUID.randomUUID() : editor.recipeId;
        if (!manager.available(name, id)) { player.sendMessage("Ya existe una receta con ese nombre."); return; }
        names.remove(player.getUniqueId());
        if (editor == null) menus.editor(player, id, name, 0);
        else { editor.name = name; editor.finishing = false; player.openInventory(editor.inventory); }
    }
    public void cancelName(Player player) {
        NameRequest request = names.remove(player.getUniqueId());
        if (request != null && request.editor != null) {
            Menu editor = request.editor;
            // Se conservan los cambios al cancelar un cambio de nombre, si son válidos.
            editor.finishing = false; finish(player, editor, false);
        }
    }
    private Recipe snapshot(Menu menu) {
        ItemStack[] ingredients = new ItemStack[9];
        for (int i = 0; i < 9; i++) ingredients[i] = menu.inventory.getItem(RecipeLayout.INGREDIENTS[i]);
        return new Recipe(menu.recipeId, menu.name, ingredients, menu.inventory.getItem(RecipeLayout.RESULT));
    }
    private void finish(Player player, Menu menu, boolean returnToCatalog) {
        if (menu.finishing) return;
        if (!Permissions.admin(player)) {
            menu.finishing = true; manager.release(menu.recipeId, player.getUniqueId());
            if (returnToCatalog) player.closeInventory();
            player.sendMessage("Edición descartada: ya no tenés permiso."); return;
        }
        final Recipe recipe;
        try { recipe = snapshot(menu); }
        catch (IllegalArgumentException ex) {
            player.sendMessage("No se guardó: " + ex.getMessage() + ". La versión anterior se conserva.");
            if (returnToCatalog) return; // Guardado manual inválido: continuar editando.
            menu.finishing = true; manager.release(menu.recipeId, player.getUniqueId()); return;
        }
        menu.finishing = true;
        var future = manager.save(recipe, player.getUniqueId());
        future.whenComplete((unused, error) -> {
            if (!plugin.isEnabled()) { if (error != null) plugin.getLogger().severe("Error de guardado: " + error); return; }
            later(() -> {
                manager.complete(recipe.id(), player.getUniqueId(), recipe, error, false);
                if (error != null) {
                    plugin.getLogger().severe("Error guardando " + recipe.id() + ": " + error);
                    player.sendMessage("Error de escritura. No se modificó la receta guardada; revisá la consola.");
                    if (player.isOnline() && Permissions.admin(player)) {
                        recovery.put(player.getUniqueId(), menu);
                        if (menu(player) == menu) resume(player);
                        else player.sendMessage("Usá /editrecetas para recuperar los cambios y reintentar.");
                    }
                } else {
                    if (player.isOnline()) player.sendMessage("Receta guardada: " + recipe.name());
                    if (returnToCatalog && player.isOnline() && menu(player) == menu) menus.catalog(player, true, menu.page);
                }
            });
        });
    }
    private void restoreConfirmation(Player player, Menu confirmation) {
        Menu editor = confirmations.remove(player.getUniqueId());
        if (editor == null || !authorized(player, confirmation)) return;
        confirmation.finishing = true; editor.finishing = false; player.openInventory(editor.inventory);
    }
    private void delete(Player player, Menu confirmation) {
        if (confirmation.finishing || !authorized(player, confirmation)) return;
        confirmation.finishing = true; confirmations.remove(player.getUniqueId());
        var future = manager.delete(confirmation.recipeId, player.getUniqueId()); player.closeInventory();
        future.whenComplete((unused, error) -> {
            if (!plugin.isEnabled()) return;
            later(() -> {
                manager.complete(confirmation.recipeId, player.getUniqueId(), null, error, true);
                if (error != null) plugin.getLogger().severe("Error eliminando receta: " + error);
                if (player.isOnline()) {
                    player.sendMessage(error == null ? "Receta eliminada." : "No se pudo eliminar. La receta se conserva.");
                    if (menu(player) == null) menus.catalog(player, true, confirmation.page);
                }
            });
        });
    }
    public boolean resume(Player player) {
        Menu editor = recovery.get(player.getUniqueId());
        if (editor == null || !Permissions.admin(player)) return false;
        if (!manager.acquire(editor.recipeId, player.getUniqueId())) {
            player.sendMessage("Esperá: la receta está ocupada; se conservan tus cambios para reintentar."); return true;
        }
        recovery.remove(player.getUniqueId());
        if (menu(player) == editor) { editor.finishing = false; return true; }
        editor.finishing = false; player.openInventory(editor.inventory); return true;
    }
    @EventHandler public void quit(PlayerQuitEvent event) { cleanup(event.getPlayer()); recovery.remove(event.getPlayer().getUniqueId()); }
    private void cleanup(Player player) {
        cancelName(player); brushes.remove(player.getUniqueId());
        Menu confirmation = confirmations.remove(player.getUniqueId());
        if (confirmation != null) manager.release(confirmation.recipeId, player.getUniqueId());
    }
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Menu menu = menu(player);
            if (menu != null) player.closeInventory();
            cleanup(player);
        }
    }
}
