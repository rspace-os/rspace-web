package com.researchspace.model.record;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Transient;

import org.apache.commons.lang3.Validate;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.RecordSharingACL;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class DecoratedFolder extends BaseRecord {
	
	DecoratedFolder (Folder linked) {
		Validate.isTrue(linked.isRootFolder(), "Can only link to user root folder");
		this.linkedFolder = linked;
		setName(linkedFolder.getName());
		setCreationDate(linkedFolder.getCreationDate());
		setModificationDate(linkedFolder.getModificationDate());
		addType(RecordType.DECORATED);
	}
	
	private Folder linkedFolder;
	
	public String getName() {
		return linkedFolder.getName();
	}
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.LF;
	}

	@Override
	public Set<BaseRecord> getChildrens() {
		return Collections.emptySet();
	}

	@Override
	public BaseRecord copy() {
		return null;
	}
	
	BaseRecord unionACL(RecordSharingACL other) {
		super.unionACL(other);
		linkedFolder.unionACL(other);
		return this;
	}
	
	BaseRecord removeACLs(final List<ACLElement> toRemove) {
		super.removeACLs(toRemove);
		linkedFolder.removeACLs(toRemove);
		return this;
	}
	
	@Transient
	public boolean isFolder() {
		return true;
	}
	
}