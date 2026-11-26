package com.researchspace.model.record;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.RecordSharingACL;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The child inherits the parent's ACL; onAddDescendant passes each parent's ACL on to its content.
 */
public class InheritACLFromParentsPropagationPolicy implements ACLPropagationPolicy {

  @Override
  public void onAdd(BaseRecord propagationRoot, BaseRecord child) {
    child.unionACL(removeAnonymousFromAcl(propagationRoot.getSharingACL()));
  }

  // do not propagate the published state of a notebook to entries (for example -  when they are
  // moved into the notebook)
  private RecordSharingACL removeAnonymousFromAcl(RecordSharingACL source) {
    RecordSharingACL copy = new RecordSharingACL();
    source.getAclElements().stream()
        .filter(
            aclElement ->
                !aclElement.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER))
        .map(copy::addACLElement)
        .collect(Collectors.toList());
    return copy;
  }

  @Override
  public boolean propagatesToDescendants() {
    return true;
  }

  @Override
  public void onAddDescendant(BaseRecord parent, BaseRecord descendant) {
    RecordSharingACL parentACL = parent.getSharingACL().copy();
    if (parent.isNotebook()) {
      // PI/Lab Admins don't get any extra permissions to entries in shared notebook
      parentACL.removeACLsforRolesInGroup(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN);
    }
    descendant.unionACL(parentACL);
  }

  /**
   * Removes the ACL (permissions) that the "parent" record (usually a folder) and child record had
   * in common from the child record.
   *
   * <p>WARNING!!! This means that the child record may lose some ACL groups (permissions) that it
   * should rightfully have from some of its other parents! Usually this is not a problem because
   * each parent will have different ACLs (lab group, other user, original user), but might be an
   * issue when testing / in the future!
   *
   * <p>DO NOT REMOVE SHARING WITH ANONYMOUS USER
   *
   * @param propagationRoot (usually parent folder) from which the child record inherited its ACL
   *     from.
   * @param removed the child record to remove the ACLs from.
   */
  @Override
  public void onRemove(BaseRecord propagationRoot, BaseRecord removed) {
    removed.removeACLs(aclsRemovedFromDescendants(propagationRoot, removed));
  }

  @Override
  public List<ACLElement> aclsRemovedFromDescendants(
      BaseRecord propagationRoot, BaseRecord removed) {
    List<ACLElement> aclsInCommonToParentAndChild =
        propagationRoot.getSharingACL().intersectionWith(removed.getSharingACL());
    return aclsInCommonToParentAndChild.stream()
        .filter(el -> !el.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER))
        .collect(Collectors.toList());
  }
}
