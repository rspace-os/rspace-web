package com.researchspace.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.RSChemElementDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.linkedelements.FieldParserImpl;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.FileProperty;
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
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
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
  @Mock FileStore filestore;
  User user;
  String exampleContent;
  RSChemElementManagerImpl chemElementManager;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    exampleContent = RSpaceTestUtils.getExampleFieldContent();
    chemElementManager = new RSChemElementManagerImpl(chemDao);
    chemElementManager.setChemistryFileManager(ecatChemistryFileManager);
    chemElementManager.setFieldManager(mockFieldMgr);
    chemElementManager.setPermissionUtils(mockPermissionUtils);
    chemElementManager.setFieldParser(new FieldParserImpl());
    chemElementManager.setRsChemElementDao(chemDao);
    chemElementManager.setRecordAdapter(recordAdapter);
    chemElementManager.setChemistryProvider(chemistryProvider);
    chemElementManager.setFileStore(filestore);
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
    assertEquals(chemElement, chemElementManager.get(1L, user));
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
  public void testGetChemSearchedItemsWithSearchCutoff() throws Exception {
    // mock 4 chem elements, each with separate doc to which user has access to
    final Long fieldID1 = 11L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID1, 1L);
    final StructuredDocument sd1 = TestFactory.createAnySD();
    when(mockPermissionUtils.isPermitted(sd1, PermissionType.READ, user)).thenReturn(true);
    when(mockFieldMgr.get(fieldID1, user)).thenReturn(setUpField(fieldID1, sd1));

    final Long fieldID2 = 12L;
    RSChemElement chemElement2 = TestFactory.createChemElement(fieldID2, 1L);
    final StructuredDocument sd2 = TestFactory.createAnySD();
    when(mockPermissionUtils.isPermitted(sd2, PermissionType.READ, user)).thenReturn(true);
    when(mockFieldMgr.get(fieldID2, user)).thenReturn(setUpField(fieldID2, sd2));

    final Long fieldID3 = 13L;
    RSChemElement chemElement3 = TestFactory.createChemElement(fieldID3, 1L);
    final StructuredDocument sd3 = TestFactory.createAnySD();
    when(mockPermissionUtils.isPermitted(sd3, PermissionType.READ, user)).thenReturn(true);
    when(mockFieldMgr.get(fieldID3, user)).thenReturn(setUpField(fieldID3, sd3));

    final Long fieldID4 = 14L;
    RSChemElement chemElement4 = TestFactory.createChemElement(fieldID4, 1L);
    final StructuredDocument sd4 = TestFactory.createAnySD();
    when(mockPermissionUtils.isPermitted(sd4, PermissionType.READ, user)).thenReturn(true);
    when(mockFieldMgr.get(fieldID4, user)).thenReturn(setUpField(fieldID4, sd4));

    final List<Long> results =
        List.of(
            chemElement.getId(), chemElement2.getId(), chemElement3.getId(), chemElement4.getId());
    ChemicalSearchResultsDTO res =
        ChemicalSearchResultsDTO.builder().chemicalHits(results).totalHits(2).build();
    when(chemistryProvider.search(anyString(), anyString())).thenReturn(res);
    when(chemDao.getChemElementsForChemIds(res))
        .thenReturn(List.of(chemElement, chemElement2, chemElement3, chemElement4));

    // no cutoff
    int searchHitsNumber = chemElementManager.search("", "", null, user).size();
    assertEquals(4, searchHitsNumber);

    // cutoff at 3 - manager will only return 3 elems
    searchHitsNumber = chemElementManager.search("", "", 3, user).size();
    assertEquals(3, searchHitsNumber);
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
    verify(mockFieldMgr, Mockito.never()).get(fieldID, user);
  }

  @Test
  public void testGetChemSearchedNoField() throws Exception {
    final Long fieldID = 1L;
    RSChemElement chemElement = TestFactory.createChemElement(fieldID, 1L);
    final StructuredDocument sd = TestFactory.createAnySD();
    when(mockFieldMgr.get(fieldID, user)).thenReturn(Optional.ofNullable(null));
    assertEquals(0, getNumberSearchHits(chemElement));
  }

  @Test
  public void whenSmilesConversionSucceeds_thenSmilesIsSetInRSChemElement() throws IOException {
    EcatChemistryFile chemFile = createMockChemFile();
    String expectedDefaultFormat = "defaultFormatString";
    String expectedSmiles = "C1=CC=CC=C1";
    ChemElementsFormat defaultFormat = ChemElementsFormat.MOL;

    ArgumentCaptor<RSChemElement> chemElementCaptor = ArgumentCaptor.forClass(RSChemElement.class);

    when(chemistryProvider.defaultFormat()).thenReturn(defaultFormat);
    when(chemistryProvider.convert(
            chemFile.getChemString(), chemFile.getExtension(), defaultFormat.getLabel()))
        .thenReturn(expectedDefaultFormat);
    when(chemistryProvider.convert(chemFile.getChemString(), chemFile.getExtension(), "smiles"))
        .thenReturn(expectedSmiles);
    when(chemistryProvider.exportToImage(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});
    when(chemDao.save(any(RSChemElement.class))).thenReturn(new RSChemElement());
    when(filestore.createAndSaveFileProperty(any(), any(), anyString(), any()))
        .thenReturn(new FileProperty());

    chemElementManager.generateRsChemElementForNewlyUploadedChemistryFile(chemFile, user);

    verify(chemDao, times(2)).save(chemElementCaptor.capture());
    RSChemElement savedElement = chemElementCaptor.getAllValues().get(0);
    assertThat(savedElement.getSmilesString(), is(equalTo(expectedSmiles)));
  }

  @Test
  public void whenSmilesConversionFails_thenChemIsSavedAndSmilesIsNull() throws IOException {
    EcatChemistryFile chemFile = createMockChemFile();
    String expectedDefaultFormat = "defaultFormatString";
    ChemElementsFormat defaultFormat = ChemElementsFormat.MOL;

    ArgumentCaptor<RSChemElement> chemElementCaptor = ArgumentCaptor.forClass(RSChemElement.class);

    when(chemistryProvider.defaultFormat()).thenReturn(defaultFormat);
    when(chemistryProvider.convert(
            chemFile.getChemString(), chemFile.getExtension(), defaultFormat.getLabel()))
        .thenReturn(expectedDefaultFormat);
    when(chemistryProvider.convert(chemFile.getChemString(), chemFile.getExtension(), "smiles"))
        .thenThrow(new RuntimeException("SMILES conversion failed"));
    when(chemistryProvider.exportToImage(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});
    when(chemDao.save(any(RSChemElement.class))).thenReturn(new RSChemElement());
    when(filestore.createAndSaveFileProperty(any(), any(), anyString(), any()))
        .thenReturn(new FileProperty());

    chemElementManager.generateRsChemElementForNewlyUploadedChemistryFile(chemFile, user);

    verify(chemDao, times(2)).save(chemElementCaptor.capture());
    RSChemElement savedElement = chemElementCaptor.getAllValues().get(0);
    assertNull(savedElement.getSmilesString());
  }

  private EcatChemistryFile createMockChemFile() {
    EcatChemistryFile file = new EcatChemistryFile();
    file.setId(1L);
    file.setChemString("mockChemString");
    file.setExtension("mol");

    return file;
  }

  private int getNumberSearchHits(final RSChemElement chemElement) {
    final List<Long> results = List.of(chemElement.getId());
    ChemicalSearchResultsDTO res =
        ChemicalSearchResultsDTO.builder().chemicalHits(results).totalHits(1).build();

    when(chemistryProvider.search(anyString(), anyString())).thenReturn(res);
    when(chemDao.getChemElementsForChemIds(res)).thenReturn(List.of(chemElement));
    return chemElementManager.search("ignored", "ignored", null, user).size();
  }
}
