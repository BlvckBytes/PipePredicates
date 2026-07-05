package me.blvckbytes.pipe_predicates;

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
  LOCATE_PREDICATES,
  CAPACITIES,
  VISUALIZE,
  GENERATE,
  CLEAR_VISUALIZE,
  ;

  public static final EnumMatcher<CommandAction> matcher = new EnumMatcher<>(values());

  public static boolean canExecuteAny(CommandSender sender) {
    return (
        PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_SEARCH.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_CAPACITIES.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_LOCATE_PREDICATES.has(sender)
        || PluginPermission.PIPE_PREDICATE_COMMAND_VISUALIZE.has(sender)
    );
  }

  public static EnumPredicate<CommandAction> getPermissionFilterFor(CommandSender sender) {
    return value -> (
      switch (value.constant) {
        case GET -> PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender);
        case SET, SET_LANGUAGE, REMOVE, GENERATE -> PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender);
        case RELOAD -> PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender);
        case SEARCH -> PluginPermission.PIPE_PREDICATE_COMMAND_SEARCH.has(sender);
        case CAPACITIES -> PluginPermission.PIPE_PREDICATE_COMMAND_CAPACITIES.has(sender);
        case LOCATE_PREDICATES -> PluginPermission.PIPE_PREDICATE_COMMAND_LOCATE_PREDICATES.has(sender);
        case VISUALIZE, CLEAR_VISUALIZE -> PluginPermission.PIPE_PREDICATE_COMMAND_VISUALIZE.has(sender);
      }
    );
  }
}
