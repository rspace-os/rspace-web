package com.researchspace.api.v1.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSharePostItem {

  @NotNull(message = "Must specify id of the user")
  private Long id;

  @Pattern(regexp = "(READ|EDIT|read|edit)")
  private String permission;
}
