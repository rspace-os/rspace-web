package com.researchspace.core.util;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import org.apache.commons.beanutils.BeanUtils;

public class ObjectToStringPropertyTransformer<T> implements Function<T, String> {
	private String fieldName;

	/**
	 * The field name of a String property of the object to be transformed
	 * 
	 * @param fieldName
	 */
	public ObjectToStringPropertyTransformer(String fieldName) {
		super();
		this.fieldName = fieldName;
	}

	/**
	 * 
	 */
	@Override
	public String apply(T toTransform) {
		try {
			return BeanUtils.getProperty(toTransform, fieldName);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new IllegalArgumentException(String.format("[%s] not a property of [%s]", fieldName, toTransform));
		}
	}
}
