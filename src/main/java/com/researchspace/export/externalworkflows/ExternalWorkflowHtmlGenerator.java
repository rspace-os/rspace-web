package com.researchspace.export.externalworkflows;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

import com.researchspace.archive.ArchiveExternalWorkFlow;
import com.researchspace.archive.ArchiveExternalWorkFlowData;
import com.researchspace.archive.ArchiveExternalWorkFlowInvocation;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Renders persisted external workflow data in human-readable document exports. */
@Service
public class ExternalWorkflowHtmlGenerator {
  private static final String NO_VALUE = "-";

  @Value("${server.urls.prefix}")
  private String urlPrefix;

  @Autowired private VelocityEngine velocityEngine;

  public String getHtmlForExternalWorkflowData(Set<ExternalWorkFlowData> externalWorkFlowData) {
    if (externalWorkFlowData == null || externalWorkFlowData.isEmpty()) {
      return "";
    }
    return renderRows(createRowsForExternalWorkflowData(externalWorkFlowData));
  }

  public String getHtmlForArchivedExternalWorkflowData(
      Set<ArchiveExternalWorkFlowData> externalWorkFlowData,
      Set<ArchiveExternalWorkFlowInvocation> invocations) {
    if (externalWorkFlowData == null || externalWorkFlowData.isEmpty()) {
      return "";
    }
    return renderRows(createRowsForArchivedExternalWorkflowData(externalWorkFlowData, invocations));
  }

  void setUrlPrefix(String urlPrefix) {
    this.urlPrefix = urlPrefix;
  }

