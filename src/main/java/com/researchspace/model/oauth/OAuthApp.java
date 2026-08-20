package com.researchspace.model.oauth;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.Validate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.User;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Entity
@EqualsAndHashCode(of = {"clientId"})
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@NoArgsConstructor
public class OAuthApp implements Serializable {
	private static final int SECRET_LENGTH = 64; // SHA-256 hash in hex

	public OAuthApp(
			@NonNull User user,
			@NonNull String name,
			@NonNull String clientId,
			@NonNull String hashedClientSecret
	) {
		Validate.isTrue(!isBlank(name), "name must be non-empty");
		Validate.isTrue(!isBlank(clientId), "clientId must be non-empty");

		this.name = name;
		this.user = user;
		this.clientId = clientId;
		this.hashedClientSecret = hashedClientSecret;
	}

	@Id
	@Getter
	@Setter(AccessLevel.PRIVATE)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Getter
	@Setter
	@Size(min = 1, max = 100)
	@Column(nullable = false, length = 100)
	private String name;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@Size(min = 1, max = 100)
	@Column(unique = true, nullable = false, length = 100)
	private String clientId;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@Size(min = SECRET_LENGTH, max = SECRET_LENGTH)
	@Column(name = "clientSecret", unique = true, nullable = false, length = 100)
	private String hashedClientSecret;

	@ManyToOne(optional = false)
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private User user;
}
