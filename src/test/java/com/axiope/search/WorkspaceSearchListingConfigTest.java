package com.axiope.search;

import static org.junit.Assert.assertTrue;

import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.dtos.WorkspaceSettings;
import java.text.ParseException;
import org.junit.Test;
import org.springframework.test.util.JsonPathExpectationsHelper;

public class WorkspaceSearchListingConfigTest {

  @Test
  public void jsonConversion() throws ParseException {
    WorkspaceSettings settings = new WorkspaceSettings();
    WorkspaceListingConfig cfg =
        new WorkspaceListingConfig(settings, new String[] {"a"}, new String[] {"b"});
    String json = cfg.toJson();

    JsonPathExpectationsHelper jsonAssert = new JsonPathExpectationsHelper("$.options[0]");
    jsonAssert.assertValue(json, "a");
  }

  @Test
  public void srchCfg() throws ParseException {
    WorkspaceSettings settings = new WorkspaceSettings();
    WorkspaceListingConfig cfg =
        new WorkspaceListingConfig(
            settings, new String[] {SearchConstants.ATTACHMENT_SEARCH_OPTION}, new String[] {"b"});
    assertTrue(cfg.isAttachmentSearch());
    cfg =
        new WorkspaceListingConfig(
            settings,
            new String[] {SearchConstants.CREATION_DATE_SEARCH_OPTION},
            new String[] {"b"});
    assertTrue(cfg.isSimpleCreationDateSearch());
    cfg =
        new WorkspaceListingConfig(
            settings, new String[] {SearchConstants.FORM_SEARCH_OPTION}, new String[] {"b"});
    assertTrue(cfg.isSimpleFormSearch());
    cfg =
        new WorkspaceListingConfig(
            settings,
            new String[] {SearchConstants.MODIFICATION_DATE_SEARCH_OPTION},
            new String[] {"b"});
    assertTrue(cfg.isSimpleModificationDateSearch());
    cfg =
        new WorkspaceListingConfig(
            settings, new String[] {SearchConstants.NAME_SEARCH_OPTION}, new String[] {"b"});
    assertTrue(cfg.isSimpleNameSearch());
  }
}
