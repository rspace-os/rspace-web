package com.researchspace.webapp.integrations.raid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"raidServerAlias", "raidIdentifier"})
@ToString
public class RaIDReferenceDTO implements Serializable {

  @JsonIgnore private Long id;

  private String raidServerAlias;
  private String raidIdentifier;

  public RaIDReferenceDTO(String raidServerAlias, String raidIdentifier) {
    this.raidServerAlias = raidServerAlias;
    this.raidIdentifier = raidIdentifier;
  }
}
