package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Audited
@DiscriminatorValue("radio")
@Setter
public class InventoryRadioField extends InventoryEntityField {

	private static final long serialVersionUID = -5576520929775736727L;

	private InventoryRadioFieldDef radioDef;

	public InventoryRadioField(InventoryRadioFieldDef inventoryRadioFieldDef, String name) {
		super(FieldType.RADIO, name);
		this.radioDef = inventoryRadioFieldDef;
	}

	public InventoryRadioField() {
		this(null, "");
	}

	@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	public InventoryRadioFieldDef getRadioDef() {
		return radioDef;
	}

	@Override
	@Transient
	public boolean isOptionsStoringField() {
		return true;
	}
	
	/**
	 * Returns a <code>List</code> of possible radio options, or an empty list if no
	 * radio options have yet been set.
	 * 
	 * @return A possibly empty but not-null <code>List</code> of {@link String}.
	 */
	@Override
	@Transient
	public List<String> getAllOptions() {
		return radioDef.getRadioOptionsList();
	}

	/**
	 * Returns selected option wrapped in a list.
	 * 
	 * @return An empty, or one-element list, holding selected radio option
	 */
	@Override
	@Transient
	public List<String> getSelectedOptions() {
		if (StringUtils.isEmpty(getFieldData())) {
			return Collections.emptyList();
		}
		return Arrays.asList(getFieldData());
	}

	/**
	 * Convenient method for setting radio field option. 
	 *  
	 * Radio field allows only one option being selected, so
	 * method accepts only a list with size 0 or 1. 
	 * 
	 * @throws IllegalArgumentException if selected options contains more than one value 
	 *
	 */
	@Override
	@Transient
	public void setSelectedOptions(List<String> selectedOptions) {
		if (CollectionUtils.isEmpty(selectedOptions)) {
			setFieldData("");
		} else if (selectedOptions.size() == 1) {
			setFieldData(selectedOptions.get(0));
		} else {
			throw new IllegalArgumentException("only one option can be selected on radio field");
		}
	}

	@Override
	public InventoryRadioField shallowCopy() {
		InventoryRadioField radioField = new InventoryRadioField(this.radioDef, getName());
		copyFields(radioField);
		radioField.setRadioDef(radioDef);
		return radioField;
	}
	
	@Override
	public boolean updateToLatestTemplateDefinition() {
		boolean fieldUpdated = super.updateToLatestTemplateDefinition();
		if (isDeleted()) {
			return fieldUpdated; // skip definition update for deleted radio field
		}
		
		InventoryRadioField templateField = (InventoryRadioField) getTemplateField();
		InventoryRadioFieldDef templateRadioDef = templateField.getRadioDef();
		Long currentRadioDefId = radioDef.getId();
		if (currentRadioDefId != null && !currentRadioDefId.equals(templateRadioDef.getId())) {
			ErrorList validationResult = templateRadioDef.validate(getData());
			if (validationResult.hasErrorMessages()) {
				throw new IllegalStateException("Field [" + getName() + "] value [" + getData() + "] "
						+ "is invalid according to latest template field definition"); 
			}
			// switch to latest radio def
			setRadioDef(templateRadioDef);
			fieldUpdated = true;
		}
		return fieldUpdated;
	}
	
	public ErrorList validate(String fieldData) {
		ErrorList errorList = super.validate(fieldData);
		errorList.addErrorList(radioDef.validate(fieldData));
		return errorList;
	}

}
