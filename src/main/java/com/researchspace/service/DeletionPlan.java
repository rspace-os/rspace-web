package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import org.apache.commons.lang3.Validate;

/**
 * Implements a reverse depth-first iteration of a folder tree, i.e from descendants up to parents.
 *
 * <h4>Implementation notes </h4>
 *
 * Methods can be pulled out into an interface for future plan implementations
 */
public class DeletionPlan implements Iterable<BaseRecord> {

  private Deque<BaseRecord> itemsToDelete = new ArrayDeque<>();
  private final User user;
  private final RSPath path;
  private final Folder parentOfDeletedItem;

  /**
   * No null args
   *
   * @param user
   * @param path
   * @param parent
   */
  public DeletionPlan(User user, RSPath path, Folder parent) {
    super();
    Validate.noNullElements(new Object[] {user, path, parent});
    this.user = user;
    this.path = path;
    this.parentOfDeletedItem = parent;
  }

  public void add(BaseRecord toDelete) {
    itemsToDelete.push(toDelete);
  }

  public Folder getParentOfDeletedItem() {
    return parentOfDeletedItem;
  }

  @Override
  public String toString() {
    return "DeletionPlan [itemsToDelete="
        + itemsToDelete
        + ", user="
        + user
        + ", path="
        + path
        + ", parentOfDeletedItem="
        + parentOfDeletedItem
        + "]";
  }

  public User getUser() {
    return user;
  }

  public RSPath getPath() {
    return path;
  }

  @Override
  public Iterator<BaseRecord> iterator() {
    return itemsToDelete.iterator();
  }

  public BaseRecord getFinalElementToRemove() {
    return itemsToDelete.getLast();
  }

  public int size() {
    return itemsToDelete.size();
  }
}
