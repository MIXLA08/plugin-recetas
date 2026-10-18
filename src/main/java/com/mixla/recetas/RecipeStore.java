package com.mixla.recetas;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;

/** Un archivo por receta. ItemStack bytes conserva componentes y DataVersion de Paper. */
public final class RecipeStore implements AutoCloseable {
    private static final int MAGIC = 0x52504331;
    private final Path directory;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "recetas-persistence"));
    public RecipeStore(Path directory, Logger logger) throws IOException {
        this.directory = directory; this.logger = logger; Files.createDirectories(directory);
    }
    public List<Recipe> load() throws IOException {
        List<Recipe> recipes = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".recipe")).sorted().toList()) {
                try {
                    Recipe recipe = decode(Files.readAllBytes(path));
                    if (!path.getFileName().toString().equals(recipe.id() + ".recipe"))
                        throw new IOException("El UUID no coincide con el archivo");
                    recipes.add(recipe);
                } catch (Exception ex) {
                    // No sobrescribir silenciosamente datos dañados o de una versión incompatible.
                    throw new IOException("No se pudo cargar " + path.getFileName() + "; archivo conservado", ex);
                }
            }
        }
        return recipes;
    }
    public CompletableFuture<Void> save(Recipe recipe) {
        final byte[] bytes;
        try { bytes = encode(recipe); }
        catch (IOException | RuntimeException ex) { return CompletableFuture.failedFuture(ex); }
        return CompletableFuture.runAsync(() -> {
            try { AtomicFiles.write(directory.resolve(recipe.id() + ".recipe"), bytes); }
            catch (IOException ex) { throw new CompletionException(ex); }
        }, writer);
    }
    public CompletableFuture<Void> delete(UUID id) {
        return CompletableFuture.runAsync(() -> {
            try { Files.deleteIfExists(directory.resolve(id + ".recipe")); }
            catch (IOException ex) { throw new CompletionException(ex); }
        }, writer);
    }
    static byte[] encode(Recipe recipe) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC); out.writeUTF(recipe.id().toString()); out.writeUTF(recipe.name());
            for (ItemStack item : recipe.ingredients()) writeItem(out, item);
            writeItem(out, recipe.result());
        }
        return buffer.toByteArray();
    }
    static Recipe decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IOException("Formato de receta desconocido");
            UUID id = UUID.fromString(in.readUTF()); String name = in.readUTF();
            ItemStack[] items = new ItemStack[9];
            for (int i = 0; i < 9; i++) items[i] = readItem(in);
            ItemStack result = readItem(in);
            if (in.available() != 0) throw new IOException("Datos adicionales inesperados");
            return new Recipe(id, name, items, result);
        }
    }
    private static void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        byte[] bytes = item == null ? new byte[0] : item.serializeAsBytes();
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static ItemStack readItem(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 16 * 1024 * 1024 || length > in.available()) throw new IOException("ItemStack inválido");
        return length == 0 ? null : ItemStack.deserializeBytes(in.readNBytes(length));
    }
    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) logger.severe("Hay escrituras pendientes tras 30 segundos");
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
