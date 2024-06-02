package com.researchspace.model.repository;

import com.researchspace.model.User;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IDepositor;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.Size;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Adapter to adapt a {@link User} to the {@link IDepositor} interface required for archive
 * deposition.
 */
public class UserDepositorAdapter implements IDepositor {

  @NotEmpty(message = "Email {errors.required.field}")
  @Email(message = "{errors.email.format}")
  private String email;

  @NotBlank(message = "Name {errors.required.field}")
  @Size(max = 200, message = "Name {errors.string.range}")
  private String uniqueName;

  private String institutionalAffiliation;
  private List<ExternalId> externalIds = new ArrayList<>();

  /*
   * For framework
   */
  public UserDepositorAdapter() {
    super();
  }

  public UserDepositorAdapter(User user) {
    super();
    this.email = user.getEmail();
    this.uniqueName = user.getUniqueName();
  }

  public UserDepositorAdapter(String email, String name) {
    super();
    this.email = email;
    this.uniqueName = name;
  }

  @Override
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public String getUniqueName() {
    return uniqueName;
  }

  public void setUniqueName(String uniqueName) {
    this.uniqueName = uniqueName;
  }

  @Override
  public List<ExternalId> getExternalIds() {
    return externalIds;
  }

  public void setExternalIds(List<ExternalId> externalIds) {
    this.externalIds = externalIds;
  }

  public String getInstitutionalAffiliation() {
    return institutionalAffiliation;
  }

  public void setInstitutionalAffiliation(String institutionalAffiliation) {
    this.institutionalAffiliation = institutionalAffiliation;
  }

  @Override
  public String toString() {
    return "UserDepositorAdapter [email=" + email + ", uniqueName=" + uniqueName + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((email == null) ? 0 : email.hashCode());
    result = prime * result + ((uniqueName == null) ? 0 : uniqueName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    UserDepositorAdapter other = (UserDepositorAdapter) obj;
    if (email == null) {
      if (other.email != null) return false;
    } else if (!email.equals(other.email)) return false;
    if (uniqueName == null) {
      if (other.uniqueName != null) return false;
    } else if (!uniqueName.equals(other.uniqueName)) return false;
    return true;
  }
}
