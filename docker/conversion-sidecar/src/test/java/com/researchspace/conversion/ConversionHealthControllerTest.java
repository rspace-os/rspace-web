package com.researchspace.conversion;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversionHealthController.class)
class ConversionHealthControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private LibreOfficeSandbox sandbox;
  @MockitoBean private GotenbergProxy gotenberg;
  @MockitoBean private OfficeConversionLimiter limiter;

  @Test
  void overallReadinessRequiresBothConversionDependencies() throws Exception {
    when(sandbox.isReady()).thenReturn(true);
    when(gotenberg.isReady()).thenReturn(false);

    mvc.perform(get("/health/ready"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }

  @Test
  void overallReadinessRequiresCapacityInBothRoles() throws Exception {
    when(sandbox.isReady()).thenReturn(true);
    when(gotenberg.isReady()).thenReturn(true);
    when(limiter.hasWordCapacity()).thenReturn(true);
    when(limiter.hasPdfCapacity()).thenReturn(false);
    mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable());
    when(limiter.hasPdfCapacity()).thenReturn(true);
    when(limiter.hasWordCapacity()).thenReturn(false);
    mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable());
    when(limiter.hasWordCapacity()).thenReturn(true);
    mvc.perform(get("/health/ready")).andExpect(status().isOk());
  }

  @Test
  void roleReadinessIncludesCapacity() throws Exception {
    when(sandbox.isReady()).thenReturn(true);
    when(limiter.hasWordCapacity()).thenReturn(true);
    when(gotenberg.isReady()).thenReturn(true);
    when(limiter.hasPdfCapacity()).thenReturn(false);

    mvc.perform(get("/health/ready/word"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/health/ready/pdf"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"));
  }
}
