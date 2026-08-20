package com.researchspace.model.audittrail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a method or class as containing data to be included in audit trail.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
@Inherited
public @interface AuditTrailData {

	/**
	 * The domain area of RSpace that is being audited. <br/>
	 * If delegateToCollection() is set, this value is ignored - the objects in
	 * the collection are inspected for their audit domains. E.g.,
	 * 
	 * <pre>
	 * &#064;AuditTrailData(auditDomain = AuditDomain.COMMUNITY, delegateToCollection = &quot;exampleCollection&quot;)
	 * class DelegatingToCollectionSubclass {
	 * 	private List&lt;AuditTrailTestObject&gt; exampleCollection = new ArrayList&lt;AuditTrailTestObject&gt;();
	 * 
	 * 	public List&lt;AuditTrailTestObject&gt; getExampleCollection() {
	 * 		return exampleCollection;
	 * 	}
	 * }
	 * </pre>
	 * 
	 * @return an {@link AuditDomain}
	 */
	AuditDomain auditDomain() default AuditDomain.UNKNOWN;

	/**
	 * If set, this instructs that the class annotated with this annotation
	 * holds a collection of objects to process for auditing, and these will be
	 * processed as individual events.
	 * <p>
	 * The value of this must be a Javabean style property name, and the
	 * annotated class must contain  a public getter method returning a
	 * collection of any objects. For example,
	 * 
	 * <pre>
	 * &#064;AuditTrailData(delegateToCollection = &quot;exampleCollection&quot;)
	 * class DelegatingToCollectionSubclass {
	 * 	private List&lt;AuditTrailTestObject&gt; exampleCollection = new ArrayList&lt;AuditTrailTestObject&gt;();
	 * 
	 * 	public List&lt;AuditTrailTestObject&gt; getExampleCollection() {
	 * 		return exampleCollection;
	 * 	}
	 * }
	 * </pre>
	 *
   */
	String delegateToCollection() default "";
}
