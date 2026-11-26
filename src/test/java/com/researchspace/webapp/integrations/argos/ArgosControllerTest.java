package com.researchspace.webapp.integrations.argos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.researchspace.argos.model.ArgosDMPListing;
import com.researchspace.argos.model.DataTableData;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ArgosControllerTest {

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
    when(provider.importDmp("plan-1")).thenThrow(new ResourceAccessException("Read timed out"));

    AjaxReturnObject<Boolean> result = controller.importDmp("plan-1");

    assertNull(result.getData());
    assertEquals(List.of("Couldn't import DMP"), result.getError().getErrorMessages());
  }
}
