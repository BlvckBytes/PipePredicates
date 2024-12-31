package me.blvckbytes.craft_book_pipe_predicates;

import com.cryptomorin.xseries.XMaterial;
import me.blvckbytes.bukkitevaluable.CommandUpdater;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.bukkitevaluable.ConfigManager;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.config.PipePredicateCommandSection;
import me.blvckbytes.item_predicate_parser.ItemPredicateParserPlugin;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public class CraftBookPipePredicatesPlugin extends JavaPlugin implements Listener {

  // TODO: Add configuration

  @Override
  public void onEnable() {
    var logger = getLogger();

    try {
      // First invocation is quite heavy - warm up cache
      XMaterial.matchXMaterial(Material.AIR);

      var configManager = new ConfigManager(this, "config");
      var config = new ConfigKeeper<>(configManager, "config/config.yml", MainSection.class);

      var parserPlugin = ItemPredicateParserPlugin.getInstance();

      if (parserPlugin == null)
        throw new IllegalStateException("Depending on ItemPredicateParser to be successfully loaded");

      var language = TranslationLanguage.ENGLISH_US;
      var predicateHelper = parserPlugin.getPredicateHelper();

      var dataHandler = new PredicateDataHandler(this, predicateHelper, language);

      var pipeEventHandler = new PipeEventHandler(dataHandler);

      Bukkit.getServer().getPluginManager().registerEvents(pipeEventHandler, this);

      var commandUpdater = new CommandUpdater(this);
      var pipePredicateCommandExecutor = new PipePredicateCommand(dataHandler, predicateHelper, language, config, logger);
      var pipePredicateCommand = Objects.requireNonNull(getCommand(PipePredicateCommandSection.INITIAL_NAME));

      pipePredicateCommand.setExecutor(pipePredicateCommandExecutor);

      Runnable updateCommands = () -> {
        config.rootSection.commands.pipePredicate.apply(pipePredicateCommand, commandUpdater);

        commandUpdater.trySyncCommands();
      };

      updateCommands.run();
      config.registerReloadListener(updateCommands);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Could not initialize plugin", e);
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }
}
