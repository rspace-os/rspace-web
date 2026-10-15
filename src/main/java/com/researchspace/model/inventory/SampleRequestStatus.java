package com.researchspace.model.inventory;

/**
 * Lifecycle of a request for material from a sample. Persisted as a string, so values may be
 * reordered or renamed only alongside a data migration.
 */
public enum SampleRequestStatus {

  /** Raised by the requester, awaiting the sample owner. */
  PENDING,

  /** The owner has agreed to provide material, which has not been produced yet. */
  APPROVED,

  /** The owner will not provide material. */
  REJECTED,

  /** Material has been produced for the requester. */
  FULFILLED,

  /** Withdrawn by the requester. Deliberately distinct from REJECTED. */
  CANCELLED
}
