package com.researchspace.model.views;

import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;

/**
 * Holds a record and a parent folder. Since a record can have many parent folders,
 * this class can be used to return  the relevant parent folder back to the
 * controller /UI layer.
 */
public class FolderRecordPair {
	
	private Record record;
	
	private Folder parent;

	/**
	 * Non-null arguments expected
	 * @param record
	 * @param parent
	 */
	public FolderRecordPair(Record record, Folder parent) {
		super();
		this.record = record;
		this.parent = parent;
	}

	public Record getRecord() {
		return record;
	}

	public Folder getParent() {
		return parent;
	}
	
	

}
