package com.researchspace.api.v1.controller;

import static com.researchspace.service.impl.DocumentTagManagerImpl.RSPACTAGS_COMMA__;
import static com.researchspace.service.impl.DocumentTagManagerImpl.RSPACTAGS_FORSL__;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.Transient;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class InventoryApiSearchConfig extends ApiSearchConfig {

  public static final int MAX_QUERY_LENGTH = 2000;

  @Size(max = MAX_QUERY_LENGTH, message = "Max query length is " + MAX_QUERY_LENGTH)
  private String query;

  @Pattern(
      regexp = "SAMPLE|SUBSAMPLE|CONTAINER|TEMPLATE",
      message = "Requested result type must be SAMPLE, SUBSAMPLE, CONTAINER or TEMPLATE")
  private String resultType;

  @Pattern(
      regexp = "(IC|BE|SA|IT|BA)(\\d+)(v\\d+)?",
      message =
          "Requested parentGlobalId is incorrect, must be global id of a Container, Workbench,"
              + " Sample, Sample Template, or Basket")
  @JsonProperty("parentGlobalId")
  private String parentGlobalId;

  @Size(max = User.MAX_UNAME_LENGTH, message = "Provided value is too long for a username")
  @JsonProperty("ownedBy")
  private String ownedBy;

  @Pattern(
      regexp = "EXCLUDE|INCLUDE|DELETED_ONLY",
      message = "Requested deletedItems option must be one of: EXCLUDE, INCLUDE or DELETED_ONLY")
  private String deletedItems;

  /**
   * Inspect query string for tags: and modify the search term if it contains values we substitute -
   * eg '/'
   *
   * @param query a query string for an Inventory search
   * @return
   */
  public static String modifyTagSearch(String query) {
    if (!org.springframework.util.StringUtils.hasText(query)
        || query.indexOf("tags:") == -1
        || (query.indexOf("/") == -1 && query.indexOf(",") == -1)) {
      return query;
    } else {
      if (query.indexOf(" AND ") == -1
          && query.indexOf(" OR ") == -1
          && query.indexOf(" NOT ") == -1) {
        return getModifiedString(query);
      } else {
        int tagsPos = query.indexOf("tags:");
        // terms can be joined by (AND OR NOT) - find the closest to tags position
        int ANDEND = query.indexOf(" AND ", tagsPos);
        int OREND = query.indexOf(" OR ", tagsPos);
        int NOTEND = query.indexOf(" NOT ", tagsPos);
        List<Integer> combinerPositions = new ArrayList<>();
        if (ANDEND > 0) {
          combinerPositions.add(ANDEND);
        }
        if (OREND > 0) {
          combinerPositions.add(OREND);
        }
        if (NOTEND > 0) {
          combinerPositions.add(NOTEND);
        }
        Collections.sort(combinerPositions);
        int endPositionForTags = combinerPositions.get(0);
        String modified = getModifiedString(query.substring(tagsPos, endPositionForTags));
        return query.substring(0, tagsPos) + modified + query.substring(endPositionForTags);
      }
    }
  }

  @NotNull
  private static String getModifiedString(String query) {
    int tagsPos = query.indexOf("tags:") + 5;
    int end = query.lastIndexOf("\"");
    // search terms are enclosed in quotes but tag values may CONTAIN quotes
    int startingTagQuotePos = query.indexOf("\"", tagsPos) + 1;
    String tagValue = query.substring(startingTagQuotePos, end);
    tagValue = tagValue.replaceAll("/", RSPACTAGS_FORSL__);
    tagValue = tagValue.replaceAll(",", RSPACTAGS_COMMA__);
    query = query.substring(0, startingTagQuotePos) + tagValue + query.substring(end);
    return query;
  }

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    if (!StringUtils.isBlank(query)) {
      rc.add("query", query);
    }
    if (resultType != null) {
      rc.add("resultType", resultType);
    }
    if (parentGlobalId != null) {
      rc.add("parentGlobalId", parentGlobalId);
    }
    if (ownedBy != null) {
      rc.add("ownedBy", ownedBy);
    }
    if (deletedItems != null) {
      rc.add("deletedItems", deletedItems);
    }
    return rc;
  }

  @Transient
  public InventorySearchDeletedOption getDeletedItemsAsEnum() {
    String requestedDeletedItems = getDeletedItems();
    if (StringUtils.isNotBlank(requestedDeletedItems)) {
      return InventorySearchDeletedOption.valueOf(requestedDeletedItems);
    }
    return null;
  }

  @Transient
  public InventorySearchType getResultTypeAsEnum() {
    String requestedResultType = getResultType();
    if (StringUtils.isNotBlank(requestedResultType)) {
      return InventorySearchType.valueOf(requestedResultType);
    }
    return null;
  }
}
