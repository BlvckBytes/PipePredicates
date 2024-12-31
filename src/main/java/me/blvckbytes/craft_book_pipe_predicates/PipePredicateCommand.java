package me.blvckbytes.craft_book_pipe_predicates;

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
  private final PredicateHelper predicateHelper;
  private final TranslationLanguage language;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  public PipePredicateCommand(
    PredicateDataHandler dataHandler,
    PredicateHelper predicateHelper,
    TranslationLanguage language,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.dataHandler = dataHandler;
    this.predicateHelper = predicateHelper;
    this.language = language;
    this.config = config;
    this.logger = logger;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player))
      return false;

    var actionFilter = CommandAction.getPermissionFilterFor(sender);
    NormalizedConstant<CommandAction> normalizedAction;

    if (args.length == 0 || (normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter)) == null) {
      player.sendMessage("§cUsage: /" + label + " <" + CommandAction.matcher.createCompletions(null, actionFilter) + "> [expression]");
      return true;
    }

    var pistonBlock = BlockUtility.resolvePistonBlock(player.getTargetBlock(null, 5));

    if (pistonBlock == null) {
      player.sendMessage("§cPlease look at a pipe-output (container, piston or sign)");
      return true;
    }

    var pistonSign = BlockUtility.getPistonSign(pistonBlock);

    if (pistonSign == null) {
      player.sendMessage("§cCould not locate pipe-sign");
      return true;
    }

    switch (normalizedAction.constant) {
      case REMOVE -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          sender.sendMessage("§cYou do not have permission to modify pipe-predicates!");
          return false;
        }

        PredicateData predicateData;

        if ((predicateData = dataHandler.remove(pistonSign)) == null) {
          player.sendMessage("§cHad no predicate stored");
          return true;
        }

        predicateData.restoreLines(pistonSign);

        player.sendMessage("§aPredicate removed successfully");
        return true;
      }

      case GET -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender)) {
          sender.sendMessage("§cYou do not have permission to read pipe-predicates!");
          return false;
        }

        var predicateData = dataHandler.access(pistonSign);

        if (predicateData == null) {
          player.sendMessage("§cThere's currently no predicate stored on this pipe");
          return true;
        }

        player.sendMessage("§8§m------------------------------");

        player.spigot().sendMessage(
          new ComponentBuilder("§7Current token predicate: ")
            .append(
              new ComponentBuilder("§a" + predicateData.tokensPredicate())
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + label + " set " + predicateData.tokensPredicate()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aClick to suggest").create()))
                .create()
            )
            .create()
        );

        player.spigot().sendMessage(
          new ComponentBuilder("§7Current expanded predicate: ")
            .append(
              new ComponentBuilder("§a" + predicateData.expandedPredicate())
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + label + " set " + predicateData.expandedPredicate()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aClick to suggest").create()))
                .create()
            )
            .create()
        );

        if (predicateData.parseException() != null)
          player.sendMessage("§7Error: §c" + predicateHelper.createExceptionMessage(predicateData.parseException()));

        player.sendMessage("§8§m------------------------------");
        return true;
      }

      case SET -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          sender.sendMessage("§cYou do not have permission to modify pipe-predicates!");
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
          player.sendMessage("§cPlease provide a non-empty predicate");
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

        var stringifyState = new StringifyState(true);

        predicate.stringify(stringifyState);

        player.sendMessage("§7Set predicate: §a" + stringifyState);
        return true;
      }
      case RELOAD -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender)) {
          sender.sendMessage("§cYou do not have permission to reload the plugin!");
          return false;
        }

        try {
          this.config.reload();

          sender.sendMessage("§aPlugin successfully reloaded.");
        } catch (Exception e) {
          logger.log(Level.SEVERE, "An error occurred while trying to reload the config", e);

          sender.sendMessage("§cAn error occurred while trying to reload the plugin! Check out the console for more details.");
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
