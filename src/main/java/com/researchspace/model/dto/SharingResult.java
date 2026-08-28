package com.researchspace.model.dto;

import com.researchspace.model.field.ErrorList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SharingResult {
  private List<Long> sharedIds;
  private List<String> publicLinks;
  private ErrorList error;
}
