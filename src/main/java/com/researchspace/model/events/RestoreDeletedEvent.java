package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.service.RestoreDeletedItemResult;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class RestoreDeletedEvent implements RestoreEvent<RestoreDeletedItemResult> {

  private RestoreDeletedItemResult restored;
  private User subject;
}
