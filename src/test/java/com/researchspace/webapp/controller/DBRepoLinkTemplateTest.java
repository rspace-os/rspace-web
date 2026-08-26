package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class DBRepoLinkTemplateTest {

  @Test
  void dbrepoLinkTemplateDoesNotUseAttachmentParserMarkers() throws Exception {
    String template = readTemplate();

    assertTrue(template.contains("class=\"dbrepo_link\""));
    assertTrue(template.contains("id=\"dbrepoLink_$id\""));
    assertFalse(template.contains("attachmentLinked"));
    assertFalse(template.contains("attachOnText_"));
  }

  private String readTemplate() throws Exception {
    URL templateUrl =
        getClass()
            .getClassLoader()
            .getResource("velocityTemplates/textFieldElements/dbrepoLink.vm");

    assertNotNull(templateUrl);
    return Files.readString(Paths.get(templateUrl.toURI()), StandardCharsets.UTF_8);
  }
}
