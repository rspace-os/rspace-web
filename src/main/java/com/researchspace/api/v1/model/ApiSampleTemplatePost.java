package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.units.RSUnitDef;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ApiSampleTemplatePost extends ApiSampleTemplateInfo {

  @JsonProperty("subSampleAlias")
  @NotNull
  private ApiSubSampleAlias subSampleAlias =
      new ApiSubSampleAlias(
          SubSampleName.SUBSAMPLE.getDisplayName(), SubSampleName.SUBSAMPLE.getDisplayNamePlural());

  @JsonProperty("defaultUnitId")
  @Min(1)
  private Integer defaultUnitId = RSUnitDef.MILLI_LITRE.getId();

  @JsonProperty("fields")
  protected List<ApiInventoryEntityField> fields = new ArrayList<>();

  @JsonProperty(value = "sharedWith")
  private List<ApiGroupInfoWithSharedFlag> sharedWith;

  public ApiSampleTemplatePost(SampleTemplate template) {
    super(template);
    for (InventoryEntityField f : template.getActiveFields()) {
      fields.add(new ApiInventoryEntityField(f));
    }
  }
}
