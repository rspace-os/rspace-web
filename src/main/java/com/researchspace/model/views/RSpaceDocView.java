package com.researchspace.model.views;

import java.util.Date;

import org.apache.commons.lang3.Validate;

import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.core.Person;
import com.researchspace.model.core.RecordType;

import lombok.Data;
import lombok.NonNull;
/**
 * Simple implementation of IRSpaceDoc suitable for basic information retrieved from a DB query.
 */
@Data
public class RSpaceDocView implements IRSpaceDoc {
	@NonNull
	private String name;
	@NonNull
	private Long id;
	@NonNull
	private Date creationDate;
	@NonNull
	private Date modificationDateAsDate;
	@NonNull
	private String type;
	
	@NonNull
	private Person owner;
	

	@Override
	public String getGlobalIdentifier() {
		return RecordType.getGlobalIdFromType(type).name() + getId().toString();
	}

	@Override
	public boolean isMediaRecord() {
		return type.contains(RecordType.MEDIA_FILE.name()); 
	}

	@Override
	public boolean isStructuredDocument() {
		return type.contains(RecordType.NORMAL.name()) 
				|| type.contains(RecordType.TEMPLATE.name());
	}

	/**
	 * For creation from database query
	 * @param name
	 * @param id
	 * @param creationDate
	 * @param modificationDateAsDate
	 * @param type
	 * @param userId
	 * @param username
	 * @param email
	 * @param fullName
	 */
	public RSpaceDocView(String name, Long id, Date creationDate, Date modificationDateAsDate, String type, Long userId, 
			String username, String email, String fullName) {
		super();
		this.name = name;
		this.id = id;
		this.creationDate = creationDate;
		this.modificationDateAsDate = modificationDateAsDate;
		this.type = type;
		this.owner = new UserView(userId, username, email, fullName);
	}
	
	/**
	 * For creation from database query
	 * @param name
	 * @param id
	 * @param creationDate
	 * @param modificationDateAsDate
	 * @param type
	 * @param person Person
	 */
	public RSpaceDocView(String name, Long id, Date creationDate, Date modificationDateAsDate, String type, Person person) {
		super();
		Validate.notNull(person, "Person cannot be null");
		this.name = name;
		this.id = id;
		this.creationDate = new Date(creationDate.getTime());
		this.modificationDateAsDate = new Date(modificationDateAsDate.getTime());
		this.type = type;
		this.owner = person;
	}

	/**
	 * Just for testing
	 */
	public RSpaceDocView() {		
	}

}
