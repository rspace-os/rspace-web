package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.FieldManager;
import com.researchspace.service.StoichiometryService;
import com.researchspace.service.archive.export.StoichiometryReaderWriter;
import lombok.EqualsAndHashCode;

public class StoichiometryImporter {

  private final StoichiometryService service;
  private final ArchivalField oldField;
  private final Field newField;
  private final User user;
  private final StoichiometryReaderWriter reader;
  private final FieldManager fieldManager;

  @EqualsAndHashCode(of = "id")
  public static class IdAndRevision {
    public Long id;
    public Long revision;
  }

  public StoichiometryImporter(
      StoichiometryService service,
      StoichiometryReaderWriter reader,
      ArchivalField oldField,
      Field newField,
      FieldManager fieldmanager,
      User user) {
    this.service = service;
    this.oldField = oldField;
    this.newField = newField;
    this.user = user;
    this.reader = reader;
    this.fieldManager = fieldmanager;
  }

  public void importStoichiometries(
      ArchivalGalleryMetadata oldChemElement, RSChemElement currentChem) {
    StoichiometryDTO matching =
        oldField.getStoichiometries().stream()
            .filter(
                s ->
                    s.getParentReactionId() != null
                        && s.getParentReactionId() == oldChemElement.getId())
            .findFirst()
            .orElse(null);
    if (matching != null) {
      Stoichiometry created =
          service.createNewFromDataWithoutInventoryLinks(matching, currentChem, user);
      IdAndRevision newDTO = new IdAndRevision();
      newDTO.id = created.getId();
      String updatedStoichiometriesFieldContent =
          reader.replaceTargetStoichiometryWithNew(newField.getFieldData(), matching, newDTO);
      newField.setFieldData(updatedStoichiometriesFieldContent);
      fieldManager.save(newField, user);
    }
  }
}
