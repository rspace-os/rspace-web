package com.researchspace.model.audittrail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.BeanUtilsBean;

import com.researchspace.model.core.Person;

public abstract class HistoricalEvent {

	/**
	 * Subclasses must implement and never return <code>null</code>
	 *
   */
	public abstract AuditAction getAuditAction();

	private final Person subject;

	/**
	 * Optional free-text description of the event.
	 *
   */
	public String getDescription() {
		return description;
	}

	protected String description;

	public Person getSubject() {
		return subject;
	}

	HistoricalEvent(final Person subject, Object auditedObject) {
		this(subject, auditedObject, null);
	}

	public HistoricalEvent(Person subject, Object auditedObject, String description) {
		super();
		this.subject = subject;
		this.auditedObject = auditedObject;
		this.description = description;
	}

	public Object getAuditedObject() {
		return auditedObject;
	}

	private Object auditedObject;

	abstract void accept(AuditEventVisitor visitor);

	protected AuditData getAuditData(Object other) {
		AuditData data = new AuditData();
		if (other == null || other instanceof Class) {
			return data;
		}
		AuditTrailIdentifier identifier;
		Class<?> clazz = getClass(other);

		for (Method method : clazz.getMethods()) {
			AuditTrailProperty auditable = method.getAnnotation(AuditTrailProperty.class);

			if (auditable != null) {
				try {
					Object value = method.invoke(other);
					if (value != null && !(value instanceof Iterable) && auditable.properties() != null
							&& auditable.properties().length > 0) {
						String[] properties = auditable.properties();
						Map<String, Object> props = new HashMap<>();
						for (String property : properties) {
							String valueStr = BeanUtils.getProperty(value, property);
							props.put(property, valueStr);
						}
						String fieldName = auditable.name();
						data.put(fieldName, props);
					} else if ((value instanceof Collection) && auditable.properties() != null
							&& auditable.properties().length > 0) {
						Collection<?> collection = ((Collection<?>) value);

						String[] properties = auditable.properties();
						BeanUtilsBean bean = new BeanUtilsBean();
						List<Object> auditData = new ArrayList<>();
						for (Object item : collection) {
							Map<String, Object> props = new HashMap<>();
							for (String property : properties) {
								Object valueStr = bean.getPropertyUtils().getProperty(item,
										property);
								props.put(property, valueStr);
							}
							auditData.add(props);
						}
						String fieldName = auditable.name();
						data.put(fieldName, auditData);
					} else {
						String fieldName = auditable.name();
						data.put(fieldName, value);
					}

				} catch (IllegalArgumentException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
					e.printStackTrace();
				} 
			}

			identifier = method.getAnnotation(AuditTrailIdentifier.class);
			if (identifier != null) {
				try {
					Object value = method.invoke(other);
					data.put("id", value);
				} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
					e.printStackTrace();
				} 
			}
		}

		return data;
	}

	protected AuditData getAuditData() {
		return getAuditData(auditedObject);
	}

	private Class<?> getClass(Object object) {
		if (object instanceof Class) {
			return (Class) object;
		} else {
			return object.getClass();
		}
	}

}
