package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Optional;

/** Shared domain service for reading and changing booking configurations. */
public interface BookingConfigurationManager {

  /** Values accepted when creating a booking configuration. */
  record Create(
      boolean enabled,
      String timeZone,
      ResolvedBookableTarget target,
      BookingSchedulingSettings.Patch schedulingSettings) {

    public Create(boolean enabled, String timeZone, ResolvedBookableTarget target) {
      this(enabled, timeZone, target, BookingSchedulingSettings.Patch.empty());
    }

    public Create {
      if (schedulingSettings == null) {
        schedulingSettings = BookingSchedulingSettings.Patch.empty();
      }
    }
  }

  /** Values accepted when changing a booking configuration; {@code null} means unchanged. */
  record Patch(
      Boolean enabled,
      String timeZone,
      ResolvedBookableTarget target,
      BookingSchedulingSettings.Patch schedulingSettings) {

    public Patch(Boolean enabled, String timeZone, ResolvedBookableTarget target) {
      this(enabled, timeZone, target, BookingSchedulingSettings.Patch.empty());
    }

    public Patch {
      if (schedulingSettings == null) {
        schedulingSettings = BookingSchedulingSettings.Patch.empty();
      }
    }
  }

  /** Returns one page selected by a parsed collection request. */
  ResourcePage<BookingConfiguration> getConfigurations(ResourceRequest request, User actor);

  /** Counts configurations selected by a parsed collection request. */
  long countConfigurations(ResourceRequest request, User actor);

  /** Finds one configuration without throwing when it is absent. */
  Optional<BookingConfiguration> getConfiguration(Long id, User actor);

  /** Creates as {@code subject}, retaining the originating {@code actor} for audit. */
  BookingConfiguration createConfiguration(Create create, User subject, User actor);

  /** Bulk-creates as {@code subject}, retaining the originating {@code actor} for audit. */
  List<BookingConfiguration> createConfigurations(List<Create> creates, User subject, User actor);

  /** Updates as {@code subject}, retaining the originating {@code actor} for audit. */
  Optional<BookingConfiguration> updateConfiguration(
      Long id, Patch patch, User subject, User actor);

  /** Bulk-updates as {@code subject}, retaining the originating {@code actor} for audit. */
  List<BookingConfiguration> updateConfigurations(
      ResourceRequest request, Patch patch, User subject, User actor);

  /** Removes as {@code subject}, retaining the originating {@code actor} for audit. */
  Optional<BookingConfiguration> removeConfiguration(Long id, User subject, User actor);

  /** Bulk-removes as {@code subject}, retaining the originating {@code actor} for audit. */
  List<BookingConfiguration> removeConfigurations(
      ResourceRequest request, User subject, User actor);
}
