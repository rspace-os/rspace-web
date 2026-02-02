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
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class ApiShareSearchConfig extends ApiGenericSearchConfig {

  public static final int MAX_SHARE_ITEM_IDS = 1000;

  @Size(
      max = MAX_SHARE_ITEM_IDS,
      message = "Max number of ids on sharedItemIds list is " + MAX_SHARE_ITEM_IDS + ".")
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
