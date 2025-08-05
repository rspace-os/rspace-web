package com.researchspace.integrations.galaxy.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GalaxySummaryStatusReport {

  private String rspaceFieldName;
  private String galaxyHistoryName;
  private String galaxyHistoryId;
  private String galaxyDataNames;
  private String galaxyInvocationName;
  private String galaxyInvocationStatus;
  private String galaxyInvocationId;
  private String galaxyBaseUrl;
  private Date createdOn;

  /**
   * Creates a summary report for all data uploaded to a SINGLE GALAXY HISTORY. That data is not
   * used in any invocations
   */
  public static GalaxySummaryStatusReport createForHistoryForDataNotUsedInAnyInvocations(
      List<ExternalWorkFlowData> allDataUploadedToGalaxyForThisGalaxyHistory) {
    GalaxySummaryStatusReport report = new GalaxySummaryStatusReport();
    ExternalWorkFlowData first = allDataUploadedToGalaxyForThisGalaxyHistory.get(0);
    List<String> allDataNames = new ArrayList<>();
    for (ExternalWorkFlowData data : allDataUploadedToGalaxyForThisGalaxyHistory) {
      allDataNames.add(data.getExtName());
    }
    report.setGalaxyDataNames(String.join(", ", allDataNames));
    report.setRspaceFieldName(first.getRspacecontainerName());
    report.setGalaxyBaseUrl(first.getBaseUrl());
    report.setGalaxyHistoryName(first.getExtContainerName());
    report.setGalaxyHistoryId(first.getExtContainerID());
    return report;
  }

  /** Creates a summary report for all data uploaded PER HISTORY when there are NO INVOCATIONS */
  public static List<GalaxySummaryStatusReport> createPerHistoryForDataUnusedByAnyInvocation(
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField) {
    List<GalaxySummaryStatusReport> summaryReports = new ArrayList<>();
    Map<String, List<ExternalWorkFlowData>> groupedByHistoryId =
        groupByHistoryId(allDataUploadedToGalaxyForThisRSpaceField);
    for (String historyId : groupedByHistoryId.keySet()) {
      summaryReports.add(
          createForHistoryForDataNotUsedInAnyInvocations(groupedByHistoryId.get(historyId)));
    }
    return summaryReports;
  }

  /**
   * Groups workflow data into lists which have the historyID as key in the map
   *
   * @param allDataUploadedToGalaxyForThisRspaceProject a List of workflowdata
   */
  public static Map<String, List<ExternalWorkFlowData>> groupByHistoryId(
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRspaceProject) {
    Map<String, List<ExternalWorkFlowData>> groupedByHistoryId = new HashMap<>();
    for (ExternalWorkFlowData data : allDataUploadedToGalaxyForThisRspaceProject) {
      groupedByHistoryId.computeIfPresent(
          data.getExtContainerID(),
          (extContainerID, existingListForHistoryId) -> {
            existingListForHistoryId.add(data);
            return existingListForHistoryId;
          });
      groupedByHistoryId.computeIfAbsent(
          data.getExtContainerID(),
          k -> {
            List newList = new ArrayList<>();
            newList.add(data);
            return newList;
          });
    }
    return groupedByHistoryId;
  }

  /**
   * Creates summary report when there are histories with invocations (and optionally some histories
   * with no invocations)
   */
  public static List<GalaxySummaryStatusReport> createForInvocationsAndForDataAlone(
      Set<GalaxyInvocationDetails> invocationsAndDataSetsMatchingRSpaceData,
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField) {
    List<GalaxySummaryStatusReport> summaryReports = new ArrayList<>();
    for (GalaxyInvocationDetails galaxyInvocationDetails :
        invocationsAndDataSetsMatchingRSpaceData) {
      summaryReports.add(
          createForInvocation(galaxyInvocationDetails, allDataUploadedToGalaxyForThisRSpaceField));
    }
    List<String> historyIdsWithAnInvocations =
        invocationsAndDataSetsMatchingRSpaceData.stream()
            .map(i -> i.getInvocation().getHistoryId())
            .collect(Collectors.toList());
    Set<ExternalWorkFlowData> notHavingAnInvocation =
        allDataUploadedToGalaxyForThisRSpaceField.stream()
            .filter(data -> !historyIdsWithAnInvocations.contains(data.getExtContainerID()))
            .collect(Collectors.toSet());
    List<GalaxySummaryStatusReport> dataOnlyReports =
        createPerHistoryForDataUnusedByAnyInvocation(notHavingAnInvocation);
    summaryReports.addAll(dataOnlyReports);
    return summaryReports;
  }

  public static GalaxySummaryStatusReport createForInvocation(
      GalaxyInvocationDetails galaxyInvocationDetails,
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField) {
    GalaxySummaryStatusReport report = new GalaxySummaryStatusReport();
    report.setGalaxyInvocationStatus(galaxyInvocationDetails.getState());
    report.setGalaxyInvocationId(galaxyInvocationDetails.getInvocation().getInvocationId());
    if (galaxyInvocationDetails.getPersistedInvocation() != null) {
      ExternalWorkFlowInvocation externalWorkFlowInvocation =
          galaxyInvocationDetails.getPersistedInvocation();
      report.setGalaxyInvocationName(externalWorkFlowInvocation.getExternalWorkFlow().getName());
      report.setGalaxyDataNames(
          String.join(
              ", ",
              externalWorkFlowInvocation.getExternalWorkFlowData().stream()
                  .map(data -> data.getExtName())
                  .collect(Collectors.toList())));
    } else {
      report.setGalaxyInvocationName(galaxyInvocationDetails.getWorkflowName());
      String dataSetCollectionNames = "";
      String dataSetNames = "";
      if (galaxyInvocationDetails.getDataSetCollectionsUsedInInvocation() != null) {
        dataSetCollectionNames =
            String.join(
                ", ",
                galaxyInvocationDetails.getDataSetCollectionsUsedInInvocation().stream()
                    .map(data -> data.getName())
                    .collect(Collectors.toList()));
      }
      if (galaxyInvocationDetails.getDataSetsUsedInInvocation() != null) {
        dataSetNames =
            String.join(
                ", ",
                galaxyInvocationDetails.getDataSetsUsedInInvocation().stream()
                    .map(data -> data.getName())
                    .collect(Collectors.toList()));
      }
      report.setGalaxyDataNames((dataSetCollectionNames + " ").trim() + dataSetNames);
    }
    ExternalWorkFlowData first =
        groupByHistoryId(allDataUploadedToGalaxyForThisRSpaceField)
            .get(galaxyInvocationDetails.getInvocation().getHistoryId())
            .get(0);
    report.setRspaceFieldName(first.getRspacecontainerName());
    report.setGalaxyBaseUrl(first.getBaseUrl());
    report.setGalaxyHistoryName(first.getExtContainerName());
    report.setGalaxyHistoryId(first.getExtContainerID());
    report.setCreatedOn(galaxyInvocationDetails.getInvocation().getCreateTime());
    return report;
  }
}
