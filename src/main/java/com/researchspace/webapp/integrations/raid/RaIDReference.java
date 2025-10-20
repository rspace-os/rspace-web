package com.researchspace.webapp.integrations.raid;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class RaIDReference implements Serializable {

  private String raidServerAlias;
  private String raidIdentifier;
}
