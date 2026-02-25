package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public enum PluginPermission {
  PIPE_PREDICATE_COMMAND_RELOAD("command.pipepredicate.reload"),
  PIPE_PREDICATE_COMMAND_SEARCH("command.pipepredicate.search"),
  PIPE_PREDICATE_COMMAND_CAPACITIES("command.pipepredicate.capacities"),
  PIPE_PREDICATE_COMMAND_LOCATE_PREDICATES("command.pipepredicate.locate-predicates"),
  PIPE_PREDICATE_COMMAND_VISUALIZE("command.pipepredicate.visualize"),
  PIPE_PREDICATE_COMMAND_LOCK_FRAMES("command.pipepredicate.lock-frames"),
  AUTO_INITIALIZE_SIGNS("auto-init-signs"),
  ;

  private static final String PREFIX = "craftbookpipepredicates";
  public final String node;

  PluginPermission(String node) {
    this.node = PREFIX + "." + node;
  }

  public boolean has(CommandSender sender) {
    if (sender instanceof ConsoleCommandSender)
      return true;

    if (sender instanceof Player player)
      return player.hasPermission(node);

    return false;
  }

}
