package com.researchspace.model.views;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
/**
 * Keeps track of original->copy mappings during a copy operation, for auditing purposes.
 */
@AuditTrailData(delegateToCollection = "originalToCopy")
public class RecordCopyResult {

	private Folder parent;
	private Folder parentCopy;
	private boolean isFolderCopy = false;

	/**
	 * Whether the copy is of a folder/notebook or documents/individual items
	 * @return
	 */
	public boolean isFolderCopy() {
		return isFolderCopy;
	}

	private Map<BaseRecord, BaseRecord> originalToCopy  = new HashMap <>();

	/**
	 * The parent container of the records being copied.
	 * @param parent
	 */
	public RecordCopyResult(Folder parent, boolean isFolderCopy) {
		super();
		this.parent = parent;
		this.isFolderCopy= isFolderCopy;
	}

	public Folder getParent() {
		return parent;
	}
	/**
	 * For folder copies, this stores the top-level root of the copy 
	 * @return the copied top-level folder
	 */
	public Folder getParentCopy() {
		return parentCopy;
	}

	public void setParentCopy(Folder parentCopy) {
		this.parentCopy = parentCopy;
	}

	/**
	 * Returns a mapping of original to copy.
	 * @return
	 */
	public Map<BaseRecord, BaseRecord> getOriginalToCopy() {
		return originalToCopy;
	}
	
	/**
	 * Adds an original to copy mapping
	 * @param original
	 * @param copy
	 */
	public void add(BaseRecord original, BaseRecord copy) {
		originalToCopy.put(original, copy);
	}

	public void addAll(Map<BaseRecord, BaseRecord> toAdd) {
		for (Entry<BaseRecord, BaseRecord> entry : toAdd.entrySet()) {
			originalToCopy.put(entry.getKey(), entry.getValue());
		}
	}
	
	/**
	 * GEts the copy of the supplied  original
	 * @param original or <code>null</code> if could not be found.
	 * @return
	 */
	public BaseRecord getCopy(BaseRecord original) {
		return originalToCopy.get(original);
	}
	
	public BaseRecord getUniqueCopy() {
		if (originalToCopy.values().size() > 0) {
			return originalToCopy.values().iterator().next();
		}
		return null;
	}
	
	public BaseRecord getOriginalFromId(Long id){
		for (BaseRecord br: originalToCopy.keySet()){
			if(br.getId().equals(id)){
				return br;
			}
		}
		return null;
	}

}
