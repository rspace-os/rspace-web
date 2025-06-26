package com.researchspace.integrations.galaxy.service;

import com.researchspace.galaxy.model.output.upload.DatasetCollection;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import java.sql.Date;
import java.util.List;

public class GalaxyInvocationDetailsTestMother {
  public static final Date invocationDate = Date.valueOf("2019-01-01");

  public static GalaxyInvocationDetails createInvocationdetails(
      String name, String state, String historyID, DatasetCollection used) {
    WorkflowInvocationResponse wir = new WorkflowInvocationResponse();
    wir.setInvocationId("test_invocation_id");
    wir.setHistoryId(historyID);
    wir.setCreateTime(invocationDate);
    wir.setState(state);
    GalaxyInvocationDetails gid = new GalaxyInvocationDetails();
    gid.setInvocation(wir);
    gid.setDataUsedInInvocation(List.of(used));
    gid.setWorkflowName(name);
    return gid;
  }
}
