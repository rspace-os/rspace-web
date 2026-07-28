package com.researchspace.model.inventory.field;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;

import lombok.Setter;

@Entity
@Audited
@DiscriminatorValue("choice")
@Setter
public class InventoryChoiceField extends InventoryEntityField {
	
	private static final long serialVersionUID = -6222866058180609072L;

	private InventoryChoiceFieldDef choiceDef;

	public InventoryChoiceField(InventoryChoiceFieldDef inventoryChoiceFieldDef, String name) {
		super(FieldType.CHOICE, name);
		this.choiceDef = inventoryChoiceFieldDef;
	}

	public InventoryChoiceField() {
		this(null, "");
	}

	@Transient
	public boolean isMultipleChoice() {
		return choiceDef.isMultipleChoice();
	}

	@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public InventoryChoiceFieldDef getChoiceDef() {
		return choiceDef;
	}

	@Override
	@Transient
	public boolean isOptionsStoringField() {
		return true;
	}
	
	/**
	 * Returns a <code>List</code> of possible choices, or an empty list if no choice
	 * options have yet been set.
	 * 
	 * @return
	 */
	@Override
	@Transient
	public List<String> getAllOptions() {
		return choiceDef.getChoiceOptionsList();
	}

	/**
	 * Convenient method for retrieving selected choice field options.
	 */
	@Override
	@Transient
	public List<String> getSelectedOptions() {
		return choiceDef.getOptionListFromString(getFieldData());
	}

	/**
	 * Convenient method for setting selected choice field options.
	 */
	@Override
	@Transient
	public void setSelectedOptions(List<String> selectedOptions) {
		setFieldData(choiceDef.getStringFromOptionList(selectedOptions));
	}
	
	
	@Override
	public InventoryChoiceField shallowCopy() {
		InventoryChoiceField cf = new InventoryChoiceField(this.choiceDef, getName());
		copyFields(cf);
		cf.setChoiceDef(choiceDef);
		return cf;
	}

	@Override
	public boolean updateToLatestTemplateDefinition() {
		boolean fieldUpdated = super.updateToLatestTemplateDefinition();
		if (isDeleted()) {
			return fieldUpdated; // skip definition update for deleted choice field
		}
		
		InventoryChoiceField templateField = (InventoryChoiceField) getTemplateField();
		InventoryChoiceFieldDef templateChoiceDef = templateField.getChoiceDef();
		Long currentChoiceDefId = choiceDef.getId();
		if (currentChoiceDefId != null && !currentChoiceDefId.equals(templateChoiceDef.getId())) {
			ErrorList validationResult = templateChoiceDef.validate(getData());
			if (validationResult.hasErrorMessages()) {
				throw new IllegalStateException("Field [" + getName() + "] value [" + getData() + "] "
						+ "is invalid according to latest template field definition"); 
			}
			// switch to latest radio def
			setChoiceDef(templateChoiceDef);
			fieldUpdated = true;
		}
		return fieldUpdated;
	}
	
	public ErrorList validate(String fieldData) {
		ErrorList errorList = super.validate(fieldData);
		errorList.addErrorList(choiceDef.validate(fieldData));
		return errorList;
	}

}
