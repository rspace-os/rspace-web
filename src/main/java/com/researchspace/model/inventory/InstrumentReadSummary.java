package com.researchspace.model.inventory;

/** Scalar instrument data for REST API read pages and batched relationship expansion. */
public record InstrumentReadSummary(Long id, String name, boolean deleted) {}
