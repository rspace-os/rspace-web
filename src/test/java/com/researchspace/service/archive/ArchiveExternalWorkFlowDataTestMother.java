package com.researchspace.service.archive;

import com.researchspace.archive.ArchiveExternalWorkFlowData;

public class ArchiveExternalWorkFlowDataTestMother {
  public static final String EXTERNAL_SERVICE = "GALAXY";
  public static final Long RSPACE_DATA_ID = 1L;
  public static final String EXT_ID = "extID";
  public static final String BASE_URL = "baseUrl";
  public static final String EXT_CONTAINER_NAME = "extContainerName";
  public static final String EXT_SECONDARY_ID = "extSecondaryId";
  public static final String LINK_FILE = "linkFile";
  public static final String EXT_CONTAINER_ID = "extContainerId";
  private static final long WF_DATA_ID = 1L;

  public static ArchiveExternalWorkFlowData makeDefault() {
    ArchiveExternalWorkFlowData defaultArchiveExternalWorkFlowData =
        new ArchiveExternalWorkFlowData();
    defaultArchiveExternalWorkFlowData.setId(WF_DATA_ID);
    defaultArchiveExternalWorkFlowData.setExternalService(EXTERNAL_SERVICE);
    defaultArchiveExternalWorkFlowData.setRspaceDataId(RSPACE_DATA_ID);
    defaultArchiveExternalWorkFlowData.setExtId(EXT_ID);
    defaultArchiveExternalWorkFlowData.setBaseUrl(BASE_URL);
    defaultArchiveExternalWorkFlowData.setExtContainerName(EXT_CONTAINER_NAME);
    defaultArchiveExternalWorkFlowData.setExtContainerId(EXT_CONTAINER_ID);
    defaultArchiveExternalWorkFlowData.setExtSecondaryId(EXT_SECONDARY_ID);
    defaultArchiveExternalWorkFlowData.setLinkFile(LINK_FILE);
    return defaultArchiveExternalWorkFlowData;
  }
}
