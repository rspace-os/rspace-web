package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.SamplesApi;
import com.researchspace.api.v1.SubSamplesApi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.function.Consumer;
import org.hibernate.stat.Statistics;
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

  @Test
  void leanSampleHasCountsAndDeleteEligibilityWithoutLoadingChildren() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    var sample = createBasicSampleForUser(user);
    var child = sample.getSubSamples().get(0);
    var container = createBasicContainerForUser(user);
    moveSubSampleIntoListContainer(child.getId(), container.getId(), user);

    Statistics statistics = sessionFactory.getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      var children =
          statistics.getCollectionStatistics(SampleEntity.class.getName() + ".subSamples");
      long before = children.getFetchCount();
      var response =
          mockMvc
              .perform(
                  getSampleById(user, apiKey, sample.getId()).param("includeSubSamples", "false"))
              .andExpect(status().isOk())
              .andReturn();
      var details = new ObjectMapper().readTree(response.getResponse().getContentAsString());
      assertTrue(details.required("subSamples").isNull());
      assertEquals(1, details.required("subSamplesCount").asInt());
      assertTrue(details.required("canBeDeleted").isBoolean());
      assertFalse(details.required("canBeDeleted").asBoolean());
      assertEquals(0L, children.getFetchCount() - before);
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }

  @Test
  void leanPreviewCanBeSavedWithoutChangingItsChildren() throws Exception {
    User owner = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(owner);
    var sample = createBasicSampleForUser(owner);
    String resource = "/samples/" + sample.getId();
    ObjectMapper mapper = new ObjectMapper();
    var preview =
        mockMvc
            .perform(
                getSampleById(owner, apiKey, sample.getId()).param("includeSubSamples", "false"))
            .andExpect(status().isOk())
            .andReturn();
    var previewDetails = mapper.readTree(preview.getResponse().getContentAsString());
    assertTrue(previewDetails.required("subSamples").isNull());
    assertTrue(previewDetails.required("canBeDeleted").asBoolean());

    var saved =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, resource, owner, "{\"name\":\"Updated sample\"}"))
            .andExpect(status().isOk())
            .andReturn();
    var savedDetails = mapper.readTree(saved.getResponse().getContentAsString());
    assertEquals("Updated sample", savedDetails.required("name").asText());
    assertEquals(
        sample.getSubSamples().get(0).getId().longValue(),
        savedDetails.required("subSamples").get(0).required("id").asLong());

    User other = createInitAndLoginAnyUser();
    var restricted =
        mockMvc
            .perform(getSampleById(other, createNewApiKeyForUser(other), sample.getId()))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(
        mapper
            .readTree(restricted.getResponse().getContentAsString())
            .required("subSamples")
            .isNull());
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
