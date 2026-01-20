package com.researchspace.webapp.integrations.raid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.raid.model.exception.RaIDException;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = {"raidServerAlias", "raidIdentifier"})
@ToString
public class RaIDReferenceDTO implements Serializable {

  @JsonIgnore private Long id; // raid DB id

  @JsonIgnore private String briefIdentifier; // format raid:10.2343/FDR364

  private String raidServerAlias;
  private String raidIdentifier; // raid URL

  public RaIDReferenceDTO(Long id, String raidServerAlias, String raidIdentifier) {
    this(raidServerAlias, raidIdentifier);
    this.id = id;
  }

  public RaIDReferenceDTO(String raidServerAlias, String raidIdentifier) {
    this.raidServerAlias = raidServerAlias;
    this.raidIdentifier = raidIdentifier;
    try {
      String[] raidIdentifierSplit = raidIdentifier.split("/");
      this.briefIdentifier =
          "raid:"
              + raidIdentifierSplit[raidIdentifierSplit.length - 2]
              + "/"
              + raidIdentifierSplit[raidIdentifierSplit.length - 1];
    } catch (Exception e) {
      throw new RaIDException(
          "The raid identifier \""
              + raidIdentifier
              + "\" "
              + "is not on the right format. (e.g.: https:/raid.org/10.12345/ERT987)");
    }
  }
}
