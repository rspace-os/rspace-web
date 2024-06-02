/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Representation of a User */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "username",
      "email",
      "firstName",
      "lastName",
      "homeFolderId",
      "workbenchId",
      "hasPiRole",
      "hasSysAdminRole"
    })
public class ApiUser extends LinkableApiObject implements IdentifiableObject {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("username")
  private String username;

  @JsonProperty("email")
  private String email;

  @JsonProperty("firstName")
  private String firstName;

  @JsonProperty("lastName")
  private String lastName;

  @JsonProperty("homeFolderId")
  private Long homeFolderId;

  @JsonProperty("workbenchId")
  private Long workbenchId;

  @JsonProperty("hasPiRole")
  private Boolean hasPiRole;

  @JsonProperty("hasSysAdminRole")
  private Boolean hasSysAdminRole;

  public ApiUser(User user) {
    this.id = user.getId();
    this.username = user.getUsername();
    this.email = user.getEmail();
    this.firstName = user.getFirstName();
    this.lastName = user.getLastName();
    this.homeFolderId = user.getRootFolder() == null ? null : user.getRootFolder().getId();
    this.hasPiRole = user.isPI();
    this.hasSysAdminRole = user.hasRole(Role.SYSTEM_ROLE);
  }

  public ApiUser(User user, boolean onlyPublicData) {
    this(user);
    if (onlyPublicData) {
      this.email = null;
      this.setHomeFolderId(null);
      this.setHasPiRole(null);
      this.setHasSysAdminRole(null);
    }
  }

  /* for testing, does not set homefolder/workbench id etc. */
  public ApiUser(Long id, String username, String email, String firstName, String lastName) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
  }
}
