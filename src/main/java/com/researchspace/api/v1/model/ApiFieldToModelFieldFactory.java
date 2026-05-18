package com.researchspace.api.v1.model;

import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Maps incoming template ApiInventoryEntityField to InventoryEntityField based on value of 'type'
 * property
 */
@Component
public class ApiFieldToModelFieldFactory {

  public InventoryEntityField apiInventoryFieldToModelField(ApiInventoryEntityField field) {
    InventoryEntityField toAdd = null;
    switch (field.getType()) {
      case STRING:
      case TEXT:
      case DATE:
      case NUMBER:
      case TIME:
      case ATTACHMENT:
      case REFERENCE:
      case URI:
      case IDENTIFIER:
        toAdd = InventoryEntityField.fromFieldTypeString(field.getType().toString());
        break;
      case CHOICE:
        InventoryChoiceFieldDef def = new InventoryChoiceFieldDef();
        if (definitionIsEmpty(field)) {
          throw new IllegalArgumentException("Choice field must supply a definition");
        }
        def.setChoiceOptionsList(field.getDefinition().getOptions());
        def.setMultipleChoice(field.getDefinition().isMultiple());
        toAdd = new InventoryChoiceField(def, field.getName());
        break;
      case RADIO:
        InventoryRadioFieldDef radioDef = new InventoryRadioFieldDef();
        if (definitionIsEmpty(field)) {
          throw new IllegalArgumentException("Radio field must supply a definition");
        }
        radioDef.setRadioOptionsList(field.getDefinition().getOptions());
        toAdd = new InventoryRadioField(radioDef, field.getName());
        break;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported field type %s", field.getType()));
    }

    toAdd.setName(field.getName());
    toAdd.setColumnIndex(field.getColumnIndex());
    if (toAdd.isOptionsStoringField()) {
      toAdd.setSelectedOptions(field.getSelectedOptions());
    } else {
      toAdd.setFieldData(field.getContent());
    }
    if (field.getMandatory() != null) {
      toAdd.setMandatory(field.getMandatory());
    }
    return toAdd;
  }

  private boolean definitionIsEmpty(ApiInventoryEntityField field) {
    return field.getDefinition() == null
        || CollectionUtils.isEmpty(field.getDefinition().getOptions());
  }
}
