package com.researchspace.service.archive.export;

import static org.junit.Assert.assertEquals;
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
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ui.velocity.VelocityEngineUtils;

public class HTMLExporterTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("htmlexportWriter")
  ArchiveExportServiceManager mgr;

  @Autowired private VelocityEngine velocity;

  private static final String EXPECTED_HTML = "<!DOCTYPE HTML>\n" +
          "<html>\n" +
          "<head>\n" +
          "<title>$folderName </title>\n" +
          "<link href=\"resources/index.css\" rel=\"stylesheet\" />\n" +
          "<meta charset=\"UTF-8\">\n" +
          "</head>\n" +
          "<body>\n" +
          "<div class=\"navigationSection\">\n" +
          "<a href=\"index.html\">Top</a></div>\n" +
          " <h3>Listing of $folderName</h3>\n" +
          "   <img src=\"./resources/document.png\" width=32 height=32/><a href = \"url1\">record1</a><br/>\n" +
          "    <img src=\"./resources/folder.png\" width=32 height=32/><a href = \"url2\">record2</a><br/>\n" +
          "  \n" +
          "</body>\n" +
          "</html>";

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
    assertEquals(EXPECTED_HTML, msg.trim());
  }
}
