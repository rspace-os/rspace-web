package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class ApiInventoryImportSubSampleImportResult extends ApiInventoryImportPartialResult {

  /* map storing record number (basically csv row number) and import identifier of their requested sample */
  @JsonIgnore
  private Map<Integer, String> resultNumberToParentSampleImportIdMap = new LinkedHashMap<>();

  /* map storing record number (basically csv row number) and global id of their requested sample */
  @Getter @JsonIgnore
  private Map<Integer, GlobalIdentifier> resultNumberToParentSampleGlobalIdMap =
      new LinkedHashMap<>();

  public ApiInventoryImportSubSampleImportResult() {
    super(ApiInventoryRecordInfo.ApiInventoryRecordType.SUBSAMPLE);
  }

  public void addResultNumberWithParentSampleImportId(Integer recResultNumber, String importId) {
    resultNumberToParentSampleImportIdMap.put(recResultNumber, importId);
  }

  public String getParentSampleImportIdForResultNumber(Integer recResultNumber) {
    return resultNumberToParentSampleImportIdMap.get(recResultNumber);
  }

  public void addResultNumberWithParentSampleGlobalId(
      Integer recResultNumber, GlobalIdentifier importId) {
    resultNumberToParentSampleGlobalIdMap.put(recResultNumber, importId);
  }

  public GlobalIdentifier getParentSampleGlobalIdForResultNumber(Integer recResultNumber) {
    return resultNumberToParentSampleGlobalIdMap.get(recResultNumber);
  }
}
