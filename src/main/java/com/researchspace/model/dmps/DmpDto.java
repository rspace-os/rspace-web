package com.researchspace.model.dmps;

import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An imported DMP. Not an entity, just a collection of DMP attributes we want to store in RSpace
 */
@Data
@NoArgsConstructor
public class DmpDto {

  private String dmpId;
  private String title;
  private DMPSource source;
  private String doiLink;
  private String dmpLink;

  public DmpDto(String dmpId, String title, DMPSource source, String doiLink, String dmpLink) {
    this.dmpId = dmpId;
    this.title = title;
    this.source = source;
    this.doiLink = doiLink;
    this.dmpLink = dmpLink;
  }

  public DmpDto(String dmpId, String title) {
    this.dmpId = dmpId;
    this.title = title;
    this.source = DMPSource.UNKNOWN;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DmpDto dmpDto = (DmpDto) o;
    return Objects.equals(dmpId, dmpDto.dmpId) &&
        Objects.equals(source, dmpDto.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dmpId, source);
  }
}
