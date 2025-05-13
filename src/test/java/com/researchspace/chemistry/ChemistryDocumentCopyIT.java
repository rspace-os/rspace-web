package com.researchspace.chemistry;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@Ignore(
    "Requires chemistry service to run. See"
        + " https://documentation.researchspace.com/article/1jbygguzoa")
@TestPropertySource(
    properties = {"chemistry.service.url=http://localhost:8090", "chemistry.provider=indigo"})
public class ChemistryDocumentCopyIT extends RealTransactionSpringTestBase {

  @Autowired RecordManager recordManager;

  @Autowired RSChemElementManager chemElementManager;

  @Test
  public void testCopiedDocumentsAreChemistrySearchable() throws Exception {
    User user = createInitAndLoginAnyUser();
    RSForm form = createAnyForm(user);

    // create doc with chemical structure
    StructuredDocument doc = createDocumentInFolder(user.getRootFolder(), form, user);
    RSChemElement chem = addChemStructureToField(doc.getFields().get(0), user);

    // confirm 1 search result when searching for the chemical structure
    List<ChemSearchedItem> searchHits =
        chemElementManager.search(chem.getSmilesString(), "EXACT", 1000, user);
    assertEquals(1, searchHits.size());

    // copy the doc
    recordManager.copy(doc.getId(), "copy", user, user.getRootFolder().getId());

    // confirm 2 search results
    List<ChemSearchedItem> searchHitsAfterCopy =
        chemElementManager.search(chem.getSmilesString(), "EXACT", 1000, user);
    assertEquals(2, searchHitsAfterCopy.size());
  }
}
