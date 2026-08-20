package com.researchspace.model;

import com.researchspace.model.record.BaseRecord;

/**
 * POJO class to hold chemistry search result
 */
public class ChemSearchedItem {

	private Long chemId;
	private Long recordId;
	private String recordName;
	private BaseRecord record;

	public Long getChemId() {
		return chemId;
	}

	public void setChemId(Long chemId) {
		this.chemId = chemId;
	}

	public Long getRecordId() {
		return recordId;
	}

	public void setRecordId(Long recordId) {
		this.recordId = recordId;
	}

	public String getRecordName() {
		return recordName;
	}

	public void setRecordName(String recordName) {
		this.recordName = recordName;
	}

	public void setRecord(BaseRecord record) {
		this.record = record;
	}

	public BaseRecord getRecord() {
		return record;
	}
}
