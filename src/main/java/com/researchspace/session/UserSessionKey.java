package com.researchspace.session;

import java.io.Serializable;

public class UserSessionKey implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3361715138288767624L;
	
	private String username;
	private String session;
	
	public UserSessionKey(String u, String s) {
		username = u;
		session = s;
	}
	
	public String getUsername() {
		return username;
	}
	
	public String getSession() {
		return session;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((session == null) ? 0 : session.hashCode());
		result = prime * result
				+ ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		UserSessionKey other = (UserSessionKey) obj;
		
		if (session == null) {
			if (other.session != null) {
				return false;
			}
		} else if (!session.equals(other.session)) {
			return false;
		}
		
		if (username == null) {
			if (other.username != null) {
				return false;
			}
		} else if (!username.equals(other.username)) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "UserSessionKey [username=" + username + ", session=" + session
				+ "]";
	}
}
