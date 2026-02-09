package com.researchspace.webapp.integrations.raid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.raid.model.exception.RaIDException;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = {"raidServerAlias", "raidIdentifier"})
@ToString(of = {"raidServerAlias", "raidTitle", "raidIdentifier"})
public class RaIDReferenceDTO implements Serializable {

  @JsonIgnore private Long id; // raid DB id

  @JsonIgnore private String briefIdentifier; // format raid:10.2343/FDR364
  @JsonIgnore private String raidPrefix;
  @JsonIgnore private String raidSuffix;

  @JsonInclude(value = Include.NON_NULL)
  private String raidAgencyUrl;

  private String raidServerAlias;

  @JsonInclude(value = Include.NON_NULL)
  private String raidTitle;

  private String raidIdentifier; // raid URL

  public RaIDReferenceDTO(UserRaid userRaid) {
    this(
        userRaid.getId(),
        userRaid.getRaidServerAlias(),
        userRaid.getRaidTitle(),
        userRaid.getRaidIdentifier(),
        userRaid.getRaidAgencyUrl());
  }

  public RaIDReferenceDTO(String raidServerAlias, String raidIdentifier) {
    this(raidServerAlias, null, raidIdentifier, null);
  }

  public RaIDReferenceDTO(
      String raidServerAlias, String raidTitle, String raidIdentifier, String raidAgencyUrl) {
    this.raidServerAlias = raidServerAlias;
    this.raidTitle = raidTitle;
    this.raidIdentifier = raidIdentifier;
    this.raidAgencyUrl = raidAgencyUrl;
    try {
      String[] raidIdentifierSplit = raidIdentifier.split("/");
      this.raidPrefix = raidIdentifierSplit[raidIdentifierSplit.length - 2];
      this.raidSuffix = raidIdentifierSplit[raidIdentifierSplit.length - 1];
      this.briefIdentifier = "raid:" + this.raidPrefix + "/" + this.raidSuffix;
    } catch (Exception e) {
      throw new RaIDException(
          "The raid identifier \""
              + raidIdentifier
              + "\" "
              + "is not on the right format. (e.g.: https:/raid.org/10.12345/ERT987)");
    }
  }

  private RaIDReferenceDTO(
      Long id,
      String raidServerAlias,
      String raidTitle,
      String raidIdentifier,
      String raidAgencyUrl) {
    this(raidServerAlias, raidTitle, raidIdentifier, raidAgencyUrl);
    this.id = id;
  }
}
