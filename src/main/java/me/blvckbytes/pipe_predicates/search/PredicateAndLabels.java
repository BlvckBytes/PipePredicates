package me.blvckbytes.pipe_predicates.search;

import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.LabelPredicate;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PredicateAndLabels {

  public final @Nullable ItemPredicate itemPredicate;
  public final List<LabelPredicate> labels;
  private @Nullable String stringification;

  private PredicateAndLabels(ItemPredicate itemPredicate) {
    this.labels = new ArrayList<>();

    this.itemPredicate = itemPredicate.removeNodes(predicate -> {
      if (!(predicate instanceof LabelPredicate labelPredicate))
        return false;

      labels.add(labelPredicate);
      return true;
    });
  }

  public @Nullable String getStringification() {
    if (itemPredicate == null)
      return null;

    if (stringification == null)
      stringification = PlainStringifier.stringify(itemPredicate, false);

    return stringification;
  }

  public List<String> getLabelValues() {
    return labels.stream().map(label -> label.token.value()).toList();
  }

  public static @Nullable PredicateAndLabels of(@Nullable ItemPredicate itemPredicate) {
    if (itemPredicate == null)
      return null;

    return new PredicateAndLabels(itemPredicate);
  }
}
