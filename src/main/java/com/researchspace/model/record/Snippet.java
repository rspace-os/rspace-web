package com.researchspace.model.record;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.researchspace.model.core.GlobalIdPrefix;

/**
 * Entity class for snippets.
 */
@Entity
@Audited
@Indexed
public class Snippet extends Record {

	private static final long serialVersionUID = -6668797154341864526L;

	private String rtfContent;

	public Snippet() {
	}

	public Snippet(String content) {
		this.rtfContent = content;
	}

	/**
	 * Gets the content of this snippet (may contain html tags)
	 */
	@Lob
	public String getContent() {
		return rtfContent;
	}

	/**
	 * Sets the content of this snippet (may contain html tags)
	 */
	public void setContent(String content) {
		this.rtfContent = content;
	}

	/**
	 * Copies this record and its content. This method does not copy associated
	 * data such as image annotations, comments etc. To properly copy these,
	 * call RecordManagerImpl#copy
	 */
	@Override
	public Snippet copy() {
		Snippet clone = new Snippet();
		clone.setContent(rtfContent);
		this.shallowCopyRecordInfo(clone);
		return clone;
	}

	/**
	 * Boolean test for the innate ability of this document to be editable.
	 * 
	 * @return
	 */
	@Transient
	public boolean isEditable() {
		return false;
	}

	/**
	 * Boolean test for whether this record is a Snippet.
	 */
	@Transient
	public boolean isSnippet() {
		return true;
	}

	@Override
	@Transient
	protected GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.ST;
	}

	public RecordInformation toRecordInfo() {
		RecordInformation recordInfo = super.toRecordInfo();
		recordInfo.setType(DOCUMENT_CATEGORIES.SNIPPET);
		return recordInfo;
	}
}
