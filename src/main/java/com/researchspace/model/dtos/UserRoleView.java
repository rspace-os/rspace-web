package com.researchspace.model.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "username")
public class UserRoleView {
  private Long id;
  private String username, firstName, lastName, email, affiliation;
  private List<String> roles;
}
