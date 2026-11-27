package com.researchspace.webapp.integrations.argos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.dmps.argos.ArgosDMPListing;
import com.researchspace.model.dmps.argos.DataTableData;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ArgosControllerTest {

  private static final UUID PLAN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  @Mock private ArgosDMPProvider provider;
  @InjectMocks private ArgosController controller;

  @BeforeEach
  void setUp() {
    controller.setMessageSource(new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void upstreamErrorWhenListingReturnsListError() {
    when(provider.listPlans(10, 0, null))
        .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

    AjaxReturnObject<DataTableData<ArgosDMPListing>> result = controller.listDMPs(10, 0, null);

    assertNull(result.getData());
    assertEquals(List.of("Couldn't list DMPs"), result.getError().getErrorMessages());
  }

  @Test
  void upstreamTimeoutWhenImportingReturnsImportError() throws Exception {
    when(provider.importDmp(PLAN_ID)).thenThrow(new ResourceAccessException("Read timed out"));

    AjaxReturnObject<Boolean> result = controller.importDmp(PLAN_ID.toString());

    assertNull(result.getData());
    assertEquals(List.of("Couldn't import DMP"), result.getError().getErrorMessages());
  }

  @ParameterizedTest
  @ValueSource(strings = {"..", "../../reference/query", "plan-1", ""})
  void idThatIsNotAUuidReturnsImportErrorWithoutCallingArgos(String id) {
    AjaxReturnObject<Boolean> result = controller.importDmp(id);

    assertNull(result.getData());
    assertEquals(List.of("Couldn't import DMP"), result.getError().getErrorMessages());
    verifyNoInteractions(provider);
  }
}
