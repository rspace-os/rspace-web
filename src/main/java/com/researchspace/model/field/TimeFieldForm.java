package com.researchspace.model.field;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

/**
 * Default time format is HH:mm
 */
@Entity
@DiscriminatorValue(FieldType.TIME_TYPE)
@Audited
public class TimeFieldForm extends FieldForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4192185343324510570L;
	private String timeFormat = "HH:mm";
	private long defaultTime;
	private long minTime;
	private long maxTime;

	public TimeFieldForm(String name) {
		super(name);
		setType(FieldType.TIME);
	}

	public TimeFieldForm() {
		setType(FieldType.TIME);
	}

	public String getTimeFormat() {
		return timeFormat;
	}

	public void setTimeFormat(String timeFormat) {
		this.timeFormat = timeFormat;
	}

	public long getDefaultTime() {
		return defaultTime;
	}

	public void setDefaultTime(long defaultTime) {
		this.defaultTime = defaultTime;
	}

	public long getMinTime() {
		return minTime;
	}

	public void setMinTime(long minTime) {
		this.minTime = minTime;
	}

	public long getMaxTime() {
		return maxTime;
	}

	public void setMaxTime(long maxTime) {
		this.maxTime = maxTime;
	}

	public TimeFieldForm shallowCopy() {
		TimeFieldForm nft = new TimeFieldForm();
		copyPropertiesToCopy(nft);
		nft.defaultTime = defaultTime;
		nft.maxTime = maxTime;
		nft.minTime = minTime;
		nft.timeFormat = timeFormat;
		return nft;
	}

	@Override
	public String toString() {
		return "TimeFieldTemplate [timeFormat=" + timeFormat + ", defaultTime=" + defaultTime + ", minTime=" + minTime
				+ ", maxTime=" + maxTime + "]";
	}

	public TimeField _createNewFieldFromForm() {
		TimeField cf = new TimeField(this);
		return cf;
	}

	@Transient
	public String getDefaultTimeAsString() {
		String result = "";
		if (getDefaultTime() != 0) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getTimeFormat());
			Date date = new Date(getDefaultTime());
			result = simpleDateFormat.format(date);
		}
		return result;
	}

	@Transient
	public String getmaxTimeAsString() {
		String result = "";
		if (getMaxTime() != 0) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getTimeFormat());
			Date date = new Date(getMaxTime());
			result = simpleDateFormat.format(date);
		}
		return result;
	}

	@Transient
	public int getmaxHour() throws ParseException {
		int result = -1;
		if (getMaxTime() != 0) {
			Date date = new Date(getMaxTime());
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			result = cal.get(Calendar.HOUR_OF_DAY);
		}
		return result;
	}

	@Transient
	public int getmaxMinutes() throws ParseException {
		int result = -1;
		if (getMaxTime() != 0) {
			Date date = new Date(getMaxTime());
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			result = cal.get(Calendar.MINUTE);
		}
		return result;
	}

	@Transient
	public int getminHour() throws ParseException {
		int result = -1;
		if (getMinTime() != 0) {
			Date date = new Date(getMinTime());
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			result = cal.get(Calendar.HOUR_OF_DAY);
		}
		return result;
	}

	@Transient
	public int getminMinutes() throws ParseException {
		int result = -1;
		if (getMinTime() != 0) {
			Date date = new Date(getMinTime());
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			result = cal.get(Calendar.MINUTE);
		}
		return result;
	}

	@Transient
	public String getMinTimeAsString() {
		String result = "";
		if (getMinTime() != 0) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getTimeFormat());
			Date date = new Date(getMinTime());
			result = simpleDateFormat.format(date);
		}
		return result;
	}

	@Transient
	public String getSummary() {
		String minValue = StringUtils.isEmpty(getMinTimeAsString()) ? "Unspecified" : getMinTimeAsString();
		String maxValue = StringUtils.isEmpty(getmaxTimeAsString()) ? "Unspecified" : getmaxTimeAsString();
		String defValue = StringUtils.isEmpty(getDefaultTimeAsString()) ? "Unspecified" : getDefaultTimeAsString();
		StringBuffer sb = new StringBuffer();
		sb.append("Type: [" + getType() + "], ").append("Min date: [" + minValue + "], ")
				.append("Max date: [" + maxValue + "], ").append("Default date: [" + defValue + "]");
		return sb.toString();
	}

	@Override
	public ErrorList validate(String data) {
		return new ErrorList();
	}

	@Override
	@Transient
	public String getDefault() {
		return getDefaultTimeAsString();
	}

}
