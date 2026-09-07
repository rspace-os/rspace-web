package com.researchspace.testutils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.webapp.filter.ApiV2StatelessRequestFilter;
import com.researchspace.webapp.filter.LocaleFilter;
import jakarta.servlet.ServletException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * Fixtures and an authenticated {@link MockMvc} for REST API v2 integration tests.
 *
 * <p>Exists because {@link ApiV2WebIntegrationTest} is an annotation rather than a base class, so a
 * test that uses it gets a Spring context but none of the {@code RealTransactionSpringTestBase}
 * helpers, all of which are {@code protected} on a JUnit 4 chain. Without something like this, the
 * only reachable v2 HTTP tests are anonymous ones, which is why every authenticated v2 behaviour
 * was previously asserted against mocks only.
 *
 * <p>Obtain one with {@link #in(WebApplicationContext)} in {@code @BeforeEach} and call {@link
 * #cleanUp()} in {@code @AfterEach}. The annotation deliberately omits {@code
 * TransactionalTestExecutionListener}, so everything created here commits and each test owns its
 * own teardown.
 *
 * <p>Rows are created through the API itself wherever a create route exists. That keeps the fixture
 * free of manager-specific setup and means a fixture that stops working is itself a signal. {@code
 * instruments} is read-only over HTTP, so its rows come from the domain manager.
 */
public final class ApiV2Fixture {

  /** Marks rows created by one test method so a query can select only them. */
  private final String marker = "apiv2-it-" + UUID.randomUUID();

  @Autowired private UserManager userManager;
  @Autowired private UserApiKeyManager apiKeyManager;
  @Autowired private RoleManager roleManager;
  @Autowired private GroupManager groupManager;
  @Autowired private IContentInitializer contentInitializer;
  @Autowired private ContainerApiManager containerManager;
  @Autowired private InstrumentEntityApiManager instrumentManager;
  @Autowired private TimeSlotBookingDao bookingDao;
  @Autowired private FeatureFlagManager featureFlagManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private IPermissionUtils permissionUtils;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<Runnable> teardown = new ArrayList<>();
  private boolean bookingFlagEnabled;

  private MockMvc mockMvc;
  private String sysadminKey;
  private User user;
  private String userKey;
  private User otherUser;
  private String otherUserKey;
  private User thirdUser;
  private String thirdUserKey;

  private ApiV2Fixture() {}

  /**
   * Builds a fixture wired from the test's Spring context.
   *
   * <p>Both production servlet filters are installed. {@code webAppContextSetup} builds only the
   * {@code DispatcherServlet} chain, so without this {@link LocaleFilter} never runs and no
   * response carries {@code Content-Language}, and {@link ApiV2StatelessRequestFilter} never runs
   * so cookies reach Shiro. Either omission would make a v2 contract assertion pass or fail for
   * reasons that have nothing to do with the code under test.
   */
  public static ApiV2Fixture in(WebApplicationContext context) {
    ApiV2Fixture fixture = new ApiV2Fixture();
    context.getAutowireCapableBeanFactory().autowireBean(fixture);
    fixture.mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(localeFilter(context), new ApiV2StatelessRequestFilter())
            .build();
    return fixture;
  }

  /**
   * {@code LocaleFilter} resolves its {@code UserLocaleService} in {@code initFilterBean}, and
   * MockMvc does not run that for a filter handed to {@code addFilters}, so the filter would NPE on
   * the first request. Initialising it here against the test's real servlet context is what makes
   * {@code Content-Language} and {@code Vary} observable at all.
   */
  private static LocaleFilter localeFilter(WebApplicationContext context) {
    LocaleFilter filter = new LocaleFilter();
    try {
      filter.init(new MockFilterConfig(context.getServletContext(), "localeFilter"));
    } catch (ServletException e) {
      throw new IllegalStateException("Could not initialise LocaleFilter for the test chain", e);
    }
    return filter;
  }

  public MockMvc mockMvc() {
    return mockMvc;
  }

  /** A token unique to this test method, usable as a maintenance message and as a filter value. */
  public String marker() {
    return marker;
  }

