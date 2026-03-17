package com.researchspace.archive.elninventory;

import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ArchivalListOfMaterials {

  @XmlElement(required = true)
  private Integer schemaVersion = 1;

  private Long originalElnFieldId;
  private String name;
  private String description;

  @XmlElementWrapper(name = "materials")
  @XmlElement(name = "materialUsage")
  private List<ArchivalMaterialUsage> materials = new ArrayList<>();

  public ArchivalListOfMaterials(ListOfMaterials dbLom) {
    setOriginalElnFieldId(dbLom.getElnField().getId());
    setName(dbLom.getName());
    setDescription(dbLom.getDescription());

    if (dbLom.getMaterials() != null) {
      for (MaterialUsage mu : dbLom.getMaterials()) {
        getMaterials().add(new ArchivalMaterialUsage(mu));
      }
    }
  }
}
