package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.syllables_matcher.EnumMatcher;
import me.blvckbytes.syllables_matcher.EnumPredicate;
import me.blvckbytes.syllables_matcher.MatchableEnum;
import org.bukkit.command.CommandSender;

public enum CommandAction implements MatchableEnum {
  GET,
  SET,
  SET_LANGUAGE,
  REMOVE,
  RELOAD,
  SEARCH,
  VISUALIZE,
  CLEAR_VISUALIZE,
  LOCK_FRAMES,
  UNLOCK_FRAMES,
  ;

  public static final EnumMatcher<CommandAction> matcher = new EnumMatcher<>(values());

  public static boolean canExecuteAny(CommandSender sender) {
    return (
      PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_SEARCH.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_VISUALIZE.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_LOCK_FRAMES.has(sender)
    );
  }

  public static EnumPredicate<CommandAction> getPermissionFilterFor(CommandSender sender) {
    return value -> (
      switch (value.constant) {
        case GET -> PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender);
        case SET, SET_LANGUAGE, REMOVE -> PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender);
        case RELOAD -> PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender);
        case SEARCH -> PluginPermission.PIPE_PREDICATE_COMMAND_SEARCH.has(sender);
        case VISUALIZE, CLEAR_VISUALIZE -> PluginPermission.PIPE_PREDICATE_COMMAND_VISUALIZE.has(sender);
        case LOCK_FRAMES, UNLOCK_FRAMES -> PluginPermission.PIPE_PREDICATE_COMMAND_LOCK_FRAMES.has(sender);
      }
    );
  }
}
