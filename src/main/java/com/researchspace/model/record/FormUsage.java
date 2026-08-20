package com.researchspace.model.record;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;

import com.researchspace.model.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/***
 * Table class for recording the usage of forms on StructuredDOcument creation.
 * <br/>
 * Equality is based on Form id, User and time of creation, based on the
 * assumption that 2 documents won't be created simultaneously by the same
 * person.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormUsage implements Serializable {
	private String formStableID;

	public String getFormStableID() {
		return formStableID;
	}

	void setFormStableID(String formStableID) {
		this.formStableID = formStableID;
	}

	@Override
	public String toString() {
		return "FormUsage [user=" + user.getFullName() + ", formID=" + formStableID + " lastUsedTimeInMillis="
				+ lastUsedTimeInMillis + "]";
	}

	/**
	 * Clients should use this constructor with non-null arguments. This also
	 * sets the time of use of the given template.
	 */
	public FormUsage(User user, RSForm form) {
		this.user = user;
		this.lastUsedTimeInMillis = new Date().getTime();
		this.formStableID = form.getStableID();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lastUsedTimeInMillis == null) ? 0 : lastUsedTimeInMillis.hashCode());
		result = prime * result + ((formStableID == null) ? 0 : formStableID.hashCode());
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
		FormUsage other = (FormUsage) obj;
		if (lastUsedTimeInMillis == null) {
			if (other.lastUsedTimeInMillis != null) {
				return false;
			}
		} else if (!lastUsedTimeInMillis.equals(other.lastUsedTimeInMillis)) {
			return false;
		}
		if (formStableID == null) {
			if (other.formStableID != null) {
				return false;
			}
		} else if (!formStableID.equals(other.formStableID)) {
			return false;
		}
		return true;
	}

	private static final long serialVersionUID = -6506846224833814269L;

	private User user;

	private Long id;

	private Long lastUsedTimeInMillis;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "form_usage_gen")
	@TableGenerator(name = "form_usage_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	/*
	 * For hibernate
	 */
	public Long getLastUsedTimeInMillis() {
		return lastUsedTimeInMillis;
	}

	/*
	 * For hibernate ,should not be set by client
	 */
	void setLastUsedTimeInMillis(Long lastUsedTimeInMillis) {
		this.lastUsedTimeInMillis = lastUsedTimeInMillis;
	}

}
