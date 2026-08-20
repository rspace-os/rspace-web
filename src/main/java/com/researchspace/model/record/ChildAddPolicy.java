package com.researchspace.model.record;

/**
 * Strategy for deciding whether a parent-child relationship between two record
 * types is allowed.
 */
public interface ChildAddPolicy {

	boolean canAdd(Folder parentToBe, BaseRecord childToBe);

	/**
	 * Default policy allows any folder to be a parent except a notebook can't be parent of a folder.
	 */
	ChildAddPolicy DEFAULT = (Folder parentToBe, BaseRecord childToBe) -> {
			if (parentToBe.isNotebook() && childToBe.isFolder()) {
				return false;
			}
			if (parentToBe.isFolder()) {
				return true;
			}
			return false;	
	};

	/**
	 * For testing, always returns false.
	 */
	ChildAddPolicy NULL_OBJECT = (Folder parentToBe, BaseRecord childToBe) -> {
			return false;
	};

}
