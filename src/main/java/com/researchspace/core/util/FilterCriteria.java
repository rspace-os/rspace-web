package com.researchspace.core.util;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Base class of search criteria classes which uses reflection and annotation
 * processing to identify search fields.<br/>
 * Subclasses should use regular JavaBean conventions (ie., to supply get/set
 * methods for properties), and identify search terms by the @UISearchTerm
 * annotation.
 */
public class FilterCriteria implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5176599724806142955L;

	public static final short MAX_SEARCH_LENGTH = 255;

	private Map<String, Object> criteria = new TreeMap<>();

	/**
	 * Only returns non-null search terms. Values of returned map can be arrays
	 */
	public Map<String, Object> getSearchTermField2Values()
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Field[] fields = getClass().getDeclaredFields();
		criteria.clear();
		for (Field field : fields) {

			Annotation sterm = field.getAnnotation(UISearchTerm.class);
			if (sterm != null) {
				Object val;

				if (field.getType().isArray()) {
					val = BeanUtils.getArrayProperty(this, field.getName());
					String[] details = (String[]) val;
					if (val != null && details.length > 0) {
						criteria.put(field.getName(), val);
					}
				} else if (field.getType().isEnum()) {
					try {
						// get javabean property to get the object
						Method getter = new PropertyDescriptor(field.getName(), this.getClass()).getReadMethod();
						val = getter.invoke(this);
						if (val != null) {
							criteria.put(field.getName(), val);
						}
					} catch (IntrospectionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					val = BeanUtils.getProperty(this, field.getName());
					if (!StringUtils.isBlank((String) val)) {
						criteria.put(field.getName(), val);
					}
				}
			}
		}
		return criteria;
	}

	/**
	 * Returns a string of this search class in URL query format. It does not
	 * include trailing or leading '&' characters. Client code should decide if
	 * these are necessary, depending on if there are other query items in the
	 * URL.
	 * 
	 * @return A String in the form
	 *         'search_propname=propvalue&search_propname2='propvalue'
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 */
	public String getURLQueryString() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Map<String, Object> searchTermField2Values = getSearchTermField2Values();
		StringBuilder sb = new StringBuilder();
		for (Entry<String, Object> searchTermEntry : searchTermField2Values.entrySet()) {
			sb.append(searchTermEntry.getKey()).append("=");
			if (searchTermEntry.getValue() instanceof String[]) {
				sb.append(StringUtils.join((String[]) searchTermEntry.getValue(), ","));
			} else {
				sb.append(searchTermEntry.getValue());
			}
			sb.append("&");
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1); // remove last '&' if there is one
		}
		return sb.toString();
	}

}
