package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.syllables_matcher.EnumMatcher;
import me.blvckbytes.syllables_matcher.EnumPredicate;
import org.bukkit.command.CommandSender;

public enum CommandAction {
  GET_ENTERED,
  GET_EXPANDED,
  SET,
  SET_MANY,
  SET_MANY_LOCALIZED,
  SET_LOCALIZED,
  REMOVE,
  RELOAD,
  ;

  public static final EnumMatcher<CommandAction> matcher = new EnumMatcher<>(values());

  public static boolean canExecuteAny(CommandSender sender) {
    return (
      PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender) ||
      PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender) ||
      PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender)
    );
  }

  public static EnumPredicate<CommandAction> getPermissionFilterFor(CommandSender sender) {
    return value -> (
      switch (value.constant) {
        case GET_ENTERED, GET_EXPANDED -> PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender);
        case SET, SET_MANY, SET_LOCALIZED, SET_MANY_LOCALIZED, REMOVE -> PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender);
        case RELOAD -> PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender);
      }
    );
  }
}
