package com.researchspace.extmessages.base;

import com.drew.lang.annotations.NotNull;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.core.Person;
import java.util.Collection;
import lombok.Value;

/** Value object representing message informaion to send to an external message sender */
@Value
public class MessageDetails {
  @NotNull private Person originator;
  @NotNull private String message;

  /** Typed as an interface so not tied to loading a whole entity from database. */
  @NotNull private Collection<IRSpaceDoc> records;
}
