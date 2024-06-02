/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormState;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.Validate;

/** The Form on which the document structure is based. Does not include fields. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ApiAbstractFormInfo extends IdentifiableNameableApiObject {

  @JsonProperty("stableId")
  private String stableId = null;

  @JsonProperty("version")
  private Integer version = null;

  private FormState formState;
  private String tags;
  private Long iconId;

  public ApiAbstractFormInfo(AbstractForm form) {
    setId(form.getId());
    setGlobalId(form.getOid().getIdString());
    setName(form.getName());
    setFormState(form.getPublishingState());
    setTags(form.getTags());
    this.version = form.getVersion().getVersion().intValue();
    this.stableId = form.getStableID();
    this.iconId = form.getIconId();
  }

  private static Pattern formOrTemplateGlobalIdPattern = Pattern.compile("(IT\\d+)|(FM\\d+)");

  /**
   * Validates that GlobaId is consistent with Id and bot
   *
   * @return the id, or null if the formId is not set (e.g. it's a new form)
   */
  public Long retrieveFormIdFromApiForm() {

    Long apiFormId = getId();
    String apiFormGlobalIdString = getGlobalId();
    if (apiFormId == null && apiFormGlobalIdString == null) {
      return null; // form field may be empty
    }

    if (apiFormGlobalIdString != null) {
      Validate.isTrue(
          isFormOrTemplateId(apiFormGlobalIdString),
          "Wrong format of provided form.globalId [" + apiFormGlobalIdString + "]");

      Long apiFormGlobalId = parseIdFromGlobalId(apiFormGlobalIdString);
      if (apiFormId != null) {
        Validate.isTrue(
            apiFormId.equals(apiFormGlobalId),
            String.format(
                "form.id [%d] doesn't match " + "form.globalId [%s]",
                apiFormId, apiFormGlobalIdString));
      }

      return apiFormGlobalId;
    }
    return apiFormId;
  }

  private Long parseIdFromGlobalId(String apiFormGlobalIdString) {
    return new GlobalIdentifier(apiFormGlobalIdString).getDbId();
  }

  private boolean isFormOrTemplateId(String globaIdStr) {
    Matcher matcher = formOrTemplateGlobalIdPattern.matcher(globaIdStr);
    return GlobalIdentifier.isValid(globaIdStr) && matcher.matches();
  }
}
