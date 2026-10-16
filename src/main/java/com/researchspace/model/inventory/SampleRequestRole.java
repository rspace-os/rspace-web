package com.researchspace.model.inventory;

/** Which side of a sample request the caller is asking about. */
public enum SampleRequestRole {

  /** Requests the caller raised. */
  REQUESTER,

  /** Requests received by the caller, as the sample's owner when each was raised. */
  OWNER
}
