package com.researchspace.model.core;

import com.researchspace.core.util.Transformer;

/**
 * Mixin interface to define an object with a getId() method that defines it
 * uniquely amongst other objects in its class, and a getOid() method to
 * identify it uniquely in the entire database.
 */
public interface UniquelyIdentifiable {

	/**
	 * Gets a persistentIdentifier
	 * 
	 * @return
	 */
	Long getId();

	/**
	 * Returns an identifier that is unique within an RSpace installation
	 * 
	 * @return
	 */
	GlobalIdentifier getOid();

	/**
	 * Takes an object with a getId() method and returns its ID - for use with
	 * TransformerUtils.
	 */
	Transformer<Long, UniquelyIdentifiable> OBJECT_TO_ID_TRANSFORMER = new Transformer<>() {
		public Long transform(UniquelyIdentifiable toTransform) {
			return toTransform.getId();
		}
	};

}
