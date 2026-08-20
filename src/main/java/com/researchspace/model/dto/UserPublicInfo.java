package com.researchspace.model.dto;

import java.io.Serializable;
import java.util.Date;

import com.researchspace.model.UserProfile;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO class with public info on user suitable for return to UI.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper=true)
public class UserPublicInfo extends UserBasicInfo implements Serializable {

	private static final long serialVersionUID = 1511653030855290713L;
	/**
	 * The last -but -one login time, so user can verify noone nas logged in
	 * since.
	 * 
	 * @return
	 */
	@Setter(AccessLevel.NONE)
	private Date previousLastLogin;
	private boolean accountLocked;
	private boolean enabled;
	private boolean temporary;
	private UserProfile profile;
	private String usernameAlias;

	public void setLastLogin(Date lastLogin) {
		if (lastLogin != null) {
			this.previousLastLogin = new Date(lastLogin.getTime());
		}
	}

	public String getFullNameSurnameFirst() {
		return getLastName() + ", " + getFirstName();
	}

}
