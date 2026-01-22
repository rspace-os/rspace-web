package com.researchspace.export.stoichiometry;

import static org.junit.Assert.assertEquals;

import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.QuantityInfo;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  public void testInventoryLinkGlobalID() {
    testee = createStoichiometryDataTableWithDefaults();
    assertEquals("SA11", testee.getInventoryLinkedItem());
  }

  @Test
  public void testInventoryLinkGlobalIDWhenLinkNull() {
    testee = createStoichiometryDataTableWithNullInventoryLink();
    assertEquals("-", testee.getInventoryLinkedItem());
  }

  private StoichiometryTableData createStoichiometryDataTableWithNullInventoryLink() {
    StoichiometryMoleculeDTO stdo = createStoichiometryMoleculeDTOWithDefaultsPopulated();
    stdo.setInventoryLink(null);
    return new StoichiometryTableData(stdo);
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
    Sample sample = new Sample();
    sample.setId(11L);
    QuantityInfo qi = QuantityInfo.of("1");
    StoichiometryInventoryLink invL = new StoichiometryInventoryLink();
    ReflectionTestUtils.setField(invL, "quantity", qi);
    invL.setSample(sample);
    StoichiometryMolecule mol = new StoichiometryMolecule();
    invL.setStoichiometryMolecule(mol);
    StoichiometryInventoryLinkDTO linkDTO = new StoichiometryInventoryLinkDTO(invL);
    StoichiometryMoleculeDTO stdo =
        new StoichiometryMoleculeDTO(
            1L,
            2L,
            linkDTO,
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
