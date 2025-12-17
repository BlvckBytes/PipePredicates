package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PipeSearchCommand implements CommandExecutor, TabCompleter {

  private final PipePredicateCommand predicateCommandExecutor;
  private final Command predicateCommand;

  public PipeSearchCommand(
    PipePredicateCommand predicateCommandExecutor,
    Command predicateCommand
  ) {
    this.predicateCommandExecutor = predicateCommandExecutor;
    this.predicateCommand = predicateCommand;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
    return predicateCommandExecutor.onCommand(sender, predicateCommand, predicateCommand.getName(), extendArgs(args));
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
    return predicateCommandExecutor.onTabComplete(sender, predicateCommand, predicateCommand.getName(), extendArgs(args));
  }

  private String[] extendArgs(String[] args) {
    var extendedArgs = new String[args.length + 1];
    extendedArgs[0] = CommandAction.matcher.getNormalizedName(CommandAction.SEARCH);
    System.arraycopy(args, 0, extendedArgs, 1, args.length);
    return extendedArgs;
  }
}
