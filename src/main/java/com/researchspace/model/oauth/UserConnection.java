package com.researchspace.model.oauth;

import com.researchspace.model.permissions.TextEncryptor;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Maps to possible Spring-generated UserConnection class.
 */
@Entity
@Getter
@Setter
@ToString
@EqualsAndHashCode(of={"id", "accessToken"})
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class UserConnection {

	public UserConnection(UserConnectionId id, String accessToken) {
		Validate.noNullElements(new Object[] { id, accessToken }, "arguments cannot be null");
		this.id = id;
		this.accessToken = accessToken;
	}

	@EmbeddedId
	private UserConnectionId id;

	private int rank = 1;

	private String displayName;

	@Column(length = 512)
	private String profileUrl;

	@Column(length = 512)
	private String imageUrl;

	@Column(length = 4096, nullable = false)
	private String accessToken;

	@Column(length = 4096)
	private String secret;

	@Column(length = 4096)
	private String refreshToken;
	
	/**
	 * Whether or not secret, access token and refreshToken are encrypted or not when persisted. 
	 * AccessType.PROPERTY ensures setter (which updates 'transientlyEncrypted' field) is called.
	 */
	@Access(AccessType.PROPERTY)
	private boolean encrypted = false;

	/**
	 * Transiently encrypted in memory, needed for merging state between persisted and evicted entities.
	 */
	@Transient
	private boolean transientlyEncrypted = false;

	void setEncrypted(boolean encrypted) {
		this.encrypted = encrypted;
		this.transientlyEncrypted = encrypted;
	}

	/**
	 * The expiry time of this token, in epoch millis.
	 */
	private Long expireTime;

	/**
	 * Decrypts tokens using supplied encryptor, if tokens were marked as encrypted.
	 *  It is assumed that the same encryptor and key  
	 *  is used to decrypt as encrypt.
	 * @param encryptor
	 */
	public UserConnection decryptTokens(TextEncryptor encryptor) {
		if (isEncrypted() && transientlyEncrypted) {
			setAccessToken(encryptor.decrypt(getAccessToken()));
			if (!StringUtils.isEmpty(getRefreshToken())) {
				setRefreshToken(encryptor.decrypt(getRefreshToken()));
			}
			if (!StringUtils.isEmpty(getSecret())) {
				setSecret(encryptor.decrypt(getSecret()));
			}
			transientlyEncrypted = false;
		}
		return this;
	}

	/**
	 * Encrypts tokens using supplied encryptor. Sets the 'encrypted' flag to true.
	 * @param encryptor
	 */
	public UserConnection encryptTokens(TextEncryptor encryptor) {
		if (!transientlyEncrypted) {
			setAccessToken(encryptor.encrypt(getAccessToken()));
			if (!StringUtils.isEmpty(getRefreshToken())) {
				setRefreshToken(encryptor.encrypt(getRefreshToken()));
			}
			if (!StringUtils.isEmpty(getSecret())) {
				setSecret(encryptor.encrypt(getSecret()));
			}
			setEncrypted(true);
		}
		return this;
	}

}
