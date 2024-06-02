package com.researchspace.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShareConfigCommand {

  private Long[] idsToShare;
  private ShareConfigElement[] values;
  private boolean publish;

  public ShareConfigCommand(Long[] idToShare, ShareConfigElement[] values) {
    this(idToShare, values, false);
  }
}
