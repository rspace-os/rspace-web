/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.Group;
import com.researchspace.model.core.GlobalIdPrefix;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Basic details about a User or a Group */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "uniqueName"})
public class ApiGroupBasicInfo extends IdentifiableNameableApiObject {

  public String uniqueName;

  public ApiGroupBasicInfo(Group group) {
    super(group.getId(), GlobalIdPrefix.GP.name() + group.getId(), group.getDisplayName());
    setUniqueName(group.getUniqueName());
  }
}
