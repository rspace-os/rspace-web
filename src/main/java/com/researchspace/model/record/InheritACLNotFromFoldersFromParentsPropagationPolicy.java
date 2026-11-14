package com.researchspace.model.record;

import com.researchspace.model.permissions.ACLElement;
import java.util.List;

/** Only propagates removal of permissions from documents, not from folders. */
public class InheritACLNotFromFoldersFromParentsPropagationPolicy
    extends InheritACLFromParentsPropagationPolicy {

  @Override
  public List<ACLElement> aclsRemovedFromDescendants(
      BaseRecord propagationRoot, BaseRecord removed) {
    if (removed.isStructuredDocument()) {
      return super.aclsRemovedFromDescendants(propagationRoot, removed);
    }
    return List.of();
  }
}
