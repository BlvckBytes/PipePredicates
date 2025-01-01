package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.bbconfigmapper.ScalarType;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.item_predicate_parser.PredicateHelper;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import me.blvckbytes.syllables_matcher.NormalizedConstant;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipePredicateCommand implements CommandExecutor, TabCompleter {

  private final PredicateDataHandler dataHandler;
  private final PipeEventHandler pipeEventHandler;
  private final PredicateHelper predicateHelper;
  private final TranslationLanguage language;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  public PipePredicateCommand(
    PredicateDataHandler dataHandler,
    PipeEventHandler pipeEventHandler,
    PredicateHelper predicateHelper,
    TranslationLanguage language,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.dataHandler = dataHandler;
    this.pipeEventHandler = pipeEventHandler;
    this.predicateHelper = predicateHelper;
    this.language = language;
    this.config = config;
    this.logger = logger;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player))
      return false;

    if (!CommandAction.canExecuteAny(sender)) {
      config.rootSection.playerMessages.missingPermissionPipePredicateCommand.sendMessage(sender, config.rootSection.builtBaseEnvironment);
      return false;
    }

    var actionFilter = CommandAction.getPermissionFilterFor(sender);
    NormalizedConstant<CommandAction> normalizedAction;

    if (args.length == 0 || (normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter)) == null) {
      config.rootSection.playerMessages.commandPipePredicateUsage.sendMessage(
        player,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("label", label)
          .withStaticVariable("action_names", CommandAction.matcher.createCompletions(null, actionFilter))
          .build()
      );
      return true;
    }

    var pistonBlock = BlockUtility.resolvePistonBlock(player.getTargetBlock(null, 5));

    if (pistonBlock == null) {
      config.rootSection.playerMessages.commandPipePredicateNoPiston.sendMessage(player, config.rootSection.builtBaseEnvironment);
      return true;
    }

    var pistonSign = BlockUtility.getPistonSign(pistonBlock);

    if (pistonSign == null) {
      config.rootSection.playerMessages.commandPipePredicateNoSign.sendMessage(player, config.rootSection.builtBaseEnvironment);
      return true;
    }

    if (!pipeEventHandler.canEditSign(player, pistonSign)) {
      config.rootSection.playerMessages.commandPipePredicateCannotEditSign.sendMessage(sender, config.rootSection.builtBaseEnvironment);
      return false;
    }

    switch (normalizedAction.constant) {
      case REMOVE -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateModify.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        PredicateData predicateData;

        if ((predicateData = dataHandler.remove(pistonSign)) == null) {
          config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player, config.rootSection.builtBaseEnvironment);
          return true;
        }

        predicateData.restoreLines(pistonSign);

        config.rootSection.playerMessages.commandPipePredicateRemoveSuccess.sendMessage(sender, config.rootSection.builtBaseEnvironment);
        return true;
      }

      case GET_ENTERED, GET_EXPANDED -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateRead.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        var predicateData = dataHandler.access(pistonSign);

        if (predicateData == null) {
          config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player, config.rootSection.builtBaseEnvironment);
          return true;
        }

        if (predicateData.parseException() != null) {
          config.rootSection.playerMessages.commandPipePredicateGetError.sendMessage(
            player,
            config.rootSection.getBaseEnvironment()
              .withStaticVariable("predicate_error", predicateHelper.createExceptionMessage(predicateData.parseException()))
              .build()
          );
        }

        var isRequestingExpanded = normalizedAction.constant == CommandAction.GET_EXPANDED;
        var requestedPredicate = isRequestingExpanded ? predicateData.expandedPredicate() : predicateData.tokensPredicate();

        player.spigot().sendMessage(
          new ComponentBuilder(
            config.rootSection.playerMessages.commandPipePredicateGetPredicate.asScalar(
              ScalarType.STRING,
              config.rootSection.getBaseEnvironment()
                .withStaticVariable("predicate", requestedPredicate)
                .build()
            )
          )
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + label + " set " + requestedPredicate))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(
              config.rootSection.playerMessages.commandPipePredicateGetPredicateHover.asScalar(ScalarType.STRING, config.rootSection.builtBaseEnvironment)
            ).create()))
            .create()
        );

        return true;
      }

      case SET -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateModify.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        ItemPredicate predicate;

        try {
          var tokens = predicateHelper.parseTokens(args, 1);
          predicate = predicateHelper.parsePredicate(language, tokens);
        } catch (ItemPredicateParseException e) {
          player.sendMessage(predicateHelper.createExceptionMessage(e));
          return true;
        }

        if (predicate == null) {
          config.rootSection.playerMessages.commandPipePredicateEmptyPredicate.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return true;
        }

        var existingPredicateData = dataHandler.access(pistonSign);

        PredicateData newPredicateData;

        if (existingPredicateData != null)
          newPredicateData = PredicateData.makeUpdate(predicate, existingPredicateData);
        else
          newPredicateData = PredicateData.makeInitial(predicate, pistonSign);

        pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_OK_COLOR + MarkerConstants.PREDICATE_MARKER);
        pistonSign.setLine(2, "");
        pistonSign.setLine(3, "");
        pistonSign.update(true, false);

        dataHandler.store(newPredicateData, pistonSign);

        config.rootSection.playerMessages.commandPipePredicateSetSuccess.sendMessage(
          sender,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("predicate", new StringifyState(true).appendPredicate(predicate))
            .build()
        );
        return true;
      }
      case RELOAD -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateReload.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        try {
          this.config.reload();

          config.rootSection.playerMessages.commandPipePredicateReloadSuccess.sendMessage(sender, config.rootSection.builtBaseEnvironment);
        } catch (Exception e) {
          logger.log(Level.SEVERE, "An error occurred while trying to reload the config", e);

          config.rootSection.playerMessages.commandPipePredicateReloadFailure.sendMessage(sender, config.rootSection.builtBaseEnvironment);
        }

        return true;
      }
      default -> { return true; }
    }
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player))
      return null;

    if (!CommandAction.canExecuteAny(sender))
      return null;

    var actionFilter = CommandAction.getPermissionFilterFor(sender);

    if (args.length == 1)
      return CommandAction.matcher.createCompletions(null, actionFilter);

    var normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter);

    if (normalizedAction == null || normalizedAction.constant != CommandAction.SET)
      return null;

    try {
      var tokens = predicateHelper.parseTokens(args, 1);
      var completions = predicateHelper.createCompletion(TranslationLanguage.ENGLISH_US, tokens);

      if (completions.expandedPreviewOrError() != null)
        showActionBarMessage(player, completions.expandedPreviewOrError());

      return completions.suggestions();
    } catch (ItemPredicateParseException e) {
      showActionBarMessage(player, predicateHelper.createExceptionMessage(e));
      return null;
    }
  }

  private void showActionBarMessage(Player player, String message) {
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
  }
}
