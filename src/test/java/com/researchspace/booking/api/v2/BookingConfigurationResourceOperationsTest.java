package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingConfigurationResourceOperationsTest {

  private final BookingConfigurationManager manager = mock(BookingConfigurationManager.class);
  private final BookingConfigurationResourceOperations operations =
      new BookingConfigurationResourceOperations(manager);
  private final User actor = mock(User.class);

  @Test
  void translatesRestCreatesToTheSharedManagerInterface() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Europe/Berlin");
    ResolvedResourceReference<BookableTargetType, Long> resolved = resolved(12L);
    ResolvedBookableTarget target = target(resolved);
    when(manager.createConfiguration(new Create(true, "Europe/Berlin", target), actor))
        .thenReturn(configuration);

    BookingConfiguration created =
        operations.create(
            new ParsedDocument(
                WriteOperation.CREATE,
                Map.of("enabled", true, "timezone", "Europe/Berlin", "target", resolved)),
            actor);

    assertEquals(configuration, created);
    verify(manager).createConfiguration(new Create(true, "Europe/Berlin", target), actor);
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
    when(manager.createConfigurations(creates, actor)).thenReturn(configurations);

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
            actor));
    verify(manager).createConfigurations(creates, actor);
  }

  @Test
  void translatesRestPatchesAndDeletesToTheSharedManagerInterface() {
    ParsedDocument document = ParsedDocument.update(Map.of("enabled", true));
    BookingConfiguration configuration = new BookingConfiguration();
    when(manager.updateConfiguration(42L, new Patch(true, null, null), actor))
        .thenReturn(Optional.of(configuration));
    when(manager.removeConfiguration(42L, actor)).thenReturn(Optional.of(configuration));

    assertEquals(configuration, operations.update(42L, document, actor).orElseThrow());
    assertEquals(configuration, operations.delete(42L, actor).orElseThrow());

    verify(manager).updateConfiguration(42L, new Patch(true, null, null), actor);
    verify(manager).removeConfiguration(42L, actor);
  }

  @Test
  void translatesAResolvedTargetPatchToTheBookingDomainValue() {
    BookingConfiguration configuration = new BookingConfiguration();
    ResolvedResourceReference<BookableTargetType, Long> resolved = resolved(18L);
    ResolvedBookableTarget target = target(resolved);
    when(manager.updateConfiguration(42L, new Patch(null, null, target), actor))
        .thenReturn(Optional.of(configuration));

    assertEquals(
        configuration,
        operations
            .update(42L, ParsedDocument.update(Map.of("target", resolved)), actor)
            .orElseThrow());

    verify(manager).updateConfiguration(42L, new Patch(null, null, target), actor);
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
