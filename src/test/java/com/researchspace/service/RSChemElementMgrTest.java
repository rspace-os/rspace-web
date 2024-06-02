package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.researchspace.dao.RSChemElementDao;
import com.researchspace.linkedelements.FieldParserImpl;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.impl.RSChemElementManagerImpl;
import com.researchspace.testutils.RSpaceTestUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RSChemElementMgrTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock FieldManager mockFieldMgr;
  @Mock IPermissionUtils mockPermissionUtils;
  @Mock RSChemElementDao chemDao;
  @Mock EcatChemistryFileManager ecatChemistryFileManager;
  @Mock BaseRecordAdaptable recordAdapter;
  @Mock ChemistryProvider chemistryProvider;
  User user;
  String exampleContent;
  RSChemElementManagerImpl searcher;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    exampleContent = RSpaceTestUtils.getExampleFieldContent();
    searcher = new RSChemElementManagerImpl(chemDao);
    searcher.setChemistryFileManager(ecatChemistryFileManager);
    searcher.setFieldManager(mockFieldMgr);
    searcher.setPermissionUtils(mockPermissionUtils);
    searcher.setFieldParser(new FieldParserImpl());
    searcher.setRsChemElementDao(chemDao);
    searcher.setRecordAdapter(recordAdapter);
    searcher.setChemistryProvider(chemistryProvider);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testByIdChecksPermissions() throws Exception {
    final Long fieldID = 10L;
    final RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();

    final Optional<Field> expectedField = setUpField(fieldID, sd);
    final Optional<BaseRecord> sdopt = Optional.of(sd);
    when(recordAdapter.getAsBaseRecord(chemElement)).thenReturn(sdopt);
    when(chemDao.get(1L)).thenReturn(chemElement);
    when(mockPermissionUtils.isPermitted(sd, PermissionType.READ, user)).thenReturn(true);
    assertEquals(chemElement, searcher.get(1L, user));
  }

  @Test
  public void testGetChemSearchedItemsHappyCase() throws Exception {
    final Long fieldID = 10L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();

    final Optional<Field> expectedField = setUpField(fieldID, sd);
    when(mockPermissionUtils.isPermitted(sd, PermissionType.READ, user)).thenReturn(true);
    when(mockFieldMgr.get(fieldID, user)).thenReturn(expectedField);
    // happy case
    assertEquals(1, getNumberSearchHits(chemElement));
  }

  @Test
  public void testGetChemSearchedItemsNoPermission() throws Exception {
    final Long fieldID = 10L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();
    final Optional<Field> expectedField = setUpField(fieldID, sd);
    // no set permissions false, should be empty
    when(mockPermissionUtils.isPermitted(sd, PermissionType.READ, user)).thenReturn(false);
    when(mockFieldMgr.get(fieldID, user)).thenReturn(expectedField);
    assertEquals(0, getNumberSearchHits(chemElement));
  }

  private Optional<Field> setUpField(final Long fieldID, final StructuredDocument sd) {
    final Field expectedField = sd.getFields().get(0);
    expectedField.setFieldData(exampleContent);
    expectedField.setId(fieldID);
    return Optional.of(expectedField);
  }

  @Test
  public void testGetChemSearchedItemsNoLinkInText() throws Exception {
    final Long fieldID = 10L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();

    final Optional<Field> expectedField = setUpField(fieldID, sd);
    when(mockFieldMgr.get(fieldID, user)).thenReturn(expectedField);

    expectedField.get().setFieldData("no element in text");
    assertEquals(0, getNumberSearchHits(chemElement));
  }

  @Test
  public void testGetChemSearchedItemRecordDeleted() throws Exception {
    final Long fieldID = 10L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();
    sd.setRecordDeleted(true);

    final Optional<Field> expectedField = setUpField(fieldID, sd);
    when(mockFieldMgr.get(fieldID, user)).thenReturn(expectedField);
    assertEquals(0, getNumberSearchHits(chemElement));
  }

  @Test
  public void testGetChemSearchedNoFieldID() throws Exception {
    final Long fieldID = null;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();
    assertEquals(0, getNumberSearchHits(chemElement));
    Mockito.verify(mockFieldMgr, Mockito.never()).get(fieldID, user);
  }

  @Test
  public void testGetChemSearchedNoField() throws Exception {
    final Long fieldID = 1L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();
    when(mockFieldMgr.get(fieldID, user)).thenReturn(Optional.ofNullable(null));
    assertEquals(0, getNumberSearchHits(chemElement));
  }

  // simulate the absence of a link in text (e.g., link has been deleted)

  private int getNumberSearchHits(final RSChemElement chemElement) {

    final List results = Arrays.asList(new Long[] {chemElement.getId()});
    ChemicalSearchResultsDTO res =
        ChemicalSearchResultsDTO.builder().structureHits(results).totalHits(1).build();

    when(chemistryProvider.search(anyString(), anyString())).thenReturn(res);
    when(chemDao.getChemElementsForChemIds(res))
        .thenReturn(Arrays.asList(new RSChemElement[] {chemElement}));
    return searcher.search("ignored", "ignored", user).size();
  }
}
