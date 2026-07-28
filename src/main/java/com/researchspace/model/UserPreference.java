package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;

import com.researchspace.model.preference.Preference;
import com.researchspace.model.preference.SettingsType;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Persists user preferences.
 */
@Entity
@XmlType
@XmlAccessorType(XmlAccessType.NONE)
@NoArgsConstructor
@EqualsAndHashCode(of = { "preference", "user"})
public class UserPreference implements Serializable {

	private static final long serialVersionUID = -575396996158819435L;

	@Setter
	private Long id;

	@Setter
	private Preference preference;

	@Setter
	private User user;

	private String value;

	/**
	 * Convenience constructor. Does fail-fast checking of <code>value</code>
	 * which should be:
	 * <ul>
	 * <li>"true" or "false" if boolean
	 * <li>Parseable into a Double if number
	 * <li>less than 65535 characters length if Text.
	 * </ul>
	 * 
	 * @param preference
	 * @param user
	 * @param value
	 *            - should be parsable into preference type. Any String should
	 *            be &lt; 65535 characters.
	 * @throws if
	 *             <code>value</code> is incompatible with its preference type.
	 *             This ensures that no invalid data is stored in the database.
	 */
	public UserPreference(Preference preference, User user, String value) {
		checkValue(preference, value);
		this.preference = preference;
		this.user = user;
		this.value = value;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "user_preference_gen")
	@TableGenerator(name = "user_preference_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	@XmlElement
	public Preference getPreference() {
		return preference;
	}

	@ManyToOne
	@XmlIDREF
	@XmlElement
	public User getUser() {
		return user;
	}

	@XmlElement
	@Column(length = 65535)
	public String getValue() {
		if (value == null && preference != null) {
			value = preference.getDefaultValue();
		}
		return value;
	}

	/**
	 * @throws IllegalArgumentException
	 *             if <code>value</code> is incompatible with its preference type.
	 */
	public void setValue(String value) {
		if (preference != null && value != null) {
			checkValue(preference, value);
		}
		this.value = value;
	}

	private void checkValue(Preference preference, String value) {
		SettingsType.validate(preference.getPrefType(), value);

		// for ENUM preference type run the validator to confirm that string value is correct
		if (preference.getPrefType().equals(SettingsType.ENUM)) {
			String validationMsg = preference.getInvalidErrorMessageForValue(value);
			if (validationMsg != null) {
				throw new IllegalArgumentException(validationMsg);
			}
		}
	}

	@Override
	public String toString() {
		return "UserPreference [preference=" + preference + ", user=" + (user == null ? user : user.getUniqueName())
				+ ", value=" + value + "]";
	}

	@Transient
	public boolean isBooleanType() {
		return SettingsType.BOOLEAN.equals(this.preference.getPrefType());
	}

	/**
	 * Convenience getter for a boolean-type preference value
	 * 
	 * @return
	 * @throws if
	 *             is not a {@link Boolean} type-preference
	 */
	@Transient
	public boolean getValueAsBoolean() {
		if (!isBooleanType()) {
			throw new IllegalStateException(" Not a boolean type");
		}
		return Boolean.valueOf(getValue());
	}

	/**
	 * Convenience getter for a numeric-type preference value
	 * 
	 * @return
	 * @throws IllegalStateException
	 *             if is not a {@link Boolean} type-preference
	 */
	@Transient
	public Number getValueAsNumber() {
		if (!isNumeric()) {
			throw new IllegalStateException(" Not a numeric type");
		}
		return Double.valueOf(getValue());
	}

	@Transient
	public boolean isNumeric() {
		return SettingsType.NUMBER.equals(this.preference.getPrefType());
	}

}
