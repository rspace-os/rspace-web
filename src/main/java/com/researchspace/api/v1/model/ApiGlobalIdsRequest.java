/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API representation of an API request that contains a list of global ids */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiGlobalIdsRequest {

  @Size(max = 1000)
  @JsonProperty("globalIds")
  private List<String> globalIds;
}
