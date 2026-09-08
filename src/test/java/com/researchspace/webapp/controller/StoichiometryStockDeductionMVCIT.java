package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.API_MVC_InventoryTestBase;
import com.researchspace.api.v1.controller.API_VERSION;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.stoichiometry.StockDeductionRequest;
import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Concurrency coverage for Stoichiometry stock deduction, split out of {@code
 * StoichiometryControllerMVCIT} so it can actually run: that class is {@code @Disabled} because
 * creating a stoichiometry sends the reaction to the external chemistry service for analysis. Only
 * the FIXTURE needs chemistry (the analysis that seeds the molecules); the behaviour under test,
 * {@code deductStock}'s locking, never calls it. So this class swaps the two external edges for
 * mocks: the {@link ChemistryProvider} (returns a canned one-molecule analysis) and the {@link
 * ChemicalSearcher} (PubChem name lookup, stubbed to the failure path the code already tolerates).
 * The swap is on the live singletons, restored after each test because the Spring context is
 * shared.
 */
@WebAppConfiguration
public class StoichiometryStockDeductionMVCIT extends API_MVC_InventoryTestBase {

  private static final String URL = "/api/v1/stoichiometry";

  private @Autowired StoichiometryService stoichiometryService;
  private @Autowired StoichiometryManager stoichiometryManager;

  private Principal principal;
  private User user;
  private String apiKey;

  private Object serviceTarget;
  private Object managerTarget;
  private ChemistryProvider realProvider;
  private ChemicalSearcher realSearcher;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    principal = new MockPrincipal(user.getUsername());
    apiKey = createNewApiKeyForUser(user);

    serviceTarget = AopTestUtils.getUltimateTargetObject(stoichiometryService);
    managerTarget = AopTestUtils.getUltimateTargetObject(stoichiometryManager);
    realProvider =
        (ChemistryProvider) ReflectionTestUtils.getField(serviceTarget, "chemistryProvider");
    realSearcher =
        (ChemicalSearcher) ReflectionTestUtils.getField(managerTarget, "chemicalSearcher");

