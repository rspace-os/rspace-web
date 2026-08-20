package com.researchspace.model.record;

import com.researchspace.core.util.CollectionFilter;

/**
 * Abstract class for any kind of filter that filters collections of Records.
 */
public abstract class RecordFilter implements CollectionFilter<BaseRecord> {

	@Override
	public abstract boolean filter(BaseRecord toFilter);

}
