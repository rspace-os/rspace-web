package com.researchspace.model.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * Class to represent a global ObjectIdentifier. <br>
 * Objects are immutable once constructed.
 */
@EqualsAndHashCode(of = {"prefix", "dbId", "versionId"})
public final class GlobalIdentifier implements Serializable, Comparable<GlobalIdentifier> {

  private static final long serialVersionUID = -1223981575582142299L;

  private static final String VERSION_PREFIX = "v";
  private static final String VERSION_PATTERN_STRING = "(" + VERSION_PREFIX + "(\\d+))?";

  public static final String OID_PATTERN_STRING = "([A-Z][A-Z])(\\d+)" + VERSION_PATTERN_STRING;

  /** Regex to enforce Global ID syntax, e.g., AB12345v4 */
  public static final Pattern OID_PATTERN = Pattern.compile(OID_PATTERN_STRING);

  private GlobalIdPrefix prefix;
  private Long dbId;
  private Long versionId;

  /**
   * Generates a {@link GlobalIdentifier} from a string. Also used by JSON deserializer in MVC
   * tests.
   *
   * @param oid A String representation of the OID
   * @throws IllegalArgumentException if <code>oid</code> is null, empty or does not match {@link
   *     GlobalIdentifier#OID_PATTERN}, or is not a known {@link GlobalIdPrefix}
   */
  @JsonCreator
  public GlobalIdentifier(@JsonProperty("idString") final String oid) {
    if (StringUtils.isBlank(oid)) {
      throw new IllegalArgumentException("oid cannot be null or empty");
    }
    Matcher matcher = OID_PATTERN.matcher(oid);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          String.format("OID %s did not match pattern %s", oid, OID_PATTERN_STRING));
    }

    prefix = GlobalIdPrefix.valueOf(matcher.group(1));
    dbId = Long.valueOf(matcher.group(2));
    if (matcher.group(4) != null) {
      versionId = Long.valueOf(matcher.group(4));
    }
  }

  /**
   * @param prefix
   * @param dbId
   * @throws IllegalArgumentException if either argument is <code>null</code>
   */
  public GlobalIdentifier(final GlobalIdPrefix prefix, final Long dbId) {
    if (prefix == null || dbId == null) {
      throw new IllegalArgumentException("attempt to construct global id without prefix or dbId");
    }
    this.prefix = prefix;
    this.dbId = dbId;
  }

  /**
   * @param prefix
   * @param dbId
   * @param versionId
   * @throws IllegalArgumentException if either argument is <code>null</code>
   */
  public GlobalIdentifier(final GlobalIdPrefix prefix, final Long dbId, final Long versionId) {
    this(prefix, dbId);
    if (versionId != null) {
      this.versionId = versionId;
    }
  }

  /**
   * @return a string representation of this object
   */
  public String getIdString() {
    return prefix.name() + dbId + (versionId != null ? VERSION_PREFIX + versionId : "");
  }

  /**
   * @return the prefix of the identifier, that states what type of object is represented by this ID
   */
  @JsonIgnore
  public GlobalIdPrefix getPrefix() {
    return prefix;
  }

  /**
   * @return the database identifier part of this identifier
   */
  @JsonIgnore
  public Long getDbId() {
    return dbId;
  }

  /**
   * @return the version identifier part of this identifier (may be null)
   */
  @JsonIgnore
  public Long getVersionId() {
    return versionId;
  }

  @JsonIgnore
  public boolean hasVersionId() {
    return versionId != null;
  }

  @Override
  public String toString() {
    return getIdString();
  }

  @Override
  public int compareTo(GlobalIdentifier o) {
    if (dbId.equals(o.getDbId())) {
      if (!hasVersionId()) {
        return 1;
      }
      if (!o.hasVersionId()) {
        return -1;
      }
      return getVersionId().compareTo(o.getVersionId());
    }
    return dbId.compareTo(o.getDbId());
  }

  /**
   * Boolean test for whether a string identifier is valid oid syntax or not.
   *
   * @param oid
   * @return <code>true</code> if is valid syntax, <code>false</code> otherwise.
   */
  public static boolean isValid(String oid) {
    try {
      new GlobalIdentifier(oid);
    } catch (IllegalArgumentException iae) {
      return false;
    }
    return true;
  }
}
