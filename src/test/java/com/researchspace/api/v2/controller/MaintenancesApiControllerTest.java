package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.dtos.IControllerInputValidator;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

class MaintenancesApiControllerTest {

  private static final String ENDPOINT = "/api/v2/maintenances";
  private final MaintenanceManager maintenanceManager = mock(MaintenanceManager.class);
  private final IControllerInputValidator inputValidator = mock(IControllerInputValidator.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MaintenancesApiController controller = new MaintenancesApiController();
    ReflectionTestUtils.setField(controller, "maintenanceManager", maintenanceManager);
    ReflectionTestUtils.setField(controller, "inputValidator", inputValidator);
    doAnswer(
            invocation -> {
              Object target = invocation.getArgument(0);
              Validator validator = invocation.getArgument(1);
              Errors errors = invocation.getArgument(2);
              validator.validate(target, errors);
              return null;
            })
        .when(inputValidator)
        .validate(any(), any(Validator.class), any(Errors.class));
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiV2ControllerAdvice())
            .build();
  }

  @Test
  void appliesDefaultPaginationWhenNoMaintenanceIsScheduled() throws Exception {
    when(maintenanceManager.getAllFutureMaintenances()).thenReturn(Collections.emptyList());

    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty())
        .andExpect(jsonPath("$.totalDocs").value(0))
        .andExpect(jsonPath("$.limit").value(20))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalPages").value(0))
        .andExpect(jsonPath("$.hasPrevPage").value(false))
        .andExpect(jsonPath("$.hasNextPage").value(false))
        .andExpect(jsonPath("$.prevPage").isEmpty())
        .andExpect(jsonPath("$.nextPage").isEmpty());
  }

  @Test
  void pagesAllFutureMaintenancesThroughTheEnvelope() throws Exception {
    when(maintenanceManager.getAllFutureMaintenances())
        .thenReturn(
            List.of(
                futureMaintenance(2, "Planned database upgrade"),
                futureMaintenance(26, "Second window"),
                futureMaintenance(50, "Third window")));

    // Page 1 of 2: the first two windows, and a next page.
    mockMvc
        .perform(get(ENDPOINT).param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.length()").value(2))
        .andExpect(jsonPath("$.docs[0].startDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].endDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].stopUserLoginDate", isoDateTime()))
        .andExpect(jsonPath("$.docs[0].message").value("Planned database upgrade"))
        .andExpect(jsonPath("$.docs[0].canUserLoginNow").doesNotExist())
        .andExpect(jsonPath("$.docs[0].activeNow").doesNotExist())
        .andExpect(jsonPath("$.totalDocs").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.hasPrevPage").value(false))
        .andExpect(jsonPath("$.hasNextPage").value(true))
        .andExpect(jsonPath("$.nextPage").value(2));

    // Page 2 of 2: the remaining window, and a previous page.
    mockMvc
        .perform(get(ENDPOINT).param("limit", "2").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.length()").value(1))
        .andExpect(jsonPath("$.docs[0].message").value("Third window"))
        .andExpect(jsonPath("$.totalDocs").value(3))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.hasPrevPage").value(true))
        .andExpect(jsonPath("$.prevPage").value(1))
        .andExpect(jsonPath("$.hasNextPage").value(false));
  }

  @Test
  void returnsAnEmptyPageWhenPagingPastTheEnd() throws Exception {
    when(maintenanceManager.getAllFutureMaintenances())
        .thenReturn(List.of(futureMaintenance(2, "Only window")));

    mockMvc
        .perform(get(ENDPOINT).param("page", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty())
        .andExpect(jsonPath("$.totalDocs").value(1));
  }

  @Test
  void rejectsInvalidOrNonNumericPaginationWithProblemDetails() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("page", "0").param("limit", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").exists());
    mockMvc
        .perform(get(ENDPOINT).param("page", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void doesNotRequireAnAuthenticatedUserRequestAttribute() throws Exception {
    when(maintenanceManager.getAllFutureMaintenances()).thenReturn(Collections.emptyList());
    mockMvc.perform(get(ENDPOINT)).andExpect(status().isOk());
  }

  private static ScheduledMaintenance futureMaintenance(int hoursFromNow, String message) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.HOUR_OF_DAY, hoursFromNow);
    Date startDate = calendar.getTime();
    calendar.add(Calendar.HOUR_OF_DAY, 1);
    ScheduledMaintenance maintenance = new ScheduledMaintenance(startDate, calendar.getTime());
    maintenance.setMessage(message);
    return maintenance;
  }

  private static Matcher<String> isoDateTime() {
    return allOf(startsWith("20"), containsString("T"), endsWith("Z"));
  }
}
