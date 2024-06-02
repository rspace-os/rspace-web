package com.researchspace.model.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkedDMP {

  private Long dmpUserInternalId;
  private String dmpTitle;
  private String dmpId;
}
