package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Organisation that owns the instrument (PIDINST {@code Owner}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instOwner {

  /** Name of the owning organisation. */
  @JsonProperty("ownerName")
  private String ownerName;

  /**
   * Contact (for example an email address) for the owner. Bound when a registry record is read;
   * ownerOf leaves it unset. It must never come from an account attribute such as the owner's
   * email, which would sit on a permanent public record (ADR 0008, decision 4).
   */
  @JsonProperty("ownerContact")
  private String ownerContact;
}
