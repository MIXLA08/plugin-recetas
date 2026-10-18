package com.mixla.recetas;

public final class RecipeLayout {
    private RecipeLayout() {}
    public static final int[] INGREDIENTS = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    public static final int RESULT = 15;
    public static final int PAGE_SIZE = 45;
    public static boolean editable(int slot) {
        if (slot == RESULT) return true;
        for (int ingredient : INGREDIENTS) if (ingredient == slot) return true;
        return false;
    }
    public static int pages(int count) { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static int page(int requested, int count) { return Math.max(0, Math.min(requested, pages(count) - 1)); }
}
