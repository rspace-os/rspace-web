package com.researchspace.service.exceptions;

/** Exception thrown when attempting to create a stoichiometry for a chemId that already has one. */
public class StoichiometryAlreadyExistsException extends RuntimeException {

  private final Long chemId;

  /**
   * Constructs a new StoichiometryAlreadyExistsException with the specified chemId.
   *
   * @param chemId the ID of the chemical element that already has a stoichiometry
   */
  public StoichiometryAlreadyExistsException(Long chemId) {
    super("Stoichiometry already exists for chemId: " + chemId);
    this.chemId = chemId;
  }

  /**
   * Returns the ID of the chemical element that already has a stoichiometry.
   *
   * @return the chemId
   */
  public Long getChemId() {
    return chemId;
  }
}
