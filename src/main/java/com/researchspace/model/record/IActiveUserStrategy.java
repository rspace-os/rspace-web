package com.researchspace.model.record;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.PrincipalCollection;

/**
 * Strategy for altering the modified by property of a user depending on whether
 * the user is running as another user.
 */
public interface IActiveUserStrategy {

	String getOriginalUser(String originalModifier);

	/**
	 * Does not alter the active user, a null implementation for testing
	 */
	IActiveUserStrategy NULL = new IActiveUserStrategy() {
		@Override
		public String getOriginalUser(String currentSubjectUsername) {
			return currentSubjectUsername;
		}

	};

	/**
	 * Consults security manager for whether modifier is running as another
	 * user, and returns the original user if this is indeed the case. <br/>
	 * Otherwise, return the current username, unchanged.
	 */
	IActiveUserStrategy CHECK_OPERATE_AS = new IActiveUserStrategy() {
		public String getOriginalUser(String currentSubjectUsername) {

			if (SecurityUtils.getSubject().isRunAs()) {
				PrincipalCollection prev = SecurityUtils.getSubject().getPreviousPrincipals();
				currentSubjectUsername = prev.getPrimaryPrincipal().toString();
			}
			return currentSubjectUsername;
		}
	};

}
