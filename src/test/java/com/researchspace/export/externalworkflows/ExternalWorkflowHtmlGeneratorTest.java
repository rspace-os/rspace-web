package com.researchspace.export.externalworkflows;

import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_INVOCATION_STATE;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.HISTORY_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.INVOCATION_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED;
import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.archive.ArchiveExternalWorkFlow;
import com.researchspace.archive.ArchiveExternalWorkFlowData;
import com.researchspace.archive.ArchiveExternalWorkFlowInvocation;
import com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserLocaleService;
import com.researchspace.service.archive.ArchiveExternalWorkFlowDataTestMother;
import com.researchspace.service.archive.ArchiveExternalWorkFlowInvocationTestMother;
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
    ReflectionTestUtils.setField(
        generator, "messages", new MessageSourceUtils(new JsonMessageSource()));
    ReflectionTestUtils.setField(generator, "userLocaleService", new UserLocaleService());
  }

  @Test
  public void rendersPersistedGalaxyInvocationData() {
    ExternalWorkFlowData data =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithInvocations(
            HISTORY_ID_1, "dataset-id", "Test History", DEFAULT_INVOCATION_STATE);

    String html = generator.getHtmlForExternalWorkflowData(Set.of(data));

    assertThat(html).contains("Galaxy Workflow Data");
    assertThat(html).contains("default-name");
    assertThat(html).contains("http://localhost:8080/gallery/item/0");
    assertThat(html).contains("Test History");
    assertThat(html).contains("default-baseurl/histories/view?id=" + HISTORY_ID_1);
    assertThat(html).contains(WORKFLOWTHATWASUSED);
    assertThat(html).contains("default-baseurl/workflows/invocations/" + INVOCATION_ID_1);
    assertThat(html).contains(DEFAULT_INVOCATION_STATE);
  }

  @Test
  public void rendersPersistedGalaxyDataWithoutInvocationAsDataOnlyRow() {
    ExternalWorkFlowData data =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithNonDefaultName(
            HISTORY_ID_1, "dataset-id", "Test History", "dataset &lt;one&gt;");

    String html = generator.getHtmlForExternalWorkflowData(Set.of(data));

    assertThat(html).contains("dataset &amp;lt;one&amp;gt;");
    assertThat(html).contains("Test History");
    assertThat(html).contains("<td>-</td>");
  }

  @Test
  public void rendersArchivedGalaxyDataWithLocalArchiveLinks() {
    ArchiveExternalWorkFlowData data = ArchiveExternalWorkFlowDataTestMother.makeDefault();
    ArchiveExternalWorkFlowInvocation invocation =
        ArchiveExternalWorkFlowInvocationTestMother.makeDefault();
    ArchiveExternalWorkFlow workflow = new ArchiveExternalWorkFlow();
    workflow.setName("Archived workflow");
    invocation.setWorkFlowMetaData(workflow);

    String html =
        generator.getHtmlForArchivedExternalWorkflowData(Set.of(data), Set.of(invocation));

    assertThat(html).contains("linkFile");
    assertThat(html).contains("extContainerName");
    assertThat(html).contains("baseUrl/histories/view?id=extContainerId");
    assertThat(html).contains("Archived workflow");
    assertThat(html).contains("baseUrl/workflows/invocations/extID");
    assertThat(html).contains("RUNNING");
    assertThat(html).doesNotContain("http://localhost:8080/gallery/item");
  }
}
