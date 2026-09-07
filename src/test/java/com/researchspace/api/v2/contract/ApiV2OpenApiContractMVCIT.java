package com.researchspace.api.v2.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The generated OpenAPI document is the public HTTP contract, so it is asserted here as a document
 * clients read rather than as generator output.
 *
 * <p>{@code ApiV2OpenApiGeneratorTest} already covers generation from synthetic descriptions. What
 * only this layer can show is that the document a deployment actually serves matches the resources
 * that deployment actually registered, including the Swagger annotations merged in from concrete
 * controllers.
 *
 * <p>The {@code dev} profile regenerates per request and sends {@code no-store}. Production
 * caching, {@code ETag} and 304 are asserted in {@code ApiV2OpenApiControllerTest} instead, because
 * they are selected by the active profile rather than by anything a request can carry.
 */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 OpenAPI contract")
class ApiV2OpenApiContractMVCIT {

  private static final String OPENAPI = "/api/v2/openapi.json";

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  @DisplayName("the document is public and declares OpenAPI 3.1")
  void theDocumentIsPublicAndDeclares31() throws Exception {
    mockMvc
        .perform(get(OPENAPI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value(Matchers.startsWith("3.1")))
        .andExpect(jsonPath("$.paths").exists())
        .andExpect(jsonPath("$.components.schemas").exists());
  }

  @Test
  @DisplayName("development serves a freshly generated, uncached document")
  void developmentServesAnUncachedDocument() throws Exception {
    mockMvc
        .perform(get(OPENAPI))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
        .andExpect(header().doesNotExist(HttpHeaders.ETAG));
  }

  @Nested
  @DisplayName("registered collections")
  class RegisteredCollections {

    @ParameterizedTest(name = "{0} publishes its collection and item paths")
    @ValueSource(strings = {"maintenances", "booking-configurations", "instruments", "users"})
    @DisplayName("every registered resource publishes its read paths")
    void everyRegisteredResourcePublishesItsReadPaths(String resource) throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['/api/v2/%s'].get".formatted(resource)).exists())
          .andExpect(jsonPath("$.paths['/api/v2/%s/count'].get".formatted(resource)).exists())
          .andExpect(jsonPath("$.paths['/api/v2/%s/{id}'].get".formatted(resource)).exists());
    }

    @Test
    @DisplayName("a collection that exposes writes publishes every write path")
    void aWritableCollectionPublishesEveryWritePath() throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['/api/v2/maintenances'].post").exists())
          .andExpect(jsonPath("$.paths['/api/v2/maintenances'].patch").exists())
          .andExpect(jsonPath("$.paths['/api/v2/maintenances'].delete").exists())
          .andExpect(jsonPath("$.paths['/api/v2/maintenances/bulk'].post").exists())
          .andExpect(jsonPath("$.paths['/api/v2/maintenances/{id}'].patch").exists())
          .andExpect(jsonPath("$.paths['/api/v2/maintenances/{id}'].delete").exists());
    }

