package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.User;
import java.io.Serializable;
import java.util.Arrays;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCloudGroup implements Serializable {

  /** */
  private static final long serialVersionUID = -5964641408157633733L;

  private String piEmail;
  private String groupName;
  private String[] emails;
  @JsonIgnore private User sessionUser;

  public CreateCloudGroup() {
    piEmail = "";
    groupName = "";
    emails = new String[] {""};
    this.sessionUser = null;
  }

  @Override
  public String toString() {
    return "CreateCloudGroup principalEmail="
        + piEmail
        + ", groupName="
        + groupName
        + ", emails="
        + Arrays.toString(emails)
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(emails);
    result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
    result = prime * result + ((piEmail == null) ? 0 : piEmail.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CreateCloudGroup other = (CreateCloudGroup) obj;
    if (!Arrays.equals(emails, other.emails)) {
      return false;
    }
    if (groupName == null) {
      if (other.groupName != null) {
        return false;
      }
    } else if (!groupName.equals(other.groupName)) {
      return false;
    }

    if (piEmail == null) {
      if (other.piEmail != null) {
        return false;
      }
    } else if (!piEmail.equals(other.piEmail)) {
      return false;
    }
    return true;
  }

  public boolean isNomination(User sessionUser) {
    return !sessionUser.getEmail().equals(getPiEmail());
  }
}
