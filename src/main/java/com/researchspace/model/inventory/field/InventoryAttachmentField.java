package com.researchspace.model.inventory.field;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InventoryFile;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.validation.ConstraintViolationException;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.Validate;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;

@Entity
@Audited
@DiscriminatorValue("attachment")
public class InventoryAttachmentField extends InventoryEntityField {

	private static final long serialVersionUID = -5815246533648394407L;

	/**
	 * All files ever connected to that Inventory Entity field, deleted or not.
	 * There should be at most one non-deleted file there.
	 */
	@Setter(AccessLevel.PRIVATE)
	@IndexedEmbedded
	private List<InventoryFile> files = new ArrayList<>();
	
	public InventoryAttachmentField() {
		super(FieldType.ATTACHMENT,"");
	}
	
	public InventoryAttachmentField(String name) {
		super(FieldType.ATTACHMENT, name);
	}

	@Transient
	public String getData() {
		return super.getData();
	}

	@OneToMany(mappedBy = "inventoryEntityField", cascade = CascadeType.ALL, orphanRemoval = true)
	public List<InventoryFile> getFiles() {
		return files;
	}

	/**
	 * @return non-deleted attached file, or null
	 */
	@Transient
	@Override
	public InventoryFile getAttachedFile() {
		return getFiles().stream().filter(iFile -> !iFile.isDeleted()).findFirst().orElse(null);
	}

	@Override
	public void setAttachedFile(InventoryFile newFile) {
		Validate.notNull(newFile);
		
		InventoryFile oldAttachedFile = getAttachedFile();
		if (oldAttachedFile != null) {
			oldAttachedFile.setDeleted(true);
		}
		
		files.add(newFile);
		newFile.setInventoryEntityField(this);
	}
	
	@Override
	public InventoryAttachmentField shallowCopy() {
		InventoryAttachmentField copy = new InventoryAttachmentField();
		copyFields(copy);
		InventoryFile attachedFile = getAttachedFile();
		if (attachedFile != null) {
			copy.setAttachedFile(attachedFile.shallowCopy());
		}
		return copy;
	}

	@PrePersist
	@PreUpdate
	public void validateBeforeSave() {
		long attachmentCount = getFiles().stream().filter(iFile -> !iFile.isDeleted()).count();
		if (attachmentCount > 1) { 
			throw new ConstraintViolationException("Inventory attachment field can link only one attachment", null);
		}
	}	
	
}
