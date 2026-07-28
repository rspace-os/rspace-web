package com.researchspace.model.apps;

import java.io.Serializable;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppConfigElement implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7911460363789518663L;

	@Override
	public String toString() {
		return "AppConfigElement [id=" + id + ", value=" + value + ", appConfigElementDescriptor="
				+ appConfigElementDescriptor + "]";
	}

	private Long id;

	private String value;

	private AppConfigElementDescriptor appConfigElementDescriptor;

	private AppConfigElementSet appConfigElementSet;
	
	public AppConfigElement (AppConfigElementDescriptor descriptor, String value) {
		this(descriptor);
		this.value = value;
	}
	
	public AppConfigElement (AppConfigElementDescriptor descriptor) {
		this.appConfigElementDescriptor = descriptor;
	}
	 
	@ManyToOne
	public AppConfigElementSet getAppConfigElementSet() {
		return appConfigElementSet;
	}

	public void setAppConfigElementSet(AppConfigElementSet appConfigElementSet) {
		this.appConfigElementSet = appConfigElementSet;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	public String getValue() {
		return value;
	}

	/**
	 * Sets value if validated by descriptor, which gives weak type checking for numbers and booleans
	 * @param value
	 */
	public void setValue(String value) {
		appConfigElementDescriptor.validate(value);
		this.value = value;
	}

	@ManyToOne
	public AppConfigElementDescriptor getAppConfigElementDescriptor() {
		return appConfigElementDescriptor;
	}

	void setAppConfigElementDescriptor(AppConfigElementDescriptor appConfigElementDescriptor) {
		this.appConfigElementDescriptor = appConfigElementDescriptor;
	}
}