    /**
     * {@code exposedOperations} controls whether a route exists at all, so a read-only collection
     * must not advertise a write. A client generating a SDK from this document would otherwise ship
     * methods that can only ever return 405.
     */
    @ParameterizedTest(name = "{0} advertises no write operation")
    @ValueSource(strings = {"users", "instruments"})
    @DisplayName("a read-only collection advertises no write operation")
    void aReadOnlyCollectionAdvertisesNoWrite(String resource) throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['/api/v2/%s'].post".formatted(resource)).doesNotExist())
          .andExpect(jsonPath("$.paths['/api/v2/%s'].patch".formatted(resource)).doesNotExist())
          .andExpect(jsonPath("$.paths['/api/v2/%s'].delete".formatted(resource)).doesNotExist())
          .andExpect(jsonPath("$.paths['/api/v2/%s/bulk']".formatted(resource)).doesNotExist())
          .andExpect(
              jsonPath("$.paths['/api/v2/%s/{id}'].patch".formatted(resource)).doesNotExist())
          .andExpect(
              jsonPath("$.paths['/api/v2/%s/{id}'].delete".formatted(resource)).doesNotExist());
    }

    @ParameterizedTest(name = "{0} publishes its default audit routes")
    @ValueSource(strings = {"maintenances", "booking-configurations", "instruments", "users"})
    @DisplayName("every registered resource publishes its default audit routes")
    void everyRegisteredResourcePublishesAuditRoutes(String resource) throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['/api/v2/%s/{id}/audit'].get".formatted(resource)).exists())
          .andExpect(
              jsonPath("$.paths['/api/v2/%s/{id}/audit/count'].get".formatted(resource)).exists());
    }
  }

  @Nested
  @DisplayName("concrete controller routes")
  class ConcreteControllerRoutes {

    @ParameterizedTest(name = "{0} {1} is documented")
    @CsvSource({
      "get,  /api/v2/config",
      "get,  /api/v2/openapi.json",
      "post, /api/v2/oauth/tokens",
      "get,  /api/v2/users/me",
      "get,  /api/v2/users/me/profile-image",
      "get,  /api/v2/users/me/booking-preferences",
      "put,  /api/v2/users/me/booking-preferences",
      "delete, /api/v2/users/me/booking-preferences",
    })
    @DisplayName("annotated routes outside the collection controller appear in the document")
    void annotatedConcreteRoutesAppear(String method, String path) throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['%s'].%s".formatted(path.trim(), method.trim())).exists());
    }

    /**
     * {@code /users/me} is a concrete route, not a collection item, and must not collide with one.
     */
    @Test
    @DisplayName("the concrete users route coexists with the users collection item route")
    void theConcreteUsersRouteCoexistsWithTheItemRoute() throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['/api/v2/users/me'].get").exists())
          .andExpect(jsonPath("$.paths['/api/v2/users/{id}'].get").exists());
    }
  }

  @Nested
  @DisplayName("schemas, parameters and errors")
  class SchemasParametersAndErrors {

    @Test
    @DisplayName("a resource schema publishes its declared fields and documentation")
    void aResourceSchemaPublishesItsDeclaredFields() throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(
              jsonPath(
                  "$.components.schemas", Matchers.hasKey(Matchers.containsString("Maintenance"))));
    }

    @Test
    @DisplayName("the query parameters a collection accepts are documented")
    void theQueryParametersAreDocumented() throws Exception {
      String parameters = "$.paths['/api/v2/maintenances'].get.parameters[*].name";

      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath(parameters, Matchers.hasItem("where")))
          .andExpect(jsonPath(parameters, Matchers.hasItem("sort")))
          .andExpect(jsonPath(parameters, Matchers.hasItem("page")))
          .andExpect(jsonPath(parameters, Matchers.hasItem("limit")));
    }

    /**
     * A declared {@code ApiV2ErrorMapping} must reach the document, otherwise a client only learns
     * about a resource-specific failure by triggering it.
     */
    @Test
    @DisplayName("a resource-specific error mapping is published on its operation")
    void aResourceSpecificErrorMappingIsPublished() throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(jsonPath("$.paths['/api/v2/maintenances'].post.responses['400']").exists())
          // The presence of a 400 proves nothing: generic request validation already publishes one.
          // The declared mapping's own text is what shows the ApiV2ErrorMapping reached the
          // document.
          .andExpect(
              content().string(Matchers.containsString("The maintenance window is invalid.")));
    }

    /**
     * Internal selectors exist so a server rule can use a property clients must not query.
     * Publishing one would both invite a 400 and disclose the shape of the inventory access rule.
     *
     * <p>Asserted against the whole document rather than one schema or parameter list: these names
     * must not surface anywhere, and the check should not have to know which of the two the
     * generator would leak them through.
     */
    @ParameterizedTest(name = "the internal selector {0} appears nowhere in the document")
    @ValueSource(strings = {"ownerUsername", "sharingMode", "sharingAcl", "sharingACL"})
    @DisplayName("an internal filter selector is absent from the document")
    void anInternalFilterSelectorIsAbsent(String selector) throws Exception {
      mockMvc
          .perform(get(OPENAPI))
          .andExpect(status().isOk())
          .andExpect(content().string(Matchers.not(Matchers.containsString(selector))));
    }
  }
}