  private String renderRows(List<ExternalWorkflowTableData> rows) {
    Map<String, Object> context = new HashMap<>();
    context.put("rows", rows);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocityEngine, "pdf/external-workflow-table.vm", "UTF-8", context);
  }

  private List<ExternalWorkflowTableData> createRowsForExternalWorkflowData(
      Set<ExternalWorkFlowData> externalWorkFlowData) {
    List<ExternalWorkflowTableData> rows = new ArrayList<>();
    List<ExternalWorkFlowData> sortedData = sortExternalWorkflowData(externalWorkFlowData);
    Set<String> historyIdsWithInvocations = new HashSet<>();
    Set<ExternalWorkFlowInvocation> invocations = collectInvocations(sortedData);

    for (ExternalWorkFlowInvocation invocation : sortInvocations(invocations)) {
      Map<String, List<ExternalWorkFlowData>> invocationDataByHistory =
          groupExternalWorkflowDataByHistoryId(
              sortedData.stream()
                  .filter(data -> invocation.getExternalWorkFlowData().contains(data))
                  .collect(Collectors.toList()));
      for (List<ExternalWorkFlowData> dataForInvocation : invocationDataByHistory.values()) {
        ExternalWorkFlowData first = dataForInvocation.get(0);
        historyIdsWithInvocations.add(first.getExtContainerID());
        rows.add(createInvocationRow(dataForInvocation, invocation, first));
      }
    }

    rows.addAll(createDataOnlyRows(sortedData, historyIdsWithInvocations));
    return rows;
  }

  private List<ExternalWorkflowTableData> createRowsForArchivedExternalWorkflowData(
      Set<ArchiveExternalWorkFlowData> externalWorkFlowData,
      Set<ArchiveExternalWorkFlowInvocation> invocations) {
    List<ExternalWorkflowTableData> rows = new ArrayList<>();
    List<ArchiveExternalWorkFlowData> sortedData =
        sortArchivedExternalWorkflowData(externalWorkFlowData);
    Set<String> historyIdsWithInvocations = new HashSet<>();

    for (ArchiveExternalWorkFlowInvocation invocation : sortArchivedInvocations(invocations)) {
      Map<String, List<ArchiveExternalWorkFlowData>> invocationDataByHistory =
          groupArchivedExternalWorkflowDataByHistoryId(
              sortedData.stream()
                  .filter(data -> invocation.getDataIds().contains(data.getId()))
                  .collect(Collectors.toList()));
      for (List<ArchiveExternalWorkFlowData> dataForInvocation : invocationDataByHistory.values()) {
        ArchiveExternalWorkFlowData first = dataForInvocation.get(0);
        historyIdsWithInvocations.add(first.getExtContainerId());
        rows.add(createArchivedInvocationRow(dataForInvocation, invocation, first));
      }
    }

    rows.addAll(createArchivedDataOnlyRows(sortedData, historyIdsWithInvocations));
    return rows;
  }

  private Set<ExternalWorkFlowInvocation> collectInvocations(List<ExternalWorkFlowData> data) {
    return data.stream()
        .flatMap(item -> item.getExternalWorkflowInvocations().stream())
        .collect(Collectors.toSet());
  }

  private List<ExternalWorkflowTableData> createDataOnlyRows(
      List<ExternalWorkFlowData> data, Set<String> historyIdsWithInvocations) {
    return groupExternalWorkflowDataByHistoryId(
            data.stream()
                .filter(item -> !historyIdsWithInvocations.contains(item.getExtContainerID()))
                .collect(Collectors.toList()))
        .values()
        .stream()
        .map(items -> createDataOnlyRow(items, items.get(0)))
        .collect(Collectors.toList());
  }

  private List<ExternalWorkflowTableData> createArchivedDataOnlyRows(
      List<ArchiveExternalWorkFlowData> data, Set<String> historyIdsWithInvocations) {
    return groupArchivedExternalWorkflowDataByHistoryId(
            data.stream()
                .filter(item -> !historyIdsWithInvocations.contains(item.getExtContainerId()))
                .collect(Collectors.toList()))
        .values()
        .stream()
        .map(items -> createArchivedDataOnlyRow(items, items.get(0)))
        .collect(Collectors.toList());
  }

  private ExternalWorkflowTableData createInvocationRow(
      List<ExternalWorkFlowData> dataForInvocation,
      ExternalWorkFlowInvocation invocation,
      ExternalWorkFlowData first) {
    return new ExternalWorkflowTableData(
        dataForInvocation.stream().map(this::createDataLink).collect(Collectors.toList()),
        display(first.getExtContainerName()),
        galaxyHistoryHref(first.getBaseUrl(), first.getExtContainerID()),
        display(invocation.getExternalWorkFlow().getName()),
        galaxyInvocationHref(first.getBaseUrl(), invocation.getExtId()),
        display(invocation.getStatus()));
  }

  private ExternalWorkflowTableData createArchivedInvocationRow(
      List<ArchiveExternalWorkFlowData> dataForInvocation,
      ArchiveExternalWorkFlowInvocation invocation,
      ArchiveExternalWorkFlowData first) {
    ArchiveExternalWorkFlow workFlow = invocation.getWorkFlowMetaData();
    String invocationName = workFlow != null ? workFlow.getName() : null;
    return new ExternalWorkflowTableData(
        dataForInvocation.stream().map(this::createArchivedDataLink).collect(Collectors.toList()),
        display(first.getExtContainerName()),
        galaxyHistoryHref(first.getBaseUrl(), first.getExtContainerId()),
        display(invocationName),
        galaxyInvocationHref(first.getBaseUrl(), invocation.getExtId()),
        display(invocation.getStatus()));
  }

  private ExternalWorkflowTableData createDataOnlyRow(
      List<ExternalWorkFlowData> dataForHistory, ExternalWorkFlowData first) {
    return new ExternalWorkflowTableData(
        dataForHistory.stream().map(this::createDataLink).collect(Collectors.toList()),
        display(first.getExtContainerName()),
        galaxyHistoryHref(first.getBaseUrl(), first.getExtContainerID()),
        NO_VALUE,
        "",
        NO_VALUE);
  }

  private ExternalWorkflowTableData createArchivedDataOnlyRow(
      List<ArchiveExternalWorkFlowData> dataForHistory, ArchiveExternalWorkFlowData first) {
    return new ExternalWorkflowTableData(
        dataForHistory.stream().map(this::createArchivedDataLink).collect(Collectors.toList()),
        display(first.getExtContainerName()),
        galaxyHistoryHref(first.getBaseUrl(), first.getExtContainerId()),
        NO_VALUE,
        "",
        NO_VALUE);
  }

  private ExternalWorkflowDataLink createDataLink(ExternalWorkFlowData data) {
    return new ExternalWorkflowDataLink(
        display(data.getExtName()), escapeHtml4(rspaceGalleryHref(data.getRspacedataid())));
  }

  private ExternalWorkflowDataLink createArchivedDataLink(ArchiveExternalWorkFlowData data) {
    return new ExternalWorkflowDataLink(
        display(data.getExtName()), escapeHtml4(data.getLinkFile()));
  }

  private String rspaceGalleryHref(long rspaceDataId) {
    String prefix = removeTrailingSlash(StringUtils.defaultString(urlPrefix));
    return prefix + "/gallery/item/" + rspaceDataId;
  }

  private String galaxyHistoryHref(String baseUrl, String historyId) {
    if (StringUtils.isAnyBlank(baseUrl, historyId)) {
      return "";
    }
    return escapeHtml4(removeTrailingSlash(baseUrl) + "/histories/view?id=" + historyId);
  }

  private String galaxyInvocationHref(String baseUrl, String invocationId) {
    if (StringUtils.isAnyBlank(baseUrl, invocationId)) {
      return "";
    }
    return escapeHtml4(removeTrailingSlash(baseUrl) + "/workflows/invocations/" + invocationId);
  }

  private String removeTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String display(String value) {
    return StringUtils.isBlank(value) ? NO_VALUE : escapeHtml4(value);
  }

  private Map<String, List<ExternalWorkFlowData>> groupExternalWorkflowDataByHistoryId(
      Collection<ExternalWorkFlowData> data) {
    return data.stream()
        .collect(
            Collectors.groupingBy(
                item -> StringUtils.defaultString(item.getExtContainerID()),
                LinkedHashMap::new,
                Collectors.toList()));
  }

  private Map<String, List<ArchiveExternalWorkFlowData>>
      groupArchivedExternalWorkflowDataByHistoryId(Collection<ArchiveExternalWorkFlowData> data) {
    return data.stream()
        .collect(
            Collectors.groupingBy(
                item -> StringUtils.defaultString(item.getExtContainerId()),
                LinkedHashMap::new,
                Collectors.toList()));
  }

  private List<ExternalWorkFlowData> sortExternalWorkflowData(Set<ExternalWorkFlowData> data) {
    return data.stream()
        .sorted(
            Comparator.comparing(
                    ExternalWorkFlowData::getExtContainerName,
                    Comparator.nullsFirst(String::compareTo))
                .thenComparing(
                    ExternalWorkFlowData::getExtContainerID,
                    Comparator.nullsFirst(String::compareTo))
                .thenComparing(
                    ExternalWorkFlowData::getExtName, Comparator.nullsFirst(String::compareTo)))
        .collect(Collectors.toList());
  }

  private List<ArchiveExternalWorkFlowData> sortArchivedExternalWorkflowData(
      Set<ArchiveExternalWorkFlowData> data) {
    return data.stream()
        .sorted(
            Comparator.comparing(
                    ArchiveExternalWorkFlowData::getExtContainerName,
                    Comparator.nullsFirst(String::compareTo))
                .thenComparing(
                    ArchiveExternalWorkFlowData::getExtContainerId,
                    Comparator.nullsFirst(String::compareTo))
                .thenComparing(
                    ArchiveExternalWorkFlowData::getExtName,
                    Comparator.nullsFirst(String::compareTo)))
        .collect(Collectors.toList());
  }

  private List<ExternalWorkFlowInvocation> sortInvocations(
      Set<ExternalWorkFlowInvocation> invocations) {
    List<ExternalWorkFlowInvocation> sortedInvocations = new ArrayList<>(invocations);
    sortedInvocations.sort(
        (first, second) -> {
          int workflowNameComparison =
              compareNullableStrings(getWorkflowName(first), getWorkflowName(second));
          if (workflowNameComparison != 0) {
            return workflowNameComparison;
          }
          return compareNullableStrings(first.getExtId(), second.getExtId());
        });
    return sortedInvocations;
  }

  private List<ArchiveExternalWorkFlowInvocation> sortArchivedInvocations(
      Set<ArchiveExternalWorkFlowInvocation> invocations) {
    if (invocations == null) {
      return new ArrayList<>();
    }
    List<ArchiveExternalWorkFlowInvocation> sortedInvocations = new ArrayList<>(invocations);
    sortedInvocations.sort(
        (first, second) -> {
          int workflowNameComparison =
              compareNullableStrings(
                  getArchivedWorkflowName(first), getArchivedWorkflowName(second));
          if (workflowNameComparison != 0) {
            return workflowNameComparison;
          }
          return compareNullableStrings(first.getExtId(), second.getExtId());
        });
    return sortedInvocations;
  }

  private String getWorkflowName(ExternalWorkFlowInvocation invocation) {
    return invocation.getExternalWorkFlow() != null
        ? invocation.getExternalWorkFlow().getName()
        : null;
  }

  private String getArchivedWorkflowName(ArchiveExternalWorkFlowInvocation invocation) {
    ArchiveExternalWorkFlow workflow = invocation.getWorkFlowMetaData();
    return workflow != null ? workflow.getName() : null;
  }

  private int compareNullableStrings(String first, String second) {
    return Comparator.nullsFirst(String::compareTo).compare(first, second);
  }
}
