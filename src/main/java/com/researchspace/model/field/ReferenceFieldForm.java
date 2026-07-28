package com.researchspace.model.field;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;

/**
 * A Reference to an RSpace GlobalId
 * To be valid, the syntax must be valid GlobalId syntax and also the Prefix must be a known prefix
 * in the Enum {@link GlobalIdPrefix}
 *
 */
@Entity
@DiscriminatorValue(FieldType.REFERENCE_TYPE)
@Audited
public class ReferenceFieldForm extends FieldForm {
	private static List<String> validPrefixes ;
	static {
		validPrefixes =	Arrays.stream(GlobalIdPrefix.values()).map(GlobalIdPrefix::name)
		  .map(s->s.substring(0, 2)).collect(Collectors.toList());
	}

	public ReferenceFieldForm(String name) {
		super(name);
		setType(FieldType.REFERENCE);
	}

	public ReferenceFieldForm() {
		setType(FieldType.REFERENCE);
	}

	private static final long serialVersionUID = -1027880121852290768L;
	
	@Override
	public String toString() {
		return "SourceFieldForm "+ super.toString();
	}

	public ReferenceFieldForm shallowCopy() {
		ReferenceFieldForm nft = new ReferenceFieldForm();
		copyPropertiesToCopy(nft);		
		return nft;
	}

	public ReferenceField _createNewFieldFromForm() {
		return new ReferenceField(this);
	}

	@Transient
	public String getSummary() {
		return "SourceFieldForm";
	}

	/**
	 * Is valid if <code>data</code> is a String value of a {@link GlobalIdPrefix} enum
	 */
	@Override
	public ErrorList validate(String data) {
		ErrorList el =  new ErrorList();
		if (!StringUtils.isBlank(data)  ) {
			String [] values = data.trim().split(",");
			for (String val: values) {
				val = val.trim();
				if(!GlobalIdentifier.isValid(val)) {
					el.addErrorMsg(invalidDataMsg(val, data));
				}
				if (val.length() >=2) {
					if(!validPrefixes.contains(val.substring(0, 2))) {
						el.addErrorMsg(invalidPrefixMsg(val));
					}
				}
			}	
		}
		return el;
	}

	private String invalidDataMsg(String val,String data) {
		return String.format("Invalid data (%s) for Source Field [%s] - must be parsable into GlobalIds",val, data);
	}

	private String invalidPrefixMsg(String val) {
		return String.format("The prefix %s is not a globalIdPrefix. "
				+ "It must belong to GlobalIdPrefix enumset",
				(val.substring(0, 2)));
	}

	@Override
	@Transient
	public String getDefault() {
		return "";
	}


}
