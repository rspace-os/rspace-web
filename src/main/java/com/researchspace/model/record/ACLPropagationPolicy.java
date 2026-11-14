package com.researchspace.model.record;

import com.researchspace.model.permissions.ACLElement;
import java.util.List;

/**
 * Defines the behaviour on how the ACL of a parent folder is propagated to a child record when the
 * child is added to the parent. onAdd/onRemove only update the child itself; services that add or
 * remove a folder or notebook apply onAddDescendant / aclsRemovedFromDescendants to its content.
 */
public interface ACLPropagationPolicy {

  /**
   * Behaviour on addition of a child to a parent
   *
   * @param propagationRoot
   * @param child
   */
  void onAdd(BaseRecord propagationRoot, BaseRecord child);

  /**
   * Behaviour on removal of a child from a parent
   *
   * @param propagationRoot
   * @param removed
   */
  void onRemove(BaseRecord propagationRoot, BaseRecord removed);

  /** Whether services walk the content of an added folder to apply onAddDescendant. */
  default boolean propagatesToDescendants() {
    return false;
  }

  /**
   * Behaviour for a descendant of a child added with {@link #onAdd}, given the descendant's direct
   * parent. Services call it top-down, so <code>parent</code> already has its new ACL.
   */
  default void onAddDescendant(BaseRecord parent, BaseRecord descendant) {}

  /**
   * ACL elements services remove from the content of <code>removed</code> (a policy may remove the
   * same from <code>removed</code> itself in {@link #onRemove}). Must be called before {@link
   * #onRemove}, which changes the ACL of <code>removed</code>.
   */
  default List<ACLElement> aclsRemovedFromDescendants(
      BaseRecord propagationRoot, BaseRecord removed) {
    return List.of();
  }

  /** A static constant stateless, threadsafe default policy. */
  ACLPropagationPolicy DEFAULT_POLICY = new InheritACLFromParentsPropagationPolicy();

  /** A policy for sharing document into a notebook. */
  ACLPropagationPolicy SHARE_INTO_NOTEBOOK_POLICY = new ShareIntoNotebookACLPolicy();

  /**
   * A static constant stateless, threadsafe default policy that does not propagate any ACLS from
   * parent hierarchy.
   */
  ACLPropagationPolicy NULL_POLICY = new NullACLPolicy();
}
