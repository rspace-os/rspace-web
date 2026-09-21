package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.user.UserResourceOperations;
import com.researchspace.booking.api.v2.BookingConfigurationResourceOperations;
import com.researchspace.booking.api.v2.BookingInstrumentRelationshipOperations;
import com.researchspace.booking.api.v2.TimeSlotBookingResourceOperations;
import com.researchspace.booking.config.BookingResourceAccessConfiguration;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.BookingConfigurationProtectedResourceAccess;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.inventory.api.v2.InstrumentResourceOperations;
import com.researchspace.maintenance.api.v2.MaintenanceResourceOperations;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldNamespaces;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;

class ApiV2ResourceConfigTest {

  @Test
  void rejectsMissingOrIncompatibleRuntimeSourcesAtStartup() {
    var target =
        new ApiV2RelationshipTargetSpec<>(
            ApiV2BookingInstrumentResource.DESCRIPTION,
            Long.class,
            (ids, actor) -> Map.<Long, Instrument>of(),
            "users");
    assertThrows(
        IllegalArgumentException.class, () -> new ApiV2ResourceCatalog(List.of(), List.of(target)));
    ResourceOperations<User, Long> operations = mock(ResourceOperations.class);
    var users =
        new ApiV2ResourceSpec<>(
            ApiV2UserResource.DESCRIPTION, operations, Long::valueOf, "create", "update");
    assertThrows(
        IllegalArgumentException.class,
        () -> new ApiV2ResourceCatalog(List.of(users), List.of(target)));
  }

  @Test
  void bookingTargetsPublishAndResolveTheInstrumentRuntimeContractWithoutCrudRoutes() {
    try (AnnotationConfigApplicationContext context = newContext()) {
      context.register(ApiV2ResourceConfig.class);
      context.refresh();
      ApiV2ResourceCatalog catalog = context.getBean(ApiV2ResourceCatalog.class);
      assertTrue(catalog.find("booking-instruments").isEmpty());
      assertEquals(
          List.of("customFields", "extraFields"),
          catalog.runtimeFieldsOf("booking-instruments").stream()
              .map(RuntimeCollectionFields::namespace)
              .toList());
      var field =
          new ResolvedRuntimeField(
              new RuntimeFieldDefinition(
                  "SF1",
                  "customFields.SF1",
                  "Safety level",
                  RuntimeFieldValueType.TEXT,
                  "IT1",
                  "Template",
                  List.of()),
              new RuntimeFieldBinding(
                  InventoryEntityField.class,
                  "instrumentEntity.id",
                  "data",
                  Map.of("templateField.id", 1L)));
      var actor = new User("actor");
      when(context
              .getBean(InstrumentCustomFieldManager.class)
              .resolveAll(Set.of("customFields.SF1"), actor))
          .thenReturn(Map.of("customFields.SF1", field));
      var registration = catalog.find("booking-configurations").orElseThrow();
      var request =
          ApiV2ResourceRequestParser.filtered(
              "target.customFields.SF1==BSL-2",
              registration.description(),
              catalog.registry(),
              registration.runtimeFieldContext(actor, catalog::runtimeFieldsOf));
      assertEquals("instruments", request.runtime().find("target.customFields.SF1").readResource());
      assertEquals("target", request.runtime().relationshipFor("target.customFields.SF1"));

      var document =
          new ObjectMapper()
              .valueToTree(new ApiV2OpenApiGenerator(catalog, "Test", "2").generate());
      assertFalse(document.path("paths").has("/api/v2/booking-instruments"));
      String metadata = document.toString();
      assertTrue(metadata.contains("target.customFields"));
      assertTrue(metadata.contains("target.extraFields"));
      assertTrue(metadata.contains("/api/v2/instruments/fields/customFields"));
      assertFalse(metadata.contains("/api/v2/booking-instruments/fields/"));
    }
  }

