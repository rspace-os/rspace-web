package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@Audited
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("number")
public class ExtraNumberField extends ExtraField {

  private static final long serialVersionUID = 616794358851234028L;
  private static final String DEFAULT_NAME = "Numeric data";

  public ExtraNumberField() {
    setName(DEFAULT_NAME);
  }

  /**
   * Checks if the passed value contains valid number, then saves it as a field value.
   *
   * @param data
   */
  @Override
  public void setData(String data) {
    ErrorList errors = validateNewData(data);
    if (errors.hasErrorMessages()) {
      throw new LocalizedIllegalArgumentException("validation.inventoryField.invalidData", errors);
    }
    super.setData(data);
  }

  @Transient
  @Override
  public FieldType getType() {
    return FieldType.NUMBER;
  }

  @Override
  public ErrorList validateNewData(String data) {
    ErrorList errors = new ErrorList();
    if (StringUtils.isNotEmpty(data)) {
      try {
        new BigDecimal(data);
      } catch (NumberFormatException nfe) {
        errors.addErrorMsgCode("validation.inventoryField.extraNumberInvalid", data);
      }
    }
    return errors;
  }

  @Override
  public ExtraNumberField shallowCopy() {
    ExtraNumberField copy = new ExtraNumberField();
    copyProperties(copy);
    return copy;
  }
}
