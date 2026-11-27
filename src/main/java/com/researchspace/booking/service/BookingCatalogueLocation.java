package com.researchspace.booking.service;

import com.researchspace.model.inventory.Container.ContainerType;

/** Safe immediate location shown in the booking catalogue. */
public record BookingCatalogueLocation(Long id, String name, ContainerType containerType) {}
