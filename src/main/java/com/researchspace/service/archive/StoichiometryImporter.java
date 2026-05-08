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
import com.researchspace.service.archive.export.StoichiometryReader;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StoichiometryImporter {

  private final StoichiometryService service;
  private final ArchivalField oldField;
  private final Field newField;
  private final User user;
  private final StoichiometryReader reader;
  private final FieldManager fieldManager;

  @EqualsAndHashCode(of = "id")
  public static class IdAndRevision {
    public Long id;
    public Long revision;
  }

  public StoichiometryImporter(
      StoichiometryService service,
      StoichiometryReader reader,
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
                        && s.getParentReactionId().equals(oldChemElement.getId()))
            .findFirst()
            .orElse(null);
    if (matching != null) {
      Stoichiometry created =
          service.createNewFromDataWithoutInventoryLinks(matching, currentChem, user);
      IdAndRevision newDTO = new IdAndRevision();
      newDTO.id = created.getId();
      String updatedStoichiometriesFieldContent =
          reader.createReplacementHtmlContentForTargetStoichiometryInFieldData(
              newField.getFieldData(), matching, newDTO);
      newField.setFieldData(updatedStoichiometriesFieldContent);
      fieldManager.save(newField, user);
    }
  }

  /**
   * Imports stoichiometries with no parent reaction (RSDEV-874 reaction-less tables) and logs a
   * warning for orphan stoichiometries whose {@code parentReactionId} does not match any chem
   * element in the archive.
   *
   * <p>For each archive stoichiometry with {@code parentReactionId == null}, creates a new
   * stoichiometry on the importing instance bound to {@code newField}'s structured document, copies
   * every molecule (each backed by a fresh {@link RSChemElement} — {@code rs_chem_id} is NOT NULL
   * on {@code StoichiometryMolecule}), rewrites the field's {@code data-stoichiometry-table} JSON
   * to reference the new id, and saves the field.
   *
   * @param archivedChemElementIds the set of chem element IDs present in the archive (from {@code
   *     ArchivalField.getChemElementMeta()}); used to detect orphan stoichiometries.
   */
  public void importReactionlessStoichiometries(Set<Long> archivedChemElementIds) {
    for (StoichiometryDTO archived : oldField.getStoichiometries()) {
      if (archived.getParentReactionId() == null) {
        Stoichiometry created =
            service.createReactionlessFromArchive(archived, newField.getStructuredDocument(), user);
        IdAndRevision newDTO = new IdAndRevision();
        newDTO.id = created.getId();
        String updatedFieldData =
            reader.createReplacementHtmlContentForTargetStoichiometryInFieldData(
                newField.getFieldData(), archived, newDTO);
        newField.setFieldData(updatedFieldData);
        fieldManager.save(newField, user);
      } else if (!archivedChemElementIds.contains(archived.getParentReactionId())) {
        log.warn(
            "Archive stoichiometry id={} references parentReactionId={} which is not present "
                + "in the imported chem elements; molecules will not be imported.",
            archived.getId(),
            archived.getParentReactionId());
      }
    }
  }
}
