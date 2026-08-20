package com.researchspace.model.units;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Component class to be included by classes implementing {@link Quantifiable}.
 * Stores quantity information with numerical precision up to 3 digital places.
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class QuantityInfo implements Quantifiable, Serializable {
	
	/**
	 * Factory constructor
	 * @param value
	 * @param unit
	 * @return A {@link QuantityInfo}
	 */
	public static QuantityInfo of(BigDecimal value, RSUnitDef unit) {
		return new QuantityInfo(value, unit.getId());
	}
	
	public static QuantityInfo of(int value, RSUnitDef unit) {
		return new QuantityInfo(BigDecimal.valueOf(value), unit.getId());
	}

	public static QuantityInfo of(Quantifiable quantifiable) {
		return new QuantityInfo(quantifiable.getNumericValue(), quantifiable.getUnitId());
	}

	/**
	 * Tries parsing provided string to quantity info.
	 * @see QuantityUtils#parseQuantityInfo(String)
	 */
	public static QuantityInfo of(String quantityString) {
		return QuantityUtils.parseQuantityInfo(quantityString);
	}


	private static final long serialVersionUID = -7919940280639374006L;

	private BigDecimal numericValue;
	
	private Integer unitId;
 
	public QuantityInfo(BigDecimal numericValue, Integer unitId) {
		setNumericValue(numericValue); 
		setUnitId(unitId);
	}
	
	@Column(precision =  19, scale = 3)
	public BigDecimal getNumericValue() {
		return numericValue;
	}

	/**
	 * Rounds at 3dp and strips trailing zeros.
	 *
	 * @param numericValue
	 */
	public void setNumericValue(BigDecimal numericValue) {
		if (numericValue == null) {
			this.numericValue = null;
			return;
		}
		this.numericValue = numericValue.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
	}

	@Transient
	public String getNumericValuePlainString() {
		return numericValue == null ? null : numericValue.toPlainString(); 
	}

	/**
	 * Makes a copy. 
	 * <code>copy.equals(original) == true</code>
	 * @return
	 */
	public QuantityInfo copy() {
		return new QuantityInfo(numericValue, unitId);
	}

	/**
	 * @return a new quantity that is a negative of current one (-this).
	 */
	public QuantityInfo negate() {
		return new QuantityInfo(numericValue.negate(), unitId);
	}

	public String toPlainString() {
		return String.format("%s %s", numericValue.toPlainString(), RSUnitDef.getUnitById(unitId).getLabel()); 
	}
	
	/**
	 * Convenience 0 quantity for a given unit.
	 * @param def
	 * @return
	 */
	public static QuantityInfo zero(RSUnitDef def) {
		return QuantityInfo.of(BigDecimal.ZERO, def);
	}

}
