package com.mixla.recetas;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class RecipeStoreTest {
    @TempDir Path directory;
    RecipeStore store;
    RecipeManager manager;
    UUID admin = UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        MockBukkit.mock();
        store = new RecipeStore(directory, Logger.getAnonymousLogger()); manager = new RecipeManager(store);
    }
    @AfterEach void teardown() { store.close(); MockBukkit.unmock(); }
    Recipe recipe(String name) {
        ItemStack[] ingredients = new ItemStack[9]; ingredients[0] = new ItemStack(Material.DIAMOND, 3);
        return new Recipe(UUID.randomUUID(), name, ingredients, new ItemStack(Material.DIAMOND_SWORD));
    }
    void save(Recipe recipe) throws Exception {
        assertTrue(manager.acquire(recipe.id(), admin));
        manager.save(recipe, admin).get(); manager.complete(recipe.id(), admin, recipe, null, false);
    }
    @Test void createsManyRecipesAndReloads() throws Exception {
        for (int i = 0; i < 100; i++) save(recipe("Receta " + i));
        assertEquals(100, manager.all().size());
        RecipeManager reloaded = new RecipeManager(store);
        assertEquals(100, reloaded.all().size());
        assertEquals(3, reloaded.all().getFirst().ingredients()[0].getAmount());
    }
    @Test void editsAndDeletesPersistently() throws Exception {
        Recipe original = recipe("Original"); save(original);
        Recipe edited = new Recipe(original.id(), "Editada", new ItemStack[9], new ItemStack(Material.APPLE, 5)); save(edited);
        assertEquals("Editada", new RecipeManager(store).get(original.id()).name());
        assertEquals(5, new RecipeManager(store).get(original.id()).result().getAmount());
        assertTrue(manager.acquire(original.id(), admin)); manager.delete(original.id(), admin).get();
        manager.complete(original.id(), admin, null, null, true);
        assertNull(manager.get(original.id())); assertTrue(store.load().isEmpty());
    }
    @Test void preservesMetadataAndDefensiveCopies() throws Exception {
        ItemStack result = new ItemStack(Material.DIAMOND_SWORD);
        result.editMeta(meta -> {
            meta.displayName(Component.text("Espada especial"));
            meta.lore(List.of(Component.text("Historia de la espada")));
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.getPersistentDataContainer().set(new NamespacedKey("test", "custom"), PersistentDataType.STRING, "valor");
        });
        Recipe recipe = new Recipe(UUID.randomUUID(), "Especial", new ItemStack[9], result);
        save(recipe);
        assertEquals(result, store.load().getFirst().result());
        result.setAmount(5); assertEquals(1, recipe.result().getAmount());
        recipe.result().setAmount(10); assertEquals(1, recipe.result().getAmount());
    }
    @Test void namesAreUniqueIgnoringCaseAndReservedDuringWrite() throws Exception {
        Recipe first = recipe("Única"); assertTrue(manager.acquire(first.id(), admin));
        var future = manager.save(first, admin);
        assertFalse(manager.available("ÚNICA", UUID.randomUUID()));
        future.get(); manager.complete(first.id(), admin, first, null, false);
        assertFalse(manager.available("única", UUID.randomUUID()));
        assertTrue(manager.available("única", first.id()));
    }
    @Test void simultaneousAdminsCannotOverwrite() throws Exception {
        Recipe recipe = recipe("Bloqueada"); save(recipe);
        assertTrue(manager.acquire(recipe.id(), admin));
        UUID other = UUID.randomUUID(); assertFalse(manager.acquire(recipe.id(), other));
        assertThrows(Exception.class, () -> manager.save(recipe, other).get());
        manager.release(recipe.id(), admin); assertTrue(manager.acquire(recipe.id(), other));
    }
    @Test void requiresValidResultAndName() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(UUID.randomUUID(), "Vacía", new ItemStack[9], null));
        assertThrows(IllegalArgumentException.class, () -> recipe(" "));
        assertThrows(IllegalArgumentException.class, () -> recipe("x".repeat(65)));
    }
    @Test void corruptDataIsPreservedAndBlocksLoad() throws Exception {
        Path file = directory.resolve(UUID.randomUUID() + ".recipe"); Files.write(file, new byte[]{1, 2, 3});
        assertThrows(java.io.IOException.class, store::load);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }
    @Test void atomicReplaceLeavesCompleteFileAndNoTemporaryFiles() throws Exception {
        Path target = directory.resolve("atomic.bin");
        AtomicFiles.write(target, new byte[]{1, 2}); AtomicFiles.write(target, new byte[]{3, 4, 5});
        assertArrayEquals(new byte[]{3, 4, 5}, Files.readAllBytes(target));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void writeFailureDoesNotPublishInMemory() throws Exception {
        Recipe recipe = recipe("Fallo");
        Path blocked = directory.resolve(recipe.id() + ".recipe"); Files.createDirectory(blocked);
        Files.writeString(blocked.resolve("keep"), "No borrar");
        assertTrue(manager.acquire(recipe.id(), admin));
        Throwable error = assertThrows(Exception.class, () -> manager.save(recipe, admin).get());
        manager.complete(recipe.id(), admin, recipe, error, false);
        assertNull(manager.get(recipe.id())); assertTrue(Files.exists(blocked.resolve("keep")));
    }
}
