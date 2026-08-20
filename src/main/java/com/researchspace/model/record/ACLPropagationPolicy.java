package com.researchspace.model.record;

/**
 * Defines the behaviour on how the ACL of a parent folder is propagated to a
 * child record when the child is added to the parent.
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

	/**
	 * Recursively adds ACLs of <code>propagationRoot</code> to all children. If
	 * the {@link BaseRecord} cannot or does not contain children, there is no
	 * effect
	 * 
	 * @param propagationRoot
	 */
	void propagate(BaseRecord propagationRoot);

	/**
	 * A static constant stateless, threadsafe default policy.
	 */
	ACLPropagationPolicy DEFAULT_POLICY = new InheritACLFromParentsPropagationPolicy();

	/**
	 * A policy for sharing document into a notebook.
	 */
	ACLPropagationPolicy SHARE_INTO_NOTEBOOK_POLICY = new ShareIntoNotebookACLPolicy();

	/**
	 * A static constant stateless, threadsafe default policy that does not
	 * propagate any ACLS from parent hierarchy.
	 */
	ACLPropagationPolicy NULL_POLICY = new NullACLPolicy();

}
