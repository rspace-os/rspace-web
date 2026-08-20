package com.researchspace.core.util.jsonserialisers;

import java.io.IOException;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.researchspace.core.util.DateUtil;
/**
 * Serialises a long millis-since-epoch to simple ISO date format with no time.
 */
public class ISO8601DateSerialiser extends StdSerializer<Long> {
	
    static final String ISO8601_FORMAT_DATE = "yyyy-MM-dd";
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	public ISO8601DateSerialiser() {
		this(null);
	}

	public ISO8601DateSerialiser(Class<Long> t) {
		super(t);
	}

	@Override
	public void serialize(Long value, JsonGenerator gen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {	
		 String dateOnly = getDateString(value);
		 gen.writeString(dateOnly);
	}

	 String getDateString(Long value) {
		String iso8601 = DateUtil.convertDateToISOFormat(value, TimeZone.getTimeZone("UTC"));
		String dateOnly = iso8601.substring(0, iso8601.indexOf("T"));
		return dateOnly;
	}
}
