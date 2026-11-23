package com.researchspace.model.apps;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppConfigElement implements Serializable {

  /** */
  private static final long serialVersionUID = 7911460363789518663L;

  @Override
  public String toString() {
    return "AppConfigElement [id="
        + id
        + ", value="
        + value
        + ", appConfigElementDescriptor="
        + appConfigElementDescriptor
        + "]";
  }

  private Long id;

  // Field access so Hibernate does not run setValue's validation while loading: with batch
  // fetching the descriptor association may not be set yet when the value is hydrated.
  @Access(AccessType.FIELD)
  private String value;

  private AppConfigElementDescriptor appConfigElementDescriptor;

  private AppConfigElementSet appConfigElementSet;

  public AppConfigElement(AppConfigElementDescriptor descriptor, String value) {
    this(descriptor);
    this.value = value;
  }

  public AppConfigElement(AppConfigElementDescriptor descriptor) {
    this.appConfigElementDescriptor = descriptor;
  }

  @ManyToOne
  public AppConfigElementSet getAppConfigElementSet() {
    return appConfigElementSet;
  }

  public void setAppConfigElementSet(AppConfigElementSet appConfigElementSet) {
    this.appConfigElementSet = appConfigElementSet;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long getId() {
    return id;
  }

  void setId(Long id) {
    this.id = id;
  }

  @Transient
  public String getValue() {
    return value;
  }

  /**
   * Sets value if validated by descriptor, which gives weak type checking for numbers and booleans
   *
   * @param value
   */
  public void setValue(String value) {
    appConfigElementDescriptor.validate(value);
    this.value = value;
  }

  @ManyToOne
  public AppConfigElementDescriptor getAppConfigElementDescriptor() {
    return appConfigElementDescriptor;
  }

  void setAppConfigElementDescriptor(AppConfigElementDescriptor appConfigElementDescriptor) {
    this.appConfigElementDescriptor = appConfigElementDescriptor;
  }
}
