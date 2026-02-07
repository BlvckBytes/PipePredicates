package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SelectionState {

  private List<SortingCriterionSelection> sortingSelections;
  private int selectedSortingSelectionIndex;

  public SelectionState(
    List<SortingCriterionSelection> sortingSelections,
    int selectedSortingSelectionIndex
  ) {
    this.sortingSelections = sortingSelections;
    this.selectedSortingSelectionIndex = selectedSortingSelectionIndex;
  }

  public SelectionState() {
    resetSorting();
  }

  public void applySort(List<? extends CapacityDisplayRenderable> renderables) {
    renderables.sort((a, b) -> {
      for (var sortingSelection : sortingSelections) {
        if (sortingSelection.selection == SortingSelection.INACTIVE)
          continue;

        int sortingResult = sortingSelection.criterion.compare(a, b, sortingSelection.selection == SortingSelection.DESCENDING);

        if (sortingResult != 0)
          return sortingResult;
      }

      return 0;
    });
  }

  public void resetSorting() {
    this.sortingSelections = makeDefaultSortingSelections();
    this.selectedSortingSelectionIndex = 0;
  }

  public void nextSortingSelection() {
    if (++this.selectedSortingSelectionIndex == this.sortingSelections.size())
      this.selectedSortingSelectionIndex = 0;
  }

  public void nextSortingOrder() {
    var sortingSelection = sortingSelections.get(this.selectedSortingSelectionIndex);
    sortingSelection.selection = sortingSelection.selection.next();
  }

  public void moveSortingSelectionDown() {
    var removalIndex = selectedSortingSelectionIndex;
    nextSortingSelection();
    var targetSelection = this.sortingSelections.remove(removalIndex);
    this.sortingSelections.add(selectedSortingSelectionIndex, targetSelection);
  }

  public void extendSortingEnvironment(InterpretationEnvironment environment) {
    for (var index = 0; index < sortingSelections.size(); ++index)
      sortingSelections.get(index).active = index == selectedSortingSelectionIndex;

    environment.withVariable("sorting_selections", this.sortingSelections);
  }

  private static ArrayList<SortingCriterionSelection> makeDefaultSortingSelections() {
    var result = new ArrayList<SortingCriterionSelection>();

    for (var criterion : SortingCriteria.values)
      result.add(new SortingCriterionSelection(criterion, SortingSelection.INACTIVE));

    return result;
  }

  public JsonObject toJson() {
    var result = new JsonObject();

    var sortingSelectionsObject = new JsonObject();

    for (var sortingSelection : sortingSelections)
      sortingSelectionsObject.addProperty(String.valueOf(sortingSelection.criterion.ordinal()), String.valueOf(sortingSelection.selection.ordinal()));

    result.add("sortingSelections", sortingSelectionsObject);
    result.addProperty("selectedSortingSelectionIndex", selectedSortingSelectionIndex);

    return result;
  }

  public static SelectionState fromJson(JsonObject json) {
    var sortingSelections = makeDefaultSortingSelections();
    var sortingSelectionsObject = json.getAsJsonObject("sortingSelections");

    var sortingSelectionIndex = 0;

    for (var sortingSelectionEntry : sortingSelectionsObject.entrySet()) {
      var criterion = SortingCriteria.byOrdinalOrFirst(Integer.parseInt(sortingSelectionEntry.getKey()));
      var selection = SortingSelection.byOrdinalOrFirst(sortingSelectionEntry.getValue().getAsInt());

      sortingSelections.removeIf(x -> x.criterion == criterion);
      sortingSelections.add(sortingSelectionIndex, new SortingCriterionSelection(criterion, selection));

      ++sortingSelectionIndex;
    }

    return new SelectionState(sortingSelections, json.get("selectedSortingSelectionIndex").getAsInt());
  }
}
