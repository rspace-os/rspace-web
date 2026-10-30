package com.researchspace.model.inventory;

/** Scalar container data for authorized relationship expansion without Inventory hydration. */
public record ContainerReadSummary(
    Long id, String name, Container.ContainerType containerType, Boolean deleted) {}
