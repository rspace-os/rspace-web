package com.researchspace.service.archive.export;

import static org.junit.Assert.assertTrue;

import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.export.HTMLArchiveExporter.IndexItem;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ui.velocity.VelocityEngineUtils;

public class HTMLExporterTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("htmlexportWriter")
  ArchiveExportServiceManager mgr;

  @Autowired private VelocityEngine velocity;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testIsArchiveType() {
    assertTrue(mgr.isArchiveType(ArchiveExportConfig.HTML));
  }

  @Test
  public void testIndexGenerator() {

    IndexItem item1 = new HTMLArchiveExporter.IndexItem("url1", "record1", true);
    IndexItem item2 = new HTMLArchiveExporter.IndexItem("url2", "record2", false);
    List<IndexItem> items = Arrays.asList(new IndexItem[] {item1, item2});
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("indexItems", items);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "htmlIndex.vm", "UTF-8", velocityModel);
    System.err.println(msg);
  }
}
