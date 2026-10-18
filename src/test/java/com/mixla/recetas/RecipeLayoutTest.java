package com.mixla.recetas;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeLayoutTest {
    @Test void usesExactlyTheTenSpecifiedSlots() {
        Set<Integer> expected = Set.of(1, 2, 3, 10, 11, 12, 19, 20, 21, 15);
        Set<Integer> actual = IntStream.range(-1, 55).filter(RecipeLayout::editable).boxed().collect(Collectors.toSet());
        assertEquals(expected, actual);
        assertArrayEquals(new int[]{1, 2, 3, 10, 11, 12, 19, 20, 21}, RecipeLayout.INGREDIENTS);
        assertEquals(15, RecipeLayout.RESULT);
    }
    @Test void paginationHasNoSmallLimitAndHandlesBoundaries() {
        assertEquals(1, RecipeLayout.pages(0));
        assertEquals(1, RecipeLayout.pages(45));
        assertEquals(2, RecipeLayout.pages(46));
        assertEquals(223, RecipeLayout.pages(10000));
    }
    @Test void stalePageIsClampedAfterDeletion() {
        assertEquals(0, RecipeLayout.page(3, 0));
        assertEquals(1, RecipeLayout.page(100, 46));
        assertEquals(0, RecipeLayout.page(-1, 100));
    }
}
