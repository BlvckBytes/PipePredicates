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

      var language = TranslationLanguage.ENGLISH_US;
      var predicateHelper = parserPlugin.getPredicateHelper();

      var dataHandler = new PredicateDataHandler(this, predicateHelper, language);

      var pipeEventHandler = new PipeEventHandler(dataHandler, config);

      Bukkit.getServer().getPluginManager().registerEvents(pipeEventHandler, this);

      var commandUpdater = new CommandUpdater(this);
      var pipePredicateCommandExecutor = new PipePredicateCommand(dataHandler, pipeEventHandler, predicateHelper, language, config, logger);
      var pipePredicateCommand = Objects.requireNonNull(getCommand(PipePredicateCommandSection.INITIAL_NAME));

      pipePredicateCommand.setExecutor(pipePredicateCommandExecutor);

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

  private static void ensureCompatibleCraftBookVersion() {
    var craftBookPlugin = Bukkit.getPluginManager().getPlugin("CraftBook");

    if (craftBookPlugin == null || !craftBookPlugin.isEnabled())
      throw new IllegalStateException("Expected there to be a loaded instance of CraftBook present");

    // Example version-string: "3.10.12-SNAPSHOT;4873-a3b3554"
    var craftBookVersion = craftBookPlugin.getDescription().getVersion();

    int major, minor, patch;

    try {
      var majorDelimiterIndex = craftBookVersion.indexOf('.');
      major = Integer.parseInt(craftBookVersion.substring(0, majorDelimiterIndex));

      var minorDelimiterIndex = craftBookVersion.indexOf('.', majorDelimiterIndex + 1);
      minor = Integer.parseInt(craftBookVersion.substring(majorDelimiterIndex + 1, minorDelimiterIndex));

      var semiIndex = craftBookVersion.indexOf(';');
      var snapshotHyphenIndex = craftBookVersion.indexOf('-');

      // Within the commit-hash, not marking a snapshot right before the semicolon version-delimiter
      if (snapshotHyphenIndex > semiIndex)
        snapshotHyphenIndex = -1;

      // Hyphens on the target major- and minor-version indicate a snapshot; for some odd reason,
      // version 3.10.11 has been named 3.10.12-SNAPSHOT; thus, disallow snapshots on an exact target match.
      if (major == 3 && minor == 10 && snapshotHyphenIndex >= 0)
        patch = -1;

      else {
        var versionEnd = snapshotHyphenIndex;

        if (versionEnd < 0)
          versionEnd = craftBookVersion.indexOf(';');

        if (versionEnd < 0)
          versionEnd = craftBookVersion.length() - 1;
        else
          --versionEnd;

        patch = Integer.parseInt(craftBookVersion.substring(minorDelimiterIndex + 1, versionEnd + 1));
      }
    } catch (Exception e) {
      throw new IllegalStateException("An error occurred while trying to parse CraftBook's version-string of \"" + craftBookVersion + "\"", e);
    }

    // 3.10.12 is the first release of CraftBook which shipped my PR for a filter-event
    if (compareVersions(new int[] { major, minor, patch }, new int[] { 3, 10, 12 }) < 0)
      throw new IllegalStateException("Please update CraftBook to version 3.10.12 or higher, as intercepting pipe-filters is not supported on prior releases");
  }

  private static int compareVersions(int[] a, int[] b) {
    if (a.length != b.length)
      throw new IllegalStateException("Tried to compare versions of different lengths: " + a.length + " and " + b.length);

    for (var i = 0; i < a.length; ++i) {
      var aPart = a[i];
      var bPart = b[i];

      if (aPart == bPart)
        continue;

      return Integer.compare(aPart, bPart);
    }

    return 0;
  }
}
