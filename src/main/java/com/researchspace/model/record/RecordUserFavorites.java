package com.researchspace.model.record;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.UniqueConstraint;

import com.researchspace.model.User;

/**
 * Keeps track of favorite records within a specific user.
 */
@Entity
@Table(name = "RecordUserFavorites", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "record_id", "user_id" }) })
public class RecordUserFavorites implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5187044429719979002L;
	private Long id;
	private User user;
	private BaseRecord record;

	@Id()
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "record_user_favorites_gen")
	@TableGenerator(name = "record_user_favorites_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public RecordUserFavorites(User user, BaseRecord record) {
		super();
		this.user = user;
		this.record = record;
	}

	/**
	 * For hibernate
	 */
	public RecordUserFavorites() {
	}

	@ManyToOne
	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	@ManyToOne
	public BaseRecord getRecord() {
		return record;
	}

	public void setRecord(BaseRecord record) {
		this.record = record;
	}
}
