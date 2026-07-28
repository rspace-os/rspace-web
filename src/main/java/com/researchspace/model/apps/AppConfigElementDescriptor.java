package com.researchspace.model.apps;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.preference.SettingsType;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Getter
@Setter
@EqualsAndHashCode(of={"descriptor"})
@ToString
@NoArgsConstructor
public class AppConfigElementDescriptor implements Serializable {

	private static final long serialVersionUID = 8510816500531846867L;
	
	@ManyToOne
	private PropertyDescriptor descriptor;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(value=AccessLevel.PACKAGE)
	private Long id;
	
	@ManyToOne
	private App app;

	public AppConfigElementDescriptor(PropertyDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	public void validate(String value) {
		if (getDescriptor() != null) { 
			SettingsType.validate(getDescriptor().getType(), value);
		}
	}

}
