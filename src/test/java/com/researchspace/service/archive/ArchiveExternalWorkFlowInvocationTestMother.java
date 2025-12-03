package com.researchspace.service.archive;

import com.researchspace.archive.ArchiveExternalWorkFlow;
import com.researchspace.archive.ArchiveExternalWorkFlowInvocation;
import java.util.Set;

public class ArchiveExternalWorkFlowInvocationTestMother {
  public static final String EXTERNAL_SERVICE = "GALAXY";
  public static final Long RSPACE_DATA_ID = 1L;
  public static final String NAME = "extWorkFlowName";
  private static final long WORKFLOW_ID = 2L;
  public static final String EXT_ID = "extID";

  public static ArchiveExternalWorkFlowInvocation makeDefault(){
    ArchiveExternalWorkFlowInvocation defaultArchiveExternalWorkFlowInvocation = new ArchiveExternalWorkFlowInvocation();
    defaultArchiveExternalWorkFlowInvocation.setExternalService(EXTERNAL_SERVICE);
    defaultArchiveExternalWorkFlowInvocation.setExtId(EXT_ID);
    defaultArchiveExternalWorkFlowInvocation.setName(NAME);
    defaultArchiveExternalWorkFlowInvocation.setStatus("RUNNING");
    defaultArchiveExternalWorkFlowInvocation.setWorkFlowId(WORKFLOW_ID);
    defaultArchiveExternalWorkFlowInvocation.setDataIds(Set.of(RSPACE_DATA_ID));
    return defaultArchiveExternalWorkFlowInvocation;
  }

  public static ArchiveExternalWorkFlowInvocation withDataId(long l) {
    ArchiveExternalWorkFlowInvocation defaultArchiveExternalWorkFlowInvocation = makeDefault();
    defaultArchiveExternalWorkFlowInvocation.setDataIds(Set.of(l));
    defaultArchiveExternalWorkFlowInvocation.setExtId("extDataID"+l);
    return defaultArchiveExternalWorkFlowInvocation;
  }

  public static ArchiveExternalWorkFlowInvocation withWFId(long l) {
    ArchiveExternalWorkFlowInvocation defaultArchiveExternalWorkFlowInvocation = makeDefault();
    defaultArchiveExternalWorkFlowInvocation.setExtId("extWFId"+l);
    defaultArchiveExternalWorkFlowInvocation.setWorkFlowId(l);
    return defaultArchiveExternalWorkFlowInvocation;
  }
}
