package com.researchspace.api.v1.controller;

import java.util.List;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ApiShareSearchConfig extends ApiGenericSearchConfig {

  private static final int MAX_SHARE_ITEM_IDS = 1000;

  @Size(
      max = MAX_SHARE_ITEM_IDS,
      message = "Maximum number of ids in sharedItemIds is " + MAX_SHARE_ITEM_IDS + ".")
  private List<Long> sharedItemIds;

  @Override
  public MultiValueMap<String, String> toMap() {
    MultiValueMap<String, String> rc = super.toMap();
    if (!CollectionUtils.isEmpty(sharedItemIds)) {
      rc.add("sharedItemIds", StringUtils.join(sharedItemIds, ","));
    }
    return rc;
  }
}
