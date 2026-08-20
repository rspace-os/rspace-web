package com.researchspace.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.TableGenerator;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;
import java.io.Serializable;

/** Stores profile information about a user. */
@Entity
@XmlType
@XmlAccessorType(XmlAccessType.NONE)
public class UserProfile implements Serializable, Comparable<UserProfile> {

  private static final long serialVersionUID = 6039997143520226780L;

  private Long id;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "user_profile_gen")
  @TableGenerator(
      name = "user_profile_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  void setId(Long id) {
    this.id = id;
  }

  private ImageBlob profilePicture;

  public UserProfile(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User cannot be null");
    }
    this.owner = user;
  }

  /** Default constructor */
  public UserProfile() {}

  private User owner;

  @OneToOne(optional = false)
  @XmlElement
  @XmlIDREF
  @JsonIgnore
  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    if (owner == null) {
      throw new IllegalArgumentException("user cannot be null");
    }
    this.owner = owner;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((owner == null) ? 0 : owner.hashCode());
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
    UserProfile other = (UserProfile) obj;
    if (owner == null) {
      if (other.owner != null) {
        return false;
      }
    } else if (!owner.equals(other.owner)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UserProfile for user [" + (owner == null ? owner : owner.getUsername()) + "]";
  }

  /**
   * @return
   */
  @OneToOne(cascade = CascadeType.ALL)
  public ImageBlob getProfilePicture() {
    return profilePicture;
  }

  public void setProfilePicture(ImageBlob profilePicture) {
    this.profilePicture = profilePicture;
  }

  @Override
  public int compareTo(UserProfile other) {
    return this.owner.compareTo(other.getOwner());
  }
}
