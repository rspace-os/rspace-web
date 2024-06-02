package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.views.RecordCopyResult;
import lombok.Value;

@Value
public class RecordCopyEvent implements CopyEvent<RecordCopyResult> {

  private RecordCopyResult copiedItem;
  private User subject;
}
