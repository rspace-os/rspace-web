package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.views.SigningResult;
import lombok.Value;

@Value
public class SigningCreationEvent implements CreationEvent<SigningResult> {

  private SigningResult createdItem;

  private User createdBy;
}
