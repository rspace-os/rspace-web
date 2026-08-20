package com.researchspace.model.views;

import com.researchspace.model.core.Person;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
/**
 * Simple view of User entity for implementing Person 
 */
@Data
@EqualsAndHashCode(of="uniqueName")
public class UserView implements Person {
	private  @NonNull Object id;
	private  @NonNull String uniqueName;
	private  @NonNull String email;
	
	private String fullName;
	
	public UserView(Object id, String uniqueName, String email, String fullname) {
		super();
		this.id = id;
		this.uniqueName = uniqueName;
		this.email = email;
		this.fullName = fullname;
	}	

}
