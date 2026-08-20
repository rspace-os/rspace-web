package com.researchspace.model.audittrail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a Javabean getter method that retrieves a  value (primitive or
 * string) that is needed in the audit trail. <br/>
 * Data from associated objects can be specified by use of the <code>properties</code>value
 * <p>
 * E.g.,
 * if a class has method 
 * <code>
 * User getUser()
 * </code>
 * and we  want to log the username property, we can annotate it as follows:
 * <pre>
 * {@literal @}AuditTrailProperty(name="originator", properties="username")
 * User getUser()
 * </pre>
 * Collections can also be audited in this annotation, too.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface AuditTrailProperty {
	

	/**
	 * The property name, as it should appear in the audit records
	 *
   */
	String name();
	
	/**
	 * If this annotation is used to annotate an object, this field defines the
	 *  properties of that object to log. If the annotated object is a collection, then
	 *  each element is iterated over and the property of each eleemnt in the collection is logged.
   */
	String [] properties() default {};

}
