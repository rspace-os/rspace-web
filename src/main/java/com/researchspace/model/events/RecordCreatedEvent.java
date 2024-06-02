package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import lombok.Value;

@Value
public class RecordCreatedEvent implements CreationEvent<BaseRecord> {

  private BaseRecord createdItem;
  private User subject;

  @Override
  public BaseRecord getCreatedItem() {
    return createdItem;
  }

  @Override
  public User getCreatedBy() {
    return subject;
  }
}
