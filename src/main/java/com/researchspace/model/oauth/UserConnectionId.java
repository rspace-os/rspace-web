package com.researchspace.model.oauth;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Composite primary key.
 */
@Embeddable
@Data
@NoArgsConstructor
public class UserConnectionId implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4882611430012595435L;
	
	public UserConnectionId(String userId, String providerId, String providerUserId) {
		this.userId = userId;
		this.providerId = providerId;
		this.providerUserId = providerUserId;
	}
	
	@Column(length=50, nullable=false)
	private String userId;
	
	
	@Column(length=50, nullable=false)
	private String providerId;
	
	@Column(length=50, nullable=false)
	private String providerUserId;
	

}
