package com.researchspace.model.apps;

import static org.apache.commons.collections.SetUtils.isEqualSet;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;

import com.researchspace.model.PropertyDescriptor;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

@Entity
@Data
@EqualsAndHashCode(of="naturalId")
public class AppConfigElementSet implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6246887101758982270L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(value = AccessLevel.PACKAGE)
	private Long id;

	/** Stable natural key for equals/hashCode; avoids HashSet corruption when id is assigned after insertion. */
	@Setter(value = AccessLevel.NONE)
	@Column(unique = true, nullable = false, updatable = false, length = 36)
	private String naturalId = UUID.randomUUID().toString();

	@OneToMany(mappedBy = "appConfigElementSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch=FetchType.EAGER)
	private Set<AppConfigElement> configElements = new HashSet<>();

	@ManyToOne
	private UserAppConfig userAppConfig;


	/**
	 * Adds an {@link AppConfigElement} and forms bidirectional association
	 * @param toAdd
	 * @return <code>true</code> if added
	 */
	public boolean addConfigElement (AppConfigElement toAdd){
		toAdd.setAppConfigElementSet(this);
		return this.configElements.add(toAdd);
	}
	
	@Transient
	public Set<PropertyDescriptor> getProperties (){
		return configElements.stream().map((el)->el.getAppConfigElementDescriptor().getDescriptor())
				.collect(Collectors.toSet());
	}
	
	/**
	 * Boolean test as to whether the properties of this set are the same number and type as the other
	 * @param other
	 * @return 
	 */
	public boolean propertiesMatch (AppConfigElementSet other) {
		Set<PropertyDescriptor> theseProperties = getProperties();
		Set<PropertyDescriptor> otherProperties = other.getProperties();
		return isEqualSet(theseProperties, otherProperties) && isEqualSet(otherProperties, theseProperties);
		
	}

	/**
	 * Replaces property values of this set  with values from  <code>other</code> based on property name
	 * @param other
	 */
	public void merge(AppConfigElementSet other) {
		for (AppConfigElement thisEl: getConfigElements()) {
			for (AppConfigElement otherEl: other.getConfigElements()) {
				if (thisEl.getAppConfigElementDescriptor().equals(otherEl.getAppConfigElementDescriptor())) {
					thisEl.setValue(otherEl.getValue());
				}
			}
		}
		
	}
	/**
	 * Gets an {@link AppConfigElement} from this set with the given property name
	 * @param name
	 * @return the {@link AppConfigElement} or <code>null</code> if not found
	 */
	public AppConfigElement findElementByPropertyName (String  name) {
		return configElements.stream().filter(
				(el)->el.getAppConfigElementDescriptor().getDescriptor().getName().equals(name)).findFirst()
				.orElse(null);
	}
	
	@Transient
	public App getApp (){
		return getUserAppConfig().getApp();
	}



}
