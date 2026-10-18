package com.mixla.recetas;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {
    @TempDir Path directory;
    @Test void replacesCompleteContentsAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("recipe.bin");
        AtomicFiles.write(target, new byte[]{1, 2});
        AtomicFiles.write(target, new byte[]{3, 4, 5});
        assertArrayEquals(new byte[]{3, 4, 5}, Files.readAllBytes(target));
        try (Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void failedReplacePreservesTargetAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("protected"); Files.createDirectory(target);
        Path existing = target.resolve("original"); Files.writeString(existing, "conservar");
        assertThrows(IOException.class, () -> AtomicFiles.write(target, new byte[]{9}));
        assertEquals("conservar", Files.readString(existing));
        try (Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void writesLargePayloadFully() throws Exception {
        byte[] payload = new byte[2_000_000]; new java.util.Random(123).nextBytes(payload);
        Path target = directory.resolve("large"); AtomicFiles.write(target, payload);
        assertArrayEquals(payload, Files.readAllBytes(target));
    }
}
