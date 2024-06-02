package com.researchspace.model.dtos.chemistry;

import lombok.Data;

@Data
public class ChemElementImageUpdateDto {

  private Long ecatChemFileId;
  private Integer height;
  private Integer width;
  private Integer previewHeight;
  private Integer previewWidth;
}
