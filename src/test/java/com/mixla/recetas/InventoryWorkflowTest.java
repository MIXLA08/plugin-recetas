package com.mixla.recetas;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class InventoryWorkflowTest {
    ServerMock server;
    RecetasPlugin plugin;
    PlayerMock admin;
    PlayerMock viewer;
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(RecetasPlugin.class);
        admin = server.addPlayer(); admin.setOp(true); viewer = server.addPlayer();
    }
    @AfterEach void teardown() { MockBukkit.unmock(); }
    Menu menu(Player player) { return (Menu) player.getOpenInventory().getTopInventory().getHolder(); }
    void tick() { server.getScheduler().performOneTick(); }
    InventoryClickEvent click(Player player, int rawSlot, ClickType click) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            rawSlot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event); return event;
    }
    void createEditor(String name) {
        admin.performCommand("editrecetas"); click(admin, 47, ClickType.LEFT); tick();
        admin.performCommand("editrecetas nombre " + name); assertEquals(Menu.Kind.EDITOR, menu(admin).kind);
    }
    void seed(String name) throws Exception {
        Recipe recipe = new Recipe(UUID.randomUUID(), name, new ItemStack[9], new ItemStack(Material.APPLE, 4));
        var manager = plugin.manager(); assertTrue(manager.acquire(recipe.id(), admin.getUniqueId()));
        manager.save(recipe, admin.getUniqueId()).get(); manager.complete(recipe.id(), admin.getUniqueId(), recipe, null, false);
    }
    void awaitSave() throws Exception {
        // Esperar la escritura y la devolución al hilo del servidor, con límite explícito.
        long limit = System.nanoTime() + 5_000_000_000L;
        while (plugin.manager().all().isEmpty() && System.nanoTime() < limit) { Thread.sleep(10); tick(); }
        assertFalse(plugin.manager().all().isEmpty(), "El guardado no completó dentro de 5 segundos");
    }
    @Test void normalPlayersCannotOpenAdminAndCanViewEmptyCatalog() {
        viewer.performCommand("editrecetas"); assertTrue(viewer.getOpenInventory().getTopInventory() == null || !(viewer.getOpenInventory().getTopInventory().getHolder() instanceof Menu));
        viewer.performCommand("recetas"); assertEquals(Menu.Kind.PUBLIC, menu(viewer).kind);
        assertEquals(54, menu(viewer).inventory.getSize()); assertNotNull(menu(viewer).inventory.getItem(22));
    }
    @Test void editingCopiesKeepsPersonalInventoryAndCursorIntactAndSavesOnClose() throws Exception {
        createEditor("Espada");
        ItemStack personal = new ItemStack(Material.DIAMOND_SWORD); admin.getInventory().setItem(0, personal);
        ItemStack[] before = admin.getInventory().getContents();
        ItemStack cursor = new ItemStack(Material.GOLD_INGOT, 7); admin.setItemOnCursor(cursor);
        assertTrue(click(admin, 54, ClickType.LEFT).isCancelled()); // primer slot hotbar en vista de 27 espacios
        assertTrue(click(admin, 15, ClickType.LEFT).isCancelled());
        assertEquals(Material.DIAMOND_SWORD, menu(admin).inventory.getItem(15).getType());
        assertArrayEquals(before, admin.getInventory().getContents()); assertEquals(cursor, admin.getItemOnCursor());
        // Vaciar cursor antes del cierre: comportamiento de cierre vanilla queda fuera del editor.
        admin.setItemOnCursor(null); admin.closeInventory(); awaitSave();
        assertEquals(Material.DIAMOND_SWORD, plugin.manager().all().getFirst().result().getType());
        assertArrayEquals(before, admin.getInventory().getContents());
    }
    @Test void viewerPreservesExactSlotsAndBlocksSpecialClicksAndDrag() throws Exception {
        ItemStack[] items = new ItemStack[9]; for (int i = 0; i < 9; i++) items[i] = new ItemStack(Material.DIAMOND, i + 1);
        Recipe recipe = new Recipe(UUID.randomUUID(), "Matriz", items, new ItemStack(Material.APPLE, 12));
        var manager = plugin.manager(); manager.acquire(recipe.id(), admin.getUniqueId()); manager.save(recipe, admin.getUniqueId()).get();
        manager.complete(recipe.id(), admin.getUniqueId(), recipe, null, false);
        viewer.performCommand("recetas"); click(viewer, 0, ClickType.LEFT); tick();
        Menu menu = menu(viewer); assertEquals(Menu.Kind.VIEWER, menu.kind); assertEquals(27, menu.inventory.getSize());
        for (int i = 0; i < 9; i++) assertEquals(items[i], menu.inventory.getItem(RecipeLayout.INGREDIENTS[i]));
        assertEquals(recipe.result(), menu.inventory.getItem(15));
        for (ClickType click : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY,
            ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE)) {
            assertTrue(click(viewer, 15, click).isCancelled());
        }
        InventoryDragEvent drag = new InventoryDragEvent(viewer.getOpenInventory(), new ItemStack(Material.DIAMOND),
            new ItemStack(Material.DIAMOND, 2), false, Map.of(1, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag); assertTrue(drag.isCancelled()); assertEquals(recipe.result(), menu.inventory.getItem(15));
    }
    @Test void invalidResultIsNotSavedOnClose() {
        createEditor("Sin resultado"); admin.closeInventory(); assertTrue(plugin.manager().all().isEmpty());
    }
    @Test void revokedPermissionPreventsSavingEditor() {
        createEditor("Revocada"); menu(admin).inventory.setItem(15, new ItemStack(Material.APPLE));
        admin.setOp(false); admin.closeInventory(); assertTrue(plugin.manager().all().isEmpty());
    }
    @Test void decorationCannotBeEditedAndRightClickClearsOnlyRecipeSlots() {
        createEditor("Segura"); Menu editor = menu(admin); ItemStack decoration = editor.inventory.getItem(0).clone();
        admin.getInventory().setItem(0, new ItemStack(Material.DIAMOND)); click(admin, 54, ClickType.LEFT);
        click(admin, 0, ClickType.LEFT); assertEquals(decoration, editor.inventory.getItem(0));
        click(admin, 1, ClickType.LEFT); assertEquals(Material.DIAMOND, editor.inventory.getItem(1).getType());
        click(admin, 1, ClickType.RIGHT); assertNull(editor.inventory.getItem(1));
    }
    @Test void paginationDisplaysSecondPageAndBack() throws Exception {
        for (int i = 0; i < 46; i++) seed(String.format("Receta %02d", i));
        viewer.performCommand("recetas"); assertEquals(45, menu(viewer).entries.size());
        click(viewer, 53, ClickType.LEFT); tick(); assertEquals(1, menu(viewer).page); assertEquals(1, menu(viewer).entries.size());
        click(viewer, 45, ClickType.LEFT); tick(); assertEquals(0, menu(viewer).page);
    }
    @Test void renameKeepsUnsavedIngredientsAndDeleteRequiresConfirmation() throws Exception {
        seed("Original"); admin.performCommand("editrecetas"); click(admin, 0, ClickType.LEFT); tick();
        Menu editor = menu(admin); editor.inventory.setItem(1, new ItemStack(Material.DIAMOND, 8));
        click(admin, 6, ClickType.LEFT); tick(); admin.performCommand("editrecetas nombre Renombrada");
        assertEquals("Renombrada", menu(admin).name); assertEquals(8, menu(admin).inventory.getItem(1).getAmount());
        click(admin, 7, ClickType.LEFT); tick(); assertEquals(Menu.Kind.DELETE, menu(admin).kind);
        click(admin, 15, ClickType.LEFT); tick(); assertEquals(Menu.Kind.EDITOR, menu(admin).kind);
        assertEquals(8, menu(admin).inventory.getItem(1).getAmount()); assertEquals(1, plugin.manager().all().size());
    }
    @Test void manualSaveReturnsToCatalogAfterDurableWrite() throws Exception {
        createEditor("Manual"); menu(admin).inventory.setItem(15, new ItemStack(Material.APPLE, 6));
        click(admin, 8, ClickType.LEFT); tick(); awaitSave(); tick();
        assertEquals(Menu.Kind.ADMIN, menu(admin).kind);
        assertEquals(6, plugin.manager().all().getFirst().result().getAmount());
    }
    @Test void confirmsDeletionAndRefreshesPublicCatalog() throws Exception {
        seed("Eliminar"); admin.performCommand("editrecetas"); click(admin, 0, ClickType.LEFT); tick();
        click(admin, 7, ClickType.LEFT); tick(); click(admin, 11, ClickType.LEFT); tick();
        long limit = System.nanoTime() + 5_000_000_000L;
        while (!plugin.manager().all().isEmpty() && System.nanoTime() < limit) { Thread.sleep(10); tick(); }
        assertTrue(plugin.manager().all().isEmpty());
        viewer.performCommand("recetas"); assertTrue(menu(viewer).entries.isEmpty());
    }
    @Test void secondAdminCannotOpenLockedEditorUntilFirstCloses() throws Exception {
        seed("Compartida"); admin.performCommand("editrecetas"); click(admin, 0, ClickType.LEFT); tick();
        PlayerMock other = server.addPlayer(); other.setOp(true); other.performCommand("editrecetas");
        click(other, 0, ClickType.LEFT); tick(); assertEquals(Menu.Kind.ADMIN, menu(other).kind);
        admin.closeInventory();
        long limit = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limit) {
            Thread.sleep(10); tick(); click(other, 0, ClickType.LEFT); tick();
            if (menu(other).kind == Menu.Kind.EDITOR) break;
        }
        assertEquals(Menu.Kind.EDITOR, menu(other).kind);
    }
    @Test void closingDeleteConfirmationAutoSavesOriginalEditor() throws Exception {
        seed("Cerrar confirmación"); admin.performCommand("editrecetas"); click(admin, 0, ClickType.LEFT); tick();
        menu(admin).inventory.setItem(15, new ItemStack(Material.DIAMOND, 9));
        click(admin, 7, ClickType.LEFT); tick(); admin.closeInventory();
        long limit = System.nanoTime() + 5_000_000_000L;
        while (plugin.manager().all().getFirst().result().getType() != Material.DIAMOND && System.nanoTime() < limit) { Thread.sleep(10); tick(); }
        assertEquals(Material.DIAMOND, plugin.manager().all().getFirst().result().getType());
        assertEquals(9, plugin.manager().all().getFirst().result().getAmount());
    }
    @Test void blocksHandSwapDropsAndEditorDragWithoutTouchingPersonalItems() {
        createEditor("Movimientos");
        ItemStack diamond = new ItemStack(Material.DIAMOND, 3); admin.getInventory().setItem(0, diamond);
        var swap = new org.bukkit.event.player.PlayerSwapHandItemsEvent(admin, diamond, new ItemStack(Material.APPLE));
        server.getPluginManager().callEvent(swap); assertTrue(swap.isCancelled());
        InventoryDragEvent drag = new InventoryDragEvent(admin.getOpenInventory(), diamond, diamond, false, Map.of(15, diamond));
        server.getPluginManager().callEvent(drag); assertTrue(drag.isCancelled());
        var dropped = admin.getWorld().dropItem(admin.getLocation(), diamond);
        var drop = new org.bukkit.event.player.PlayerDropItemEvent(admin, dropped);
        server.getPluginManager().callEvent(drop); assertTrue(drop.isCancelled());
        assertEquals(diamond, admin.getInventory().getItem(0)); assertNull(menu(admin).inventory.getItem(15));
    }
    @Test void failedManualWriteRetainsEditorAndCanBeRetried() throws Exception {
        createEditor("Reintento"); Menu editor = menu(admin);
        editor.inventory.setItem(15, new ItemStack(Material.APPLE, 2));
        var blocked = plugin.getDataFolder().toPath().resolve("recipes").resolve(editor.recipeId + ".recipe");
        java.nio.file.Files.createDirectory(blocked);
        java.nio.file.Files.writeString(blocked.resolve("keep"), "conservar");
        click(admin, 8, ClickType.LEFT); tick();
        long limit = System.nanoTime() + 5_000_000_000L;
        while (editor.finishing && System.nanoTime() < limit) { Thread.sleep(10); tick(); }
        assertFalse(editor.finishing); assertSame(editor, menu(admin)); assertTrue(plugin.manager().all().isEmpty());
        assertEquals(2, editor.inventory.getItem(15).getAmount());
        java.nio.file.Files.delete(blocked.resolve("keep")); java.nio.file.Files.delete(blocked);
        click(admin, 8, ClickType.LEFT); tick(); awaitSave();
        assertEquals(2, plugin.manager().all().getFirst().result().getAmount());
    }
}
