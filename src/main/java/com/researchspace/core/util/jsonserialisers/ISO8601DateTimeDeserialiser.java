package com.researchspace.core.util.jsonserialisers;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.researchspace.core.util.DateUtil;
/**
 * Converts an ISO8601 date time into millis-since-epoch
 */
public class ISO8601DateTimeDeserialiser extends StdDeserializer<Long> {

	protected ISO8601DateTimeDeserialiser(Class<Long> vc) {
		super(vc);
		// TODO Auto-generated constructor stub
	}
	
	public ISO8601DateTimeDeserialiser() {
		this(null);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		if (p.getCurrentName().equals("created") 
				|| p.getCurrentName().equals("lastModified")
				|| p.getCurrentName().equals("lastLogin")
				|| p.getCurrentName().equals("deletedDate")
				|| p.getCurrentName().equals("lastMoveDate")) {
			return DateUtil.convertISO8601ToMillis(p.getValueAsString());
        }
		return null;
	}

}
