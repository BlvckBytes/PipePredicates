# CraftBookPipePredicates

This project has been one of the main motivators to prototype and implement the universal
[ItemPredicateParser](https://github.com/BlvckBytes/ItemPredicateParser) and thereby makes
use of its exposed functionality.

🧨 WARNING: Please keep in mind that this plugin only represents a draft and is not yet ready for proper use.

![Short Preview](readme_images/short_preview.gif)

## Feature Description

The goal of this little plugin is to hook into the newly added [PipeFilterEvent](https://github.com/EngineHub/CraftBook/pull/1332)
of the awesome Pipe-System as implemented by the [CraftBook](https://github.com/EngineHub/CraftBook)-Plugin,
in order to provide a far more versatile and nuanced alternative to their default filter notation.

Use the command `/pipepredicate` while looking at either a pipe-output's chest, piston or sign in order to
modify the attached predicate. Once a predicate has been set using the `SET` action, the sign changes; it's
first line indicates predicate mode, while the `[Pipe]`-marker on the next line remains; the third and fourth
lines (default filters) are cleared and persisted until predicate-mode is exited again - they will be restored
thereafter. Predicate strings are stored on the `[Pipe]`-sign's [PersistentDataContainer](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/persistence/PersistentDataContainer.html).

By invoking the action `GET`, the currently stored predicate, if any, is retrieved and sent to the player via chat;
clicking on the predicate string will suggest a command to edit and re-set it on the currently looked-at pipe-output.
To leave predicate-mode and restore default filters, make use of the action called `REMOVE`.

The modifications applied to Pipes by this plugin do not affect performance negatively in any way
whatsoever, as stored predicate-strings are parsed once and cached until the corresponding Block is
garbage-collected again. Parsed predicates are naturally depicted as an Abstract Syntax Tree, which
arguably executes even faster than standard filters within the same realm of functionality; complex
filters may take a few more nanoseconds, :).
