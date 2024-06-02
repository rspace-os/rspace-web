package com.researchspace.model.dtos.chemistry;

import com.researchspace.model.RSChemElement;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChemElementDataDto {
  private Long ecatChemFileId;
  private Long rsChemElementId;
  private String fileName;
  private Long fieldId;
  private String chemString;
  @Builder.Default private String chemElementsFormat = "mrv";
  private Integer fullHeight;
  private Integer fullWidth;
  private Integer previewHeight;
  private Integer previewWidth;
  private List<RSChemElement> rsChemElements;
}
