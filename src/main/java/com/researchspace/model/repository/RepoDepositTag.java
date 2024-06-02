package com.researchspace.model.repository;

import java.net.URI;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RepoDepositTag {
  private String value;
  private String vocabulary;
  private URI uri;
}
