package com.researchspace.model.inventory.field;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldType;
import java.util.regex.Pattern;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue("identifier")
@Audited
public class InventoryIdentifierField extends InventoryEntityField {

  // as per specs on https://www.doi.org/#:~:text=TRY%20RESOLVING%20A%20DOI
  private static final String DOI_REGEX =
      "((http(s){0,1}://){0,1}doi[.]org/){0,1}(10[.][0-9]{4,}[^\\s\\\"\\/<>]*\\/[^\\s\\\"<>]+)";
  private static final Pattern doiPattern = Pattern.compile(DOI_REGEX, CASE_INSENSITIVE);
  private static final long serialVersionUID = -1757283716055607179L;
  public static final String DOI_URL_PREFIX = "https://doi.org/";

  public InventoryIdentifierField() {
    this("");
  }

  public InventoryIdentifierField(String name) {
    super(FieldType.IDENTIFIER, name);
  }

  /**
   * Fails if is not empty and is not a valid DOI format   */
  @Override
  public ErrorList validate(String fieldData) {
    ErrorList errorList = super.validate(fieldData);
    if (StringUtils.isNotBlank(fieldData) && !isValidDOI(fieldData)) {
      errorList.addErrorMsg("Invalid IGSN format: [" + fieldData + "]");
    }
    return errorList;
  }

  @Override
  public InventoryIdentifierField shallowCopy() {
    InventoryIdentifierField copy = new InventoryIdentifierField();
    copyFields(copy);
    return copy;
  }

  @Override
  public boolean isSuggestedFieldForData(String data) {
    return !validate(data).hasErrorMessages();
  }


  public static boolean isValidDOI(String identifier) {
    return doiPattern.matcher(identifier).matches();
  }

}
