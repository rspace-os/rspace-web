package com.researchspace.service.archive;

import com.researchspace.archive.ArchiveExternalWorkFlow;

public class ArchiveExternalWorkFlowTestMother {
  public static final String EXTERNAL_SERVICE = "GALAXY";
  public static final String NAME = "extWorkFlowName";
  private static final String DESCRIPTION = "description";
  private static final String EXT_ID = "extId";
  private static final long WF_ID = 2L;

  public static ArchiveExternalWorkFlow makeDefault(){
    ArchiveExternalWorkFlow defaultArchiveExternalWorkFlow = new ArchiveExternalWorkFlow();
    defaultArchiveExternalWorkFlow.setExternalService(EXTERNAL_SERVICE);
    defaultArchiveExternalWorkFlow.setName(NAME);
    defaultArchiveExternalWorkFlow.setDescription(DESCRIPTION);
    defaultArchiveExternalWorkFlow.setExtId(EXT_ID);
    defaultArchiveExternalWorkFlow.setId(WF_ID);
    return defaultArchiveExternalWorkFlow;
  }

  public static ArchiveExternalWorkFlow withIdAppendedToValues(long l) {
    ArchiveExternalWorkFlow defaultArchiveExternalWorkFlow = makeDefault();
    defaultArchiveExternalWorkFlow.setExtId(defaultArchiveExternalWorkFlow.getExtId()+l);
    defaultArchiveExternalWorkFlow.setName(defaultArchiveExternalWorkFlow.getName()+l);
    defaultArchiveExternalWorkFlow.setId(l);
    return defaultArchiveExternalWorkFlow;
  }
}