  @Test
  void genericMutationsRequireAuthenticationBeforeRequestBodyBinding() {
    ApiV2EndpointCatalog endpoints = new ApiV2ResourceConfig().apiV2EndpointCatalog();
    ApiV2CrudController controller = new ApiV2CrudController(mock(ApiV2ResourceCatalog.class));
    HandlerMethod create = handler(controller, "create");
    HandlerMethod list = handler(controller, "list");
    MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v2/maintenances");
    MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v2/maintenances");

    assertThrows(ApiV2AuthenticationException.class, () -> endpoints.authorize(post, create, null));
    assertDoesNotThrow(() -> endpoints.authorize(get, list, null));
  }

  @Test
  void wiresRegistrationsIntoTheGenericController() {
    try (AnnotationConfigApplicationContext context = newContext()) {
      context.register(ApiV2ResourceConfig.class, ApiV2CrudController.class);
      context.refresh();

      assertTrue(context.containsBean("maintenanceApiV2Resource"));
      assertTrue(context.containsBean("userApiV2Resource"));
      assertTrue(context.containsBean("timeSlotBookingApiV2Resource"));
      assertNotNull(context.getBean(ApiV2CrudController.class));
      assertEquals(
          List.of(
              "booking-configurations",
              "booking-instruments",
              "bookings",
              "instruments",
              "maintenances",
              "users"),
          context.getBean(ApiV2ResourceCatalog.class).registry().resources().stream()
              .map(CollectionDescription::resourceName)
              .sorted()
              .toList());
    }
  }

  /**
   * The point of flat aggregation: a resource spec declared in a separate configuration, with no
   * edit to {@link ApiV2ResourceConfig}, reaches both the registry and the generic controller.
   */
  @Test
  @DisplayName("a resource spec declared elsewhere is picked up")
  void aggregatesContributionsFromAnotherConfiguration() {
    try (AnnotationConfigApplicationContext context = newContext()) {
      context.register(
          ApiV2ResourceConfig.class, OtherModuleConfig.class, ApiV2CrudController.class);
      context.refresh();

      ApiV2ResourceCatalog catalog = context.getBean(ApiV2ResourceCatalog.class);
      assertEquals(
          List.of(
              "booking-configurations",
              "booking-instruments",
              "bookings",
              "instruments",
              "maintenances",
              "users",
              "widgets"),
          catalog.registry().resources().stream()
              .map(CollectionDescription::resourceName)
              .sorted()
              .toList());
      assertEquals(
          6,
          context.getBeansOfType(ApiV2ResourceSpec.class).size(),
          "the five built-in route specs plus the contributed one");
      assertNotNull(context.getBean(ApiV2CrudController.class));
    }
  }

  /**
   * Duplicate detection still spans modules, and now surfaces during context refresh rather than at
   * class initialisation.
   */
  @Test
  @DisplayName("a duplicate resource name across modules fails startup")
  void rejectsADuplicateResourceNameAcrossModules() {
    try (AnnotationConfigApplicationContext context = newContext()) {
      context.register(ApiV2ResourceConfig.class, DuplicateMaintenanceConfig.class);

      BeanCreationException thrown = assertThrows(BeanCreationException.class, context::refresh);

      IllegalArgumentException cause =
          assertInstanceOf(IllegalArgumentException.class, rootCause(thrown));
      assertTrue(cause.getMessage().contains("maintenances"), cause.getMessage());
    }
  }

