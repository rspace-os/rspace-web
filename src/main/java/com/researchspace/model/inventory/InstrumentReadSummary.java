package com.researchspace.model.inventory;

/** Scalar instrument data used for batched REST API relationship expansion. */
public record InstrumentReadSummary(
    Long id,
    String name,
    boolean deleted,
    Long parentContainerId,
    String parentContainerName,
    Container.ContainerType parentContainerType) {}
