package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingConfigurationResourceOperationsTest {

  private final BookingConfigurationManager manager = mock(BookingConfigurationManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final BookingConfigurationResourceOperations operations =
      new BookingConfigurationResourceOperations(manager, featureFlags);
  private final User actor = mock(User.class);

  @BeforeEach
  void enableBooking() {
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, actor)).thenReturn(true);
  }

  @Test
  void translatesRestCreatesToTheSharedManagerInterface() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Europe/Berlin");
    ResolvedResourceReference<BookableTargetType, Long> resolved = resolved(12L);
    ResolvedBookableTarget target = target(resolved);
    when(manager.createConfiguration(new Create(true, "Europe/Berlin", target), actor, actor))
        .thenReturn(configuration);

    BookingConfiguration created =
        operations.create(
            new ParsedDocument(
                WriteOperation.CREATE,
                Map.of("enabled", true, "timezone", "Europe/Berlin", "target", resolved)),
            ApiV2Caller.direct(actor));

    assertEquals(configuration, created);
    verify(manager).createConfiguration(new Create(true, "Europe/Berlin", target), actor, actor);
  }

  @Test
  void authorizesAsTheSubjectAndRetainsTheOriginatingActorForWrites() {
    User subject = mock(User.class);
    User originatingActor = mock(User.class);
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, subject)).thenReturn(true);
    ParsedDocument document = ParsedDocument.update(Map.of("enabled", true));

    operations.update(42L, document, new ApiV2Caller(subject, originatingActor));

    verify(manager).updateConfiguration(42L, new Patch(true, null), subject, originatingActor);
  }

  @Test
  void translatesBulkCreatesToOneSharedManagerCall() {
    ResolvedResourceReference<BookableTargetType, Long> first = resolved(12L);
    ResolvedResourceReference<BookableTargetType, Long> second = resolved(13L);
    List<Create> creates =
        List.of(
            new Create(true, "Europe/Berlin", target(first)),
            new Create(false, "UTC", target(second)));
    List<BookingConfiguration> configurations =
        List.of(new BookingConfiguration(), new BookingConfiguration());
    when(manager.createConfigurations(creates, actor, actor)).thenReturn(configurations);

    assertEquals(
        configurations,
        operations.createMany(
            List.of(
                new ParsedDocument(
                    WriteOperation.CREATE,
                    Map.of("enabled", true, "timezone", "Europe/Berlin", "target", first)),
                new ParsedDocument(
                    WriteOperation.CREATE,
                    Map.of("enabled", false, "timezone", "UTC", "target", second))),
            ApiV2Caller.direct(actor)));
    verify(manager).createConfigurations(creates, actor, actor);
  }

  @Test
  void translatesRestPatchesAndDeletesToTheSharedManagerInterface() {
    ParsedDocument document = ParsedDocument.update(Map.of("enabled", true));
    BookingConfiguration configuration = new BookingConfiguration();
    when(manager.updateConfiguration(42L, new Patch(true, null), actor, actor))
        .thenReturn(Optional.of(configuration));
    when(manager.removeConfiguration(42L, actor, actor)).thenReturn(Optional.of(configuration));

    assertEquals(
        configuration, operations.update(42L, document, ApiV2Caller.direct(actor)).orElseThrow());
    assertEquals(configuration, operations.delete(42L, ApiV2Caller.direct(actor)).orElseThrow());

    verify(manager).updateConfiguration(42L, new Patch(true, null), actor, actor);
    verify(manager).removeConfiguration(42L, actor, actor);
  }

  @Test
  void translatesMaximumBookingDurationPatchesToSchedulingSettings() {
    BookingConfiguration configuration = new BookingConfiguration();
    BookingSchedulingSettings.Patch schedulingPatch =
        new BookingSchedulingSettings.Patch(null, null, null, null, null, 60L, null);
    when(manager.updateConfiguration(42L, new Patch(null, null, schedulingPatch), actor, actor))
        .thenReturn(Optional.of(configuration));

    assertEquals(
        configuration,
        operations
            .update(
                42L,
                ParsedDocument.update(Map.of("maxBookingDurationMinutes", 60L)),
                ApiV2Caller.direct(actor))
            .orElseThrow());

    verify(manager).updateConfiguration(42L, new Patch(null, null, schedulingPatch), actor, actor);
  }

  @Test
  void doesNotCallBookingManagerWhenBookingIsDisabled() {
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, actor)).thenReturn(false);
    ParsedDocument document = ParsedDocument.update(Map.of("enabled", true));
    ResourceRequest request = ResourceRequest.unpaged(null);

    assertEquals(List.of(), operations.find(request, actor).resources());
    assertEquals(0, operations.count(request, actor));
    assertEquals(Optional.empty(), operations.findById(42L, actor));
    ApiV2Caller caller = ApiV2Caller.direct(actor);
    assertThrows(AuthorizationException.class, () -> operations.create(document, caller));
    assertThrows(
        AuthorizationException.class, () -> operations.createMany(List.of(document), caller));
    assertEquals(Optional.empty(), operations.update(42L, document, caller));
    assertEquals(List.of(), operations.updateMany(request, document, caller));
    assertEquals(Optional.empty(), operations.delete(42L, caller));
    assertEquals(List.of(), operations.deleteMany(request, caller));
    verifyNoInteractions(manager);
  }

  private static ResolvedResourceReference<BookableTargetType, Long> resolved(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return new ResolvedResourceReference<>(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, id), instrument);
  }

  private static ResolvedBookableTarget target(
      ResolvedResourceReference<BookableTargetType, Long> resolved) {
    return new ResolvedBookableTarget(
        new BookableTargetReference(resolved.reference().kind(), resolved.reference().id()),
        resolved.entityAs(Instrument.class));
  }
}
