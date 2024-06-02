package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ApiInventoryImportPartialResult extends ApiInventoryBulkOperationResult {

  @Getter
  @JsonProperty(value = "type")
  private ApiInventoryRecordType type;

  /* two maps storing record number (basically csv row number) and its import identifier */
  @JsonIgnore private Map<Integer, String> resultNumberToImportIdMap = new HashMap<>();
  @JsonIgnore private Map<String, Integer> importIdToResultNumber = new HashMap<>();

  /* map storing record number (basically csv row number) and import identifier of their requested parent container */
  @Getter @JsonIgnore
  private Map<Integer, String> resultNumberToParentContainerImportIdMap = new LinkedHashMap<>();

  /* map storing record number (basically csv row number) and global id of their requested parent container */
  @Getter @JsonIgnore
  private Map<Integer, GlobalIdentifier> resultNumberToParentContainerGlobalIdMap =
      new LinkedHashMap<>();

  public ApiInventoryImportPartialResult(ApiInventoryRecordType resultType) {
    this.type = resultType;
  }

  public void addResultNumberWithImportId(Integer recResultNumber, String importId) {
    resultNumberToImportIdMap.put(recResultNumber, importId);
    importIdToResultNumber.put(importId, recResultNumber);
  }

  public Integer getResultNumberForImportId(String importId) {
    return importIdToResultNumber.get(importId);
  }

  public ApiInventoryBulkOperationRecordResult getResultForImportId(String importId) {
    Integer resultNumber = getResultNumberForImportId(importId);
    return resultNumber == null ? null : getResults().get(resultNumber);
  }

  public String getImportIdForResultNumber(Integer recResultNumber) {
    return resultNumberToImportIdMap.get(recResultNumber);
  }

  public void addResultNumberWithParentContainerImportId(Integer recResultNumber, String importId) {
    resultNumberToParentContainerImportIdMap.put(recResultNumber, importId);
  }

  public String getParentContainerImportIdForResultNumber(Integer recResultNumber) {
    return resultNumberToParentContainerImportIdMap.get(recResultNumber);
  }

  public void addResultNumberWithParentContainerGlobalId(
      Integer recResultNumber, GlobalIdentifier importId) {
    resultNumberToParentContainerGlobalIdMap.put(recResultNumber, importId);
  }

  public GlobalIdentifier getParentContainerGlobalIdForResultNumber(Integer recResultNumber) {
    return resultNumberToParentContainerGlobalIdMap.get(recResultNumber);
  }

  public ApiInventoryImportPartialResult copyWithImportIdMapsOnly() {
    ApiInventoryImportPartialResult copy = new ApiInventoryImportPartialResult(type);
    copyImportIdMaps(copy);
    return copy;
  }

  protected void copyImportIdMaps(ApiInventoryImportPartialResult copy) {
    copy.resultNumberToImportIdMap.putAll(resultNumberToImportIdMap);
    copy.importIdToResultNumber.putAll(importIdToResultNumber);
  }
}
