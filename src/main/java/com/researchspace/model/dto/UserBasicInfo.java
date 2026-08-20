package com.researchspace.model.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO with basic user information to use in UI. No sensitive content here.
 * Equality is based on username.
 */
@Data
@EqualsAndHashCode(of = "username")
@NoArgsConstructor
public class UserBasicInfo implements Serializable {

	private static final long serialVersionUID = 1959277744899780230L;

	private Long id;
	private String username;
	private String firstName;
	private String lastName;
	private String email;
	private String role;
	private String affiliation;

	public UserBasicInfo(Long id, String username, String firstName, String lastName, String email, String role,
			String affiliation) {
		this.id = id;
		this.username = username;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.role = role;
		this.affiliation = affiliation;
	}

	public String getFullName() {
		return getFirstName() + " " + getLastName();
	}

	public void setFullName(String fullName) {
		// a dummy setter, just for json converter
	}

}