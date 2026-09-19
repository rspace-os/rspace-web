package com.researchspace.model.field;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.net.URI;
import java.net.URISyntaxException;
import org.hibernate.envers.Audited;

/**
 * URIFieldForm defines a field that can hold any URI
 *
 * @see java.net.URI for details of validation/ syntax
 */
@Entity
@DiscriminatorValue(FieldType.URI_TYPE)
@Audited
public class URIFieldForm extends FieldForm {

  public URIFieldForm(String name) {
    super(name);
    setType(FieldType.URI);
  }

  public URIFieldForm() {
    setType(FieldType.URI);
  }

  private static final long serialVersionUID = -1027880121852290768L;

  @Override
  public String toString() {
    return "UriFieldForm " + super.toString();
  }

  public URIFieldForm shallowCopy() {
    URIFieldForm nft = new URIFieldForm();
    copyPropertiesToCopy(nft);
    return nft;
  }

  public UriField _createNewFieldFromForm() {
    return new UriField(this);
  }

  @Transient
  public String getSummary() {
    return "URIFieldForm";
  }

  /** Is valid if <code>data</code> can be parsed into a java.net.URL */
  @Override
  public ErrorList validate(String data) {
    try {
      new URI(data);
    } catch (URISyntaxException e) {
      ErrorList errors = new ErrorList();
      errors.addErrorMsgCode("validation.fieldData.invalidUri", e.getMessage());
      return errors;
    }
    return new ErrorList();
  }

  @Override
  @Transient
  public String getDefault() {
    return "";
  }
}
