package com.researchspace.core.util;

import static java.util.function.UnaryOperator.identity;

import java.io.IOException;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Some wrapper methods around Jackson functions
 */
public class JacksonUtil {
	
	private static Logger log = LoggerFactory.getLogger(JacksonUtil.class);
	
	/**
	 * Converts object to Json string using Jackson's object mapper without throwing a checked exception
	 * @param object
	 * @return A Json string or empty string of a {@link JsonProcessingException} was thrown internally.
	 */
	public static String toJson (Object object) {
		
		try {
			return new ObjectMapper().writeValueAsString(object);
		} catch (JsonProcessingException e) {
			log.warn("couldn't convert object to json: " + object, e);
			return "";
		}
	}
	
	/**
	 * Converts object to Json string using Jackson's object mapper without throwing a checked exception.
	 * Skips empty (null or "absent") fields.
	 *
	 * @param object
	 * @return A Json string or empty string of a {@link JsonProcessingException} was thrown internally.
	 */
	public static String toJsonWithoutEmptyFields(Object object) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.setSerializationInclusion(Include.NON_EMPTY);
			return mapper.writeValueAsString(object);
		} catch (JsonProcessingException e) {
			log.warn("couldn't convert object to json: " + object, e);
			return "";
		}
	}
	
	/**
	 * Converts Json to an object using Jackson's ObjectMapper without throwing a checked exception
	 * @param json
	 * @param clazz
	 * @param configurer an optional configurer to set and configure the ObjectMapper.
	 * @return An object or null if an internal exception was thrown from Jackson.
	 */
	public static <T> T fromJson (String json, Class<T>clazz, UnaryOperator<ObjectMapper> configurer) {
		try {
			ObjectMapper oMapper = new ObjectMapper();
			oMapper = configurer.apply(oMapper);
			return oMapper.readValue(json, clazz);
		} catch (IOException e) {
			return null;
		}
	}
	
	/**
	 * Converts Json to an object using Jackson's ObjectMapper without throwing a checked exception
	 * @param json
	 * @param clazz
	 * @return An optional, will be empty if Jackson couldn't parse.
	 */
	public static <T> T fromJson (String json, Class<T>clazz) {
		return fromJson(json, clazz, identity());
	}
	
	
	/**
	 * Converts Json to an object using Jackson's ObjectMapper without throwing a checked exception
	 * @param json
	 * @param clazz
	 * @return An optional, will be empty if Jackson couldn't parse.
	 */
	public static <T> Optional<T> fromJsonOpt (String json, Class<T>clazz) {
		return Optional.ofNullable(fromJson(json, clazz));
	}
	
	/**
	 * Converts Json to an object using Jackson's ObjectMapper without throwing a checked exception
	 * @param json
	 * @param clazz
	 * @param configurer an optional configurer to set and configure the ObjectMapper.
	 * @return An optional, will be empty if Jackson couldn't parse.
	 */
	public static <T> Optional<T> fromJsonOpt (String json, Class<T>clazz, UnaryOperator<ObjectMapper> configurer) {
		return Optional.ofNullable(fromJson(json, clazz, configurer));
	}
	/**
	 * Creates a new empty Jackson {@link ObjectNode}
	 */
	public static ObjectNode createObjectNode() {
		return new ObjectMapper().createObjectNode();
	}

}
