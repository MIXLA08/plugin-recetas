package com.mixla.recetas;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class RecipeCommands implements CommandExecutor, TabCompleter {
    private final MenuListener listener;
    private final Menus menus;
    public RecipeCommands(MenuListener listener, Menus menus) { this.listener = listener; this.menus = menus; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Este comando requiere un jugador."); return true; }
        boolean admin = command.getName().equalsIgnoreCase("editrecetas");
        if (admin ? !Permissions.admin(player) : !Permissions.view(player)) { player.sendMessage("No tenés permiso."); return true; }
        if (admin && args.length > 0) {
            if (args[0].equalsIgnoreCase("nombre") && args.length > 1) listener.acceptName(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            else if (args[0].equalsIgnoreCase("cancelar")) listener.cancelName(player);
            else player.sendMessage("Usá /editrecetas nombre <nombre> o /editrecetas cancelar");
            return true;
        }
        listener.cancelName(player);
        if (admin && listener.resume(player)) return true;
        menus.catalog(player, admin, 0); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return command.getName().equalsIgnoreCase("editrecetas") && sender.hasPermission(Permissions.ADMIN) && args.length == 1
            ? List.of("nombre", "cancelar").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
