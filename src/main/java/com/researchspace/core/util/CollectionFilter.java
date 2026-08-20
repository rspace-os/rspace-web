package com.researchspace.core.util;

/**
 * Generic interface for any filtering operation.
 */
public interface CollectionFilter<T> {
	/**
	 * returns <code>true</code> if <code>toFilter</code> is to be included.
	 * <code>false</code> otherwise.
	 * 
	 * @param toFilter
	 * @return
	 */
	boolean filter(T toFilter);

	/**
	 * Null implementation filters everything - i.e., returns <code>false</code>
	 * for toFilter argument
	 */
	CollectionFilter<Object> NULLFILTER = new CollectionFilter<>() {
		@Override
		public boolean filter(Object toFilter) {
			return false;
		}

	};

	/**
	 * filters nothing - i.e., returns <code>true</code> for toFilter argument
	 */
	CollectionFilter<Object> NO_FILTER = new CollectionFilter<>() {
		@Override
		public boolean filter(Object toFilter) {
			return true;
		}
	};

}
