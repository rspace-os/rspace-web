package com.researchspace.archive;

import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ArchivalMeta implements Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  // RA used to store the schema URL... why??
  private String source;
}
