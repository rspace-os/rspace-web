package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.TableGenerator;

/**
 * Stores meta-data about version/state of application
 */
@Entity
public class RSMetaData implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 261971692772281729L;

	private Version version;

	private boolean isInitialized;

	private Long id;

	public RSMetaData() {
		this.version = new Version(0L);
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "rs_meta_data_gen")
	@TableGenerator(name = "rs_meta_data_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@Embedded
	public Version getDBVersion() {
		return version;
	}

	public void setDBVersion(Version internalVersion) {
		this.version = internalVersion;
	}

	public boolean isInitialized() {
		return isInitialized;
	}

	public void setInitialized(boolean isInitialized) {
		this.isInitialized = isInitialized;
	}

}
