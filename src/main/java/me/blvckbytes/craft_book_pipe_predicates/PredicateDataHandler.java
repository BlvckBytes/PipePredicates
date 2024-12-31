package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.item_predicate_parser.PredicateHelper;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class PredicateDataHandler {

  private final Map<Location, PredicateData> dataCache;
  private final PredicateHelper predicateHelper;
  private final TranslationLanguage language;
  private final NamespacedKey tokensPredicateKey, expandedPredicateKey, signLine1Key, signLine3Key, signLine4Key;

  public PredicateDataHandler(Plugin plugin, PredicateHelper predicateHelper, TranslationLanguage language) {
    this.dataCache = new HashMap<>();

    this.predicateHelper = predicateHelper;
    this.language = language;

    this.tokensPredicateKey   = new NamespacedKey(plugin, "tokens-predicate");
    this.expandedPredicateKey = new NamespacedKey(plugin, "expanded-predicate");
    this.signLine1Key = new NamespacedKey(plugin, "sign-line-1");
    this.signLine3Key = new NamespacedKey(plugin, "sign-line-3");
    this.signLine4Key = new NamespacedKey(plugin, "sign-line-4");
  }

  public void store(PredicateData data, Sign sign) {
    var container = sign.getPersistentDataContainer();

    if (data.parsedPredicate() != null)
      container.set(expandedPredicateKey, PersistentDataType.STRING, new StringifyState(true).appendPredicate(data.parsedPredicate()).toString());
    else
      container.remove(expandedPredicateKey);

    container.set(tokensPredicateKey, PersistentDataType.STRING, data.tokensPredicate());
    container.set(signLine1Key, PersistentDataType.STRING, data.signLine1());
    container.set(signLine3Key, PersistentDataType.STRING, data.signLine3());
    container.set(signLine4Key, PersistentDataType.STRING, data.signLine4());

    sign.update(true, false);

    dataCache.put(sign.getLocation(), data);
  }

  public @Nullable PredicateData remove(Sign sign) {
    var predicateData = access(sign);

    if (predicateData == null)
      return null;

    var container = sign.getPersistentDataContainer();

    container.remove(expandedPredicateKey);
    container.remove(tokensPredicateKey);
    container.remove(signLine1Key);
    container.remove(signLine3Key);
    container.remove(signLine4Key);

    sign.update(true, false);

    dataCache.remove(sign.getLocation());

    return predicateData;
  }

  public @Nullable PredicateData access(Sign sign) {
    PredicateData result;

    if ((result = dataCache.get(sign.getLocation())) != null)
      return result;

    var container = sign.getPersistentDataContainer();

    var expandedPredicate = container.get(expandedPredicateKey, PersistentDataType.STRING);

    if (expandedPredicate == null)
      return null;

    var tokensPredicate = container.get(tokensPredicateKey, PersistentDataType.STRING);
    var signLine1 = container.get(signLine1Key, PersistentDataType.STRING);
    var signLine3 = container.get(signLine3Key, PersistentDataType.STRING);
    var signLine4 = container.get(signLine4Key, PersistentDataType.STRING);

    ItemPredicate predicate = null;
    ItemPredicateParseException exception = null;

    try {
      var tokens = predicateHelper.parseTokens(expandedPredicate);
      predicate = predicateHelper.parsePredicate(language, tokens);
    } catch (ItemPredicateParseException e) {
      exception = e;
    }

    result = new PredicateData(
      tokensPredicate == null ? "" : tokensPredicate,
      expandedPredicate,
      signLine1 == null ? "" : signLine1,
      signLine3 == null ? "" : signLine3,
      signLine4 == null ? "" : signLine4,
      predicate, exception
    );

    dataCache.put(sign.getLocation(), result);

    return result;
  }
}
