package com.researchspace.model.field;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;

@Entity
@DiscriminatorValue(FieldType.DATE_TYPE)
@Audited
public class DateFieldForm extends FieldForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6207688320247826521L;
	private String format = "yyyy-MM-dd";
	private long defaultDate;

	private long minValue;
	private long maxValue;

	public DateFieldForm(String name) {
		super(name);
		setType(FieldType.DATE);
	}

	public DateFieldForm() {
		setType(FieldType.DATE);
	}

	@NotBlank(message = "date format {errors.required.field}")
	public String getFormat() {
		return format;
	}

	@Override
	public String toString() {
		return "DateFieldTemplate [format=" + format + ", defaultDate=" + defaultDate + ", minValue=" + minValue
				+ ", maxValue=" + maxValue + "]";
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public long getDefaultDate() {
		return defaultDate;
	}

	public void setDefaultDate(long defaultDate) {
		this.defaultDate = defaultDate;
	}

	public long getMinValue() {
		return minValue;
	}

	public void setMinValue(long minValue) {
		this.minValue = minValue;
	}

	@Column(name = "max_value")
	public long getMaxValue() {
		return maxValue;
	}

	public void setMaxValue(long maxValue) {
		this.maxValue = maxValue;
	}

	@Override
	public DateFieldForm shallowCopy() {
		DateFieldForm dft = new DateFieldForm();
		copyPropertiesToCopy(dft);
		dft.defaultDate = defaultDate;
		dft.format = format;
		dft.maxValue = maxValue;
		dft.minValue = minValue;
		return dft;

	}

	public DateField _createNewFieldFromForm() {
		DateField cf = new DateField(this);
		return cf;
	}

	@Transient
	public String getDefaultDateAsString() {
		String result = "";
		if (this.getDefaultDate() != 0) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getFormat());
			Date date = new Date(this.getDefaultDate());
			result = simpleDateFormat.format(date);
		}

		return result;
	}

	@Transient
	public String getMaxDateAsString() {
		String result = "";
		if (getMaxValue() != 0) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getFormat());
			Date date = new Date(getMaxValue());
			result = simpleDateFormat.format(date);
		}
		return result;
	}

	@Transient
	public String getMinDateAsString() {
		String result = "";
		if (getMinValue() != 0) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getFormat());
			Date date = new Date(getMinValue());
			result = simpleDateFormat.format(date);
		}
		return result;
	}

	@Transient
	public String getSummary() {
		String minValue = StringUtils.isEmpty(getMinDateAsString()) ? "Unspecified" : getMinDateAsString();
		String maxValue = StringUtils.isEmpty(getMaxDateAsString()) ? "Unspecified" : getMaxDateAsString();
		String defValue = StringUtils.isEmpty(getDefaultDateAsString()) ? "Unspecified" : getDefaultDateAsString();
		StringBuffer sb = new StringBuffer();
		sb.append("Type: [" + getType() + "], ").append("Min date: [" + minValue + "], ")
				.append("Max date: [" + maxValue + "], ").append("Default date: [" + defValue + "]");
		return sb.toString();
	}

	@Override
	public ErrorList validate(String data) {
		ErrorList el = new ErrorList();
		if (StringUtils.isEmpty(data)) {
			return el;
		}
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getFormat());
		Date inputDate = null;
		try {
			inputDate = simpleDateFormat.parse(data);
		} catch (ParseException e) {
			el.addErrorMsg(String.format("Invalid date format [%s]",data));
			return el;
		}
		Date b4 = null;
		Date after = null;
		if (getMinValue() != 0) {
			b4 = new Date(getMinValue());
		}
		if (getMaxValue() != 0) {
			after = new Date(getMaxValue());
		}
		if (b4 != null && inputDate.before(b4)) {
			el.addErrorMsg(String.format("Input date [%s] before minimum [%s]", data, getMinDateAsString()));
		}
		if (after != null && inputDate.after(after)) {
			el.addErrorMsg(String.format("Input date [%s] after maximum [%s]", data, getMaxDateAsString()));
		}
		return el;
	}

	@Override
	@Transient
	public String getDefault() {
		return getDefaultDateAsString();
	}


}
