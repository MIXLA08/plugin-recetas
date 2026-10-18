package com.mixla.recetas;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Constructores compartidos de catálogo público, administrativo, visor y editor. */
public final class Menus {
    private final RecipeManager manager;
    private final boolean decorate;
    public Menus(RecipeManager manager, boolean decorate) { this.manager = manager; this.decorate = decorate; }
    static ItemStack button(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(title, NamedTextColor.YELLOW));
            meta.lore(Arrays.stream(lore).map(s -> Component.text(s, NamedTextColor.GRAY)).toList());
        });
        return item;
    }
    private void background(Menu menu) {
        if (!decorate) return;
        ItemStack glass = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, glass);
    }
    public void catalog(Player player, boolean admin, int requestedPage) {
        if (admin ? !Permissions.admin(player) : !Permissions.view(player)) { player.sendMessage("No tenés permiso."); return; }
        List<Recipe> recipes = manager.all(); int page = RecipeLayout.page(requestedPage, recipes.size());
        Menu menu = new Menu(admin ? Menu.Kind.ADMIN : Menu.Kind.PUBLIC, player.getUniqueId(), 54,
            admin ? "Administrar recetas" : "Recetas personalizadas", page);
        for (int i = 0; i < RecipeLayout.PAGE_SIZE && page * RecipeLayout.PAGE_SIZE + i < recipes.size(); i++) {
            Recipe recipe = recipes.get(page * RecipeLayout.PAGE_SIZE + i); ItemStack icon = recipe.result();
            icon.editMeta(meta -> {
                meta.displayName(Component.text(recipe.name(), NamedTextColor.GOLD));
                List<Component> lore = new ArrayList<>(); if (meta.lore() != null) lore.addAll(meta.lore());
                lore.add(Component.text(admin ? "Clic para editar" : "Clic para ver la receta", NamedTextColor.GRAY)); meta.lore(lore);
            });
            menu.inventory.setItem(i, icon); menu.entries.put(i, recipe.id());
        }
        if (recipes.isEmpty()) menu.inventory.setItem(22, button(Material.PAPER, "Todavía no hay recetas"));
        if (page > 0) menu.inventory.setItem(45, button(Material.ARROW, "Página anterior"));
        menu.inventory.setItem(49, button(Material.PAPER, "Página " + (page + 1) + "/" + RecipeLayout.pages(recipes.size())));
        if (page + 1 < RecipeLayout.pages(recipes.size())) menu.inventory.setItem(53, button(Material.ARROW, "Página siguiente"));
        if (admin) menu.inventory.setItem(47, button(Material.EMERALD, "Crear receta"));
        player.openInventory(menu.inventory);
    }
    public void viewer(Player player, Recipe recipe, int page) {
        Menu menu = new Menu(Menu.Kind.VIEWER, player.getUniqueId(), 27, "Receta: " + recipe.name(), page);
        background(menu); populate(menu, recipe); menu.recipeId = recipe.id();
        menu.inventory.setItem(26, button(Material.ARROW, "Volver al catálogo")); player.openInventory(menu.inventory);
    }
    private void populate(Menu menu, Recipe recipe) {
        ItemStack[] ingredients = recipe == null ? new ItemStack[9] : recipe.ingredients();
        for (int i = 0; i < 9; i++) menu.inventory.setItem(RecipeLayout.INGREDIENTS[i], ingredients[i]);
        menu.inventory.setItem(RecipeLayout.RESULT, recipe == null ? null : recipe.result());
    }
    public void editor(Player player, UUID id, String name, int page) {
        if (!Permissions.admin(player)) { player.sendMessage("No tenés permiso."); return; }
        if (!manager.acquire(id, player.getUniqueId())) { player.sendMessage("Otro administrador está editando o guardando esta receta."); return; }
        Menu menu = new Menu(Menu.Kind.EDITOR, player.getUniqueId(), 27, "Editar: " + name, page);
        menu.recipeId = id; menu.name = name; background(menu); populate(menu, manager.get(id));
        menu.inventory.setItem(6, button(Material.NAME_TAG, "Cambiar nombre"));
        menu.inventory.setItem(7, button(Material.BARRIER, "Eliminar receta"));
        menu.inventory.setItem(8, button(Material.LIME_DYE, "Guardar y volver"));
        menu.inventory.setItem(17, button(Material.PAPER, "Editor de copias", "Clic en tu inventario: seleccionar copia", "Clic en espacio: colocar copia; derecho: borrar", "Cierre: guarda si hay resultado válido"));
        menu.inventory.setItem(26, button(Material.ARROW, "Volver (guarda cambios válidos)"));
        player.openInventory(menu.inventory);
    }
    public void confirmDelete(Player player, Menu editor) {
        editor.finishing = true;
        Menu menu = new Menu(Menu.Kind.DELETE, player.getUniqueId(), 27, "¿Eliminar " + editor.name + "?", editor.page);
        menu.recipeId = editor.recipeId; menu.name = editor.name;
        // Mantener la sesión original permite cancelar sin perder cambios aún no guardados.
        menu.inventory.setItem(11, button(Material.RED_CONCRETE, "Confirmar eliminación"));
        menu.inventory.setItem(15, button(Material.LIME_CONCRETE, "Cancelar")); player.openInventory(menu.inventory);
    }
}
