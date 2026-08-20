package com.researchspace.model.views;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Abstract generic class for specifying enum types to include or exclude, for
 * example to parameterise a database query.
 *
 * @param <E>
 */
public abstract class AbstractEnumFilter<E extends Enum<E>> {

	private Collection<E> toInclude;

	private Collection<E> toExclude;

	/**
	 * For use in HQL parameterised lists
	 * 
	 * @return
	 */
	public Collection<E> getWantedTypes() {
		return EnumSet.copyOf(toInclude);
	}

	/**
	 * Takes an EnumSet to include or exclude from a search
	 * 
	 * @param types
	 *            A non-null {@link EnumSet}
	 * @param include
	 *            <code>true</code> to <em>include</em> <code>types</code>,
	 *            false to <em>exclude</em> <code>types</code>.
	 * @throws IllegalArgumentException
	 *             if <code>types</code> is <code>null</code>.
	 */
	public AbstractEnumFilter(EnumSet<E> types, boolean include) {

		if (types == null) {
			throw new IllegalArgumentException("Types cannot be null");
		}
		if (include) {
			toInclude = types;
			toExclude = EnumSet.complementOf(types);
		} else {
			toInclude = EnumSet.complementOf(types);
			toExclude = types;
		}
	}

	/**
	 * Directly specify setso enums to include or exclude.
	 * @param toInclude
	 * @param toExclude
	 */
	public AbstractEnumFilter(EnumSet<E> toInclude, EnumSet<E> toExclude) {
		this.toInclude = toInclude;
		this.toExclude = toExclude;
	}

	/**
	 * For use in HQL parameterised lists
	 * 
	 * @return
	 */
	public Collection<E> getExcludedTypes() {
		return EnumSet.copyOf(toExclude);
	}

	/**
	 * Generates an SQL 'in' clause for native MySQL query.
	 * 
	 * @return a String of form "in (a,b,c,d)" or an empty string if toInclude is empty.
	 */
	public String getSQLInClause() {
		if (toInclude == null || toInclude.isEmpty()) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		sb.append(" in (");
		for (E rt : toInclude) {
			sb.append("'").append(rt.toString()).append("', ");
		}
		sb.append(")");
		sb.deleteCharAt(sb.lastIndexOf(","));
		return sb.toString();
	}

}
