package com.researchspace.linkedelements;

import static com.researchspace.model.field.FieldTestUtils.createTextField;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.linkedelements.RichTextUpdater.ImageURL;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldTestUtils;
import com.researchspace.model.field.TextField;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RichTextUpdaterTest extends SpringTransactionalTest {

  @Autowired private RichTextUpdater updater;

  private static File htmlFolder =
      new File("src/test/resources/TestResources/attachmentHTMLSnippets");

  private final String testCommentStr =
      "<p>fdsfdsf<img id=\"11\" class=\"commentIcon\" src=\"../../../images/commentIcon.gif\""
          + " alt=\"image\" />fdsfdsfdsf<img id=\"12\" class=\"commentIcon\""
          + " src=\"../../../images/commentIcon.gif\" alt=\"image\" /></p>";
  // using ' not " to delimit attributes
  private String testCommentStr2 =
      "<p>fdsfdsf<img id='11' class='commentIcon' src='../../../images/commentIcon.gif' alt='image'"
          + " />fdsfdsfdsf<img id='12' class='commentIcon' src='../../../images/commentIcon.gif'"
          + " alt='image' /></p>";
  // only comment, no text
  private String testCommentStr3 =
      "<img id='11' class='commentIcon' src='../../../images/commentIcon.gif' alt='image'"
          + " />fdsfdsfdsf<img id='12' class='commentIcon' src='../../../images/commentIcon.gif'"
          + " alt='image'/>";
  // no class attributes, should be unaltered.
  private String testCommentStr4 =
      "<img id='11'  src='../../../images/commentIcon.gif' alt='image' />fdsfdsfdsf<img id='12' "
          + " src='../../../images/commentIcon.gif' alt='image'/>";

  // v.28 and v.29 annotations
  private static final String ANNOTATED_IMG_V28 =
      "<p><img id=\"1-1\" class=\"imageDropped\" src=\"/image/getImage/1-1/123\" height=\"50\""
          + " width=\"50\" alt=\"image\" /></p>";
  private static final String ANNOTATED_IMG_V28_EXPECTED_START =
      "<p><img id=\"2-1\" class=\"imageDropped\" src=\"/image/getImage/2-1";

  private static final String ANNOTATED_IMG_V29 =
      "<p><img id=\"1-1\" class=\"imageDropped\" data-id=\"11\" data-type=\"annotation\""
          + " src=\"/image/getAnnotation/1-1/123\" height=\"50\" width=\"50\" alt=\"image\" /></p>";
  private static final String ANNOTATED_IMG_V29_EXPECTED_START =
      "<p><img id=\"2-1\" class=\"imageDropped\" data-id=\"12\" data-type=\"annotation\""
          + " src=\"/image/getAnnotation/12/";

  @Test
  public void testUpdateCommentIdsInCopy() {
    Map<Long, Long> oldKey2NewKey = new HashMap<Long, Long>();
    oldKey2NewKey.put(11L, 1L);
    oldKey2NewKey.put(12L, 2L);
    String replaced = updater.updateCommentIdsInCopy(oldKey2NewKey, testCommentStr);
    assertTrue(testAttributeValue("id", replaced, "1"));

    String replaced2 = updater.updateCommentIdsInCopy(oldKey2NewKey, testCommentStr2);
    assertTrue(testAttributeValue("id", replaced2, "1"));

    String replaced3 = updater.updateCommentIdsInCopy(oldKey2NewKey, testCommentStr3);
    assertTrue(testAttributeValue("id", replaced3, "2"));

    String replaced4 = updater.updateCommentIdsInCopy(oldKey2NewKey, testCommentStr4);
    assertTrue(testAttributeValue("id", replaced4, "11"));
  }

  private boolean testAttributeValue(String attName, String test, String expectedValue) {
    Document d = Jsoup.parse(test);
    Elements el = d.getElementsByAttributeValue(attName, expectedValue);
    return el.size() > 0;
  }

  @Test
  public void replaceImageSrcURL() throws IOException {
    String IMAGE_HTML_EXAMPLE = readHtml("imageHTML1.html");
    String fieldData = "blah" + IMAGE_HTML_EXAMPLE + " more ..";
    final String REPLACEMENT_PNG = "replacement.png";
    String newData = updater.replaceImageSrcURL("5079041-9214", fieldData, REPLACEMENT_PNG);
    // now check that HTML was altered
    Document altered = Jsoup.parse(newData);
    assertFalse(altered.getElementsByAttributeValue("src", REPLACEMENT_PNG).isEmpty());

    // now check that if id doesn't match, no replacement:
    String newData2 = updater.replaceImageSrcURL("wrongId", fieldData, REPLACEMENT_PNG);
    // now check that HTML was altered
    Document altered2 = Jsoup.parse(newData2);
    assertTrue(altered2.getElementsByAttributeValue("src", REPLACEMENT_PNG).isEmpty());
  }

  @Test
  public void replaceImageSrc() throws IOException {
    String fieldData = "blah \n<img src=\"originalUrl\"> \n<img src=\"otherUrl\">";

    // skip the replacement if url to replace not found
    String urlNotFoundData = updater.replaceImageSrc("unexistingUrl", "anotherUrl", fieldData);
    assertEquals(fieldData, urlNotFoundData);

    // skip the replacement if url to replace is empty (RSPAC-1765)
    String urlInvalidData = updater.replaceImageSrc("", "anotherUrl", fieldData);
    assertEquals(fieldData, urlInvalidData);

    // check valid replacement
    String newData = updater.replaceImageSrc("originalUrl", "newUrl", fieldData);
    assertEquals("blah \n<img src=\"newUrl\"> \n<img src=\"otherUrl\">", newData);
  }

  @Test
  public void replaceLinkedRecordURLLink() throws IOException {
    final String LINKEDRECORD_EXAMPLE = readHtml("linkedRecordExample.html");
    String fieldData = "blah" + LINKEDRECORD_EXAMPLE + " more ..";
    final String REPLACEMENT_LINK = "../doc4670.html";

    // check replacement of internal link
    GlobalIdentifier recordOid = new GlobalIdentifier(GlobalIdPrefix.SD, 4670L, null);
    String newData = updater.replaceLinkedRecordURL(recordOid, fieldData, REPLACEMENT_LINK);
    Document altered = Jsoup.parse(newData);
    assertEquals(1, altered.getElementsByAttributeValue("href", REPLACEMENT_LINK).size());
    assertEquals(
        "Sensitivity of Cancer cell line XYZ to Drug A",
        altered.getElementsByAttributeValue("href", REPLACEMENT_LINK).get(0).text());

    // check replacement of versioned link
    GlobalIdentifier versionedOid = new GlobalIdentifier(GlobalIdPrefix.SD, 4670L, 2L);
    String newData2 = updater.replaceLinkedRecordURL(versionedOid, fieldData, REPLACEMENT_LINK);
    Document altered2 = Jsoup.parse(newData2);
    assertEquals(1, altered2.getElementsByAttributeValue("href", REPLACEMENT_LINK).size());
    assertEquals(
        "SD4670v2: Sensitivity of Cancer cell line XYZ to Drug A",
        altered2.getElementsByAttributeValue("href", REPLACEMENT_LINK).get(0).text());

    // now check that if id doesn't match, no replacement:
    GlobalIdentifier otherOid = new GlobalIdentifier(GlobalIdPrefix.SD, 15L, null);
    String newData3 = updater.replaceLinkedRecordURL(otherOid, fieldData, REPLACEMENT_LINK);
    Document altered3 = Jsoup.parse(newData3);
    assertEquals(0, altered3.getElementsByAttributeValue("href", REPLACEMENT_LINK).size());
  }

  @Test
  public void testUpdateSketchAnnotation() {
    String url =
        "<img id=\"32769\" class=\"sketch\" src=\"/image/getImageSketch/32769/12345\" alt=\"image\""
            + " />";
    String url2 =
        "<img id=\"32770\" class=\"sketch\" src=\"/image/getImageSketch/32770/12345\" alt=\"image\""
            + " />";

    String test = "blah " + url + "blah" + url2 + "blah";
    Map<Long, Long> oldKey2NewKey = new HashMap<Long, Long>();
    oldKey2NewKey.put(32769L, 3L);
    oldKey2NewKey.put(32770L, 4L);
    String updated = updater.updateSketchIdsInCopy(oldKey2NewKey, test);
    System.err.println(updated);

    assertFalse(updated.contains("32769"));
    assertFalse(updated.contains("32770"));
    //		assertTrue(testAttributeValue("src", updated, "/image/getImageSketch/"));

    assertTrue(updated.contains("/image/getImageSketch/3"));
    assertTrue(updated.contains("/image/getImageSketch/4"));
  }

  @Test
  public void testImgURLParsing() {
    // check an image URL
    ImageURL imageurl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField("../../../getImage/32-12/1234", "");
    assertTrue(imageurl.isImgURL());
    assertFalse(imageurl.isSketchURL());

    assertEquals(32L, imageurl.getParentId().longValue());
    assertEquals(12L, imageurl.getImageId().longValue());

    // revisioned image URL
    ImageURL revisionedImageUrl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField(
            "../../../getImage/32-12/1234?revision=23", "");
    assertTrue(revisionedImageUrl.isImgURL());
    assertEquals(23L, revisionedImageUrl.getRevision().longValue());

    // check an image sketch URL.
    ImageURL imageSketchurl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField(
            "../../../getImageSketch/33-114/1234", "");
    assertFalse(imageSketchurl.isImgURL());
    assertTrue(imageSketchurl.isSketchURL());

    assertEquals(33L, imageSketchurl.getParentId().longValue());
    assertEquals(114L, imageSketchurl.getImageId().longValue());

    assertNull(
        RichTextUpdater.parseImageIdFromSrcURLInTextField("../../../UNKNOWN/33-114/123", ""));
    // and from thumbnail links
    imageurl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField(
            "/thumbnail/data?sourceType=IMAGE&sourceId=10316&sourceParentId=557107&width=627&height=326&time=1406822005870",
            "IMAGE");
    assertTrue(imageurl.isImgURL());
    assertEquals(10316L, imageurl.getImageId().longValue());
    assertEquals(557107L, imageurl.getParentId().longValue());

    // revisioned thumbnail link
    revisionedImageUrl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField(
            "/thumbnail/data?sourceType=IMAGE&sourceId=10316&sourceParentId=557107&width=627&height=326&time=1406822005870&revision=23",
            "IMAGE");
    assertTrue(revisionedImageUrl.isImgURL());
    assertEquals(23L, revisionedImageUrl.getRevision().longValue());

    // and new sketch types:
    imageurl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField(
            "/image/getImageSketch/4620289/1437999702562?width=271&amp;height=219", "");
    assertTrue(imageurl.isSketchURL());
    assertEquals(4620289, imageurl.getImageId().longValue());
    assertEquals(null, imageurl.getParentId());

    // and from old chem links:
    imageurl =
        RichTextUpdater.parseImageIdFromSrcURLInTextField(
            "/chemical/getImageChem/3080192/1438069881658", "");
    assertFalse(imageurl.isSketchURL());
    assertEquals(3080192, imageurl.getImageId().longValue());
    assertEquals(null, imageurl.getParentId());

    // and from math equation link:
    imageurl = RichTextUpdater.parseImageIdFromSrcURLInTextField("/svg/12", "");
    assertEquals(12, imageurl.getImageId().longValue());
  }

  @Test
  public void testUpdateImageIdsInLink() {
    // set up 2 images for insertion into field data
    EcatImage anyImage1 = new EcatImage();
    EcatImage anyImage2 = new EcatImage();
    TextField tf = createATextField();
    anyImage1.setId(1L);
    anyImage2.setId(10L);
    String ORIG_ID = 2L + "";
    // String COPY_ID=3L+"";
    tf.setId(2L);
    String url1 = updater.generateURLStringForEcatImageLink(anyImage1, tf.getId() + "");
    String url2 = updater.generateURLStringForEcatImageLink(anyImage2, tf.getId() + "");

    // this is what we'll copy
    tf.setFieldData("blah" + url1 + "mid blah" + url2 + "endblah");
    TextField copy = tf.shallowCopy();
    copy.setId(3L);

    Map<Long, Long> oldKey2NewKey = new HashMap<Long, Long>();
    oldKey2NewKey.put(2L, 3L);

    String updated =
        updater.updateImageIdsAndAnnoIdsInCopy(oldKey2NewKey, null, copy.getFieldData());
    System.err.println(updated);
    // ORIG_ID Could be on the String because now src = /images/getImage/3-1/{ramdom long}
    assertFalse(updated.contains("id=" + ORIG_ID));
    assertTrue(testAttributeValue("id", updated, "3-1"));
    assertTrue(testAttributeValue("id", updated, "3-10"));
    assertTrue(updated.contains("src=\"/image/getImage/3-1/"));
    assertTrue(updated.contains("src=\"/image/getImage/3-10/"));
  }

  private TextField createATextField() {
    return new TextField(new TextFieldForm());
  }

  @Test
  public void testUpdateAnnotationIdsForV28AndV29Links() {

    Map<Long, Long> oldFieldId2NewFieldId = new HashMap<Long, Long>();
    oldFieldId2NewFieldId.put(1L, 2L);
    Map<Long, Long> oldAnnoId2NewAnnoId = new HashMap<Long, Long>();
    oldAnnoId2NewAnnoId.put(11L, 12L);

    String updatedV28 =
        updater.updateImageIdsAndAnnoIdsInCopy(
            oldFieldId2NewFieldId, oldAnnoId2NewAnnoId, ANNOTATED_IMG_V28);
    assertTrue(
        "updatedV28: " + updatedV28, updatedV28.startsWith(ANNOTATED_IMG_V28_EXPECTED_START));

    String updatedV29 =
        updater.updateImageIdsAndAnnoIdsInCopy(
            oldFieldId2NewFieldId, oldAnnoId2NewAnnoId, ANNOTATED_IMG_V29);
    assertTrue(
        "updatedV29: " + updatedV29, updatedV29.startsWith(ANNOTATED_IMG_V29_EXPECTED_START));
  }

  @Test
  public void testUpdateAndRemoveRevisionsFromChemElement() throws URISyntaxException {
    String chemExample = updater.generateURLStringForRSChemElementLink(1L, 2L, 50, 50);
    TextField tf = createATextField();
    tf.setId(1L);
    tf.setFieldData(chemExample);

    // there are no revisions to remove yet, so this returns false
    assertFalse(updater.removeRevisionsFromChemWithId(tf, 1 + ""));
    updater.updateLinksWithRevisions(tf, 23);
    assertTrue(tf.getFieldData().contains("revision=23"));
    Elements els = updater.getElementsOfType(tf.getFieldData(), "img");
    String newSrcURI = els.listIterator().next().attr("src");
    new URI(newSrcURI); // should be valid URI

    updater.updateLinksWithRevisions(tf, 24); // add twice, should still be valid
    Elements els2 = updater.getElementsOfType(tf.getFieldData(), "img");
    String newSrcURI2 = els2.listIterator().next().attr("src");
    new URI(newSrcURI2); // should be valid URI
    final String EXPECTED_NEW_REVISION = "revision=24";
    assertTrue(tf.getFieldData().contains(EXPECTED_NEW_REVISION));

    // now remove by wrong id; revision is still there
    assertFalse(updater.removeRevisionsFromChemWithId(tf, 12 + ""));
    assertTrue(tf.getFieldData().contains(EXPECTED_NEW_REVISION));
    // remove using correct ID, is  removed.
    assertTrue(updater.removeRevisionsFromChemWithId(tf, 1 + ""));
    assertFalse(tf.getFieldData().contains(EXPECTED_NEW_REVISION));

    // test updating doc attachment with revision number
    EcatDocumentFile file = TestFactory.createEcatDocument(1L, TestFactory.createAnyUser("any"));
    tf.setFieldData(updater.generateURLString(file));
    updater.updateLinksWithRevisions(tf, 25);
    Elements els3 = updater.getElementsOfType(tf.getFieldData(), "a");
    String newSrcURI3 = els3.listIterator().next().attr("href");
    assertTrue(newSrcURI3.endsWith("revision=25"));
  }

  @Test
  public void updateGalleryLinksWithRevision() throws IOException {

    String imageHtml = readHtml("imageHTML1.html");
    String videoHtml = readHtml("videoAudio.html");
    String videoHtml2 = readHtml("videoAudio2.html");
    String attachmentHtml = readHtml("attachmentDiv.html");

    String initialData =
        "test image: "
            + imageHtml
            + " audio/video without player: "
            + videoHtml
            + " audio/video with player: "
            + videoHtml2
            + " doc: "
            + attachmentHtml;

    // let's apply jsoup-formatting, so we can compare to initialData at the end of the test
    Document jSoupDoc = Jsoup.parse(initialData);
    initialData = jSoupDoc.body().html();

    assertFalse(initialData.contains("revision"));

    Field field = createTextField();
    field.setData(initialData);
    boolean fieldUpdated = updater.updateLinksWithRevisions(field, 23);
    assertTrue(fieldUpdated);

    // check updated content
    String updatedData = field.getData();
    // image
    assertTrue(
        updatedData.contains(
            "src=\"/thumbnail/data?sourceType=IMAGE&amp;sourceId=9214&amp;sourceParentId=5079041&amp;width=171&amp;height=147&amp;time=1406822005870&amp;revision=23\""));
    // audio/video - no player
    assertTrue(updatedData.contains("href=\"/Streamfile/79000?revision=23\""));
    assertTrue(updatedData.contains("href=\"/Streamfile/81000?revision=23\""));
    // audio/video - with flash player
    assertTrue(updatedData.contains("href=\"/Streamfile/65600?revision=23\""));
    assertTrue(
        updatedData.contains(
            "file=%2FStreamfile%2F65600%2FJames_Bond_Theme_from_Quantum_of_Solace.mp4%3Frevision=23&amp;controlbar.position=over\""));
    assertTrue(updatedData.contains("href=\"/Streamfile/65620?revision=23\""));
    assertTrue(
        updatedData.contains(
            "file=%2FStreamfile%2F65620%2Fslowik.mp3%3Frevision=23&amp;controlbar.position=over"));
    // misc attachment
    assertTrue(
        updatedData.contains(
            "href=\"/Streamfile/45/46533307407_commons-collections-3.2.1.jar?revision=23\""));
    // also, data-revision attribute should be present for every tag
    assertEquals(6, StringUtils.countMatches(updatedData, "data-rsrevision"));

    // let's run the same update again - the field shouldn't be marked as updated
    fieldUpdated = updater.updateLinksWithRevisions(field, 23);
    assertFalse(fieldUpdated);

    // now remove the revision from links
    fieldUpdated = updater.updateLinksWithRevisions(field, null);
    assertTrue(fieldUpdated);
    String clearedData = field.getData();
    assertFalse("data still contains 'revision': " + clearedData, clearedData.contains("revision"));
    assertEquals(initialData, clearedData);

    // let's run the same update again - the field shouldn't be marked as updated
    fieldUpdated = updater.updateLinksWithRevisions(field, null);
    assertFalse(fieldUpdated);
  }

  @Test
  public void testUpdateChemId() {

    String orgImg =
        "<img id=\"123\" class=\"chem\" src=\"/chemical/getImageChem/123/1\" alt=\"image\"/>";
    Map<Long, Long> oldKey2NewKey = new HashMap<Long, Long>();
    oldKey2NewKey.put(123L, 124L);
    String updatedText = updater.updateChemIdsInCopy(oldKey2NewKey, orgImg);

    String newImgStartsWith = "<img id=\"124\" class=\"chem\" src=\"/chemical/getImageChem/124/";
    String newImgEndsWith = " alt=\"image\">";
    assertTrue(updatedText, updatedText.startsWith(newImgStartsWith));
    assertTrue(updatedText, updatedText.endsWith(newImgEndsWith));
  }

  @Test
  public void testUpdateMathId() {
    String original =
        "<div/>" + updater.generateURLStringForRSMathLink(2L, "x^2", "1ex", "1ex") + "<p/>";
    Map<Long, Long> oldKey2NewKey = new HashMap<Long, Long>();
    oldKey2NewKey.put(2L, 3L);
    String updatedText = updater.updateMathIdsInCopy(oldKey2NewKey, original);
    Pattern expected = Pattern.compile("data\\-mathid\\s*=\\s*\"3\"");
    Matcher m = expected.matcher(updatedText);
    assertTrue("updated data-mathid not found in " + updatedText, m.find());

    Pattern expectedSvg = Pattern.compile("data=\"/svg/3\"");
    Matcher m2 = expectedSvg.matcher(updatedText);
    assertTrue("updated object url not found in " + updatedText, m2.find());
  }

  @Test
  public void testRSMathRevisions() {
    String mathExample =
        "<div/>" + updater.generateURLStringForRSMathLink(2L, "x^2", "1ex", "1ex") + "<p/>";
    TextField tf = createATextField();
    tf.setId(1L);
    tf.setFieldData(mathExample);

    updater.updateLinksWithRevisions(tf, 23);
    assertTrue(
        "math object source should contain revision number",
        tf.getFieldData().contains("data=\"/svg/2?revision=23\""));
  }

  @Test
  public void testReplaceTableWithImage() {
    // test replacing one node with another.
    String html =
        "<p>some content</p><div><img class='videoDropped' id='12-34' src='xxx'/><img"
            + " class='videoDropped' id='12-35' src='xxx'/></div>";
    String replaced = updater.replaceAVTableWithLinkToResource(html, "12-34", "x.mp4", "name");
    assertTrue(replaced, replaced.contains("x.mp4"));
  }

  @Test
  public void testInsertChemImageWithFileLink() {
    String imgToAppendTo =
        "<img id=\"123\" class=\"chem\" src=\"/chemical/getImageChem/123/1\" alt=\"image\""
            + " data-chemfileid=\"123\"/>";
    String appended = updater.insertHrefToChemistryFile(imgToAppendTo, "123", "chem.mol", "name");
    String newImgStartsWith = "<img id=\"123\"";
    String newImgEndsWith = "<p><a href=\"chem.mol\">name</a></p>";
    assertTrue(appended, appended.startsWith(newImgStartsWith));
    assertTrue(appended, appended.endsWith(newImgEndsWith));
  }

  @Test
  public void testUpdateExternalLinkAttaIconLink() {

    String original = updater.generateAnyURLStringForExternalDocLink();
    assertVelocityVariablesReplaced(original);
    assertFalse(original, original.contains(MediaUtils.APP_ICON_FOLDER));
    String ICON_LOCATION = "/images/icons";

    Field field = createTextField();
    field.setFieldData(original);
    field = updater.updateAttachmentIcons(field);
    assertTrue(field.getFieldData(), field.getFieldData().contains(MediaUtils.APP_ICON_FOLDER));
    assertTrue(field.getFieldData(), field.getFieldData().contains(ICON_LOCATION));
    assertFalse(field.getFieldData().contains(RichTextUpdater.DUMMY_ICON_PATH));
  }

  @Test
  public void testUpdateAVIDInCopy() {
    EcatVideo video = TestFactory.createEcatVideo(1L);
    Field field = FieldTestUtils.createTextField();
    field.setId(2L);
    String originalAV = updater.generateURLString(video, field.getId());
    assertTrue(originalAV.contains("2-1"));
    field.setId(3L); // new field
    String newAV = updater.updateAVIdsInCopy(originalAV, field.getId());
    assertTrue(newAV.contains("3-1"));
  }

  @Test
  public void testUpdateLinkedRecordLink() {
    // swap urls and make sure they're changed
    Record toLinkTo = TestFactory.createAnySD();
    toLinkTo.setName("doc SD515");
    toLinkTo.setId(515L);
    String original = updater.generateURLStringForInternalLink(toLinkTo);
    assertTrue(original, original.contains("/globalId/SD515"));
    assertTrue(original, original.contains("SD515: doc SD515"));

    Field field = FieldTestUtils.createTextField();
    field.setFieldData(original);
    Map<Long, Long> oldToNew = new HashMap<>();
    oldToNew.put(515L, 516L);
    ArrayList<Long> newLinkIds = new ArrayList<Long>();
    String updatedFieldData =
        updater.updateLinkedDocument(field.getFieldData(), oldToNew, newLinkIds);
    // link href should be updated
    assertTrue(updatedFieldData, updatedFieldData.contains("/globalId/SD516"));
    // global id at the start of link content should be updated, but the target document name remain
    // unchanged
    assertTrue(updatedFieldData, updatedFieldData.contains("SD516: doc SD515"));
    assertEquals(1, newLinkIds.size());
    assertEquals(516L, newLinkIds.get(0).longValue());
  }

  @Test
  public void testUpdateThumnbailWithNewField() {
    Field field = FieldTestUtils.createTextField();
    field.setId(12345L);
    String thumbURL =
        "/thumbnail/data?sourceType=IMAGE&sourceId=16342&sourceParentId=12877825&width=644&height=328&time=1406822005870";
    String content = "<img id='999-123' class='imageDropped' src='" + thumbURL + "'/>";
    String updateed = updater.updateThumbnailParentIds(content, field.getId());
    assertTrue(updateed.contains("sourceParentId=12345"));
    assertTrue(updateed.contains("12345-123"));
  }

  @Test
  public void changeAttachmentData() throws IOException {
    String oldAttachment = readHtml("attachmentDiv.html");
    Field field = FieldTestUtils.createTextField();
    field.setData(oldAttachment);
    long OLDID = 45123L;
    long NEWID = 70123L;
    Map<String, String> mp = new HashMap<>();
    mp.put("href", "attachmentDiv.html");
    Field updated =
        updater.changeDataForImportedField(
            field, NEWID, mp, FieldParserConstants.ATTACHMENT_CLASSNAME, 0);
    assertFalse(updated.getFieldData().contains(OLDID + ""));
  }

  @Test
  public void changeMediaData() throws IOException {
    String oldmedia = readHtml("videoAudio.html");
    Field field = FieldTestUtils.createTextField();
    long FIELDID = 45123L;
    long OLD_VIDEO_ID = 81000L; // from file
    field.setId(FIELDID);
    field.setData(oldmedia);
    long NEW_VIDEOID = 701234L;
    assertEquals(5, StringUtils.countMatches(oldmedia, OLD_VIDEO_ID + ""));
    EcatVideo video = TestFactory.createEcatVideo(NEW_VIDEOID);
    Field updated = updater.changeMediaData(field, video, 0, FieldParserConstants.VIDEO_CLASSNAME);
    // quite low chance that random integer replacing id contains '81000'
    assertEquals(0, StringUtils.countMatches(updated.getFieldData(), OLD_VIDEO_ID + ""));

    long OLD_AUDIO_ID = 79000L; // from file
    long NEW_AUDIOID = 400000L;
    EcatAudio audio = TestFactory.createEcatAudio(NEW_AUDIOID, TestFactory.createAnyUser("any"));
    assertEquals(6, StringUtils.countMatches(oldmedia, OLD_AUDIO_ID + ""));
    Field updated2 = updater.changeMediaData(field, audio, 0, FieldParserConstants.AUDIO_CLASSNAME);
    assertEquals(0, StringUtils.countMatches(updated2.getFieldData(), OLD_AUDIO_ID + ""));
  }

  @Test
  public void changeMediaData2_RSPAC_1342() throws IOException {
    String oldmedia = readHtml("videoAudio2.html");
    Field field = createTextField();
    long FIELDID = 45123L;
    long OLD_VIDEO_ID = 65600L; // from file
    field.setId(FIELDID);
    field.setData(oldmedia);
    assertEquals(10, StringUtils.countMatches(oldmedia, OLD_VIDEO_ID + ""));
    long NEW_VIDEOID = 701234L;
    EcatVideo video = TestFactory.createEcatVideo(NEW_VIDEOID);
    Field updated = updater.changeMediaData(field, video, 0, FieldParserConstants.VIDEO_CLASSNAME);
    // quite low chance that random integer replacing id contains '65600'
    assertEquals(0, StringUtils.countMatches(updated.getFieldData(), OLD_VIDEO_ID + ""));

    long OLD_AUDIO_ID = 65620L; // from file
    long NEW_AUDIOID = 400000L;
    EcatAudio audio = TestFactory.createEcatAudio(NEW_AUDIOID, TestFactory.createAnyUser("any"));
    assertTrue(StringUtils.countMatches(oldmedia, OLD_AUDIO_ID + "") > 0);
    Field updated2 = updater.changeMediaData(field, audio, 0, FieldParserConstants.AUDIO_CLASSNAME);
    // quite low chance that random integer replacing id will contain '65620'
    assertEquals(0, StringUtils.countMatches(updated2.getFieldData(), OLD_AUDIO_ID + ""));
  }

  private String readHtml(String fileName) throws IOException {
    return readFileToString(new File(htmlFolder, fileName), "UTF-8");
  }

  @Test
  public void updateNfsLinkOnExportAndImport() {
    Field tf = createATextField();
    String basicLink = updater.generateURLStringForNfs(1L, "/any.txt", false);
    tf.setFieldData(basicLink);

    String updated =
        updater.updateNfsLinkOnExport(
            basicLink,
            new NfsElement(1L, "/any.txt"),
            "/filesystem/filestore/any.txt",
            "../any.txt");
    tf.setFieldData(updated);
    Document d = Jsoup.parse(tf.getFieldData());
    assertEquals(1, d.getElementsByAttribute(NfsElement.FULL_PATH_DATA_ATTR_NAME).size());
    assertEquals(1, d.getElementsByTag("a").size());
    assertEquals("../any.txt", d.getElementsByTag("a").get(0).attr("href"));

    tf = updater.updateNfsLinksOnImport(tf);
    Document d2 = Jsoup.parse(tf.getFieldData());
    assertEquals(0, d2.getElementsByAttribute(NfsElement.FULL_PATH_DATA_ATTR_NAME).size());
    assertEquals(1, d2.getElementsByTag("a").size());
    assertEquals("#", d2.getElementsByTag("a").get(0).attr("href"));
  }
}
