package com.researchspace.core.util;

import java.util.Date;
/**
 * Mixin interface for providing and manipulating a date range
 */
public interface DateRangeAdjustable {
	
	Date getDateFrom();
	
	Date getDateTo();
	
	void setDateFrom(Date from);
	
	void setDateTo(Date to);

}
