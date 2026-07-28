package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Organisation implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -555808388668409659L;
	/**
	 * Maximum length of an indexable varchar field that can be encoded in UTF-8, in case DB uses
	 *  4 bytes per character; MySQL 5.6 uses 767 max key length = 191 max chars * 4 bytes/char.<br>
	 *  See RSPAC-932
	 */
	public static final int MAX_INDEXABLE_UTF_LENGTH = 191;//RSPAC-932 

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = MAX_INDEXABLE_UTF_LENGTH)
	@NaturalId
	private String title;

	private boolean approved;

	/**
	 * For hibernate
	 */
	public Organisation() {
	}

	public Organisation(String title, boolean approved) {
		this.title = title;
		this.approved = approved;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isApproved() {
		return approved;
	}

	public void setApproved(boolean approved) {
		this.approved = approved;
	}

	@Override
	public String toString() {
		return "Organisation [id=" + id + ", title=" + title + ", approved=" + approved + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		final int n1 = 1231, n2 = 1237;
		int result = 1;
		result = prime * result + (approved ? n1 : n2);
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((title == null) ? 0 : title.hashCode());
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
		Organisation other = (Organisation) obj;
		if (approved != other.approved) {
			return false;
		}
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (title == null) {
			if (other.title != null) {
				return false;
			}
		} else if (!title.equals(other.title)) {
			return false;
		}
		return true;
	}

}
