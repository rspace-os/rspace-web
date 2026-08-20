package com.researchspace.model.views;

import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.core.UniquelyIdentifiable;

import com.researchspace.model.record.BaseRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
/**
 * Database view object for getting data specific for TreeView
 */
@Data
@AllArgsConstructor
public class TreeViewItem implements UniquelyIdentifiable {
	
	private Long id;
	private String name;
	private String type;
	private boolean deleted;
	private Long creationDateMillis;
	private Long modificationDateMillis;
	private String extension;
	
	
	public boolean isFolder() {
		return RecordType.isFolder(type);
	}
	
	public boolean isNotebook() {
		return RecordType.isNotebook(type);
	}
	
	public boolean isSnippet() {
		return RecordType.isSnippet(type);
	}
	
	public boolean isStructuredDocument () {
		return RecordType.isDocumentOrTemplate(type);
	}
	
	public boolean isMediaRecord () {
		return RecordType.isMediaFile(type);
	}
	
	public boolean isRootMedia () {
		return RecordType.isRootMedia(type);
	}
	
	public GlobalIdentifier getOid () {
		return new GlobalIdentifier(RecordType.getGlobalIdFromType(type), id);
	}
	
	public String getGlobalId() {
		return getOid().toString();
	}

	public static TreeViewItem fromBaseRecord(BaseRecord b) {
		return new TreeViewItem(b.getId(), b.getName(), b.getType(), b.isDeleted(),
				b.getCreationDateMillis(), b.getModificationDateMillis(), "");
	}

}
