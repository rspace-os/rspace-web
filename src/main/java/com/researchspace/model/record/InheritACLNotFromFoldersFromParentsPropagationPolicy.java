package com.researchspace.model.record;

/**
 * Only propagates removal of permissions from documents, not from folders.
 */
public class InheritACLNotFromFoldersFromParentsPropagationPolicy extends InheritACLFromParentsPropagationPolicy {

	@Override
	public void onRemove(BaseRecord propagationRoot, BaseRecord removed) {
		if (removed.isStructuredDocument()) {
			super.onRemove(propagationRoot, removed);
		}
	}

}
