package com.researchspace.model.record;

/**
 * Null implementation that does not propagate any ACLs from parent to child.
 */
public class NullACLPolicy implements ACLPropagationPolicy {

	@Override
	public void onAdd(BaseRecord propagationRoot, BaseRecord child) {
	}

	@Override
	public void onRemove(BaseRecord propagationRoot, BaseRecord removed) {
	}

	@Override
	public void propagate(BaseRecord propagationRoot) {

	}

}
