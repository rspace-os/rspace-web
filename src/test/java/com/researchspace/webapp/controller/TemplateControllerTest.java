package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TemplateControllerTest extends SpringTransactionalTest {

  @Autowired private TemplateController templateController;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testGetMustacheTemplateFromVelocity() {

    String docTemplate =
        templateController.getTemplate(TemplateController.INSERTED_DOCUMENT_TEMPLATE);
    assertAllPropsReplaced(docTemplate);
    assertTrue(docTemplate.contains(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME));

    String miscTemplate =
        templateController.getTemplate(TemplateController.INSERTED_MISCDOC_TEMPLATE);
    assertAllPropsReplaced(docTemplate);
    assertTrue(miscTemplate.contains(MediaUtils.MISC_MEDIA_FLDER_NAME));

    String avTemplate = templateController.getTemplate(TemplateController.INSERTED_AV_TEMPLATE);
    assertAllPropsReplaced(avTemplate);

    String imgTemplate = templateController.getTemplate(TemplateController.INSERTED_AV_TEMPLATE);
    assertAllPropsReplaced(imgTemplate);

    String commentTemplate =
        templateController.getTemplate(TemplateController.INSERTED_COMMENT_TEMPLATE);
    assertAllPropsReplaced(commentTemplate);

    String linkTemplate = templateController.getTemplate(TemplateController.INSERTED_LINK_TEMPLATE);
    assertAllPropsReplaced(linkTemplate);

    String sktchTemplate =
        templateController.getTemplate(TemplateController.INSERTED_SKETCH_TEMPLATE);
    assertAllPropsReplaced(sktchTemplate);

    String annoTemplate =
        templateController.getTemplate(TemplateController.INSERTED_ANNOTATED_IMAGE_TEMPLATE);
    assertAllPropsReplaced(annoTemplate);

    String chemTemplate =
        templateController.getTemplate(TemplateController.INSERTED_CHEMELEMENT_TEMPLATE);
    assertAllPropsReplaced(chemTemplate);

    String equationTemplate = templateController.getTemplate(TemplateController.EQUATION_TEMPLATE);
    assertAllPropsReplaced(equationTemplate);
  }

  private void assertAllPropsReplaced(String processedTemplate) {
    assertNotNull(processedTemplate);
    assertFalse(processedTemplate.contains("$")); // check all variable replaced.
  }
}
