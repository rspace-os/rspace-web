package com.researchspace.model.apps;

import java.io.Serializable;
import java.util.Locale;
import java.util.Set;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class App implements Serializable {

	public static final String APP_DSPACE = "app.dspace";
	public static final String APP_DATAVERSE = "app.dataverse";
	public static final String APP_SLACK = "app.slack";
	public static final String APP_MSTEAMS = "app.msteams";
	public static final String APP_GITHUB = "app.github";
	public static final String APP_FIGSHARE = "app.figshare";
	public static final String APP_DRYAD = "app.dryad";
	public static final String APP_ZENODO = "app.zenodo";
	public static final String APP_DIGITAL_COMMONS_DATA = "app.digitalCommonsData";
	public static final String APP_DMP_ASSISTANT = "app.dmpassistant";
	public static final String APP_GHANGOUTSCHAT = "app.ghangoutschat";

	@OneToMany(mappedBy = "app", cascade = CascadeType.ALL)
	public Set<AppConfigElementDescriptor> getAppConfigElementDescriptors() {
		return appConfigElementDescriptors;
	}

	public void setAppConfigElementDescriptors(Set<AppConfigElementDescriptor> appConfigElementDescriptors) {
		this.appConfigElementDescriptors = appConfigElementDescriptors;
	}

	private Long id;
	private String name;
	private String label;
	private Boolean defaultEnabled;

	private Set<AppConfigElementDescriptor> appConfigElementDescriptors;

	/**
	 * Default for tools /hibernate
	 */
	public App() {
		super();
		this.defaultEnabled = true;
	}

	/**
	 * Main constructor
	 */
	public App(String name, String label, Boolean defaultEnablement) {
		super();
		this.name = name;
		this.label = label;
		this.defaultEnabled = defaultEnablement;
	}

	App(Long id, String name, String label, Boolean defaultEnablement) {
		this(name, label, defaultEnablement);
		this.id = id;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -3681076572577308167L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long getId() {
		return id;
	}

	@Override
	public String toString() {
		return "App [id=" + id + ", name=" + name + ", label=" + label + ", defaultEnablement=" + defaultEnabled
				+ ", allowMultipleConfigs=" + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		App other = (App) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	void setId(Long id) {
		this.id = id;
	}

	@Column(nullable = false, unique = true, length = 50)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public boolean isDefaultEnabled() {
		return defaultEnabled;
	}

	void setDefaultEnabled(Boolean defaultEnabled) {
		this.defaultEnabled = defaultEnabled;
	}

	/***
	 * Finds {@link AppConfigElementDescriptor} by name
	 * 
	 * @param propertyName
	 * @return the {@link AppConfigElementDescriptor} with given name or
	 *         <code>null</code> if no such descriptor exists
	 */
	@Transient
	public AppConfigElementDescriptor getDescriptorByName(String propertyName) {
		return appConfigElementDescriptors.stream()
				.filter(aced -> aced.getDescriptor().getName().equals(propertyName)).findFirst().orElse(null);
	}

	/**
	 * The number of configuration elements associated with this App
	 * 
	 * @return
	 */
	@Transient
	public int getConfigElementCount() {
		return getAppConfigElementDescriptors().size();
	}

	/**
	 * Test for whether this app is repository category or not
	 */
	@Transient
	public boolean isRepositoryApp (){
		return APP_DATAVERSE.equals(name) || APP_DSPACE.equals(name) || APP_FIGSHARE.equals(name)
				|| APP_DRYAD.equals(name) || APP_ZENODO.equals(name) || APP_DIGITAL_COMMONS_DATA.equals(name);
	}

	/**
	 * Getter for unique name of the app - e.g for 'app.slack' will return 'slack'
	 * @return
	 */
	@Transient
	public String getUniqueName (){
		if(!StringUtils.isEmpty(name) && name.contains("app.")) {
			return name.replace("app.", "");
		} else {
			return name;
		}
	}

	/**
	 * Converts App name to IntegrationInfo name
	 * e.g. 'app.slack' -> 'SLACK'
	 */
	@Transient
	public String toIntegrationInfoName(){
		return getUniqueName().toUpperCase(Locale.ROOT);
	}

}
