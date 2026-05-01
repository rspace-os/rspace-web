package com.researchspace.export.externalworkflows;

import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_INVOCATION_STATE;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.HISTORY_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.INVOCATION_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.testutils.RSpaceTestUtils;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class ExternalWorkflowHtmlGeneratorTest {
  private ExternalWorkflowHtmlGenerator generator;

  @BeforeEach
  public void setUp() throws Exception {
    generator = new ExternalWorkflowHtmlGenerator();
    generator.setUrlPrefix("http://localhost:8080");
    ReflectionTestUtils.setField(
        generator,
        "velocityEngine",
        RSpaceTestUtils.setupVelocity("src/main/resources/velocityTemplates"));
  }

  @Test
  public void rendersPersistedGalaxyInvocationData() {
    ExternalWorkFlowData data =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithInvocations(
            HISTORY_ID_1, "dataset-id", "Test History", DEFAULT_INVOCATION_STATE);

    String html = generator.getHtmlForExternalWorkflowData(Set.of(data));

    assertTrue(html.contains("Galaxy Workflow Data"));
    assertTrue(html.contains("default-name"));
    assertTrue(html.contains("http://localhost:8080/gallery/item/0"));
    assertTrue(html.contains("Test History"));
    assertTrue(html.contains("default-baseurl/histories/view?id=" + HISTORY_ID_1));
    assertTrue(html.contains(WORKFLOWTHATWASUSED));
    assertTrue(html.contains("default-baseurl/workflows/invocations/" + INVOCATION_ID_1));
    assertTrue(html.contains(DEFAULT_INVOCATION_STATE));
  }

  @Test
  public void rendersPersistedGalaxyDataWithoutInvocationAsDataOnlyRow() {
    ExternalWorkFlowData data =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithNonDefaultName(
            HISTORY_ID_1, "dataset-id", "Test History", "dataset &lt;one&gt;");

    String html = generator.getHtmlForExternalWorkflowData(Set.of(data));

    assertTrue(html.contains("dataset &amp;lt;one&amp;gt;"));
    assertTrue(html.contains("Test History"));
    assertTrue(html.contains("<td>-</td>"));
  }
}
