package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Reproduces the RSDEV-1137 follow-up variant: stoichiometry molecule rows are returned duplicated
 * once the owning document is shared, even though the persisted rows are never duplicated.
 *
 * <p>Confirmed root cause: loading a {@link Stoichiometry} eagerly join-fetches its {@code
 * molecules} collection (a {@code List}/bag) and, by reaching the owning {@code Record}, that
 * record's folder membership collection ({@code RecordToFolder}) in the same query. Join-fetching
 * two collections at once produces a Cartesian product: {@code molecules x folderMemberships}.
 * While the document sits in a single folder the multiplier is 1 and the result is correct; sharing
 * adds the document to a second folder, so every molecule row is returned twice. The database is
 * unaffected (the duplicate entries carry identical primary keys); only the loaded collection is
 * duplicated, which is why the duplication surfaces in the editable dialog and the update response
 * but not in the persisted data.
 *
 * <p>Runs outside the Spring test rollback so each manager call commits in its own transaction; see
 * {@link RealTransactionSpringTestBase}.
 */
public class StoichiometryShareDuplicationIT extends RealTransactionSpringTestBase {

  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private StoichiometryService stoichiometryService;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void rowsDoNotDuplicateInAuditedReadAfterSharingDocument() throws Exception {
    // 1-2. New document + empty stoichiometry (not attached to a chemical image), owned by piUser.
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord(false);
    StructuredDocument doc = setup.structuredDocument;

    Stoichiometry stoich = stoichiometryManager.createEmpty(doc, piUser);
    long stoichId = stoich.getId();

    // 3-6. Add two rows and save (works fine before sharing: document is in a single folder).
    update(stoichId, withNewReagent(liveMoleculeDtos(stoichId), "CCO", "Ethanol"));
    update(stoichId, withNewReagent(liveMoleculeDtos(stoichId), "O=O", "Oxygen"));
    assertEquals(
        "two distinct rows persisted before sharing", 2, distinctMoleculeIdCount(stoichId));
    assertEquals("collection not duplicated before sharing", 2, liveMoleculeCount(stoichId));

    // 7. Share the document with the group (edit permission). This adds the document to a second
    // folder, which is the trigger for the Cartesian-product duplication.
    ShareConfigElement share = new ShareConfigElement(setup.group.getId(), "edit");
    sharingMgr.shareRecord(piUser, doc.getId(), new ShareConfigElement[] {share});

    // 8-9. Add a third row and save.
    update(stoichId, withNewReagent(liveMoleculeDtos(stoichId), "CC(=O)O", "Acetic acid"));

    // Only three distinct molecules exist (the database is never duplicated)...
    assertEquals("three distinct rows persisted", 3, distinctMoleculeIdCount(stoichId));
    // ...but the loaded collection (== the GET / update response) must not return them duplicated.
    assertEquals(
        "loaded molecules collection must not be duplicated after sharing",
        3,
        liveMoleculeCount(stoichId));

    // The production read path used by the editable dialog (getById -> audited reconstruction ->
    // toDTO) must also return the rows un-duplicated.
    StoichiometryDTO viaApi = stoichiometryService.getById(stoichId, null, piUser);
    assertEquals(
        "API read (getById) must not duplicate rows after sharing",
        3,
        viaApi.getMolecules().size());
  }

  /** Number of distinct molecule primary keys persisted (the database is never duplicated). */
  private int distinctMoleculeIdCount(long stoichId) {
    return (int)
        stoichiometryManager.get(stoichId).getMolecules().stream()
            .map(StoichiometryMolecule::getId)
            .distinct()
            .count();
  }

  /** Calls the real update flow (one committed transaction == one Envers revision). */
  private void update(long stoichId, List<StoichiometryMoleculeUpdateDTO> molecules) {
    StoichiometryUpdateDTO dto =
        StoichiometryUpdateDTO.builder().id(stoichId).molecules(molecules).build();
    stoichiometryManager.update(dto, piUser);
  }

  /**
   * Snapshots the current persisted molecules as update DTOs carrying their server-assigned ids.
   */
  private List<StoichiometryMoleculeUpdateDTO> liveMoleculeDtos(long stoichId) {
    List<StoichiometryMoleculeUpdateDTO> dtos = new ArrayList<>();
    for (StoichiometryMolecule m : stoichiometryManager.get(stoichId).getMolecules()) {
      dtos.add(
          StoichiometryMoleculeUpdateDTO.builder()
              .id(m.getId())
              .role(m.getRole())
              .smiles(m.getSmiles())
              .name(m.getName())
              .formula(m.getFormula())
              .molecularWeight(m.getMolecularWeight())
              .coefficient(m.getCoefficient())
              .mass(m.getMass())
              .limitingReagent(m.getLimitingReagent())
              .build());
    }
    return dtos;
  }

  private List<StoichiometryMoleculeUpdateDTO> withNewReagent(
      List<StoichiometryMoleculeUpdateDTO> existing, String smiles, String name) {
    existing.add(
        StoichiometryMoleculeUpdateDTO.builder()
            .role(MoleculeRole.AGENT)
            .smiles(smiles)
            .name(name)
            .coefficient(1.0)
            .limitingReagent(false)
            .build());
    return existing;
  }

  private int liveMoleculeCount(long stoichId) {
    return stoichiometryManager.get(stoichId).getMolecules().size();
  }
}
