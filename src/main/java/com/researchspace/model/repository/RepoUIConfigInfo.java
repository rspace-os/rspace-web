package com.researchspace.model.repository;

import com.researchspace.repository.spi.LicenseConfigInfo;
import com.researchspace.repository.spi.Subject;
import com.researchspace.repository.spi.properties.RepoProperty;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;

@Data
public class RepoUIConfigInfo {

  public RepoUIConfigInfo(
      String repoName,
      List<Subject> subjects,
      LicenseConfigInfo license,
      List<RepoProperty> otherProperties) {
    super();
    this.repoName = repoName;
    this.subjects = subjects;
    this.license = license;
    this.otherProperties = otherProperties;
  }

  private String repoName;
  private List<Subject> subjects;
  private LicenseConfigInfo license;
  private List<RepoProperty> otherProperties;
  private List<LinkedDMP> linkedDMPs;

  /** Optional Map of key:value pairs with AppConfigInf configuration. */
  private Map<String, Object> options = new TreeMap<>();

  private String displayName;
}
