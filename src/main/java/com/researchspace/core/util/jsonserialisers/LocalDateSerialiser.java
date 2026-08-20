package com.researchspace.core.util.jsonserialisers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Converts LocalDate to UTC Date format string yyyy-MM-dd
 */
public class LocalDateSerialiser 
  extends StdSerializer<LocalDate> {
 
    /**
	 * 
	 */
	private static final long serialVersionUID = -8697511358648184418L;
	private static DateTimeFormatter formatter = 
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
 
    public LocalDateSerialiser() {
        this(null);
    }
 
    public LocalDateSerialiser(Class<LocalDate> t) {
        super(t);
    }
    
    @Override
    public void serialize(
    		LocalDate value,
      JsonGenerator gen,
      SerializerProvider arg2)
      throws IOException, JsonProcessingException {
        gen.writeString(formatter.format(value));
    }
}
