package com.researchspace.core.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;

/**
 * Further collection utilities relevant to RSpace code
 */
public class RSCollectionUtils {
	/**
	 * Takes 2 lists, which may contain overlapping elements, and returns a
	 * merged list. <br/>
	 * Assumptions
	 * <ul>
	 * <li>Elements in each input list are contiguous, and the elements in
	 * common will be contiguous
	 * <li>If there are no elements in common, returns <code>null</code>
	 * </ul>
	 * E.g.:
	 * 
	 * <pre>
	 * 2 lists [1,3,5,7,9] and [3,5,7,9,10,11] will return [1,3,5,7,9,10,11]
	 * 2 lists [1,3,5,7,9] and [5,6,7] will return undefined results
	 * </pre>
	 * 
	 * @param l1
	 * @param l2
	 * @return A new List containing the merged lists
	 */
	public static <T> List<T> mergeLists(List<T> l1, List<T> l2) {

		List<T> thisElements = new ArrayList<>(l1);
		List<T> otherElements = new ArrayList<>(l2);
		otherElements = new ArrayList<>(otherElements);
		// nothing in common
		if (!CollectionUtils.containsAny(thisElements, otherElements)) {
			return null;
		}
		List<T> common = ListUtils.intersection(thisElements, otherElements);
		// shortcut if both paths are the same.
		if (common.size() == thisElements.size() && common.size() == otherElements.size()) {
			return thisElements;
		}

		// common will be at end of 1 path and start of another.
		// find the collection with last first element
		if (thisElements.indexOf(common.get(0)) == 0) {
			otherElements.addAll(thisElements.subList(common.size(), thisElements.size()));
			return otherElements;
		} else {
			thisElements.addAll(otherElements.subList(common.size(), otherElements.size()));
			return thisElements;
		}
	}

}
