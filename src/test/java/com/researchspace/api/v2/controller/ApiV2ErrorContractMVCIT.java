package com.researchspace.api.v2.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Every REST API v2 error must reach the client as an RFC 9457 {@code application/problem+json}
 * body, including the errors Spring raises before a handler is selected.
 *
 * <p>This has to be an integration test. The v2 unit tests build MockMvc with {@code
 * standaloneSetup}, which supplies its own minimal exception-resolver chain and therefore cannot
 * see whether the production chain is wired correctly; a regression produced the right status with
 * the container's HTML error page and every {@code standaloneSetup} test still passed. {@code
 * dispatcher-test-servlet.xml} component-scans {@code WebConfig}, so this is also the only place
 * {@code ApiV2PreHandlerProblemResolver}'s registration is actually exercised.
 */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 error contract")
class ApiV2ErrorContractMVCIT {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  /**
   * Raised by the handler mapping, so no {@code HandlerMethod} exists and the package-selected
   * {@code ApiV2ControllerAdvice} is skipped. Only {@code ApiV2PreHandlerProblemResolver} can
   * render this as a problem body.
   */
  @Test
  @DisplayName("an unsupported method answers problem+json with an Allow header")
  void unsupportedMethodAnswersProblemJsonWithAnAllowHeader() throws Exception {
    mockMvc
        .perform(put("/api/v2/maintenances/1"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.code").value("errors.api.v2.methodNotAllowed"))
        .andExpect(jsonPath("$.detail").isString())
        .andExpect(header().exists(HttpHeaders.ALLOW));
  }

  @Test
  @DisplayName("an unacceptable Accept header answers problem+json")
  void unacceptableAcceptHeaderAnswersProblemJson() throws Exception {
    mockMvc
        .perform(get("/api/v2/maintenances").accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(406));
  }

  /**
   * Status only. Whether an unmapped path arrives as {@code NoHandlerFoundException} (rendered by
   * the resolver) or is answered directly by the servlet depends on {@code
   * throwExceptionIfNoHandlerFound} and on static-resource handling, so asserting the body here
   * would be asserting container configuration. {@code ApiV2PreHandlerProblemResolverTest} covers
   * rendering of both exception types directly.
   */
  @Test
  @DisplayName("an unmapped v2 path answers 404")
  void anUnmappedV2PathAnswersNotFound() throws Exception {
    mockMvc.perform(get("/api/v2/there-is-no-such-collection")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("a malformed query is rejected as problem+json")
  void aMalformedQueryIsRejectedAsProblemJson() throws Exception {
    mockMvc
        .perform(get("/api/v2/maintenances").param("where", "notAField==1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").isString());
  }

  @Test
  @DisplayName("an out-of-range page is rejected as problem+json")
  void anOutOfRangePageIsRejectedAsProblemJson() throws Exception {
    mockMvc
        .perform(get("/api/v2/maintenances").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400));
  }
}
