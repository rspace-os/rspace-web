package com.researchspace.service;

import com.researchspace.model.record.BaseRecord;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Results of a RestoreFromDeleted action */
public class RestoreDeletedItemResult {

  private BaseRecord itemToRestore;

  private Set<BaseRecord> restoredItems = new LinkedHashSet<>();

  /**
   * The top-level item to restore. This item will also be added to the getRestoredItems collection
   *
   * @param itemToRestore
   */
  public RestoreDeletedItemResult(BaseRecord itemToRestore) {
    this.itemToRestore = itemToRestore;
    restoredItems.add(itemToRestore);
  }

  /**
   * Adds a restored base record to the result set.
   *
   * @param item
   */
  public void addItem(BaseRecord item) {
    restoredItems.add(item);
  }

  /**
   * Get a read-only view of the restored items
   *
   * @return
   */
  public Set<BaseRecord> getRestoredItems() {
    return Collections.unmodifiableSet(restoredItems);
  }

  /**
   * Get first item, if exists
   *
   * @return
   */
  public Optional<BaseRecord> getFirstItem() {
    return restoredItems.isEmpty()
        ? Optional.empty()
        : Optional.of(restoredItems.iterator().next());
  }

  /**
   * Get the top-level restored item
   *
   * @return
   */
  public BaseRecord getItemToRestore() {
    return itemToRestore;
  }

  public int getRestoredItemCount() {
    return restoredItems.size();
  }
}
