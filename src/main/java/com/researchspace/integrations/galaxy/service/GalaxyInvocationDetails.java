package com.researchspace.integrations.galaxy.service;

import com.researchspace.galaxy.model.output.upload.DatasetCollection;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Wraps various Galaxy abstractions The Invocation, the datasets used in the invocation and the
 * workflow name are held in this class
 */
@Data
@EqualsAndHashCode(of = {"invocation"})
public class GalaxyInvocationDetails {

  private WorkflowInvocationResponse invocation;
  private ExternalWorkFlowInvocation persistedInvocation;
  private List<DatasetCollection> dataUsedInInvocation;
  private String workflowName;
}
