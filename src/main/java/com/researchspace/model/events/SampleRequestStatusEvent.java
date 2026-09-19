package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import lombok.Value;

/** A sample request has entered its current status, whether by being raised or by a transition. */
@Value
public class SampleRequestStatusEvent {

  private SampleRequest request;
  private User actor;
}
