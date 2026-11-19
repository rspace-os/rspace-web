package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.inventory.InventoryStructureSearchManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

@ExtendWith(MockitoExtension.class)
class InventorySearchApiControllerStructureSearchTest {

  @Mock private InventoryStructureSearchManager structureSearchManager;
  @Mock private ChemistryProvider chemistryProvider;
  @Mock private IPropertyHolder properties;
  @InjectMocks private InventorySearchApiController controller;

  private final User user = new User("searcher");

  @Test
  void queriesChemistryServiceAndPassesItsHitsToTheManager() throws BindException {
    InventoryApiSearchConfig config = new InventoryApiSearchConfig();
    config.setChemQuery("c1ccccc1");
    config.setChemSearchType("EXACT");
    when(structureSearchManager.isStructureSearchAvailable(user)).thenReturn(true);
    when(chemistryProvider.search("c1ccccc1", "EXACT"))
        .thenReturn(ChemicalSearchResultsDTO.builder().chemicalHits(List.of(4L, 7L)).build());
    ApiInventorySearchResult page = new ApiInventorySearchResult();
    page.setTotalHits(0L);
    page.setPageNumber(0);
    when(structureSearchManager.searchByStructure(
            eq(List.of(4L, 7L)),
            eq(""),
            eq(InventorySearchType.ALL),
            isNull(),
            eq(InventorySearchDeletedOption.EXCLUDE),
            isNull(),
            any(),
            eq(user)))
        .thenReturn(page);
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");

    BindingResult noErrors = mock(BindingResult.class);
    assertSame(page, controller.searchInventoryRecords(null, config, noErrors, user));
  }
}
