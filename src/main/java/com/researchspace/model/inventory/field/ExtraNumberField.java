package com.researchspace.model.inventory.field;

import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import com.researchspace.service.JsonMessageSource;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;
import org.springframework.context.i18n.LocaleContextHolder;

@Entity
@Audited
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("number")
public class ExtraNumberField extends ExtraField {

  private static final long serialVersionUID = 616794358851234028L;
  private static final String DEFAULT_NAME = "Numeric data";
  private static final String INVALID_NUMBER_MESSAGE = "errors.inventory.field.numberInvalid";
  private static final JsonMessageSource VALIDATION_MESSAGES = new JsonMessageSource();

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
    String validationMsg = validateNewData(data);
    if (validationMsg != null) {
      throw new IllegalArgumentException(validationMsg);
    }
    super.setData(data);
  }

  @Transient
  @Override
  public FieldType getType() {
    return FieldType.NUMBER;
  }

  @Override
  public String validateNewData(String data) {
    if (StringUtils.isNotEmpty(data)) {
      try {
        RuntimeFieldValueType.parseNumber(data);
      } catch (IllegalArgumentException nfe) {
        return VALIDATION_MESSAGES.getMessage(
            INVALID_NUMBER_MESSAGE, new Object[] {data}, LocaleContextHolder.getLocale());
      }
    }
    return null;
  }

  @Override
  public ExtraNumberField shallowCopy() {
    ExtraNumberField copy = new ExtraNumberField();
    copyProperties(copy);
    return copy;
  }
}
