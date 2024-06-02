package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.ANNOTATION_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.ATTACHMENTICON_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.ATTACHMENTINFOICON_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.CHEM_IMG_CLASSNAME;
import static com.researchspace.core.util.FieldParserConstants.DATA_TYPE_ANNOTATION;
import static com.researchspace.core.util.FieldParserConstants.EXTERNALLINK_BADGE_CLASSNAME;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.NumberUtils;
import com.researchspace.model.*;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * Updates links and references in to comments and images in a {@link TextField}
 *
 * @see {@link FieldParser} for documentation on the syntax of the links used in text fields.
 *     <p>For methods that generate HTML links, the Velocity templates are i
 */
@Component("richTextUpdater")
@Slf4j
public class RichTextUpdater {

  public static final String REVISION_DATA_ATTR = "data-rsrevision";

  static final String DUMMY_ICON_PATH = "/formIcons/ELISA32.png";
  static final String SDOC_URL_PATH = "/workspace/editor/structuredDocument";
  static final String CHEMICAL_URL_PATH = "/chemical/getImageChem/";
  static final String MATH_URL_PATH = "/svg/";

  private static final String GET_IMAGE_URL = "/image/getImage/";
  private static final String GET_ANNOTATION_URL = "/image/getAnnotation/";
  private static final String GET_IMG_SKETCH_URL_PART = "getImageSketch";
  private static final String GET_IMG_URL_PART = "getImage";
  private static final String DATA_MATHID = "data-mathid";
  private static final String DATA_CHEM_FILE_ID = "data-chemfileid";
  private static final String INFO_ICON_SRC = "/images/getInfo12.png";
  public static final String HEIGHT = "height";
  public static final String WIDTH = "width";
  public static final String UTF_8 = "UTF-8";
  public static final String DATA_GLOBALID = "data-globalid";
  public static final String CSS_CLASS = "cssClass";
  public static final String SOURCE_PARENT_ID = "sourceParentId";
  public static final String TSTAMP = "tstamp";
  public static final String FULLWIDTH = "fullwidth";
  public static final String FULLHEIGHT = "fullheight";
  public static final String ECAT_CHEM_FILE_ID = "ecatChemFileId";
  public static final String CHEM_ELEMENT_LINK_VM = "chemElementLink.vm";
  public static final String VALUE = "value";
  public static final String CLASS = "class";
  public static final String DATA_ID = "data-id";
  public static final String DATA_TYPE = "data-type";
  public static final String OBJECT = "object";

  private final Random randomGenerator = new Random();

  private @Autowired VelocityEngine velocity;

  public void setVelocity(VelocityEngine velocity) {
    this.velocity = velocity;
  }

  public String updateCommentIdsInCopy(Map<Long, Long> oldKey2NewKey, String fieldData) {
    if (StringUtils.isBlank(fieldData)) {
      return fieldData;
    }
    Document d = Jsoup.parse(fieldData);

    Elements elements = d.getElementsByTag("img");
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);

