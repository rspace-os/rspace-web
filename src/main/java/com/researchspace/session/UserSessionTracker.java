package com.researchspace.session;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;

import com.researchspace.model.User;

/**
 * Encapsulates underlying information about current logged in users
 */
public class UserSessionTracker {
	
	/**
	 * Name of users Set in the ServletContext
	 */
	public static final String USERS_KEY = "userNames";
	
	private Map<String, Set<UserSessionKey>> users = new ConcurrentHashMap<>();
	
	public boolean addUser(String username, HttpSession session) {
		boolean added = false;
		if (!users.keySet().contains(username)) {
			Set<UserSessionKey> sessions = new HashSet<>();
			added = sessions.add(new UserSessionKey(username, session.getId()));
			users.put(username, sessions);
		} else {
			added = users.get(username).add(new UserSessionKey(username, session.getId()));
		}
		return added;
	}
	
	public boolean removeUser(String username, HttpSession session) {
		boolean removed = false;
		if (users != null) {
			Set<UserSessionKey> sessions = users.get(username);
			if(sessions != null) {
				UserSessionKey toRemove = new UserSessionKey(username, session.getId());
				removed = sessions.remove(toRemove);
				if (removed) {
					if (sessions.isEmpty()) {
						users.remove(username);
					}
				}
			}
		}
	   return removed;
	}
   
   public int getTotalSessions() {
		int total = 0;
		for (String uname : users.keySet()) {
			if (users.get(uname) != null) {
				total += users.get(uname).size();
			}
		}
		return total;
	   }
   
   public Set<String>getActiveUsers() {
	   return users.keySet();
   }
   
   public int getTotalActiveUsers() {
	   return getActiveUsers().size();
   }
   
   public int getTotalSessionsForUser(User user) {
		String username = user.getUsername();
		if (users.get(username) == null) {
			return 0;
		} 
		return users.get(username).size();
   }

   /**
    * Removes all user sessions from this tracker. 
    * @param username, not <code>null</code>
    * @return <code>true</code> if all sessions are removed from this tracker.
    */
   public boolean forceRemoveUser(String username) {
	   if (StringUtils.isEmpty(username)) {
		   return false;
	   }

	   Set<UserSessionKey> sessions =  users.remove(username);
	   if (sessions != null) {
		   sessions.clear();
	   }
	   return users.get(username) == null;
   }

}
