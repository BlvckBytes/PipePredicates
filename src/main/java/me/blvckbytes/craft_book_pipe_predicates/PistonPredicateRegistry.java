package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

public interface PistonPredicateRegistry {

  @Nullable ItemPredicate getPredicateForPiston(Block block);

}
