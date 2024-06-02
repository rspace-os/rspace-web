package com.researchspace.archive.elninventory;

import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
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