      if (isComment(el)) {
        String oldId = el.attr("id");
        Long replacement = oldKey2NewKey.get(Long.parseLong(oldId));
        if (replacement != null) {
          el.attr("id", replacement + "");
        }
      }
    }
    return d.body().html();
  }

  public boolean isComment(Element el) {
    return getClassAttrLowerCase(el)
        .contains(FieldParserConstants.COMMENT_CLASS_NAME.toLowerCase());
  }

  public boolean isRawImage(Element el) {
    String classLC = getClassAttrLowerCase(el);
    return classLC.contains(FieldParserConstants.IMAGE_DROPPED_CLASS_NAME.toLowerCase())
        && classLC.contains(FieldParserConstants.IMAGE_THMNAIL_DROPPED_CLASS_NAME.toLowerCase());
  }

  public boolean isImageOrAnnotation(Element el) {
    return getClassAttrLowerCase(el)
        .contains(FieldParserConstants.IMAGE_DROPPED_CLASS_NAME.toLowerCase());
  }

  public boolean isSketch(Element el) {
    return getClassAttrLowerCase(el).equalsIgnoreCase(FieldParserConstants.SKETCH_IMG_CLASSNAME);
  }

  public boolean isChemImage(Element el) {
    return getClassAttrLowerCase(el).contains(CHEM_IMG_CLASSNAME);
  }

  public boolean isMathDiv(Element el) {
    return getClassAttrLowerCase(el).contains(FieldParserConstants.MATH_CLASSNAME.toLowerCase());
  }

  private String getClassAttrLowerCase(Element el) {
    return el.hasAttr(CLASS) ? el.attr(CLASS).toLowerCase() : "";
  }

  /** POJO class representing a source URL of an img or img sketch URL in a text field */
  public static class ImageURL {

    private String method;
    private Long parentId;
    private Long imageId;
    private Long revision;

    public Long getParentId() {
      return parentId;
    }

    public Long getImageId() {
      return imageId;
    }

    public Long getRevision() {
      return revision;
    }

    /**
     * Boolean test for whether this URL is the URL for an image sketch
     *
     * @return
     */
    public boolean isSketchURL() {
      return GET_IMG_SKETCH_URL_PART.equals(method);
    }

    /**
     * Boolean test for whether this URL is the URL for an image.
     *
     * @return
     */
    public boolean isImgURL() {
      return GET_IMG_URL_PART.equals(method);
    }
  }

  /**
   * Parses and img src URL for an image in a text field, returning a POJO with the link
   * information, or <code>null</code> if could not be parsed.
   *
   * @param sourceURL
   * @param sourceType one of IMAGE, CHEMfor thumbnil images, or empty string if it's another link
   *     type
   * @return An {@link ImageURL}.
   */
  public static ImageURL parseImageIdFromSrcURLInTextField(String sourceURL, String sourceType) {
    // This pattern no longer matches uploaded images
    Pattern pattern =
        Pattern.compile("(" + GET_IMG_URL_PART + "|" + GET_IMG_SKETCH_URL_PART + ")/(\\d+)-(\\d+)");
    Matcher matcher = pattern.matcher(sourceURL);
    Pattern thumbnailPattern =
        Pattern.compile("sourceType=" + sourceType + ".+sourceId=(\\d+).+sourceParentId=(\\d+)");
    Matcher thumbnailMatcher = thumbnailPattern.matcher(sourceURL);
    Pattern sketchPattern = Pattern.compile(GET_IMG_SKETCH_URL_PART + "/(\\d+)");
    Matcher sketchMatcher = sketchPattern.matcher(sourceURL);
    Pattern oldChemPattern = Pattern.compile(CHEMICAL_URL_PATH + "(\\d+)");
    Matcher oldChemMatcher = oldChemPattern.matcher(sourceURL);
    Pattern newAnnotationPattern = Pattern.compile(GET_ANNOTATION_URL + "(\\d+)");
    Matcher newAnnotationMatcher = newAnnotationPattern.matcher(sourceURL);
    Pattern mathPattern = Pattern.compile(MATH_URL_PATH + "(\\d+)");
    Matcher mathMatcher = mathPattern.matcher(sourceURL);
    Pattern revisionPattern = Pattern.compile("revision=(\\d+)");
    Matcher revisionMatcher = revisionPattern.matcher(sourceURL);

    ImageURL imgURL = null;
    if (matcher.find()) {
      imgURL = new ImageURL();
      imgURL.method = matcher.group(1);
      imgURL.parentId = Long.parseLong(matcher.group(2));
      imgURL.imageId = Long.parseLong(matcher.group(3));
    } else if (thumbnailMatcher.find()) {
      imgURL = new ImageURL();
      imgURL.method = GET_IMG_URL_PART;
      imgURL.parentId = Long.parseLong(thumbnailMatcher.group(2));
      imgURL.imageId = Long.parseLong(thumbnailMatcher.group(1));
    } else if (sketchMatcher.find()) {
      imgURL = new ImageURL();
      imgURL.method = GET_IMG_SKETCH_URL_PART;
      imgURL.imageId = Long.parseLong(sketchMatcher.group(1));
    } else if (newAnnotationMatcher.find()) {
      imgURL = new ImageURL();
      imgURL.method = GET_ANNOTATION_URL;
      imgURL.imageId = Long.parseLong(newAnnotationMatcher.group(1));
    } else if (oldChemMatcher.find()) {
      imgURL = new ImageURL();
      imgURL.method = CHEMICAL_URL_PATH;
      imgURL.imageId = Long.parseLong(oldChemMatcher.group(1));
    } else if (mathMatcher.find()) {
      imgURL = new ImageURL();
      imgURL.method = MATH_URL_PATH;
      imgURL.imageId = Long.parseLong(mathMatcher.group(1));
    }

    if (imgURL != null && revisionMatcher.find()) {
      imgURL.revision = Long.parseLong(revisionMatcher.group(1));
    }

    return imgURL;
  }

  /** Generates an img HTML element with src link of type / */
  public String generateRawImageElement(EcatImage image, String fieldId) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("fieldId", fieldId);
    velocityModel.put("itemId", image.getId());
    velocityModel.put("name", image.getName());
    velocityModel.put(WIDTH, image.getWidthResized());
    velocityModel.put(HEIGHT, image.getHeightResized());
    velocityModel.put("rotation", image.getRotation());
    velocityModel.put("milliseconds", image.getModificationDate());

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "rawImageLink.vm", UTF_8, velocityModel);
    return msg.replace("\n", " ");
  }

  public String generateAnnotatedImageElement(EcatImageAnnotation annotation, String fieldId) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", fieldId + "-" + annotation.getImageId());
    velocityModel.put("annotationId", annotation.getId());
    velocityModel.put("type", FieldParserConstants.DATA_TYPE_ANNOTATION);
    velocityModel.put(WIDTH, annotation.getWidth());
    velocityModel.put(HEIGHT, annotation.getHeight());
    velocityModel.put("unused", new Date().getTime());

    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "annotatedImageLink.vm", UTF_8, velocityModel);
  }

  /**
   * Takes a persisted {@link EcatImageAnnotation} with a non-null ID
   *
   * @param sketch annotation
   * @return an &lt;img&gt; link for the {@link EcatImageAnnotation}.
   */
  public String generateImgLinkForSketch(EcatImageAnnotation sketch) {
    if (sketch.getId() == null) {
      throw new IllegalArgumentException("id cannot be null");
    }
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", sketch.getId());
    velocityModel.put(WIDTH, sketch.getWidth());
    velocityModel.put(HEIGHT, sketch.getHeight());
    velocityModel.put("unused", new Date().getTime());
    velocityModel.put(CSS_CLASS, FieldParserConstants.SKETCH_IMG_CLASSNAME);

    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "sketchLink.vm", UTF_8, velocityModel);
  }

  /**
   * @param image An {@link EcatImage} with a non-null database id
   * @param fieldId The database id of the text field that contains this image.
   * @return A String of the image url that can be inserted insert into the TextField's content.
   */
  private String generateBaseURLStringForEcatImageLink(EcatImage image, String fieldId) {
    String id = fieldId + "-" + image.getId();
    return " id=\""
        + id
        + "\" class=\""
        + FieldParserConstants.IMAGE_DROPPED_CLASS_NAME
        + " "
        + FieldParserConstants.IMAGE_THMNAIL_DROPPED_CLASS_NAME
        + "\" src=\""
        + GET_IMAGE_URL
        + id
        + "/"
        + randomGenerator.nextLong()
        + "\" alt=\"image\" data-size=\""
        + image.getWidth()
        + "-"
        + image.getHeight()
        + "\" ";
  }

  public String generateURLStringForCommentLink(String commentId) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", commentId);
    velocityModel.put(CSS_CLASS, FieldParserConstants.COMMENT_CLASS_NAME);
    velocityModel.put("imageURL", "images/x.png");
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "commentLink.vm", UTF_8, velocityModel);
  }

  // new add methods
  public Map<String, String> makeEcatImageAttributes(EcatImage image, String fieldId) {
    Map<String, String> attrs = new HashMap<>();
    String id = fieldId + "-" + image.getId();
    attrs.put("id", id);
    attrs.put("revision", image.getModificationDateMillis() + "");
    return attrs;
  }

  public Map<String, String> makeImageAnnotationAttributes(
      EcatImageAnnotation imageA, String fieldId) {
    Map<String, String> attrs = new HashMap<>();
    String id = fieldId + "-" + imageA.getImageId();
    String annoId = "" + imageA.getId();
    attrs.put("id", id);
    attrs.put(DATA_ID, annoId);
    attrs.put(DATA_TYPE, DATA_TYPE_ANNOTATION);
    attrs.put("src", GET_ANNOTATION_URL + annoId + "/" + randomGenerator.nextLong());
    return attrs;
  }

  public Map<String, String> makeSketchAttributes(EcatImageAnnotation imageAnnotation) {
    Map<String, String> attrs = new HashMap<>();
    String id = Long.toString(imageAnnotation.getId());
    Date dt = new Date();
    String stmp = Long.toString(dt.getTime());
    String src = "/image/getImageSketch/" + imageAnnotation.getId() + "/" + stmp;
    attrs.put("id", id);
    attrs.put("src", src);
    return attrs;
  }

  public Map<String, String> makeChemImageAttributes(String chemID) {
    Map<String, String> attrs = new HashMap<>();
    Date dt = new Date();
    String stmp = Long.toString(dt.getTime());
    String src = CHEMICAL_URL_PATH + chemID + "/" + stmp;
    attrs.put("id", chemID);
    attrs.put("src", src);
    return attrs;
  }

  public Map<String, String> makeMathAttributes(Long mathId) {
    Map<String, String> attrs = new HashMap<>();
    String src = MATH_URL_PATH + mathId;
    attrs.put(DATA_MATHID, mathId + "");
    attrs.put("data", src);
    return attrs;
  }

  /*
   * @param image An {@link EcatImage} with a non-null database id
   *
   * @param fieldId The database id of the text field that contains this
   * image.
   *
   * @return A String of the image url that can be inserted into the
   * TextField's content.
   */
  public String generateURLStringForEcatImageLink(EcatImage image, String fieldId) {
    String base = generateBaseURLStringForEcatImageLink(image, fieldId);
    return "<img " + base + " />";
  }

  /*
   * Generates a URL for an image that is resized
   *
   * @param image An {@link EcatImage} with a non-null database id
   *
   * @param fieldId The database id of the text field that contains this
   * image.
   *
   * @width int the resized width, <=0 means no resizing in width
   *
   * @height int the resized height, <=0 means no resizing in height
   *
   * @return A String of the image url that can be inserted into the
   * TextField's content.
   */
  public String generateURLStringForEcatSizedImageLink(
      EcatImage image, String fieldId, int width, int height) {
    String base = generateBaseURLStringForEcatImageLink(image, fieldId);
    String sizing = null;

    if (width <= 0) { // width not set
      if (height <= 0) { // height not set
        sizing = "";
      } else { // height set
        sizing = "height=\"" + height + "\"";
      }
    } else { // width set
      if (height <= 0) { // height not set
        sizing = "width=\"" + width + "\"";
      } else { // height and width set
        sizing = "height=\"" + height + "\" width=\"" + width + "\"";
      }
    }
    return "<img " + base + sizing + " />";
  }

  /**
   * Works on the basis of the id string being of the syntax="fieldId-EcatImageId", and possible
   * data-id field for annotations.
   */
  public String updateImageIdsAndAnnoIdsInCopy(
      Map<Long, Long> oldParent2NewParent, Map<Long, Long> oldAnnoId2NewAnnoId, String fieldData) {

    if (StringUtils.isBlank(fieldData)) {
      return fieldData;
    }
    Document d = Jsoup.parse(fieldData);
    Elements elements = d.getElementsByTag("img");
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);

      if (isImageOrAnnotation(el)) {
        String oldId = el.attr("id");

        Long oldFld = Long.parseLong(oldId.split("-")[0]);
        Long oldImg = Long.parseLong(oldId.split("-")[1]);
        Long newFld = oldParent2NewParent.get(oldFld);
        if (newFld != null) {
          el.attr("id", newFld + "-" + oldImg);
          el.attr("src", GET_IMAGE_URL + newFld + "-" + oldImg + "/" + randomGenerator.nextLong());
        }

        if (el.hasAttr(DATA_TYPE)
            && FieldParserConstants.DATA_TYPE_ANNOTATION.equals(el.attr(DATA_TYPE))
            && (el.hasAttr(DATA_ID))) {
          Long oldAnnoId = Long.parseLong(el.attr(DATA_ID));
          Long newAnnotId = oldAnnoId2NewAnnoId.get(oldAnnoId);
          el.attr(DATA_ID, "" + newAnnotId);
          el.attr("src", GET_ANNOTATION_URL + newAnnotId + "/" + randomGenerator.nextLong());
        }
      }
    }
    return d.body().html();
  }

  /**
   * Works on the basis of the id string being of the
   * syntax="getImageSketch/imageAnnotationID/unused"
   *
   * @param oldKey2NewKey
   * @param fieldData
   * @return the updated string.
   */
  public String updateSketchIdsInCopy(Map<Long, Long> oldKey2NewKey, String fieldData) {
    if (StringUtils.isBlank(fieldData)) {
      return fieldData;
    }
    Document d = Jsoup.parse(fieldData);
    Elements elements = d.getElementsByTag("img");
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);

      if (isSketch(el)) {
        String oldId = el.attr("id");
        Long replacement = oldKey2NewKey.get(Long.parseLong(oldId));
        Date dt = new Date();
        String stmp = Long.toString(dt.getTime());
        el.attr("id", "" + replacement);
        el.attr("src", "/image/getImageSketch/" + replacement + "/" + stmp);
      }
    }
    return d.body().html();
  }

  public String updateMathIdsInCopy(Map<Long, Long> oldKey2NewKey, String fieldData) {
    if (StringUtils.isBlank(fieldData)) {
      return fieldData;
    }
    Document d = Jsoup.parse(fieldData);
    Elements elements = d.getElementsByTag("div");
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);
      if (isMathDiv(el)) {
        String oldId = el.attr(DATA_MATHID);
        Long replacement = oldKey2NewKey.get(Long.parseLong(oldId));
        if (replacement != null) {
          el.attr(DATA_MATHID, replacement + "");

          Element svgObject = el.getElementsByTag(OBJECT).get(0);
          String oldData = svgObject.attr("data");
          svgObject.attr("data", oldData.replace(oldId, replacement + ""));
        }
      }
    }
    return d.body().html();
  }

  /**
   * Updates chemical structure id in the html field data
   *
   * @param oldKey2NewKey
   * @param fieldData
   * @return the updated string.
   */
  public String updateChemIdsInCopy(Map<Long, Long> oldKey2NewKey, String fieldData) {
    if (StringUtils.isBlank(fieldData)) {
      return fieldData;
    }
    Document d = Jsoup.parse(fieldData);
    Elements elements = d.getElementsByTag("img");
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);
      if (isChemImage(el)) {
        String oldId = el.attr("id");
        Long replacement = oldKey2NewKey.get(Long.parseLong(oldId));
        if (replacement != null) {
          Map<String, String> newAttributes = makeChemImageAttributes("" + replacement);
          updateIdAndSrcAttributes(newAttributes, el);
        }
      }
    }
    return d.body().html();
  }

  public String generateURLStringForEcatChemistryFileAfterError(EcatChemistryFile chemistryFile) {
    return "Error generating a chemical structure for file id: <a href=\"/globalId/GL"
        + chemistryFile.getId()
        + "\">GL"
        + +chemistryFile.getId()
        + "</a>";
  }

  public String generateURLStringForEcatChemistryFile(
      Long chemId, EcatChemistryFile chemistryFile, Long fieldId, int width, int height) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", chemId);
    velocityModel.put(SOURCE_PARENT_ID, fieldId);
    velocityModel.put(CSS_CLASS, CHEM_IMG_CLASSNAME);
    velocityModel.put(TSTAMP, Long.toString(new Date().getTime()));
    velocityModel.put(WIDTH, 300);
    velocityModel.put(HEIGHT, 300);
    velocityModel.put(FULLWIDTH, width);
    velocityModel.put(FULLHEIGHT, height);
    velocityModel.put(ECAT_CHEM_FILE_ID, chemistryFile.getId());
    velocityModel.put("fileName", chemistryFile.getName());
    velocityModel.put(DATA_CHEM_FILE_ID, chemistryFile.getId());
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, CHEM_ELEMENT_LINK_VM, UTF_8, velocityModel);
  }

  public String generateURLStringForRSCheElementLinkWithFileId(
      Long chemID, Long fieldId, Long ecatChemFileId, int width, int height) {
    Date dt = new Date();
    String stmp = Long.toString(dt.getTime());
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", chemID);
    velocityModel.put(SOURCE_PARENT_ID, fieldId);
    velocityModel.put(CSS_CLASS, CHEM_IMG_CLASSNAME);
    velocityModel.put(TSTAMP, stmp);
    velocityModel.put(WIDTH, width);
    velocityModel.put(HEIGHT, height);
    velocityModel.put(FULLWIDTH, width);
    velocityModel.put(FULLHEIGHT, height);
    velocityModel.put(ECAT_CHEM_FILE_ID, ecatChemFileId);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, CHEM_ELEMENT_LINK_VM, UTF_8, velocityModel);
  }

  public String generateURLStringForRSChemElementLink(
      Long chemID, Long fieldId, int width, int height) {
    Date dt = new Date();
    String stmp = Long.toString(dt.getTime());
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", chemID);
    velocityModel.put(SOURCE_PARENT_ID, fieldId);
    velocityModel.put(CSS_CLASS, CHEM_IMG_CLASSNAME);
    velocityModel.put(TSTAMP, stmp);
    velocityModel.put(WIDTH, width);
    velocityModel.put(HEIGHT, height);
    velocityModel.put(FULLWIDTH, width);
    velocityModel.put(FULLHEIGHT, height);
    velocityModel.put(ECAT_CHEM_FILE_ID, "");
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, CHEM_ELEMENT_LINK_VM, UTF_8, velocityModel);
  }

  /** Generates HTML for Math element. */
  public String generateURLStringForRSMathLink(
      Long mathId, String latex, String svgWidth, String svgHeight) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", mathId + "");
    velocityModel.put("equation", latex);
    velocityModel.put("svgWidth", svgWidth);
    velocityModel.put("svgHeight", svgHeight);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "equationLink.vm", UTF_8, velocityModel);
  }

  public String generateURLStringForInternalLink(BaseRecord toLinkTo) {
    return generateURLStringLinkedRecord(toLinkTo.getId(), toLinkTo.getName(), toLinkTo.getOid());
  }

  public String generateURLStringForVersionedInternalLink(StructuredDocument toLinkTo) {
    return generateURLStringLinkedRecord(
        toLinkTo.getId(), toLinkTo.getName(), toLinkTo.getOidWithVersion());
  }

  private String generateURLStringLinkedRecord(Long id, String name, GlobalIdentifier oid) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", id);
    velocityModel.put("name", name);
    velocityModel.put("globalId", oid);
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "linkedRecordLink.vm", UTF_8, velocityModel);
    return msg;
  }

  /**
   * Generates an HTML fragment representing a link to an NfsFileStore resource
   *
   * @param fileStoreId
   * @param fileName The relative file path of the file
   * @return
   */
  public String generateURLStringForNfs(Long fileStoreId, String fileName, boolean isFolderLink) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("name", fileName);
    velocityModel.put(
        "linktype", isFolderLink ? NfsElement.LINKTYPE_DIR : NfsElement.LINKTYPE_FILE);
    velocityModel.put("fileStoreId", fileStoreId + "");
    velocityModel.put("relFilePath", fileName);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "nfsLink.vm", UTF_8, velocityModel);
  }

  public String generateIframeEmbedFromJove() {
    VelocityContext velocityContext = new VelocityContext();
    velocityContext.put(
        "src",
        "https://www.jove.com/embed/player?id=3923&amp;t=1&amp;s=1&amp;fpv=1&amp;access=954c8eHx&amp;utm_source=JoVE_RSpace");
    velocityContext.put(WIDTH, "460");
    velocityContext.put(HEIGHT, "440");
    velocityContext.put("frameborder", "0");
    velocityContext.put("marginwidth", "0");
    velocityContext.put("allowfullscreen", "");
    velocityContext.put("scrolling", "no");
    StringWriter stringWriter = new StringWriter();
    velocity.mergeTemplate("embedIframe.vm", UTF_8, velocityContext, stringWriter);
    return stringWriter.toString();
  }

  /**
   * Used in export process to add full path as a data element.
   *
   * @param fieldContent The field content
   * @param nfsElement The NFS ELement to modify; used to identify the correct element in the field
   *     html
   * @param fullPath The value of the fullpath
   * @param archiveHref path to nfs file inside archive, may be null
   * @return The modified field content
   */
  public String updateNfsLinkOnExport(
      String fieldContent, NfsElement nfsElement, String fullPath, String archiveHref) {
    Document d = Jsoup.parse(fieldContent);
    String relValue = nfsElement.toString();
    Elements els = d.select(String.format("a[rel=%s]", relValue));
    for (Element el : els) {
      el.attr(NfsElement.FULL_PATH_DATA_ATTR_NAME, fullPath);
      if (archiveHref != null) {
        el.attr("href", archiveHref);
      }
    }
    return d.body().html();
  }

  /**
   * Used during import to remove the data-fullPath attribute added during export ( it may be out of
   * date, and is not needed in the application.<br>
   * Has no effect if there us no data-fullPath attribute
   *
   * @param fld
   * @return The modified argument
   */
  public Field updateNfsLinksOnImport(Field fld) {
    if (StringUtils.isEmpty(fld.getFieldData())) {
      return fld;
    }
    Document dc = Jsoup.parse(fld.getFieldData());
    Elements elms = dc.select(String.format("a.%s", FieldParserConstants.NET_FS_CLASSNAME));
    for (Element nfsLink : elms) {
      nfsLink.removeAttr(NfsElement.FULL_PATH_DATA_ATTR_NAME);
      nfsLink.attr("href", "#");
    }
    fld.setFieldData(dc.body().html());
    return fld;
  }

  /**
   * Generates a string created from 'externalDocumentLink.vm' with arbitrary internal data.
   *
   * @return
   */
  public String generateAnyURLStringForExternalDocLink() {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", "someId");
    velocityModel.put("name", "some name");
    velocityModel.put("iconPath", DUMMY_ICON_PATH); // any icon is OK
    velocityModel.put("badgeIconPath", DUMMY_ICON_PATH); // any icon is OK
    velocityModel.put("recordURL", "http://externanalLink.com/someId");
    velocityModel.put("fileStore", "Files4U");
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "externalDocumentLink.vm", UTF_8, velocityModel);
    return msg;
  }

  public String generateURLString(EcatDocumentFile docFile) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", docFile.getId());
    velocityModel.put("name", docFile.getName());
    velocityModel.put("src", "src");

    String ext = MediaUtils.getExtension(docFile.getFileName());
    velocityModel.put("iconPath", getIconPathForExtension(ext));
    velocityModel.put("infoIconSrc", INFO_ICON_SRC);
    if (MediaUtils.isDocumentFile(ext)) {
      velocityModel.put("datatype", MediaUtils.DOCUMENT_MEDIA_FLDER_NAME);
    } else {
      velocityModel.put("datatype", MediaUtils.MISC_MEDIA_FLDER_NAME);
    }

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "attachmentLink.vm", UTF_8, velocityModel);
    return msg;
  }

  public String generateURLString(EcatAudio audio, long fieldId) {
    return getMediaLink(fieldId, false, audio);
  }

  public String generateURLString(EcatVideo video, long fieldId) {
    return getMediaLink(fieldId, true, video);
  }

  public String generateURLString(
      EcatChemistryFile chemistryFile, RSChemElement chemElement, long fieldId) {
    return generateURLStringForEcatChemistryFile(
        chemElement.getId(), chemistryFile, fieldId, 1000, 1000);
  }

  public String getIconPathForExtension(String extension) {
    if (MediaUtils.isChemistryFile(extension)) {
      return "/images/icons/chemistry-file.png";
    } else if (MediaUtils.isDNAFile(extension)) {
      return "/images/icons/dna-file.svg";
    }

    switch (extension) {
      case "avi":
      case "bmp":
      case "doc":
      case "docx":
      case "flv":
      case "gif":
      case "jpg":
      case "jpeg":
      case "m4v":
      case "mov":
      case "mp3":
      case "mp4":
      case "mpg":
      case "ods":
      case "odp":
      case "csv":
      case "pps":
      case "odt":
      case "pdf":
      case "png":
      case "rtf":
      case "wav":
      case "wma":
      case "wmv":
      case "xls":
      case "xlsx":
      case "xml":
      case "zip":
        return "/images/icons/" + extension + ".png";
      case "htm":
      case "html":
        return "/images/icons/html.png";
      case "ppt":
      case "pptx":
        return "/images/icons/powerpoint.png";
      case "txt":
      case "text":
        return "/images/icons/txt.png";
      default:
        return "/images/icons/unknownDocument.png";
    }
  }

  /**
   * @param imgId A composite Id (fieldId-imgId) as used as the 'id' attribute of img elements
   * @param fieldData the field data to alter
   * @param replacementURL The replacement URL
   * @return The altered field Data
   */
  public String replaceImageSrcURL(String imgId, String fieldData, String replacementURL) {

    Document d = Jsoup.parse(fieldData);
    Elements elements = d.getElementsByTag("img");
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);
      if (isRawImage(el) && el.attr("id").equals(imgId)) {
        el.attr("src", replacementURL);
      }
    }
    return d.body().html();
  }

  /**
   * Used by export code to
   *
   * @param fieldData
   * @param compositeId
   * @param replacementURL
   * @param linkName
   * @return
   */
  public String replaceAVTableWithLinkToResource(
      String fieldData, String compositeId, String replacementURL, String linkName) {

    Document d = Jsoup.parse(fieldData);
    Elements els = d.getElementsByClass(FieldParserConstants.VIDEO_CLASSNAME);
    els.addAll(d.getElementsByClass(FieldParserConstants.AUDIO_CLASSNAME));
    els = els.select("#" + compositeId).parents().select("div");
    // return unaltered field data if can't be found
    if (els.isEmpty()) {
      log.warn("could not replace " + compositeId);
      return fieldData;
    }

    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("replacementURL", replacementURL);
    velocityModel.put("linkName", linkName);

    String bodyHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "mediaPlayerHTMLExportReplacement.vm", UTF_8, velocityModel);
    Element el = Jsoup.parseBodyFragment(bodyHtml).select(".mediaPlayerHTMLReplacement").first();

    els.get(0).replaceWith(el);
    return d.body().html();
  }

  /**
   * This method inserts an extra href after chem img tag in order for html export to have a link to
   * its associated file.
   *
   * @param fieldData
   * @param chemFileId Id of EcatChemistryFile
   * @param replacementURL
   * @param linkName
   * @return returns the new html with href to chemistry file
   */
  public String insertHrefToChemistryFile(
      String fieldData, String chemFileId, String replacementURL, String linkName) {
    Document document = Jsoup.parse(fieldData);
    Elements elements = document.select("." + CHEM_IMG_CLASSNAME);
    for (Element element : elements) {
      String id = element.attr(DATA_CHEM_FILE_ID);
      if (!id.isEmpty() && id.equals(chemFileId)) {
        Element p = new Element("p");
        Element atag = new Element("a");
        atag.text(linkName);
        atag.attr("href", replacementURL);
        p.appendChild(atag);
        element.after(p);
      }
    }
    return document.body().html();
  }

  public String replaceLinkedRecordURL(
      GlobalIdentifier globalId, String fieldData, String replacementURL) {
    Document d = Jsoup.parse(fieldData);
    // RSPAC-1357 make sure we only replace relative URLs (pointing to the current instance)
    Elements linkTags =
        d.select("#" + globalId.getDbId())
            .select("." + FieldParserConstants.LINKEDRECORD_CLASS_NAME + "[href^=/]");
    for (Element link : linkTags) {
      // for new link format try matching global id, for old links only replace if global id not
      // versioned
      boolean newLinkFormat = link.hasAttr(DATA_GLOBALID);
      if (newLinkFormat && link.attr(DATA_GLOBALID).equals(globalId.toString())
          || !newLinkFormat && !globalId.hasVersionId()) {
        link.attr("href", replacementURL);
      }
    }
    return d.body().html();
  }

  /**
   * Updates document fields with revisions information. Delegates to {{@link
   * #updateLinksWithRevisions(Field, Integer)}.
   *
   * @return true if the content of any field was updated
   */
  public boolean updateLinksWithRevisions(StructuredDocument doc, Integer revision) {
    boolean docUpdated = false;
    for (Field f : doc.getFields()) {
      if (f.isTextField()) {
        boolean fieldUpdated = updateLinksWithRevisions(f, revision);
        docUpdated = docUpdated || fieldUpdated;
      }
    }
    return docUpdated;
  }

  /**
   * Updates text fields with revisions information. The text of the supplied field is updated
   * in-place.
   *
   * @param field a TextField
   * @param revision (optional) a revision number from a _AUD table. If null, any revisioned links
   *     in the content will be updated into non-revisioned
   * @return true if the field content was updated
   */
  public boolean updateLinksWithRevisions(Field field, Integer revision) {
    if (!field.isTextField()) {
      throw new IllegalArgumentException(
          "Must be text field but is of type [" + field.getType() + "]");
    }
    boolean setRevision = revision != null;
    String initialFieldContent = field.getFieldData();
    Document jSoupDoc = Jsoup.parse(initialFieldContent);
    jSoupDoc.outputSettings().prettyPrint(false);

    List<Element> modifiedElements = new ArrayList<>();
    Elements imgs = jSoupDoc.getElementsByTag("img");
    for (Element img : imgs) {
      if (isChemImage(img) || isSketch(img) || isImageOrAnnotation(img)) {
        removeRevisionParamFromUrlInsideAttr(img, "src");
        if (setRevision) {
          addRevisionParamToSrcURL(revision, img, "src");
        }
        modifiedElements.add(img);
      } else if (isComment(img)) {
        // comments do not have direct links, so just change data- attribute
        modifiedElements.add(img);
      }
    }
    Elements divs = jSoupDoc.getElementsByTag("div");
    for (Element div : divs) {
      if (isMathDiv(div)) {
        Element svgObject = div.getElementsByTag(OBJECT).get(0);
        removeRevisionParamFromUrlInsideAttr(svgObject, "data");
        if (setRevision) {
          addRevisionParamToSrcURL(revision, svgObject, "data");
        }
        modifiedElements.add(svgObject);
      }
      // gallery attachments: audios/videos/document
      if (div.hasClass("attachmentDiv")) {

        // let's check if audio/video div
        Elements mediaDivs = div.getElementsByClass(FieldParserConstants.VIDEO_CLASSNAME);
        mediaDivs.addAll(div.getElementsByClass(FieldParserConstants.AUDIO_CLASSNAME));
        boolean isAudioVideo = !mediaDivs.isEmpty();

        if (isAudioVideo) {
          setRevisionParamInMediaPlayerFlashvars(revision, div);
          // add revision data attribute to videoDropped/audioDropped icon
          modifiedElements.addAll(mediaDivs);
        } else {
          // add revision data attribute to attachment link
          modifiedElements.addAll(
              div.getElementsByClass(FieldParserConstants.ATTACHMENT_CLASSNAME));
        }

        // update download links
        Elements attachments = div.getElementsByAttributeValueStarting("href", "/Streamfile");
        for (Element att : attachments) {
          removeRevisionParamFromUrlInsideAttr(att, "href");
          if (setRevision) {
            addRevisionParamToSrcURL(revision, att, "href");
          }
        }
      }
    }

    // for all revisioned elements add data-rsrevision attribute for easy access in client-side
    for (Element modifiedElem : modifiedElements) {
      if (setRevision) {
        addRevisionInfoInDataField(revision, modifiedElem);
      } else {
        removeRevisionInfoFromDataField(modifiedElem);
      }
    }

    boolean fieldContentUpdated = false;
    if (!modifiedElements.isEmpty()) {
      String newFieldContent = jSoupDoc.body().html();
      fieldContentUpdated = !initialFieldContent.equals(newFieldContent);
      if (fieldContentUpdated) {
        field.setFieldData(newFieldContent);
      }
    }
    return fieldContentUpdated;
  }

  private Pattern flashvarRevisionPattern =
      Pattern.compile("(%3Frevision=\\d+)&controlbar.position=over");

  // update media player link in flashvar param
  private void setRevisionParamInMediaPlayerFlashvars(Integer revision, Element div) {
    String controlBarPosition = "&controlbar.position=over";
    Elements flashvarParams = div.getElementsByAttributeValue("name", "flashvars");
    if (!flashvarParams.isEmpty()) {
      Element flashvar = flashvarParams.get(0);
      String currentValue = flashvar.attr(VALUE);
      // remove current link, if present
      Matcher m = flashvarRevisionPattern.matcher(currentValue);
      String newflashvar = m.replaceAll(controlBarPosition);
      if (revision != null && (newflashvar.endsWith(controlBarPosition))) {
        newflashvar =
            newflashvar.replace(controlBarPosition, "%3Frevision=" + revision + controlBarPosition);
      }
      flashvar.attr(VALUE, newflashvar);
    }
  }

  /**
   * Removes revision attributes for a chemical sketch with the given ID
   *
   * @param f
   * @param id
   * @return <code>true</code> if revision was removed, <code>false</code> otherwise, e.g., if there
   *     were no.
   */
  public boolean removeRevisionsFromChemWithId(Field f, String id) {
    if (!f.isTextField()) {
      throw new IllegalArgumentException("Must be text field but is of type [" + f.getType() + "]");
    }
    Document d = Jsoup.parse(f.getFieldData());
    Elements elements = d.getElementsByTag("img");
    boolean removed = false;
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);
      if (isChemImage(el) && elHasIdAttr(id, el)) {
        removed = removeRevisionParamFromUrlInsideAttr(el, "src");
        removeRevisionInfoFromDataField(el);
        if (removed) {
          break;
        }
      }
    }
    if (removed) {
      f.setFieldData(d.body().html());
    }
    return removed;
  }

  private boolean elHasIdAttr(String id, Element el) {
    return el.hasAttr("id") && el.attr("id").equals(id);
  }

  private void removeRevisionInfoFromDataField(Element el) {
    el.removeAttr(REVISION_DATA_ATTR);
  }

  private void addRevisionInfoInDataField(Integer revision, Element el) {
    el.attr(REVISION_DATA_ATTR, revision + "");
  }

  private void addRevisionParamToSrcURL(
      Integer revision, Element el, String srcAttribute) { // either 'href' or 'src'
    try {
      URI srcURI = new URI(el.attr(srcAttribute));
      String query = srcURI.getQuery();
      String newSrc;
      if (StringUtils.isEmpty(query)) {
        newSrc = srcURI + "?revision=" + revision;
      } else {
        newSrc = srcURI + "&revision=" + revision;
      }
      el.attr(srcAttribute, newSrc);
    } catch (URISyntaxException e) {
      log.error("Error creating URI from source attribute {}.", srcAttribute, e);
    }
  }

  private final Pattern revisionPattern = Pattern.compile("([&?]revision=\\d+)|^(revision=\\d+)");

  private boolean removeRevisionParamFromUrlInsideAttr(Element el, String srcAttribute) {
    try {
      URI srcURI = new URI(el.attr(srcAttribute).trim());
      String query = srcURI.getQuery();

      if (StringUtils.isEmpty(query)) {
        return false; // there's no revision component to remove.
      } else {
        Matcher m = revisionPattern.matcher(query);
        String newQuery = m.replaceAll("");
        if (newQuery.isEmpty()) {
          newQuery = null;
        }
        URI newsrcURI = new URI(null, null, srcURI.getPath(), newQuery, null);

        el.attr(srcAttribute, newsrcURI.toString());
        return !query.equals(newQuery); // if they're different, has been removed
      }
    } catch (URISyntaxException e) {
      log.error("Error creating URI from source attribute {}.", srcAttribute, e);
      return false;
    }
  }

  protected Elements getElementsOfType(String text, String elName) {
    Document d = Jsoup.parse(text);
    Elements elements = d.getElementsByTag(elName);
    return elements;
  }

  private String getMediaLink(long fieldId, boolean isVideo, EcatMediaFile mediaRecord) {
    // be synchronized
    String imgClass = FieldParserConstants.VIDEO_CLASSNAME;
    if (!isVideo) {
      imgClass = FieldParserConstants.AUDIO_CLASSNAME;
    }
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("id", mediaRecord.getId());
    velocityModel.put("filename", mediaRecord.getFileName());
    velocityModel.put("extension", mediaRecord.getExtension());
    velocityModel.put("imgClass", imgClass);
    velocityModel.put("iconSrc", getIconPathForExtension(mediaRecord.getExtension()));
    velocityModel.put("infoIconSrc", INFO_ICON_SRC);
    velocityModel.put("src", "src");
    velocityModel.put("videoHTML", "");
    velocityModel.put("compositeId", BaseRecord.getCompositeId(mediaRecord, fieldId));
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "mediaPlayerHTML.vm", UTF_8, velocityModel);
    return msg;
  }

  /**
   * Replaces all instances of elements with the given original 'src' attribute value with a
   * replacement URL.
   *
   * @param originalURL the URL to replace.
   * @param replacementURL the new URL
   * @param fdata the text-field data
   * @return The modified text-field data as a new HTML string
   */
  public String replaceImageSrc(String originalURL, String replacementURL, String fdata) {
    return doReplace(originalURL, replacementURL, fdata, "src");
  }

  /**
   * Replaces all instances of elements with the given original 'data' attribute value with a
   * replacement URL.
   *
   * @param originalURL the URL to replace.
   * @param replacementURL the new URL
   * @param fdata the text-field data
   * @return The modified text-field data as a new HTML string
   */
  public String replaceObjectDataURL(String originalURL, String replacementURL, String fdata) {
    return doReplace(originalURL, replacementURL, fdata, "data");
  }

  private String doReplace(
      String originalURL, String replacementURL, String fdata, String attribute) {
    if (StringUtils.isEmpty(originalURL)) {
      return fdata;
    }
    Document d = Jsoup.parse(fdata);
    d.getElementsByAttributeValue(attribute, originalURL).attr(attribute, replacementURL);
    d.outputSettings().prettyPrint(false);
    return d.body().html();
  }

  private static final Pattern ATTACHMENT_ICON_CSSCLASSES =
      Pattern.compile(
          "("
              + ATTACHMENTICON_CLASSNAME
              + ")|"
              + "("
              + ATTACHMENTINFOICON_CLASSNAME
              + ")|"
              + "("
              + EXTERNALLINK_BADGE_CLASSNAME
              + ")");

  /**
   * replaces links to icon resources to static resource links in RSpace
   *
   * @param fld
   * @return the modified field
   */
  public Field updateAttachmentIcons(Field fld) {
    Document dc = Jsoup.parse(fld.getFieldData());

    Elements elms = dc.getElementsByAttributeValueMatching(CLASS, ATTACHMENT_ICON_CSSCLASSES);
    for (Element el : elms) {
      String name = FilenameUtils.getName(el.attr("src"));
      el.attr("src", MediaUtils.getIconPathForSuffix(name));
    }
    fld.setFieldData(dc.body().html());
    return fld;
  }

  /**
   * Changes the src/id attributes in a given field during import.
   *
   * @param fld The text field to change
   * @param id The id to change to
   * @param attrs A map of attributes of the new value - must have 'id' and 'src' keys set
   * @param linkType A {@link FieldParserConstants#getRSpaceImageCSSClasses()} CSS classname
   * @param indexToChange The index of the element to change
   * @return the updated field
   */
  public Field changeDataForImportedField(
      Field fld, long id, Map<String, String> attrs, String linkType, int indexToChange) {
    if (!FieldParserConstants.isRSpaceCSSClassname(linkType)) {
      throw new IllegalArgumentException(
          linkType
              + " is not a valid CSS link type - must be one of "
              + StringUtils.join(FieldParserConstants.getRSpaceImageCSSClasses()));
    }
    Document dc = Jsoup.parse(fld.getFieldData());

    Elements elms = dc.getElementsByClass(linkType);
    int curElementIndex = 0;
    if (linkType.equals(ANNOTATION_IMG_CLASSNAME)) {
      Elements elmsx =
          dc.getElementsByAttributeValueContaining(
              "data-importedType", FieldParserConstants.ANNOTATION_IMG_CLASSNAME);
      for (Element elmx : elmsx) {
        if (curElementIndex == indexToChange) {
          updateIdAndSrcAttributes(attrs, elmx);
          elmx.attr(DATA_ID, id + "");
          elmx.attr(DATA_TYPE, DATA_TYPE_ANNOTATION);
        }
        curElementIndex++;
      }
      fld.setFieldData(dc.body().html());
      return fld;
    }
    if (elms.size() < indexToChange + 1) {
      fld.setFieldData(dc.body().html());
      return fld;
    }
    Element elm = elms.get(indexToChange);

    if (linkType.equals(FieldParserConstants.COMMENT_CLASS_NAME)) {
      elm.attr("src", "/images/commentIcon.gif");
      elm.attr("id", Long.toString(id));

    } else if (linkType.equals(FieldParserConstants.IMAGE_DROPPED_CLASS_NAME)) {
      // rspac-2424
      Thumbnail old = createThumbnailFromExistingAttributes(fld, id, attrs, elm);
      String newSrc = thumbnailToSrcRef(old);
      attrs.put("src", newSrc);
      updateIdAndSrcAttributes(attrs, elm);
      // remove annotation attributes in case annotation cannot be mapped to an image and add
      // import-specific
      // 'data-importedType' only used during import process .
      if (DATA_TYPE_ANNOTATION.equals(elm.attr(DATA_TYPE))) {
        elm.removeAttr(DATA_ID)
            .removeAttr(DATA_TYPE)
            .attr("data-importedType", DATA_TYPE_ANNOTATION);
      }

    } else if (linkType.equals(FieldParserConstants.SKETCH_IMG_CLASSNAME)) {
      updateIdAndSrcAttributes(attrs, elm);
    } else if (linkType.equals(CHEM_IMG_CLASSNAME)) {
      updateIdAndSrcAttributes(attrs, elm);
      if (attrs.containsKey(DATA_CHEM_FILE_ID)) {
        elm.attr(DATA_CHEM_FILE_ID, attrs.get(DATA_CHEM_FILE_ID));
      }
    } else if (linkType.equals(FieldParserConstants.MATH_CLASSNAME)) {
      updateMathAttributes(attrs, elm);
    } else if (linkType.equals(FieldParserConstants.ATTACHMENT_CLASSNAME)) {
      String aid = "attachOnText_" + Long.toString(id);
      elm.attr("id", aid);
      String ahref = String.format("/Streamfile/%1$d/%2$s", id, attrs.get("href"));
      elm.attr("href", ahref);
      Element iconDiv = elm.parent().nextElementSibling();
      if (iconDiv != null && (iconDiv.hasClass("attachmentInfoDiv"))) {
        iconDiv.attr("id", "attachmentInfoDiv_" + id);
      }
    }

    String fdata2 = dc.body().html();
    fld.setFieldData(fdata2);
    return fld;
  }

  private Thumbnail createThumbnailFromExistingAttributes(
      Field fld, long id, Map<String, String> attrs, Element elm) {
    Thumbnail old = new Thumbnail();
    old.setSourceId(id);
    old.setSourceParentId(fld.getId());
    if (elm.hasAttr(WIDTH)) {
      old.setWidth(Integer.parseInt(elm.attr(WIDTH)));
    }
    if (elm.hasAttr(HEIGHT)) {
      old.setHeight(Integer.parseInt(elm.attr(HEIGHT)));
    }
    if (elm.hasAttr("data-rotation")) {
      old.setRotation(Byte.parseByte(elm.attr("data-rotation")));
    }
    old.setRevision(Long.parseLong(attrs.get("revision")));
    return old;
  }

  private void updateMathAttributes(Map<String, String> attrs, Element elm) {
    elm.attr(DATA_MATHID, attrs.get(DATA_MATHID));
    elm.getElementsByTag(OBJECT).attr("data", attrs.get("data"));
  }

  private void updateIdAndSrcAttributes(Map<String, String> attrs, Element elm) {
    elm.attr("id", attrs.get("id"));
    if (elm.hasAttr(DATA_ID) && attrs.containsKey(DATA_ID)) {
      elm.attr(DATA_ID, attrs.get(DATA_ID));
    }
    elm.attr("src", attrs.get("src"));
  }

  /**
   * Updates AV/Document links with new id/src attributes
   *
   * @param fld the Field to update
   * @param media the {@link EcatMediaFile} of values to use
   * @return the updated field
   */
  public Field changeMediaData(
      Field fld, EcatMediaFile media, int index, String audioOrVideoClassName) {
    long rand = Long.parseLong(RandomStringUtils.randomNumeric(10));
    Document dc = Jsoup.parse(fld.getFieldData());
    String id = String.format("%1$d-%2$d", fld.getId(), media.getId());
    Elements exs = dc.getElementsByTag("img").select("." + audioOrVideoClassName).eq(index);
    String title = "";
    for (Element ex : exs) {
      int oldid = NumberUtils.stringToInt(ex.attr("id").split("-")[1], -1);
      ex.attr("id", id);
      title = ex.attr("title");
      ex.attr("title", media.getName());
      String onclick =
          String.format(
              "openMedia(%1$d,'%2$s','%3$s')",
              media.getId(), media.getName(), media.getExtension());
      ex.attr("onclick", onclick);
      ex.attr("data-video", media.getName());
      ex.parent().attr("id", ex.parent().attr("id").replace(oldid + "", media.getId() + ""));
      ex.parent()
          .select("a[id^=attachOnText]")
          .attr("id", "attachOnText_" + media.getId())
          .attr("href", "/Streamfile/" + media.getId());
      ex.parent().select(".attachmentInfoDiv").attr("id", "attachmentInfoDiv_" + media.getId());
      String divId = String.format("videoContainer_%1$d_%2$d_wrapper", media.getId(), rand);
      ex.parent().select("div[id^=videoContainer_]").attr("id", divId);
      ex.parent().select("object[id^=videoContainer_]").attr("id", divId).attr("name", divId);
      Elements params = ex.parent().select("param");
      for (Element pm : params) {
        long mediaId = media.getId();
        String nm = pm.attr("name");
        if (nm.equals("flashvars")) {
          String url1 =
              String.format(
                  "http://localhost:8080/" + SDOC_URL_PATH + "/%d",
                  (fld.getStructuredDocument() != null) ? fld.getStructuredDocument().getId() : -1);
          String url2 =
              String.format("&id=videoContainer_%1$d_%2$d&className=videoTemp", mediaId, rand);
          String url3 =
              String.format(
                  "&file=/Streamfile/%1$d/%2$s&allowscriptaccess=never&controlbar.position=over",
                  mediaId, media.getName());
          String url = url1 + url2 + url3;
          pm.attr(VALUE, url);
        }
      }
    }

    String fdata2 = dc.body().html();
    if (!StringUtils.isEmpty(title)) { // RSPAC-387
      fdata2 = fdata2.replaceAll(title, media.getName());
    }
    fld.setFieldData(fdata2);
    return fld;
  }

  public String updateLinkedDocument(
      String fdata, Map<Long, Long> old2NewId, List<Long> updatedTargetIds) {
    if (fdata == null || fdata.length() < 5) { // why 5?
      return fdata;
    }
    Document dc = Jsoup.parse(fdata);
    Elements elms = dc.getElementsByClass(FieldParserConstants.LINKEDRECORD_CLASS_NAME);
    for (Element elm : elms) {
      String oldIdAttr = elm.attr("id");
      Integer oldId = NumberUtils.stringToInt(oldIdAttr, -1);

      String oldGlobalId;
      if (elm.hasAttr(DATA_GLOBALID)) {
        oldGlobalId = elm.attr(DATA_GLOBALID);
      } else {
        // pre-1.47 links don't have globalid
        oldGlobalId = GlobalIdPrefix.SD.toString() + oldId;
      }

      Long newId = old2NewId.get(Long.valueOf(oldId));
      if (newId == null) {
        log.warn(
            "No entry in old2NewId map for old Id {}, perhaps has been updated already", oldIdAttr);
        continue;
      }
      String newGlobalId = oldGlobalId.replace(oldId + "", newId + "");
      elm.attr("id", newId + "");
      elm.attr(DATA_GLOBALID, newGlobalId);
      elm.attr("href", "/globalId/" + newGlobalId);

      // replace global id at the start of link content
      String linkContent = elm.html();
      if (linkContent != null) {
        elm.html(linkContent.replaceAll("^" + oldGlobalId + ":", newGlobalId + ":"));
      }

      // for pre-1.47 archive import add mceNonEditable class (RSPAC-1343)
      String cssClass = elm.attr(CLASS);
      if (FieldParserConstants.LINKEDRECORD_CLASS_NAME.equals(cssClass)) {
        elm.attr(CLASS, cssClass + " mceNonEditable");
      }

      updatedTargetIds.add(newId);
    }
    return dc.body().html();
  }

  public String updateAVIdsInCopy(String content, Long newFieldId) {
    Document dc = Jsoup.parse(content);
    Elements elms =
        dc.getElementsByAttributeValueContaining(CLASS, FieldParserConstants.AUDIO_CLASSNAME);
    elms.addAll(
        dc.getElementsByAttributeValueContaining(CLASS, FieldParserConstants.VIDEO_CLASSNAME));
    for (Element elm : elms) {
      String compId = elm.attr("id");
      compId = compId.replaceAll("^\\d+-", newFieldId + "-");
      elm.attr("id", compId);
    }
    return dc.body().html();
  }

  public String updateThumbnailParentIds(String content, Long newParentId) {
    Document dc = Jsoup.parse(content);
    Elements elms =
        dc.getElementsByAttributeValueContaining(
            CLASS, FieldParserConstants.IMAGE_DROPPED_CLASS_NAME);
    for (Element elm : elms) {
      if (elm.attr("src").startsWith("/thumbnail")) {
        String src = elm.attr("src");
        src = src.replaceFirst("sourceParentId=\\d+", "sourceParentId=" + newParentId);
        elm.attr("src", src);
        String id = elm.attr("id");
        id = id.replaceFirst("^\\d+-", newParentId + "-");
        elm.attr("id", id);
      }
    }
    return dc.body().html();
  }

  /** Generates new src URL for a thumbnail */
  private String thumbnailToSrcRef(Thumbnail thumbnail) {
    Map<String, Object> velocityModel =
        Map.of(
            "fieldId",
            thumbnail.getSourceParentId(),
            "itemId",
            thumbnail.getSourceId(),
            WIDTH,
            thumbnail.getWidth(),
            HEIGHT,
            thumbnail.getHeight(),
            "rotation",
            thumbnail.getRotation(),
            "milliseconds",
            thumbnail.getRevision());
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "thumbnailImageSrc.vm", UTF_8, velocityModel);
    return msg.replace("\n", "");
  }
}
