package com.researchspace.core.util;

import static com.researchspace.core.util.JacksonUtil.fromJson;
import static com.researchspace.core.util.JacksonUtil.fromJsonOpt;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

class JacksonUtilTest {
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	static class TestObject {
		
		private long id;
		private String name, empty;
		
	}

	@Test
	void testIgnoreEmptyFields() {
		TestObject testObject = new TestObject(1L,"any name","");
		String jsonString = JacksonUtil.toJson(testObject);
		assertTrue(jsonString.contains("empty"));
		// empty field ignored
		jsonString = JacksonUtil.toJsonWithoutEmptyFields(testObject);
		assertFalse(jsonString.contains("empty"));
		
	}
	
	@Test
	void testConfigurerEmptyFields() {
		
		String jsonString ="{\"id\":\"23\", \"name\":\"hello\", \"empty\":\"\", \"unknown\":\"somevalue\"}";
		TestObject testObject = fromJson(jsonString, TestObject.class);
		assertNull(testObject);
		
		UnaryOperator<ObjectMapper> operator = objMapper->objMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		testObject = fromJson(jsonString, TestObject.class, operator);
		assertNotNull(testObject);
		
		assertTrue(fromJsonOpt(jsonString, TestObject.class, operator).isPresent());
		assertNotNull(testObject);
		
	}

}
