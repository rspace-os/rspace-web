package com.researchspace.linkedelements;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.FieldManager;
import com.researchspace.service.impl.FieldLinksEntitySyncImpl;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FieldLinksEntitiesSynchronizerTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  @Mock FieldParser parser;
  @Mock FieldDao fieldDao;
  @Mock FieldManager fieldMgr;
  @Mock InternalLinkDao internalLinkDao;
  @Mock EcatImageDao ecatimagedao;

  User anyUser = TestFactory.createAnyUser("any");
  FieldContents emptyContent = new FieldContents();
  FieldContents contentsWithLink = new FieldContents();

  String link = "/globalId/SD1234";
  String emflink = "/globalId/GL1234";
  Long sourceId = 123L;
  Long targetId = 1234L;

  StructuredDocument sourceDoc, targetDoc;
  Field f1;
  @InjectMocks FieldLinksEntitySyncImpl sync;

  @Before
  public void setup() {
    sourceDoc = TestFactory.createAnySD();
    targetDoc = TestFactory.createAnySD();
    f1 = sourceDoc.getFields().get(0);
    sourceDoc.setId(sourceId);
    targetDoc.setId(targetId);
  }

  @Test
  public void testSyncTextLinkWithEntities() throws IOException {
    final Field permanentField1 = TestFactory.createTextFieldForm().createNewFieldFromForm();
    final Field tempField1 = (Field) permanentField1.shallowCopy();
    String incomingData = tempField1.getFieldData();
    final Field f2 = TestFactory.createTextFieldForm().createNewFieldFromForm();
    final FieldContentDelta noChangedelta =
        new FieldContentDelta(new FieldContents(), new FieldContents());
    when(parser.findFieldElementChanges(tempField1.getFieldData(), incomingData))
        .thenReturn(noChangedelta);
    // no change in content - nothing saved
    sync.syncFieldWithEntitiesOnautosave(permanentField1, tempField1, incomingData, anyUser);
    verify(internalLinkDao, Mockito.never()).saveInternalLink(Mockito.anyLong(), Mockito.anyLong());
    verify(fieldDao, never()).save(permanentField1);

    // now set a removed element:
    EcatImage el = TestFactory.createEcatImage(2L);
    FieldContents removed = new FieldContents();
    removed.getElements(EcatImage.class).add(new FieldElementLinkPair<EcatImage>(el, ""));
    // f1 has an image. we'll simulate that the text link has been
    // removed....
    permanentField1.addMediaFileLink(el);
    assertTrue(" media link was not added!", permanentField1.getLinkedMediaFiles().size() == 1);
    // we'll simulate that the text link has been removed....
    final FieldContentDelta removedDelta = new FieldContentDelta(new FieldContents(), removed);
    Mockito.when(parser.findFieldElementChanges(permanentField1.getFieldData(), incomingData))
        .thenReturn(removedDelta);
    Mockito.when(parser.findFieldElementsInContent(Mockito.anyString()))
        .thenReturn(new FieldContents());
    sync.syncFieldWithEntitiesOnautosave(permanentField1, tempField1, incomingData, anyUser);
    assertTrue(
        "media link was not marked deleted!",
        permanentField1.getLinkedMediaFiles().iterator().next().isDeleted());
    verify(fieldDao).save(permanentField1);
  }

  @Test
  public void testSyncInternalLinkAddAndRemove() throws IOException {

    RecordInformation targetRecordInfo = new RecordInformation(targetDoc);
    contentsWithLink.addElement(targetRecordInfo, link, RecordInformation.class);

    //  adds a link when it's in delta
    final FieldContentDelta addedLinkDelta = new FieldContentDelta(contentsWithLink, emptyContent);
    when(parser.findFieldElementChanges(f1.getFieldData(), link)).thenReturn(addedLinkDelta);
    sync.syncFieldWithEntitiesOnautosave(f1, (Field) f1.shallowCopy(), link, anyUser);
    verify(internalLinkDao).saveInternalLink(sourceId, targetId);

    //  tries to remove  a link when it's in delta
    final FieldContentDelta removedLinkDelta =
        new FieldContentDelta(emptyContent, contentsWithLink);
    when(parser.findFieldElementChanges(f1.getFieldData(), "")).thenReturn(removedLinkDelta);
    when(parser.findFieldElementsInContent(Mockito.anyString())).thenReturn(contentsWithLink);
    sync.syncFieldWithEntitiesOnautosave(f1, (Field) f1.shallowCopy(), "", anyUser);
    assertInternalLinkDeleted();
  }

  @Test
  public void syncInternalLinkDoesntRemoveLinkIfExistsInOtherField() throws IOException {
    RecordInformation targetRecordInfo = new RecordInformation(targetDoc);
    contentsWithLink.addElement(targetRecordInfo, link, RecordInformation.class);
    final FieldContentDelta removedLinkDelta =
        new FieldContentDelta(emptyContent, contentsWithLink);
    when(parser.findFieldElementsInContent(Mockito.anyString())).thenReturn(contentsWithLink);
    when(parser.findFieldElementChanges(f1.getFieldData(), "")).thenReturn(removedLinkDelta);
    Field extraTextField = new TextField(new TextFieldForm());
    extraTextField.setData("extraFieldContent");
    sourceDoc.addField(extraTextField);
    sync.syncFieldWithEntitiesOnautosave(f1, (Field) f1.shallowCopy(), "", anyUser);
    assertInternalLinkNotDeleted();
  }

  @Test
  public void syncEMFDoesntRemoveIfExistsMultipleTimesInField() throws IOException {
    // removing 1 emf in field
    EcatImage targetRecordInfo = TestFactory.createEcatImage(2L);
    contentsWithLink
        .getElements(EcatImage.class)
        .add(new FieldElementLinkPair<EcatImage>(targetRecordInfo, ""));
    // but there are 2 emf in field
    FieldContents remainingemf = new FieldContents();
    remainingemf
        .getElements(EcatImage.class)
        .add(new FieldElementLinkPair<EcatImage>(targetRecordInfo, ""));
    remainingemf
        .getElements(EcatImage.class)
        .add(new FieldElementLinkPair<EcatImage>(targetRecordInfo, ""));
    f1.addMediaFileLink(targetRecordInfo);
    final FieldContentDelta removedemfDelta = new FieldContentDelta(emptyContent, contentsWithLink);
    when(parser.findFieldElementChanges(f1.getFieldData(), "")).thenReturn(removedemfDelta);
    when(parser.findFieldElementsInContent(Mockito.anyString())).thenReturn(remainingemf);

    sync.syncFieldWithEntitiesOnautosave(f1, (Field) f1.shallowCopy(), "", anyUser);

    assertEMFNotDeleted(targetRecordInfo);
    f1.removeMediaFileLink(targetRecordInfo);
  }

  @Test
  public void syncInternalLinkDoesntRemoveLinkIfExistsMultipleTimesInField() throws IOException {
    // removing 1 link in text
    RecordInformation targetRecordInfo = new RecordInformation(targetDoc);
    contentsWithLink.addElement(targetRecordInfo, link, RecordInformation.class);
    // but there are 2 links in text
    FieldContents remainingLinks = new FieldContents();
    remainingLinks.addElement(targetRecordInfo, link, RecordInformation.class);
    remainingLinks.addElement(targetRecordInfo, link, RecordInformation.class);

    final FieldContentDelta removedLinkDelta =
        new FieldContentDelta(emptyContent, contentsWithLink);
    when(parser.findFieldElementChanges(f1.getFieldData(), "")).thenReturn(removedLinkDelta);
    when(parser.findFieldElementsInContent(Mockito.anyString())).thenReturn(remainingLinks);

    sync.syncFieldWithEntitiesOnautosave(f1, (Field) f1.shallowCopy(), "", anyUser);
    assertInternalLinkNotDeleted();
  }

  private void assertInternalLinkNotDeleted() {
    verify(internalLinkDao, never()).deleteInternalLink(sourceId, targetId);
    Mockito.verifyZeroInteractions(internalLinkDao);
  }

  private void assertInternalLinkDeleted() {
    verify(internalLinkDao).deleteInternalLink(sourceId, targetId);
  }

  private void assertEMFNotDeleted(EcatMediaFile emf) {

    assertFalse(f1.getLinkedMediaFiles().iterator().next().isDeleted());
  }

  @Test
  public void syncInternalLinkremovingMultipleLinksToSameTargetAtOnce() throws IOException {
    // removing 2 links in text in 1 go
    RecordInformation targetRecordInfo = new RecordInformation(targetDoc);
    contentsWithLink.addElement(targetRecordInfo, link, RecordInformation.class);
    contentsWithLink.addElement(targetRecordInfo, link, RecordInformation.class);
    // but there are 2 links in text - we are removing both
    FieldContents remainingLinks = new FieldContents();
    remainingLinks.addElement(targetRecordInfo, link, RecordInformation.class);
    remainingLinks.addElement(targetRecordInfo, link, RecordInformation.class);

    final FieldContentDelta removedLinkDelta =
        new FieldContentDelta(emptyContent, contentsWithLink);
    when(parser.findFieldElementChanges(f1.getFieldData(), "")).thenReturn(removedLinkDelta);
    when(parser.findFieldElementsInContent(Mockito.anyString())).thenReturn(remainingLinks);

    sync.syncFieldWithEntitiesOnautosave(f1, (Field) f1.shallowCopy(), "", anyUser);
    assertInternalLinkDeleted();
  }
}
