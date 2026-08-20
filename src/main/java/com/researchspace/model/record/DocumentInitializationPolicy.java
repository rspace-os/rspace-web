package com.researchspace.model.record;

import org.apache.commons.lang3.Validate;

/**
 * Policy for initializing lazy-loaded properties of Recrds/Documents within a Hibernate session.
 * Uses decorator pattern to enable multiple policies to be combined as needed.
 */
public abstract class DocumentInitializationPolicy {
	
	private DocumentInitializationPolicy policy;

	/**
	 * Decorates other DocumentInitializationPolicy policies.
	 * <br/> Nested policies are run before outer policies.
	 * @param decoratedPolicy should not be <code>null</code>. Use the default constructor if you don't 
	 *  want to decorate another {@link DocumentInitializationPolicy}
	 */
	public DocumentInitializationPolicy(DocumentInitializationPolicy decoratedPolicy) {
		super();
		Validate.notNull(decoratedPolicy, "decorated policy cannot be null");
		this.policy = decoratedPolicy;
	}
	
	public DocumentInitializationPolicy() {
		super();
	}

	/**
	 * Perform initialization of whatever lazy-loaded properties need to be
	 * initialised, to be implemented by subclasses.
	 * 
	 * @param baseRecord
	 *            A {@link Record}
	 */
	protected abstract void doInitialize(BaseRecord baseRecord);
	
	public final void initialize (BaseRecord baseRecord) {
		if (policy != null) {
			policy.doInitialize(baseRecord);
		}
		doInitialize(baseRecord);
	}

}
