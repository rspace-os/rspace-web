package com.researchspace.model.apps;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.Validate;

import com.researchspace.model.User;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.record.PermissionsAdaptable;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames={"user_id", "app_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAppConfig implements Serializable, PermissionsAdaptable {

	private static final long serialVersionUID = 3236921143066479364L;

	private Long id;
	private User user;
	private App app;
	private Set<AppConfigElementSet> appConfigElementSets = new HashSet<>();

	private boolean enabled;

	/*
	 * Testing constructor, can add Id
	 */
	UserAppConfig(Long id, User user, App app, boolean enabled) {
		this(user, app, enabled);
		this.id = id;
	}

	public UserAppConfig(User user, App app, boolean enabled) {
		super();
		this.user = user;
		this.app = app;
		this.enabled = enabled;
	}

	@OneToMany(mappedBy = "userAppConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch=FetchType.EAGER)
	public Set<AppConfigElementSet> getAppConfigElementSets() {
		return appConfigElementSets;
	}

	void setAppConfigElementSets(Set<AppConfigElementSet> appConfigElementSets) {
		this.appConfigElementSets = appConfigElementSets;
	}

	@Override
	public String toString() {
		return "UserAppConfig [id=" + id + ", user=" + user + ", app=" + app + ", enabled=" + enabled + "]";
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	@ManyToOne(optional=false)
	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	@ManyToOne(optional=false)
	public App getApp() {
		return app;
	}

	public void setApp(App app) {
		this.app = app;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Adds a new config element set for a user
	 * 
	 * @param toAdd
	 * @return <code>true</code> if item was successfully added
	 * @throws IllegalArgumentException
	 *             if <code>toAdd</code> is empty.
	 * @throws IllegalArgumentException
	 *             if app does not allow multiple configs and there is already
	 *             an existing config for this User/App combination.
	 * 
	 */
	public boolean addConfigSet(AppConfigElementSet toAdd) {
		Validate.isTrue(!toAdd.getConfigElements().isEmpty(), "App config set is empty");
		boolean added = this.appConfigElementSets.add(toAdd);
		toAdd.setUserAppConfig(this);
		return added;
	}

	/**
	 * Removes a new config element set for a user/app combination
	 * 
	 * @param toAdd
	 * @return <code>true</code> if item was successfully added
	 * @throws IllegalArgumentException
	 *             if <code>toAdd</code> is empty.
	 * 
	 */
	public boolean removeConfigSet(AppConfigElementSet toAdd) {
		boolean removed = this.appConfigElementSets.remove(toAdd);
		toAdd.setUserAppConfig(null);
		return removed;
	}

	/**
	 * Removes all config elements from this collection
	 */
	public void clear() {
		this.appConfigElementSets.forEach(set -> set.setUserAppConfig(null));
		this.appConfigElementSets.clear();
	}

	/**
	 * Get count of config element sets
	 * 
	 * @return
	 */
	@Transient
	public int getConfigElementSetCount() {
		return appConfigElementSets.size();
	}

	@Override
	@Transient
	public AbstractEntityPermissionAdapter getPermissionsAdapter() {
		return new UserAppConfigPermissionAdapter(this);
	}

}
