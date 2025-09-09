package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.RecordGroupSharing;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"id", "sharedItemId", "shareItemName", "sharedTargetType", "permission", "_links"})
public class DocumentShares extends LinkableApiObject {
  List<RecordGroupSharing> implicitShares;
  List<RecordGroupSharing> directShares;
}
