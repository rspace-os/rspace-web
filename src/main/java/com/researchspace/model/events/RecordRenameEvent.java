package com.researchspace.model.events;

import com.researchspace.model.record.BaseRecord;
import lombok.Value;

@Value
public class RecordRenameEvent implements RenameEvent<BaseRecord> {
  private BaseRecord renamedItem;
  private String oldname, newname;
}