  private static AnnotationConfigApplicationContext newContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(MaintenanceManager.class, () -> mock(MaintenanceManager.class));
    context.registerBean(UserManager.class, () -> mock(UserManager.class));
    context.registerBean(FeatureFlagManager.class, () -> mock(FeatureFlagManager.class));
    context.registerBean(
        BookingConfigurationManager.class, () -> mock(BookingConfigurationManager.class));
    context.registerBean(
        BookingConfigurationProtectedResourceAccess.class,
        () -> mock(BookingConfigurationProtectedResourceAccess.class));
    context.registerBean(
        BookingConfigurationTargetManager.class,
        () -> mock(BookingConfigurationTargetManager.class));
    context.registerBean(TimeSlotBookingManager.class, () -> mock(TimeSlotBookingManager.class));
    context.registerBean(
        InstrumentEntityApiManager.class, () -> mock(InstrumentEntityApiManager.class));
    InstrumentCustomFieldManager customFields = mock(InstrumentCustomFieldManager.class);
    when(customFields.namespace()).thenReturn(RuntimeFieldNamespaces.CUSTOM_FIELDS);
    context.registerBean(InstrumentCustomFieldManager.class, () -> customFields);
    context.registerBean(ExtraFieldDao.class, () -> mock(ExtraFieldDao.class));
    context.registerBean(
        InstrumentReadAccess.class,
        () -> new InstrumentReadAccess(mock(InventoryPermissionUtils.class)));
    context
        .getBeanFactory()
        .registerSingleton(
            BookingResourceAccessConfiguration.BOOKING_CONFIGURATION_DESCRIPTION,
            ApiV2BookingConfigurationResource.DESCRIPTION);
    context
        .getBeanFactory()
        .registerSingleton(
            BookingResourceAccessConfiguration.TIME_SLOT_BOOKING_DESCRIPTION,
            ApiV2TimeSlotBookingResource.DESCRIPTION);
    context.register(
        BookingTimeConfig.class,
        BookingConfigurationResourceOperations.class,
        BookingInstrumentRelationshipOperations.class,
        TimeSlotBookingResourceOperations.class,
        InstrumentResourceOperations.class,
        MaintenanceResourceOperations.class,
        UserResourceOperations.class);
    return context;
  }

  private static HandlerMethod handler(ApiV2CrudController controller, String methodName) {
    return new HandlerMethod(
        controller,
        Arrays.stream(ApiV2CrudController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(methodName))
            .findFirst()
            .orElseThrow());
  }

  private static Throwable rootCause(Throwable thrown) {
    Throwable current = thrown;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * Stands in for a module contributing its own collection.
   *
   * <p>Deliberately NOT {@code @Configuration}. Flat aggregation means any scanned configuration
   * declaring an {@link ApiV2ResourceSpec} bean joins the real catalog — and test classes are on
   * the classpath that {@code applicationContext-service.xml} scans, so annotating these fixtures
   * made every Spring integration context fail to load with "Duplicate resource entity type".
   * Spring still honours the {@code @Bean} methods in lite mode when the class is registered
   * explicitly.
   */
  static class OtherModuleConfig {

    @Bean
    ApiV2ResourceSpec<Widget, Long> widgetApiV2Resource() {
      return new ApiV2ResourceSpec<>(
          WIDGETS,
          mock(ResourceOperations.class),
          Long::valueOf,
          "errors.api.v2.invalidRequest",
          "errors.api.v2.invalidRequest");
    }
  }

  /**
   * A second description claiming a name the core module already owns. Not scannable; see above.
   */
  static class DuplicateMaintenanceConfig {

    @Bean
    ApiV2ResourceSpec<Widget, Long> clashingResource() {
      CollectionDescription<Widget> description =
          new CollectionDescription<>(
              "maintenances",
              Widget.class,
              List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id)),
              List.of(),
              "id",
              List.of(new Sort("id", true)));
      return new ApiV2ResourceSpec<>(
          description,
          mock(ResourceOperations.class),
          Long::valueOf,
          "errors.api.v2.invalidRequest",
          "errors.api.v2.invalidRequest");
    }
  }

  static final CollectionDescription<Widget> WIDGETS =
      new CollectionDescription<>(
          "widgets",
          Widget.class,
          List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Widget::id)),
          List.of(),
          "id",
          List.of(new Sort("id", true)));

  record Widget(Long id) {}
}
