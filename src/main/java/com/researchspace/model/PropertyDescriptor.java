package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.preference.SettingsType;

/**
 * Generic Property Descriptor class. This is added to via database so is
 * immutable once in memory.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PropertyDescriptor implements Comparable<PropertyDescriptor>, Serializable {

	private static final long serialVersionUID = -5517021732904947064L;

	private String name;
	private SettingsType type;

	private String defaultValue;

	private Long id;

	public PropertyDescriptor(String name, SettingsType type, String defaultValue) {
		this.name = name;
		this.type = type;
		this.defaultValue = defaultValue;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	@Column(unique = true, length = 255, nullable = false)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public SettingsType getType() {
		return type;
	}

	public void setType(SettingsType type) {
		this.type = type;
	}

	@Column(length = 255, nullable = false)
	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@Override
	public int compareTo(PropertyDescriptor o) {
		return name.compareTo(o.name);
	}

	@Override
	public String toString() {
		return "PropertyDescriptor [name=" + name + ", type=" + type + ", defaultValue=" + defaultValue + ", id=" + id
				+ "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((defaultValue == null) ? 0 : defaultValue.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PropertyDescriptor other = (PropertyDescriptor) obj;
		if (defaultValue == null) {
			if (other.defaultValue != null)
				return false;
		} else if (!defaultValue.equals(other.defaultValue))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}
