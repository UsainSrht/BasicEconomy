package me.usainsrht.basiceconomy.impl.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlayerVisibility {

    /**
     * Checks whether sender can see target player using Bukkit's Player#canSee.
     */
    public static boolean canSeePlayer(CommandSender sender, Player target) {
        if (target == null) return false;
        if (sender instanceof Player player) {
            return player.canSee(target);
        }
        return true;
    }
}
