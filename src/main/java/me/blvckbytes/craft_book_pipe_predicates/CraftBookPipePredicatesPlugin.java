package me.blvckbytes.craft_book_pipe_predicates;

import com.cryptomorin.xseries.XMaterial;
import me.blvckbytes.bukkitevaluable.CommandUpdater;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.bukkitevaluable.ConfigManager;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.config.PipePredicateCommandSection;
import me.blvckbytes.craft_book_pipe_predicates.search.PipeSearchHandler;
import me.blvckbytes.item_predicate_parser.ItemPredicateParserPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public class CraftBookPipePredicatesPlugin extends JavaPlugin implements Listener {

  private int sessionTickerTaskId = -1;

  @Override
  public void onEnable() {
    var logger = getLogger();

    try {
      ensureCompatibleCraftBookVersion();

      // First invocation is quite heavy - warm up cache
      XMaterial.matchXMaterial(Material.AIR);

      var configManager = new ConfigManager(this, "config");
      var config = new ConfigKeeper<>(configManager, "config.yml", MainSection.class);

      var parserPlugin = ItemPredicateParserPlugin.getInstance();

      if (parserPlugin == null)
        throw new IllegalStateException("Depending on ItemPredicateParser to be successfully loaded");

      var predicateHelper = parserPlugin.getPredicateHelper();

      var dataHandler = new PredicateDataHandler(this, predicateHelper, config);

      var pipeEventHandler = new PipeEventHandler(this, dataHandler, config, logger);

      Bukkit.getServer().getPluginManager().registerEvents(pipeEventHandler, this);

      var pipeSearchHandler = new PipeSearchHandler(this);

      Bukkit.getServer().getPluginManager().registerEvents(pipeSearchHandler, this);

      var pipePredicateCommandExecutor = new PipePredicateCommand(dataHandler, pipeEventHandler, pipeSearchHandler, predicateHelper, config, logger);
      getServer().getPluginManager().registerEvents(pipePredicateCommandExecutor, this);

      sessionTickerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, pipePredicateCommandExecutor::tickSessions, 0L, 5L);

      var pipePredicateCommand = Objects.requireNonNull(getCommand(PipePredicateCommandSection.INITIAL_NAME));

      pipePredicateCommand.setExecutor(pipePredicateCommandExecutor);

      var commandUpdater = new CommandUpdater(this);

      Runnable updateCommands = () -> {
        config.rootSection.commands.pipePredicate.apply(pipePredicateCommand, commandUpdater);

        commandUpdater.trySyncCommands();
      };

      updateCommands.run();
      config.registerReloadListener(updateCommands);

      Bukkit.getServer().getPluginManager().registerEvents(new CommandSendListener(this, config), this);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Could not initialize plugin", e);
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    if (sessionTickerTaskId >= 0)
      Bukkit.getScheduler().cancelTask(sessionTickerTaskId);
  }

  private static void ensureCompatibleCraftBookVersion() {
    var craftBookPlugin = Bukkit.getPluginManager().getPlugin("CraftBook");

    if (craftBookPlugin == null || !craftBookPlugin.isEnabled())
      throw new IllegalStateException("Expected there to be a loaded instance of CraftBook present");

    try {
      Class.forName("com.sk89q.craftbook.mechanics.pipe.PipeFilterEvent");
    } catch (Throwable e) {
      throw new IllegalStateException("Expected the PipeFilterEvent to be implemented; your version of CraftBook is likely too outdated.");
    }
  }
}
