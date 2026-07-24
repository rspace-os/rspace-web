package com.researchspace.webapp.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class TrailingSlashCompatibilityFilterTest {

  @RestController
  static class StubController {
    @GetMapping("/stub")
    String stub() {
      return "ok";
    }
  }

  private MockMvc mvcWithFilter;
  private MockMvc mvcWithoutFilter;

  @BeforeEach
  void setUp() {
    StubController controller = new StubController();
    mvcWithFilter =
        MockMvcBuilders.standaloneSetup(controller)
            .addFilters(new TrailingSlashCompatibilityFilter())
            .build();
    mvcWithoutFilter = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  public void trailingSlashRequestReachesSlashlessHandler() throws Exception {
    mvcWithFilter
        .perform(get("/stub/"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  public void canonicalPathIsUnaffected() throws Exception {
    mvcWithFilter.perform(get("/stub")).andExpect(status().isOk());
  }

  /** Pins the Spring 6 strict-matching premise; if this matched, the filter would be redundant. */
  @Test
  public void withoutFilterTrailingSlashDoesNotMatch() throws Exception {
    mvcWithoutFilter.perform(get("/stub/")).andExpect(status().isNotFound());
  }
}
