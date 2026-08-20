package com.researchspace.model.record;

/**
 * Describes publishing state of a form:
 * <ul>
 * <li>NEW = freshly created, may be edited with no warnings, hidden from users
 * <li>PUBLISHED = available for users
 * <li>UNPUBLISHED = A form that was previously published, but is now hidden.
 * <li>OLD = A previous version of a form.
 * </ul>
 */
public enum FormState {
	NEW, PUBLISHED, UNPUBLISHED, OLD
}
