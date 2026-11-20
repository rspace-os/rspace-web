package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.SamplesApi;
import com.researchspace.api.v1.SubSamplesApi;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class InventoryDetailLookupIT extends API_MVC_InventoryTestBase {

  @Autowired private MessageSourceUtils messages;
  @Autowired private SamplesApi samplesApi;
  @Autowired private SubSamplesApi subSamplesApi;

  @Test
  void detailRoutesPreserveMissingAndTemplateResponses() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    Long templateId = createBasicSampleTemplate(user).getId();

    expectMissing(getSampleById(user, apiKey, -1L), -1L);
    expectMissing(getSubSampleById(user, apiKey, -1L), -1L);
    String body =
        mockMvc
            .perform(getSampleById(user, apiKey, templateId))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .contains("Sample template flag doesn't match the request (id " + templateId + ")");
  }

  @Test
  void liveDetailSkipsTheSeparateExistenceLookup() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    var sample = createBasicSampleForUser(user);
    var child = sample.getSubSamples().get(0);

    assertNoExistenceProbe(
        samplesApi,
        "sampleApiMgr",
        SampleApiManager.class,
        getSampleById(user, apiKey, sample.getId()),
        manager -> verify(manager, never()).exists(sample.getId()));
    assertNoExistenceProbe(
        subSamplesApi,
        "subSampleApiMgr",
        SubSampleApiManager.class,
        getSubSampleById(user, apiKey, child.getId()),
        manager -> verify(manager, never()).exists(child.getId()));
  }

  private void expectMissing(MockHttpServletRequestBuilder request, Long id) throws Exception {
    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains(messages.getResourceNotFoundMessage("Inventory record", id));
  }

  private <M> void assertNoExistenceProbe(
      Object controller,
      String managerField,
      Class<M> managerType,
      MockHttpServletRequestBuilder request,
      Consumer<M> verification)
      throws Exception {
    Object target = AopTestUtils.getUltimateTargetObject(controller);
    M manager = managerType.cast(ReflectionTestUtils.getField(target, managerField));
    M observed = mock(managerType, delegatesTo(manager));
    ReflectionTestUtils.setField(target, managerField, observed);
    try {
      mockMvc.perform(request).andExpect(status().isOk());
      verification.accept(observed);
    } finally {
      ReflectionTestUtils.setField(target, managerField, manager);
    }
  }
}
