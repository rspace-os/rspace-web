package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.dao.RSChemElementDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.StoichiometryDao;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import java.math.BigDecimal;
import java.util.List;
import javax.validation.ConstraintViolationException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class StoichiometryInventoryLinkDaoHibernateTest extends SpringTransactionalTest {

  @Autowired private StoichiometryInventoryLinkDao dao;
  @Autowired private SampleDao sampleDao;
  @Autowired private StoichiometryDao stoichDao;
  @Autowired private RSChemElementDao chemDao;

  @Test
  public void attemptToSaveSampleTemplateLinkTriggersPrePersist() {
    User user = doCreateAndInitUser("user");
    StoichiometryInventoryLink link = setupSampleTemplateAndStoichiometry(user);

    ConstraintViolationException ex =
        assertThrows(
            ConstraintViolationException.class,
            () -> {
              dao.save(link);
            });

    assertEquals("Cannot link stoichiometry to sample template", ex.getMessage());
  }

  private StoichiometryInventoryLink setupSampleTemplateAndStoichiometry(User user) {
    Sample sample = TestFactory.createBasicSampleOutsideContainer(user);
    sample.setTemplate(true);
    sampleDao.save(sample);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "test");

    RSChemElement stoichChem = new RSChemElement();
    stoichChem.setChemElementsFormat(ChemElementsFormat.MOL);
    stoichChem.setRecord(doc);
    chemDao.save(stoichChem);

    RSChemElement stoichMolChem = new RSChemElement();
    stoichMolChem.setChemElementsFormat(ChemElementsFormat.MOL);
    stoichMolChem.setRecord(doc);
    chemDao.save(stoichMolChem);

    Stoichiometry stoich = new Stoichiometry();
    StoichiometryMolecule mol = new StoichiometryMolecule();
    mol.setRsChemElement(stoichMolChem);
    mol.setRole(MoleculeRole.REACTANT);
    stoich.setParentReaction(stoichChem);
    stoich.setMolecules(List.of(mol));
    mol.setStoichiometry(stoich);
    stoichDao.save(stoich);

    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    link.setSample(sample);
    link.setStoichiometryMolecule(mol);
    link.setQuantity(new QuantityInfo(BigDecimal.ONE, RSUnitDef.MILLI_LITRE.getId()));
    return link;
  }
}
