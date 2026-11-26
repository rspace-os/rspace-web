package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue(FieldType.STRING_TYPE)
@Audited
public class StringFieldForm extends FieldForm {

  public StringFieldForm(String name) {
    super(name);
    setType(FieldType.STRING);
  }

  public StringFieldForm() {
    setType(FieldType.STRING);
  }

  /** */
  private static final long serialVersionUID = -1027880121882290768L;

  private String defaultStringValue = "";

  public String getDefaultStringValue() {
    return defaultStringValue;
  }

  public void setDefaultStringValue(String defaultStringValue) {
    this.defaultStringValue = defaultStringValue;
  }

  @Override
  public String toString() {
    return "StringFieldTemplate [defaultStringValue=" + defaultStringValue + "]" + super.toString();
  }

  public StringFieldForm shallowCopy() {
    StringFieldForm nft = new StringFieldForm();
    copyPropertiesToCopy(nft);
    nft.defaultStringValue = defaultStringValue;
    return nft;
  }

  public StringField _createNewFieldFromForm() {
    StringField cf = new StringField(this);
    return cf;
  }

  @Transient
  public String getSummary() {
    String def =
        StringUtils.isEmpty(getDefaultStringValue()) ? "Unspecified" : getDefaultStringValue();
    return "Type: [" + getType() + "], Default : [" + def + "] ";
  }

  @Override
  public ErrorList validate(String data) {
    return new ErrorList();
  }

  @Override
  @Transient
  public String getDefault() {
    return getDefaultStringValue();
  }
}
