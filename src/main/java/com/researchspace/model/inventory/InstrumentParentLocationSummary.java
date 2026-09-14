package com.researchspace.model.inventory;

/** Immediate parent-container data needed when rendering an instrument collection row. */
public record InstrumentParentLocationSummary(
    Long containerId, String containerName, Container.ContainerType containerType) {}
