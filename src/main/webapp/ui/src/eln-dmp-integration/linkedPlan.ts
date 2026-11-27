/**
 * The Id of DMP, according to the service from which is originated.
 */
type DMPId = number | string;

/**
 * The Id of DMP, according to RSpace.
 */
export type DMPUserInternalId = number;

/**
 * A definition of a DMP, for the purposes of selecting one to attach to a
 * deposit to a repository.
 */
export type Plan = {
  dmpId: DMPId;
  dmpTitle: string;
  dmpUserInternalId: DMPUserInternalId;
  platformName: string;
  /** OK, or why RSpace will not update the plan after the deposit. */
  writeBackStatus: "OK" | "NOT_CONNECTED" | "PLATFORM_OFF" | "NOT_SUPPORTED" | "UNKNOWN_SOURCE";
};