    ChemistryProvider mockProvider = mock(ChemistryProvider.class);
    when(mockProvider.getStoichiometry(any()))
        .thenReturn(
            Optional.of(
                ElementalAnalysisDTO.builder()
                    .moleculeInfo(
                        List.of(
                            MoleculeInfoDTO.builder()
                                .smiles("CCO")
                                .formula("C2H6O")
                                .mass(46.07)
                                .role(MoleculeRole.REACTANT)
                                .build()))
                    .build()));
    ChemicalSearcher mockSearcher = mock(ChemicalSearcher.class);
    when(mockSearcher.searchChemicals(any(), any()))
        .thenThrow(
            new ChemicalImportException("no PubChem in tests", HttpStatus.SERVICE_UNAVAILABLE));
    ReflectionTestUtils.setField(serviceTarget, "chemistryProvider", mockProvider);
    ReflectionTestUtils.setField(managerTarget, "chemicalSearcher", mockSearcher);
  }

  @AfterEach
  public void restoreChemistryBeans() {
    // the context (and these singletons) outlive this class; leaving mocks behind would break
    // every later test that touches stoichiometry
    if (serviceTarget != null && realProvider != null) {
      ReflectionTestUtils.setField(serviceTarget, "chemistryProvider", realProvider);
    }
    if (managerTarget != null && realSearcher != null) {
      ReflectionTestUtils.setField(managerTarget, "chemicalSearcher", realSearcher);
    }
  }

  @Test
  public void parallelDeductionsOnSiblingSubSamplesDoNotDeadlock() throws Exception {
    // A deduction used to take the origin's own row lock first (the over-use check in
    // processStockDeduction) and the sibling-set lock second (inside registerApiSubSampleUsage),
    // the inverse of the operations endpoint's order. Two deductions on two siblings of ONE sample
    // are that inversion's sharpest probe: each held its own row while asking for the set that
    // contains the other's, and InnoDB killed one. deductStock now locks every parent's sibling
    // set up front, ascending by sample id, before any row lock, so both requests queue on the
    // shared set and succeed; a non-200 here means the ordering regressed (deadlock victim or
    // lock-wait timeout). The stored parent total must also equal what the children hold
    // afterwards: both requests rewrite it, which is where the snapshot-sum bug lived.
    String subSampleJson = "{\"quantity\":{\"numericValue\":5,\"unitId\":7}}";
    String sampleJson =
        "{\"name\":\"stoich siblings\",\"subSamples\":["
            + subSampleJson
            + ","
            + subSampleJson
            + "]}";
    MvcResult sampleResult =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", user, sampleJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples sample =
        getFromJsonResponseBody(sampleResult, ApiSampleWithFullSubSamples.class);
    long firstSubSampleId = sample.getSubSamples().get(0).getId();
    long secondSubSampleId = sample.getSubSamples().get(1).getId();

    StockDeductionRequest firstDeduction =
        stoichiometryDeducting(sample.getSubSamples().get(0).getGlobalId(), "stoich siblings 1");
    StockDeductionRequest secondDeduction =
        stoichiometryDeducting(sample.getSubSamples().get(1).getGlobalId(), "stoich siblings 2");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<MvcResult> results = new ArrayList<>();
    try {
      List<Callable<MvcResult>> posts =
          List.of(() -> postDeduction(firstDeduction), () -> postDeduction(secondDeduction));
      for (Future<MvcResult> future : pool.invokeAll(posts)) {
        results.add(future.get());
      }
    } finally {
      pool.shutdown();
    }

    for (MvcResult result : results) {
      assertEquals(
          200,
          result.getResponse().getStatus(),
          "a non-200 concurrent deduction is a deadlock victim or lock-wait timeout");
      StockDeductionResult deduction = getFromJsonResponseBody(result, StockDeductionResult.class);
      assertTrue(
          deduction.getResults().get(0).isSuccess(),
          () -> "both sibling deductions should succeed: " + deduction.getResults().get(0));
    }

    // both deductions landed on their own subsample...
    BigDecimal firstRemaining = subSampleQuantity(firstSubSampleId);
    BigDecimal secondRemaining = subSampleQuantity(secondSubSampleId);
    BigDecimal five = new BigDecimal("5");
    assertTrue(firstRemaining.compareTo(five) < 0, () -> "first not deducted: " + firstRemaining);
    assertTrue(
        secondRemaining.compareTo(five) < 0, () -> "second not deducted: " + secondRemaining);
    // ...and the denormalised parent total equals what the children actually hold
    MvcResult sampleGet =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/" + sample.getId(), user))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample reloaded = getFromJsonResponseBody(sampleGet, ApiSample.class);
    assertEquals(
        0,
        firstRemaining.add(secondRemaining).compareTo(reloaded.getQuantity().getNumericValue()),
        () ->
            "parent total should equal the sum of its children, got "
                + reloaded.getQuantity()
                + " for children "
                + firstRemaining
                + " + "
                + secondRemaining);
  }

  /**
   * A stoichiometry over its own document whose one molecule (actual amount 1) is linked to the
   * given subsample, returning the request that deducts that link's stock. The molecule comes from
   * the mocked analysis, not the chemistry service.
   */
  private StockDeductionRequest stoichiometryDeducting(String subSampleGlobalId, String docName)
      throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, docName);
    RSChemElement reaction = addReactionToField(doc.getFields().get(0), user);
    MvcResult createResult =
        mockMvc
            .perform(
                post(URL)
                    .param("recordId", reaction.getRecord().getId().toString())
                    .param("chemId", reaction.getId().toString())
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andReturn();
    StoichiometryDTO stoichiometry = getFromJsonResponseBody(createResult, StoichiometryDTO.class);
    StoichiometryMoleculeUpdateDTO molUpdate =
        StoichiometryMapper.toUpdateDTO(stoichiometry.getMolecules().get(0));
    molUpdate.setActualAmount(1.0);
    molUpdate.setInventoryLink(
        StoichiometryInventoryLinkRequest.builder()
            .inventoryItemGlobalId(subSampleGlobalId)
            .build());
    StoichiometryUpdateDTO updateDTO =
        StoichiometryUpdateDTO.builder()
            .id(stoichiometry.getId())
            .molecules(List.of(molUpdate))
            .build();
    MvcResult updateResult =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", String.valueOf(stoichiometry.getId()))
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryDTO withLink = getFromJsonResponseBody(updateResult, StoichiometryDTO.class);
    Long linkId = withLink.getMolecules().get(0).getInventoryLink().getId();
    return new StockDeductionRequest(stoichiometry.getId(), List.of(linkId), false);
  }

  private MvcResult postDeduction(StockDeductionRequest request) throws Exception {
    return mockMvc
        .perform(
            post(URL + "/link/deductStock")
                .contentType(APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(request))
                .principal(principal)
                .header("apiKey", apiKey))
        .andReturn();
  }

  private BigDecimal subSampleQuantity(long subSampleId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId, user))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSubSample.class).getQuantity().getNumericValue();
  }

  private RSChemElement addReactionToField(Field field, User owner) throws IOException {
    String reactionString = "C1C=CC=CC=1.C1C=CC=C1>>C1CCCCC1";
    String imageBytes = RSpaceTestUtils.getChemImage();
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(reactionString)
            .fieldId(field.getId())
            .imageBase64(imageBytes)
            .fieldId(field.getId())
            .chemElementsFormat(ChemElementsFormat.MOL.getLabel())
            .build();

    RSChemElement chem = rsChemElementManager.saveChemElement(chemicalData, owner);

    String chemLink =
        richTextUpdater.generateURLStringForRSChemElementLink(
            chem.getId(), chem.getParentId(), 50, 50);
    String fieldData = field.getFieldData() + chemLink;
    field.setFieldData(fieldData);
    field.getStructuredDocument().notifyDelta(DeltaType.FIELD_CHG);
    recordMgr.save(field.getStructuredDocument(), owner);
    return chem;
  }
}
