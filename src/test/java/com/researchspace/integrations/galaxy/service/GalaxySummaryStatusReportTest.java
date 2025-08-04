package com.researchspace.integrations.galaxy.service;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.galaxy.model.output.upload.DatasetCollection;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class GalaxySummaryStatusReportTest {

  private List<ExternalWorkFlowData> testData;

  @Test
  public void testCreateForInvocationsOneInvocationOneHistory() {
    Set<ExternalWorkFlowData> workflowDataList = new HashSet<>();

    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1", "history_name1");
    workflowDataList.add(data1);
    DatasetCollection used = new DatasetCollection();
    used.setId("used1");
    used.setName("used_name1");
    GalaxyInvocationDetails invocationDetails =
        GalaxyInvocationDetailsTestMother.createInvocationdetails(
            "invocation_name1", "Running", "history1", used);
    List<GalaxySummaryStatusReport> reports =
        GalaxySummaryStatusReport.createForInvocationsAndForDataAlone(
            Set.of(invocationDetails), workflowDataList);
    assertEquals(1, reports.size(), "List should contain 1 item");
    GalaxySummaryStatusReport report = reports.get(0);
    makeGalaxyReportSummaryAssertions(report, "1", "Running");
  }

  private static void makeGalaxyReportSummaryAssertions(
      GalaxySummaryStatusReport report, String reportNumber, String status) {
    assertEquals(status, report.getGalaxyInvocationStatus());
    assertEquals(GalaxyInvocationDetailsTestMother.invocationDate, report.getCreatedOn());
    assertEquals(
        "test_invocation_id_invocation_name" + reportNumber, report.getGalaxyInvocationId());
    assertEquals("invocation_name" + reportNumber, report.getGalaxyInvocationName());
    assertEquals("default-baseurl", report.getGalaxyBaseUrl());
    assertEquals("history" + reportNumber, report.getGalaxyHistoryId());
    assertEquals("used_name" + reportNumber, report.getGalaxyDataNames());
    assertEquals("history_name" + reportNumber, report.getGalaxyHistoryName());
    assertEquals("default-rspace-container-name", report.getRspaceFieldName());
  }

  @Test
  public void testCreateForInvocationsTwoInvocationsTwoHistories() {
    Set<ExternalWorkFlowData> workflowDataList = new HashSet<>();

    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1", "history_name1");
    ExternalWorkFlowData data2 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history2", "data2", "history_name2");
    workflowDataList.add(data1);
    workflowDataList.add(data2);
    DatasetCollection used1 = new DatasetCollection();
    used1.setId("used1");
    used1.setName("used_name1");
    DatasetCollection used2 = new DatasetCollection();
    used2.setId("used2");
    used2.setName("used_name2");
    GalaxyInvocationDetails invocationDetails1 =
        GalaxyInvocationDetailsTestMother.createInvocationdetails(
            "invocation_name1", "Running", "history1", used1);
    GalaxyInvocationDetails invocationDetails2 =
        GalaxyInvocationDetailsTestMother.createInvocationdetails(
            "invocation_name2", "Failed", "history2", used2);
    LinkedHashSet<GalaxyInvocationDetails> invocationDetails =
        new LinkedHashSet<GalaxyInvocationDetails>();
    invocationDetails.add(invocationDetails1);
    invocationDetails.add(invocationDetails2);
    List<GalaxySummaryStatusReport> reports =
        GalaxySummaryStatusReport.createForInvocationsAndForDataAlone(
            invocationDetails, workflowDataList);
    assertEquals(2, reports.size(), "List should contain 1 item");
    GalaxySummaryStatusReport report1 = reports.get(0);
    makeGalaxyReportSummaryAssertions(report1, "1", "Running");
    GalaxySummaryStatusReport report2 = reports.get(1);
    makeGalaxyReportSummaryAssertions(report2, "2", "Failed");
  }

  @Test
  public void testCreateForInvocationsTwoInvocationsThreeHistories() {
    Set<ExternalWorkFlowData> workflowDataList = new HashSet<>();

    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1", "history_name1");
    ExternalWorkFlowData data2 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history2", "data2", "history_name2");
    ExternalWorkFlowData data3 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history3", "data3", "history_name3");
    workflowDataList.add(data1);
    workflowDataList.add(data2);
    workflowDataList.add(data3);
    DatasetCollection used1 = new DatasetCollection();
    used1.setId("used1");
    used1.setName("used_name1");
    DatasetCollection used2 = new DatasetCollection();
    used2.setId("used2");
    used2.setName("used_name2");
    GalaxyInvocationDetails invocationDetails1 =
        GalaxyInvocationDetailsTestMother.createInvocationdetails(
            "invocation_name1", "Running", "history1", used1);
    GalaxyInvocationDetails invocationDetails2 =
        GalaxyInvocationDetailsTestMother.createInvocationdetails(
            "invocation_name2", "Failed", "history2", used2);
    LinkedHashSet<GalaxyInvocationDetails> invocationDetails =
        new LinkedHashSet<GalaxyInvocationDetails>();
    invocationDetails.add(invocationDetails1);
    invocationDetails.add(invocationDetails2);
    List<GalaxySummaryStatusReport> reports =
        GalaxySummaryStatusReport.createForInvocationsAndForDataAlone(
            invocationDetails, workflowDataList);
    assertEquals(3, reports.size(), "List should contain 1 item");
    GalaxySummaryStatusReport report1 = reports.get(0);
    makeGalaxyReportSummaryAssertions(report1, "1", "Running");
    GalaxySummaryStatusReport report2 = reports.get(1);
    makeGalaxyReportSummaryAssertions(report2, "2", "Failed");
    GalaxySummaryStatusReport report3 = reports.get(2);
    assertNull(report3.getGalaxyInvocationStatus());
    assertNull(report3.getCreatedOn());
    assertNull(report3.getGalaxyInvocationId());
    assertNull(report3.getGalaxyInvocationName());
    assertEquals("default-baseurl", report3.getGalaxyBaseUrl());
    assertEquals("history3", report3.getGalaxyHistoryId());
    assertEquals("default-name", report3.getGalaxyDataNames());
    assertEquals("history_name3", report3.getGalaxyHistoryName());
    assertEquals("default-rspace-container-name", report2.getRspaceFieldName());
  }

  @Test
  public void testGroupByHistoryIdSingleDataItem() {
    Set<ExternalWorkFlowData> workflowDataList = new HashSet<>();

    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1");
    workflowDataList.add(data1);
    Map<String, List<ExternalWorkFlowData>> result =
        GalaxySummaryStatusReport.groupByHistoryId(workflowDataList);

    assertEquals(1, result.size(), "Map should contain 1 entries");
    assertTrue(result.containsKey("history1"), "Map should contain key 'history1'");
    assertEquals(1, result.get("history1").size(), "List for history1 should contain 1 items");

    List<ExternalWorkFlowData> history1Items = result.get("history1");
    assertEquals("data1", history1Items.get(0).getExtId());
  }

  @Test
  public void testGroupByHistoryIdTwoDataItemsOneHistory() {
    Set<ExternalWorkFlowData> workflowDataList = new LinkedHashSet<>();

    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1");
    ExternalWorkFlowData data2 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data2");
    workflowDataList.add(data1);
    workflowDataList.add(data2);
    Map<String, List<ExternalWorkFlowData>> result =
        GalaxySummaryStatusReport.groupByHistoryId(workflowDataList);

    assertEquals(1, result.size(), "Map should contain 1 entries");
    assertTrue(result.containsKey("history1"), "Map should contain key 'history1'");
    assertEquals(2, result.get("history1").size(), "List for history1 should contain 2 items");

    List<ExternalWorkFlowData> history1Items = result.get("history1");
    assertEquals("data1", history1Items.get(0).getExtId());

    assertEquals("data2", history1Items.get(1).getExtId());
  }

  @Test
  public void testGroupByHistoryIdTwoDataItemsTwoHistory() {
    Set<ExternalWorkFlowData> workflowDataList = new HashSet<>();
    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1");
    ExternalWorkFlowData data2 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history2", "data2");
    workflowDataList.add(data1);
    workflowDataList.add(data2);
    Map<String, List<ExternalWorkFlowData>> result =
        GalaxySummaryStatusReport.groupByHistoryId(workflowDataList);

    assertEquals(2, result.size(), "Map should contain 2 entries");
    assertTrue(result.containsKey("history1"), "Map should contain key 'history1'");
    assertTrue(result.containsKey("history2"), "Map should contain key 'history2'");
    assertEquals(1, result.get("history1").size(), "List for history1 should contain 1 items");
    assertEquals(1, result.get("history2").size(), "List for history2 should contain 1 item");

    List<ExternalWorkFlowData> history1Items = result.get("history1");
    assertEquals("data1", history1Items.get(0).getExtId());

    List<ExternalWorkFlowData> history2Items = result.get("history2");
    assertEquals("data2", history2Items.get(0).getExtId());
  }

  @Test
  public void testGroupByHistoryIdThreeDataItemsTwoHistory() {
    Set<ExternalWorkFlowData> workflowDataList = new LinkedHashSet<>();

    ExternalWorkFlowData data1 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data1");
    ExternalWorkFlowData data2 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history1", "data2");
    ExternalWorkFlowData data3 =
        ExternalWorkFlowTestMother.createExternalWorkFlowData("history2", "data3");
    workflowDataList.add(data1);
    workflowDataList.add(data2);
    workflowDataList.add(data3);
    Map<String, List<ExternalWorkFlowData>> result =
        GalaxySummaryStatusReport.groupByHistoryId(workflowDataList);

    assertEquals(2, result.size(), "Map should contain 2 entries");
    assertTrue(result.containsKey("history1"), "Map should contain key 'history1'");
    assertTrue(result.containsKey("history2"), "Map should contain key 'history2'");
    assertEquals(2, result.get("history1").size(), "List for history1 should contain 2 items");
    assertEquals(1, result.get("history2").size(), "List for history2 should contain 1 item");

    List<ExternalWorkFlowData> history1Items = result.get("history1");
    assertEquals("data1", history1Items.get(0).getExtId());
    assertEquals("data2", history1Items.get(1).getExtId());

    List<ExternalWorkFlowData> history2Items = result.get("history2");
    assertEquals("data3", history2Items.get(0).getExtId());
  }
}
