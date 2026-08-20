package com.researchspace.model.dto;

import java.io.Serializable;

import com.researchspace.model.SignupSource;
import com.researchspace.model.User;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO with data required for creating a user from UI.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class UserRegistrationInfo extends UserBasicInfo implements Serializable {

	private static final long serialVersionUID = -749399671256039631L;

	private String password;
	private String confirmPassword;

	// for the sake of csv import toUser should return same object every time
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private User user;
	
	public UserRegistrationInfo() {
		super();
	}
	
	@Builder()
	private UserRegistrationInfo(Long id, String username, String firstName, String lastName, String email, String role,
			String affiliation,String password, String confirmPassword) {
		super(id,username,firstName,lastName,email,role,affiliation);
		this.password = password;
		this.confirmPassword = confirmPassword;
	}

	public User toUser() {
		if (user == null) {
			user = new User();
		}
		user.setUsername(getUsername());
		user.setFirstName(getFirstName());
		user.setLastName(getLastName());
		user.setEmail(getEmail());
		user.setRole(getRole());
		user.setAffiliation(getAffiliation());
		user.setPassword(getPassword());
		user.setConfirmPassword(getConfirmPassword());
		user.setSignupSource(SignupSource.MANUAL);

		return user;
	}

}
