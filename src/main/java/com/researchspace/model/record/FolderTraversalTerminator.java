package com.researchspace.model.record;

public interface FolderTraversalTerminator {

	/**
	 * 
	 * @param current
	 * @param parent
	 * @return true if DFS hierarchical search is to be terminated,
	 *         <code>false</code> if it should continue.
	 */
	boolean terminate(BaseRecord current, Folder parent);

}
