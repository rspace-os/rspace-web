package com.researchspace.service.archive.export;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordContainerProcessor;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extracts ids from records( not folders). <br>
 * This class should be used in conjunction with {@link RecordContainerProcessor}
 */
public class RecordIdExtractor implements RecordContainerProcessor {

  private boolean includeDeleted;
  private boolean includeFolders;
  private boolean ownerOnly;
  private User owner;

  /**
   * @param includeDeleted
   * @param includeFolders whether ids of encountered Folders should also be extracted
   * @param ownerOnly - just add documents owned by the speci
   * @param owner can be <code>null</code> if <code>ownerOnly</code> is <code>false</code>.
   */
  public RecordIdExtractor(
      boolean includeDeleted, boolean includeFolders, boolean ownerOnly, User owner) {
    super();
    this.includeDeleted = includeDeleted;
    this.includeFolders = includeFolders;
    this.ownerOnly = ownerOnly;
    this.owner = owner;
  }

  /*
   * for testing
   */
  RecordIdExtractor() {}

  private Set<GlobalIdentifier> ids = new TreeSet<>();

  @Override
  public boolean process(BaseRecord rc) {
    boolean toAdd = true;
    if (!rc.isFolder()) {
      if (((owner != null && rc.isDeletedForUser(owner)) || rc.isDeleted()) && !includeDeleted) {
        toAdd = false;
      }
      if (ownerOnly && !rc.getOwner().equals(owner)) {
        toAdd = false;
      }
    } else {
      toAdd = includeFolders;
    }
    if (toAdd) {
      ids.add(rc.getOid());
    }
    return true;
  }

  /**
   * To be called after #process has been called.
   *
   * @return
   */
  public Set<GlobalIdentifier> getIds() {
    return ids;
  }
}
