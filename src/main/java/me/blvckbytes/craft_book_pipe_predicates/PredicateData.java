package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import org.bukkit.block.Sign;
import org.jetbrains.annotations.Nullable;

public record PredicateData(
  String tokensPredicate,
  String expandedPredicate,
  TranslationLanguage predicateLanguage,
  String signLine1,
  String signLine3,
  String signLine4,
  @Nullable ItemPredicate parsedPredicate,
  @Nullable ItemPredicateParseException parseException
) {

  public void restoreLines(Sign sign) {
    sign.setLine(0, signLine1);

    if (!signLine3.isBlank())
      sign.setLine(2, signLine3);

    if (!signLine4.isBlank())
      sign.setLine(3, signLine4);

    BlockUtility.updateSign(sign);
  }

  public static PredicateData makeInitial(ItemPredicate predicate, TranslationLanguage language, Sign sign) {
    return new PredicateData(
      new StringifyState(true).appendPredicate(predicate).toString(),
      new StringifyState(false).appendPredicate(predicate).toString(),
      language,
      sign.getLine(0),
      sign.getLine(2),
      sign.getLine(3),
      predicate, null
    );
  }

  public static PredicateData makeUpdate(ItemPredicate predicate, TranslationLanguage language, PredicateData previous) {
    return new PredicateData(
      new StringifyState(true).appendPredicate(predicate).toString(),
      new StringifyState(false).appendPredicate(predicate).toString(),
      language,
      previous.signLine1,
      previous.signLine3,
      previous.signLine4,
      predicate, null
    );
  }
}
