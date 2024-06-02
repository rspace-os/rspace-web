package com.researchspace.model.dtos;

import com.researchspace.model.UserPreference;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreferencesCommand implements Serializable {

  /** */
  private static final long serialVersionUID = 2887149275257695900L;

  private List<UserPreference> prefs;
}
