package com.researchspace.model.dtos;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;

/** Simple filter POJO for filtering Gallery displays */
@Data
@EqualsAndHashCode(callSuper = false)
public class GalleryFilterCriteria extends FilterCriteria {

  private static final long serialVersionUID = 1L;

  @UISearchTerm private String name;

  public GalleryFilterCriteria(String name) {
    this.name = name;
  }

  public GalleryFilterCriteria() {}

  /**
   * Getter for name turns into wildcard match
   *
   * @return
   */
  public String getName() {
    if (name != null && !name.endsWith("%")) {
      return "%" + name + "%";
    } else {
      return name;
    }
  }

  /**
   * Gets raw search term, not with wildcards appended.
   *
   * @return
   */
  public String getRawName() {
    return name;
  }

  /**
   * Whether some search terms have been set
   *
   * @return
   */
  public boolean isEnabled() {
    return !StringUtils.isEmpty(name);
  }
}
