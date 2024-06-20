package com.researchspace.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserUsernameAliasData {
  private Long userId;
  private String usernameAlias;
}
