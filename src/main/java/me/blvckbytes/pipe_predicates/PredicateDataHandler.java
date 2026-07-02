package me.blvckbytes.pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.item_predicate_parser.PredicateHelper;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class PredicateDataHandler {

  private final PredicateHelper predicateHelper;
  private final ConfigKeeper<MainSection> config;
  private final NamespacedKey tokensPredicateKey, expandedPredicateKey, predicateLanguageKey, signLine1Key, signLine3Key, signLine4Key;

  public PredicateDataHandler(
    Plugin plugin,
    PredicateHelper predicateHelper,
    ConfigKeeper<MainSection> config
  ) {
    this.predicateHelper = predicateHelper;
    this.config = config;

    this.tokensPredicateKey   = new NamespacedKey(plugin, "tokens-predicate");
    this.expandedPredicateKey = new NamespacedKey(plugin, "expanded-predicate");
    this.predicateLanguageKey = new NamespacedKey(plugin, "predicate-language");
    this.signLine1Key = new NamespacedKey(plugin, "sign-line-1");
    this.signLine3Key = new NamespacedKey(plugin, "sign-line-3");
    this.signLine4Key = new NamespacedKey(plugin, "sign-line-4");
  }

  public void store(PredicateData data, Sign sign) {
    var container = sign.getPersistentDataContainer();

    if (data.parsedPredicate() != null)
      container.set(expandedPredicateKey, PersistentDataType.STRING, PlainStringifier.stringify(data.parsedPredicate(), false));
    else
      container.remove(expandedPredicateKey);

    container.set(tokensPredicateKey, PersistentDataType.STRING, data.tokensPredicate());
    container.set(predicateLanguageKey, PersistentDataType.STRING, data.predicateLanguage().name());
    container.set(signLine1Key, PersistentDataType.STRING, data.signLine1());
    container.set(signLine3Key, PersistentDataType.STRING, data.signLine3());
    container.set(signLine4Key, PersistentDataType.STRING, data.signLine4());

    sign.update(true, false);
  }

  public @Nullable PredicateData remove(Sign sign) {
    var predicateData = access(sign);

    if (predicateData == null)
      return null;

    var container = sign.getPersistentDataContainer();

    container.remove(expandedPredicateKey);
    container.remove(tokensPredicateKey);
    container.remove(predicateLanguageKey);
    container.remove(signLine1Key);
    container.remove(signLine3Key);
    container.remove(signLine4Key);

    sign.update(true, false);

    return predicateData;
  }

  public @Nullable PredicateData access(Sign sign) {
    var container = sign.getPersistentDataContainer();
    var expandedPredicate = container.get(expandedPredicateKey, PersistentDataType.STRING);

    if (expandedPredicate == null)
      return null;

    var tokensPredicate = container.get(tokensPredicateKey, PersistentDataType.STRING);
    var predicateLanguageName = container.get(predicateLanguageKey, PersistentDataType.STRING);
    var signLine1 = container.get(signLine1Key, PersistentDataType.STRING);
    var signLine3 = container.get(signLine3Key, PersistentDataType.STRING);
    var signLine4 = container.get(signLine4Key, PersistentDataType.STRING);

    TranslationLanguage predicateLanguage;

    try {
      predicateLanguage = TranslationLanguage.valueOf(predicateLanguageName);
    } catch (Exception e) {
      predicateLanguage = config.rootSection.defaultPredicateLanguage;
    }

    ItemPredicate predicate = null;
    ItemPredicateParseException exception = null;

    try {
      var tokens = predicateHelper.parseTokens(expandedPredicate);
      predicate = predicateHelper.parsePredicate(predicateLanguage, tokens);
    } catch (ItemPredicateParseException e) {
      exception = e;
    }

    var result = new PredicateData(
      tokensPredicate == null ? "" : tokensPredicate,
      expandedPredicate,
      predicateLanguage,
      signLine1 == null ? "" : signLine1,
      signLine3 == null ? "" : signLine3,
      signLine4 == null ? "" : signLine4,
      predicate, exception
    );

    updateSignErrorMode(sign, result);
    return result;
  }

  private void updateSignErrorMode(Sign pistonSign, PredicateData predicateData) {
    var firstLineContent = pistonSign.getLine(0);

    if (predicateData.parsedPredicate() == null) {
      if (!firstLineContent.startsWith("§" + MarkerConstants.PREDICATE_ERROR_COLOR)) {
        pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_ERROR_COLOR + ChatColor.stripColor(firstLineContent));
        pistonSign.update(true, false);
      }

      return;
    }

    if (!firstLineContent.startsWith("§" + MarkerConstants.PREDICATE_OK_COLOR)) {
      pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_OK_COLOR + ChatColor.stripColor(firstLineContent));
      pistonSign.update(true, false);
    }
  }
}
