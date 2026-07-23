package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSharePostItem {

  @NotNull(message = "{sharing.errors.userIdRequired}")
  private Long id;

  @Pattern(regexp = "(READ|EDIT|read|edit)")
  private String permission;
}
