package com.researchspace.core.util.jsonserialisers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.JacksonUtil;

import lombok.EqualsAndHashCode;
import lombok.Setter;

public class LocalDateSerialisationTest {
	
	LocalDateDeserialiser dateSerialiser;

	@BeforeEach
	public void setUp() throws Exception {
		dateSerialiser = new LocalDateDeserialiser();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}
	
	@EqualsAndHashCode
	@Setter
	 static  class LocalDateTestClass {
		@JsonSerialize(using = LocalDateSerialiser.class)
		@JsonDeserialize(using = LocalDateDeserialiser.class)
		private LocalDate expiryDate;
		private Object anotherProperty;
	}

	@Test
	public void getDateString() {
		LocalDateTestClass originalDate = new LocalDateTestClass();
		originalDate.expiryDate = DateTimeFormatter.ISO_DATE.parse("2020-01-29", LocalDate::from);
		String json = JacksonUtil.toJson(originalDate);
		assertTrue(json.contains("2020-01-29"));
		
		LocalDateTestClass deserialised = JacksonUtil.fromJson(json, LocalDateTestClass.class);
		assertEquals(originalDate, deserialised);		
		
	}
	
	@ParameterizedTest
	@ValueSource(strings = { "", " " })
	public void blankExpiryDateReturnsMinDate(String incomingDateSte) {
		String json = String.format("{\"expiryDate\":\"%s\"}", incomingDateSte);
		LocalDateTestClass deserialised = JacksonUtil.fromJson(json, LocalDateTestClass.class);
		assertEquals(LocalDateDeserialiser.NULL_DATE, deserialised.expiryDate);
	}

	@Test
	public void nullExpiryDateReturnsMinDate() {
		String json = "{\"expiryDate\" : null}";
		LocalDateTestClass deserialised = JacksonUtil.fromJson(json, LocalDateTestClass.class);
		assertEquals(LocalDateDeserialiser.NULL_DATE, deserialised.expiryDate);
	}
	
	@Test
	public void undefinedExpiryDateReturnsNull () {
		String json = "{\"anotherProperty\" : null}";
		LocalDateTestClass deserialised = JacksonUtil.fromJson(json, LocalDateTestClass.class);
		assertNull(deserialised.expiryDate);
	}
}
