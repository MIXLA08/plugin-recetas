package com.mixla.recetas;
import org.bukkit.entity.Player;
public final class Permissions {
    public static final String ADMIN = "recetaspersonalizadas.admin";
    public static final String VIEW = "recetaspersonalizadas.ver";
    private Permissions() {}
    public static boolean admin(Player player) { return player.hasPermission(ADMIN); }
    public static boolean view(Player player) { return player.hasPermission(VIEW); }
}
