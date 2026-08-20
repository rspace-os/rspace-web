package com.researchspace.core.util.jsonserialisers;

import java.io.IOException;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.researchspace.core.util.DateUtil;
/**
 * Serialises a long millis-since-epoch into UTC timezone formatted IS0-8601 string
 */
public class ISO8601DateTimeSerialiser extends StdSerializer<Long> {
	
    static final String ISO8601_FORMAT_DATE = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	public ISO8601DateTimeSerialiser() {
		this(null);
	}

	public ISO8601DateTimeSerialiser(Class<Long> t) {
		super(t);
	}

	@Override
	public void serialize(Long value, JsonGenerator gen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {	
		 String output = getDateTimeString(value);
		 gen.writeString(output);
	}

	 String getDateTimeString(Long value) {
		String output = value != null ? DateUtil.convertDateToISOFormat(value, TimeZone.getTimeZone("UTC")) : "";
		return output;
	}
}
