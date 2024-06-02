package com.researchspace.dao.hibernate;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.researchspace.model.dtos.UserRoleView;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single row of a query result joining User to Role with minimal user information */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleViewProjection {
  private Long id;
  private String username, firstName, lastName, email, affiliation;
  private String role;

  /** Creates a UserRoleView with a List of 1 role. */
  public UserRoleView toUserRoleView() {
    UserRoleView rc = new UserRoleView();
    rc.setUsername(username);
    rc.setId(id);
    rc.setFirstName(firstName);
    rc.setLastName(lastName);
    rc.setEmail(email);
    rc.setAffiliation(affiliation);
    rc.setRoles(toList(role));
    return rc;
  }
}
