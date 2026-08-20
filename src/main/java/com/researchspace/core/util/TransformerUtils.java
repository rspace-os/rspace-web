package com.researchspace.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for map()-like methods
 */
public class TransformerUtils {

	/**
	 * Converts a list of one type to another based on supplied function.
	 * 
	 * @param toTransform
	 * @param transformer
	 * @return A new list of transformed objects
	 */
	public static <U, T> List<U> transform(Collection<? extends T> toTransform, Transformer<U, T> transformer) {
		List<U> rc = new ArrayList<>();
		for (T t : toTransform) {
			rc.add(transformer.transform(t));
		}
		return rc;
	}

	/**
	 * Convenience mechanism to transform a list of objects to list of a
	 * property of that object.
	 * 
	 * @param toTransform
	 * @param propertyName
	 * @return
	 */
	public static <T> List<String> transformToString(Collection<? extends T> toTransform, String propertyName) {
		return toTransform.stream().map(new ObjectToStringPropertyTransformer<T>(propertyName))
				.collect(Collectors.toList());
	}

	/**
	 * Utility function to create a set containing objects passed in as varargs
	 * argument
	 * 
	 * @param objects
	 *            non-null objects
	 * @return A single-element Set.
	 */
	public static <T> Set<T> toSet(T... objects) {
		Set<T> rc = new HashSet<>();
		if (objects == null) {
			return rc;
		}
		for (T obj : objects) {
			rc.add(obj);
		}
		return rc;
	}

	/**
	 * Utility function to create a list containing objects passed in as varargs
	 * argument
	 *
	 * @param objects
	 *            non-null objects
	 * @return return possibly empty but non-null set of T objects.
	 */
	public static <T> List<T> toList(T... objects) {
		List<T> rc = new ArrayList<>();
		if (objects == null) {
			return rc;
		}
		for (T obj : objects) {
			rc.add(obj);
		}
		return rc;
	}

	/**
	 * Converts an array of Strings to an EnumSet of the specified types
	 * 
	 * @param arr
	 *            a non-empty array of string representations of the Enum
	 * @param type
	 *            The Enum type
	 * @return a non-empty EnumSet of enums of the given type.
	 */
	public static <T extends Enum<T>> EnumSet<T> toEnums(String[] arr, Class<T> type) {
		Set<T> result = new HashSet<>();
		for (int i = 0; i < arr.length; i++) {
			result.add(Enum.valueOf(type, arr[i]));
		}
		return EnumSet.copyOf(result);
	}

}
