package com.researchspace.model.system;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.preference.SettingsType;
/**
 * Stores information about RSpace configuration that is configurable in RSpace webapp itself.<br>
 * Implements {@link Comparable}; native ordering is by name,
 * which is compatible with equals/hashcode implementation
 *
 * See RSPAC-861.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class SystemProperty implements Comparable<SystemProperty>, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -271105525129689158L;
	private SystemProperty dependent;
	private Long id;

	private PropertyDescriptor descriptor;

	public SystemProperty(PropertyDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}
	 void setId(Long id) {
		this.id = id;
	}
	
	@ManyToOne()
	public PropertyDescriptor getDescriptor() {
		return descriptor;
	}
	 @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((descriptor == null) ? 0 : descriptor.hashCode());
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
		SystemProperty other = (SystemProperty) obj;
		if (descriptor == null) {
			if (other.descriptor != null)
				return false;
		} else if (!descriptor.equals(other.descriptor))
			return false;
		return true;
	}
	void setDescriptor(PropertyDescriptor descriptor) {
		this.descriptor = descriptor;
	}
	@ManyToOne
	public SystemProperty getDependent() {
		return dependent;
	}
	/**
	 * Cycles aren't allowed.
	 * @param dependent, can be <code>null</code> to unset a property
	 */
	public void setDependent(SystemProperty dependent) {
		SystemProperty ancestor = null;
		SystemProperty curr = dependent;
		if (dependent == null){
			this.dependent = null;
			return;
		}
		while ((ancestor = dependent.getDependent()) != null) {
			if (ancestor.equals(curr)){
				return;
			}
			curr = ancestor;			
		}
		this.dependent = dependent;
	}
	
	
	@Override
	public int compareTo(SystemProperty o) {
		return descriptor.compareTo(o.descriptor);
	}
	
	@Transient
	public String getName(){
		return descriptor.getName();
	}
	
	@Transient
	public String getDefaultValue(){
		return descriptor.getDefaultValue();
	}
	
	@Transient
	public SettingsType getType(){
		return descriptor.getType();
	}

}
