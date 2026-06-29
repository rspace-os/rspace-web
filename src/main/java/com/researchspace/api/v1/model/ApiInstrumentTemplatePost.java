package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** POST payload describing a new {@link InstrumentTemplate} to be created. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ApiInstrumentTemplatePost extends ApiInstrumentEntityInfo {

  @JsonProperty("fields")
  protected List<ApiInventoryEntityField> fields = new ArrayList<>();

  @JsonProperty("extraFields")
  protected List<ApiExtraField> extraFields = new ArrayList<>();

  @JsonProperty(value = "sharedWith")
  private List<ApiGroupInfoWithSharedFlag> sharedWith;

  @Override
  public List<ApiExtraField> getExtraFields() {
    return extraFields;
  }

  public ApiInstrumentTemplatePost(InstrumentTemplate template) {
    super(template);
    for (InventoryEntityField f : template.getActiveFields()) {
      fields.add(new ApiInventoryEntityField(f));
    }
  }
}
