package com.researchspace.model.permissions;

import java.io.Serializable;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;

/**
 * Name value pair property constraint. <br/>
 * For resolving against entities, the property names should be Javabean names
 * for accessor methods in the object, and will work for simple properties
 * (primitive values and strings).
 * <p/>
 * An exception is the property name 'template' whose value should be a template
 * name, and will be tested if the permission to be tested is for a structured
 * document.
 * <p/>
 * Property values can be wildcards or variable expressions: W wildcard implies
 * all values.<br/>
 * 
 * A property constraint value can also be a variable , e.g.,'${self}' which
 * refers to the current logged in subject's principal name.
 */
public class PropertyConstraint implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		return "PropertyConstraint [name=" + name + ", value=" + value + "]";
	}

	private String name, value;
	private ConstraintBasedPermission owner;

	ConstraintBasedPermission getOwner() {
		return owner;
	}

	void setOwner(ConstraintBasedPermission owner) {
		this.owner = owner;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	public String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return value;
	}

	void setValue(String value) {
		this.value = value;
	}

	public String getString() {
		StringBuffer sb = new StringBuffer();
		sb.append(ConstraintPermissionResolver.PROPERTY_PARAM_PREFIX).append(name).append("=").append(value);
		return sb.toString();
	}

	public boolean satisfies(PropertyConstraint pc) {
		if (isVariable() && variableName().equals("self")) {
			String selfUname = null;
			if (getOwner() != null && getOwner().getUser() != null) {
				selfUname = getOwner().getUser().getUsername();
			} else {
				selfUname = getPrincipalFromSecurityCtxt();
			}

			if (pc.getName().equals(this.getName()) && selfUname.equals(pc.getValue())) {
				return true;

			}
		}
		if (pc.getName().equals(this.getName())) {
			if (isSingleValue(pc)) {
				return true;
			} else {
				String[] values = getValueArray();
				// if we have multiple values e.g., name=a,b,c,d,e
				if (values.length > 1) {
					for (String value : values) {
						if (pc.getValue().equals(value.trim())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private String[] getValueArray() {
		return this.getValue().split(ConstraintPermissionResolver.LIST_SEPARATOR);
	}

	private boolean isSingleValue(PropertyConstraint pc) {
		return this.getValue().equals("*") || pc.getValue().equals(this.getValue());
	}

	/*
	 * package scoped to override in tests
	 */
	String getPrincipalFromSecurityCtxt() {
		return (String) SecurityUtils.getSubject().getPrincipal();
	}

	private Object variableName() {
		String val = getValue();
		int startindx = val.indexOf("{");
		int endIndx = val.indexOf("}");
		if (startindx != -1 && endIndx != -1 && startindx < endIndx) {
			return val.substring(startindx + 1, endIndx).trim();
		} else {
			return null;
		}
	}

	private boolean isVariable() {
		return getValue().trim().startsWith("${") && getValue().trim().endsWith("}");
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PropertyConstraint other = (PropertyConstraint) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	/**
	 * Value cannot contain '_' or '=' or ':'
	 * 
	 * @param name
	 * @param value
	 * @throws IllegalArgumentException
	 *             if value has invalid characters
	 */
	public PropertyConstraint(String name, String value) {
		super();
		if (value.indexOf('=') != -1 || value.indexOf('_') != -1
				|| value.indexOf(ConstraintPermissionResolver.PART_DELIMITER) != -1) {
			throw new IllegalArgumentException("Illegal character '=' or '_' in value");
		}
		this.name = name;
		this.value = value;
	}

	/**
	 * Removes a value. If this is a single value, and is removed, this method
	 * returns <code>true</code>, indicating that this constraint should be
	 * removed from the containing permission. Otherwise,if value is a list
	 * returns false and modifies the list in place.
	 * 
	 * @param valueToRemove
	 * @return
	 */
	public boolean removeValue(String valueToRemove) {
		if (this.value.equals(valueToRemove)) {
			return true;
		} else {
			String[] values = getValueArray();
			Object[] newValues = ArrayUtils.removeElement(values, valueToRemove);
			this.value = StringUtils.join(newValues, ConstraintPermissionResolver.LIST_SEPARATOR);
			return false;
		}
	}

}