  /** An API key for the bootstrapped system administrator. */
  public String sysadminKey() {
    if (sysadminKey == null) {
      sysadminKey = keyFor(userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME));
    }
    return sysadminKey;
  }

  /** An ordinary user with an initialised folder tree. */
  public User user() {
    if (user == null) {
      user = createUser();
    }
    return user;
  }

  public String userKey() {
    if (userKey == null) {
      userKey = keyFor(user());
    }
    return userKey;
  }

  /** A second ordinary user, for tests that need one caller to be a third party. */
  public User otherUser() {
    if (otherUser == null) {
      otherUser = createUser();
    }
    return otherUser;
  }

  public String otherUserKey() {
    if (otherUserKey == null) {
      otherUserKey = keyFor(otherUser());
    }
    return otherUserKey;
  }

  /** A third ordinary user, for permission tests with distinct child and parent owners. */
  public User thirdUser() {
    if (thirdUser == null) {
      thirdUser = createUser();
    }
    return thirdUser;
  }

  public String thirdUserKey() {
    if (thirdUserKey == null) {
      thirdUserKey = keyFor(thirdUser());
    }
    return thirdUserKey;
  }

  /** Makes {@code owner}'s records role-visible to {@code viewer} through a temporary lab group. */
  public User makeOwnerRoleVisibleTo(User viewer, User owner) {
    User pi = groupManager.promoteUserToPi(viewer, sysadmin());
    Group group = new Group("apiv2" + UUID.randomUUID().toString().substring(0, 8), pi);
    group.setDisplayName(group.getUniqueName());
    for (ConstraintBasedPermission permission :
        new DefaultPermissionFactory().createDefaultGlobalGroupPermissions(group)) {
      group.addPermission(permission);
    }
    group = groupManager.saveGroup(group, pi);
    groupManager.addMembersToGroup(group.getId(), List.of(owner, pi), pi.getUsername(), null, pi);
    permissionUtils.refreshCache();
    long groupId = group.getId();
    long piId = pi.getId();
    teardown.add(
        () -> {
          User currentPi = userManager.get(piId);
          permissionUtils.notifyUserOrGroupToRefreshCache(currentPi);
          permissionUtils.refreshCache();
          RSpaceTestUtils.logout();
          RSpaceTestUtils.login(currentPi.getUsername(), BaseManagerTestCaseBase.TESTPASSWD);
          try {
            groupManager.removeGroup(groupId, currentPi);
          } finally {
            RSpaceTestUtils.logout();
          }
        });
    if (user != null && user.getId().equals(viewer.getId())) {
      user = userManager.get(viewer.getId());
    }
    return userManager.get(viewer.getId());
  }

  /**
   * Creates one maintenance window through {@code POST /api/v2/maintenances} and returns its ID.
   * The message is {@link #marker()}, so {@code ?where=message==<marker>} selects exactly this
   * test's rows.
   */
  public long maintenance(Instant start, Instant end) {
    String body =
        """
        {"startDate":"%s","endDate":"%s","message":"%s"}\
        """
            .formatted(start, end, marker);
    long id = idOf(postAsSysadmin("/api/v2/maintenances", body));
    teardown.add(() -> deleteAsSysadmin("/api/v2/maintenances/" + id));
    return id;
  }

  /** Creates an instrument owned by {@code owner}. Instruments have no HTTP create route. */
  public long instrument(User owner, String name) {
    ApiInstrument requested = new ApiInstrument();
    requested.setName(name);
    return instrumentManager.createNewApiInstrument(requested, owner).getId();
  }

  /** Creates one list container in the owner's workbench. */
  public ApiContainer container(User owner, String name) {
    return containerManager.createNewApiContainer(
        new ApiContainer(name, ContainerType.LIST), owner);
  }

  /** Creates an instrument and moves it into the supplied container. */
  public long instrumentIn(User owner, String name, ApiContainer parent) {
    long id = instrument(owner, name);
    ApiInstrument move = new ApiInstrument();
    move.setId(id);
    move.setParentContainer(parent);
    instrumentManager.updateApiInstrument(move, owner);
    return id;
  }

  /** Creates one booking configuration targeting {@code instrumentId} and returns its ID. */
  public long bookingConfiguration(long instrumentId, String timezone) {
    return bookingConfiguration(instrumentId, timezone, sysadminKey());
  }

  /** Creates one booking configuration as the caller identified by {@code apiKey}. */
  public long bookingConfiguration(long instrumentId, String timezone, String apiKey) {
    enableBookings();
    String body =
        """
        {"enabled":true,"target":{"relationTo":"booking-instruments","value":%d}}\
        """
            .formatted(instrumentId);
    long id = idOf(postJson("/api/v2/booking-configurations", body, apiKey));
    teardown.add(() -> deleteBookingConfigurationAsSysadmin(id));
    return id;
  }

  /** Creates one confirmed booking through the public endpoint. */
  public long booking(long instrumentId, Instant start, Instant end) {
    enableBookings();
    String body =
        """
        {"target":{"relationTo":"booking-instruments","value":%d},"start":"%s","end":"%s"}\
        """
            .formatted(instrumentId, start, end);
    long id = idOf(postJson("/api/v2/bookings", body, userKey()));
    teardown.add(
        () ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> bookingDao.remove(id)));
    return id;
  }

  /** Removes every row this fixture created, most recent first. */
  public void cleanUp() {
    for (int i = teardown.size() - 1; i >= 0; i--) {
      teardown.get(i).run();
    }
    teardown.clear();
  }

  private User createUser() {
    User created =
        TestFactory.createAnyUser("apiv2" + UUID.randomUUID().toString().substring(0, 8));
    String hashed = CryptoUtils.hashWithSha256inHex(BaseManagerTestCaseBase.TESTPASSWD);
    created.setPassword(hashed);
    created.setConfirmPassword(hashed);
    created.addRole(roleManager.getRole(Constants.USER_ROLE));
    User saved = userManager.save(created);
    // Matches createAndSaveUser: without this the new role is invisible to the permission layer,
    // which shows up as an inexplicable 401/403 rather than as a fixture problem.
    permissionUtils.refreshCache();
    contentInitializer.setCustomInitActive(false);
    contentInitializer.init(saved.getId());
    contentInitializer.setCustomInitActive(true);
    return userManager.get(saved.getId());
  }

  private String keyFor(User target) {
    return apiKeyManager.createKeyForUser(target).getApiKey();
  }

  private User sysadmin() {
    return userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
  }

  /** Enables Booking for this fixture and restores the previous baseline during cleanup. */
  public void enableBookings() {
    if (bookingFlagEnabled) {
      return;
    }
    bookingFlagEnabled = true;
    User target = sysadmin();
    FeatureFlagResource original =
        featureFlagManager
            .getFeatureFlag(FeatureFlags.BOOKING_ENABLED, target)
            .orElseThrow(
                () -> new IllegalStateException("bookingEnabled feature flag is not registered"));
    if (original.isValue()) {
      return;
    }
    boolean previousBaseline = original.isBaselineValue();
    featureFlagManager
        .updateFeatureFlag(
            FeatureFlags.BOOKING_ENABLED, new Patch(true, false, null), target, target)
        .orElseThrow(
            () -> new IllegalStateException("bookingEnabled feature flag is not registered"));
    teardown.add(
        () ->
            featureFlagManager.updateFeatureFlag(
                FeatureFlags.BOOKING_ENABLED,
                new Patch(previousBaseline, false, null),
                target,
                target));
  }

  private String postAsSysadmin(String path, String body) {
    return postJson(path, body, sysadminKey());
  }

  private String postJson(String path, String body, String apiKey) {
    try {
      MvcResult result =
          mockMvc
              .perform(
                  post(path)
                      .header("apiKey", apiKey)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andReturn();
      if (result.getResponse().getStatus() >= 500 && result.getResolvedException() != null) {
        throw new IllegalStateException(
            "fixture POST " + path + " reached an unexpected server error",
            result.getResolvedException());
      }
      return result.getResponse().getContentAsString();
    } catch (Exception e) {
      throw new IllegalStateException("fixture POST " + path + " failed", e);
    }
  }

  /**
   * Teardown checks the status. A fixture that ignores it leaves rows behind whenever a delete
   * route regresses, and nothing else in the suite would notice: every query here is marker- or
   * id-scoped. A noisy teardown is the point.
   */
  private void deleteAsSysadmin(String path) {
    int status;
    try {
      status =
          mockMvc
              .perform(delete(path).header("apiKey", sysadminKey()))
              .andReturn()
              .getResponse()
              .getStatus();
    } catch (Exception e) {
      throw new IllegalStateException("fixture DELETE " + path + " failed", e);
    }
    // 404 means a test already deleted the row itself, which is the outcome teardown wanted.
    if (status != 404 && (status < 200 || status >= 300)) {
      throw new IllegalStateException(
          "fixture DELETE " + path + " answered " + status + "; the row is still in the database");
    }
  }

  /** Permanently removes a versioned booking configuration created by a fixture. */
  private void deleteBookingConfigurationAsSysadmin(long id) {
    String path = "/api/v2/booking-configurations/" + id;
    try {
      MvcResult current =
          mockMvc
              .perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                      .header("apiKey", sysadminKey()))
              .andReturn();
      if (current.getResponse().getStatus() == 404) {
        return;
      }
      String etag = current.getResponse().getHeader(org.springframework.http.HttpHeaders.ETAG);
      int deleteStatus =
          mockMvc
              .perform(
                  delete(path)
                      .queryParam("permanent", "true")
                      .header("apiKey", sysadminKey())
                      .header(org.springframework.http.HttpHeaders.IF_MATCH, etag))
              .andReturn()
              .getResponse()
              .getStatus();
      if (deleteStatus != 204) {
        throw new IllegalStateException(
            "fixture permanent DELETE " + path + " answered " + deleteStatus);
      }
    } catch (Exception e) {
      throw new IllegalStateException("fixture booking configuration cleanup failed", e);
    }
  }

  private long idOf(String responseBody) {
    try {
      JsonNode node = objectMapper.readTree(responseBody);
      if (!node.hasNonNull("id")) {
        throw new IllegalStateException("fixture create returned no id: " + responseBody);
      }
      return node.get("id").asLong();
    } catch (Exception e) {
      throw new IllegalStateException("fixture create returned " + responseBody, e);
    }
  }
}
