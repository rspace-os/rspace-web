package com.researchspace.linkedelements;

/**
 * Stores a record of added/removed elements in a textField between 2 versions of the text field.
 */
public class FieldContentDelta {

  public FieldContentDelta(FieldContents added, FieldContents removed) {
    super();
    this.added = added;
    this.removed = removed;
  }

  private FieldContents added;

  private FieldContents removed;

  public FieldContents getAdded() {
    return added;
  }

  public FieldContents getRemoved() {
    return removed;
  }

  /**
   * Boolean test for whether this delta holds any elements
   *
   * @return <code>true</code> if this delta has no added or removed elements.
   */
  public boolean isUnchanged() {
    return !added.hasAnyElements() && !removed.hasAnyElements();
  }
}
