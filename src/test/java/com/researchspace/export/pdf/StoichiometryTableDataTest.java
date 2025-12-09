package com.researchspace.export.pdf;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.stoichiometry.MoleculeRole;
import org.junit.Test;

public class StoichiometryTableDataTest {

  private StoichiometryTableData testee;

  @Test
  public void testNullValuesReturnedAsZero() {
    testee = createStoichiometryDataTableWithNullActualYield();
    assertEquals("0", testee.getActualYield());
    testee = createStoichiometryDataTableWithNullActualAmount();
    assertEquals("0", testee.getActualAmount());
  }

  @Test
  public void testCalculateMoles() {
    testee = createStoichiometryDataTableWithDefaults();
    assertEquals("0.667", testee.getMoles());
  }

  @Test
  public void testCalculateNullMassMoles() {
    testee = createStoichiometryDataTableWithNullMass();
    assertEquals("0", testee.getMoles());
  }
  @Test
  public void testCalculateNullName() {
    testee = createStoichiometryDataTableWithNullName();
    assertEquals("UNKNOWN", testee.getName());
  }

  private StoichiometryTableData createStoichiometryDataTableWithNullName() {
    StoichiometryMoleculeDTO stdo = createStoichiometryMoleculeDTOWithDefaultsPopulated();
    stdo.setName(null);
    return new StoichiometryTableData(stdo);
  }

  @Test
  public void testRoundToThreeDecimals() {
    testee = createStoichiometryDataTableWithDefaults();
    assertEquals("1.000004", testee.getCoefficient());
    assertEquals("3", testee.getMolecularWeight());
    assertEquals("2", testee.getMass());
    assertEquals("3.001", testee.getActualAmount());
    assertEquals("4.01", testee.getActualYield());
  }

  private static StoichiometryTableData createStoichiometryDataTableWithNullActualAmount() {
    StoichiometryMoleculeDTO stdo = createStoichiometryMoleculeDTOWithDefaultsPopulated();
    stdo.setActualAmount(null);
    return new StoichiometryTableData(stdo);
  }

  private static StoichiometryTableData createStoichiometryDataTableWithDefaults() {
    StoichiometryMoleculeDTO stdo = createStoichiometryMoleculeDTOWithDefaultsPopulated();
    return new StoichiometryTableData(stdo);
  }

  private static StoichiometryTableData createStoichiometryDataTableWithNullActualYield() {
    StoichiometryMoleculeDTO stdo = createStoichiometryMoleculeDTOWithDefaultsPopulated();
    stdo.setActualYield(null);
    return new StoichiometryTableData(stdo);
  }

  private static StoichiometryTableData createStoichiometryDataTableWithNullMass() {
    StoichiometryMoleculeDTO stdo = createStoichiometryMoleculeDTOWithDefaultsPopulated();
    stdo.setMass(null);
    return new StoichiometryTableData(stdo);
  }

  private static StoichiometryMoleculeDTO createStoichiometryMoleculeDTOWithDefaultsPopulated() {
    StoichiometryMoleculeDTO stdo =
        new StoichiometryMoleculeDTO(
            1L,
            2L,
            MoleculeRole.REACTANT,
            "formula",
            "name",
            "smiles",
            1.000004,
            3.0004,
            2.0001,
            3.00051,
            4.01,
            true,
            "notes");
    return stdo;
  }
}
