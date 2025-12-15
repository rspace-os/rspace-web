package com.researchspace.integrations.galaxy.service;

import com.researchspace.galaxy.model.output.upload.DatasetCollection;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.sql.Date;
import java.util.List;
import java.util.Set;

public class GalaxyInvocationDetailsTestMother {
  public static final Date invocationDate = Date.valueOf("2019-01-01");

  public static GalaxyInvocationDetails createInvocationdetails(
      String name, String state, String historyID, DatasetCollection used) {
    WorkflowInvocationResponse wir = new WorkflowInvocationResponse();
    wir.setInvocationId("test_invocation_id_" + name);
    wir.setHistoryId(historyID);
    wir.setCreateTime(invocationDate);
    wir.setState(state);
    GalaxyInvocationDetails gid = new GalaxyInvocationDetails();
    gid.setInvocation(wir);
    gid.setDataSetCollectionsUsedInInvocation(List.of(used));
    gid.setWorkflowName(name);
    gid.setState(state);
    gid.setPersistedInvocation(
        ExternalWorkFlowInvocation.builder()
            .extId("test_invocation_id_" + name)
            .externalWorkFlowData(
                Set.of(
                    ExternalWorkFlowTestMother.createExternalWorkFlowDataWithNonDefaultName(
                        historyID, "data1", "history_name1", used.getName())))
            .externalWorkFlow(new ExternalWorkFlow("extID", name, ""))
            .status(state)
            .build());
    return gid;
  }
}
