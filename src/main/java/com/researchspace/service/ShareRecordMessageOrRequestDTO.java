package com.researchspace.service;

import com.researchspace.model.record.BaseRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShareRecordMessageOrRequestDTO {
  private Long requestId;
  private BaseRecord record;
  private String email, permission;
}
