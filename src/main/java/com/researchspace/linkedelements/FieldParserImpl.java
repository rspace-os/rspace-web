package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.*;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.field.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Parses and gets all the elements it could find on a textfield URL structure of the elements on a
 * text field.
 *
 * <p>Image: <img id="fieldId-imageId" class="imageDropped"
 * src="/images/getImage/parentId/imageId/timestamp"/> Video: <img id="fieldId-videoId"
 * class="videoDropped" title="video.mp4" onclick="openMedia(videoId,'video.mp4','mp4')\"
 * src="/images/icons/video.png" data-video="videoId,small.mp4,mp4" /> Audio: <img
 * id="fieldId-audioId" class="audioDropped" title="audio.mp3"
 * onclick="openMedia(audioId,'audio.mp3','mp3')\" src="/images/icons/audio.png"
 * data-video="audioId,audio.mp3,mp3" /> Attachment: <a id="attachOnText_attachId"
 * class="attachmentLinked" href="/Streamfile/attachId/filename" target="_blank"
 * data-type="Documents">filename</a> Sketch: <img id="sketchId" class="sketch"
 * src="/image/getImageSketch/skecthId/unused" alt=\"image\" /> ChemDoodle: <img id="chemElementId"
 * class="chem" src="/chemical/getImageChem/chemElementId/timestamp" alt="image" /> Comment: <img
 * id="commentId" class="commentIcon" onclick="showComments(commentId,fieldId)"
 * src="/images/commentIcon.gif" alt="image" /> LinkedRecord: <a id="recordLinkedId"
 * class="linkedRecord" href="/workspace/editor/structuredDocument/recordLinkedId"
 * data-name="recordName">recordName</a>
 */
@Slf4j
@Component("fieldParser")
public class FieldParserImpl implements FieldParser {

  // at most 18 digits, so the id always fits in a long
  private static final Pattern COMPOSITE_ID = Pattern.compile("^\\d{1,18}-(\\d{1,18})$");

  private @Autowired FieldParserFactoryImpl fieldParserFactory;

  @Setter private @Autowired ElementSelectorFactory elementSelectorFactory;

  final List<String> cssClassesToParse =
      TransformerUtils.toList(
          AUDIO_CLASSNAME,
          VIDEO_CLASSNAME,
          LINKEDRECORD_CLASS_NAME,
          SKETCH_IMG_CLASSNAME,
          COMMENT_CLASS_NAME,
          CHEM_IMG_CLASSNAME,
          MATH_CLASSNAME,
          ATTACHMENT_CLASSNAME,
          IMAGE_DROPPED_CLASS_NAME,
          NET_FS_CLASSNAME);

  public FieldContents findFieldElementsInContent(String content) {
    FieldContents contents = new FieldContents();
    Element doc = Jsoup.parse(content);
    for (String cssClass : cssClassesToParse) {
      ElementSelector sel = elementSelectorFactory.getElementSelectorForClass(cssClass);
      sel.select(doc).forEach(el -> convertToLinkableElement(contents, cssClass, el));
    }
    return contents;
  }

  @Override
  public FieldContents findFieldElementsInContentForCssClass(
      FieldContents fieldContents, String content, String cssClass) {
    ElementSelector sel = elementSelectorFactory.getElementSelectorForClass(cssClass);
    Element doc = Jsoup.parse(content);
    sel.select(doc).forEach(el -> convertToLinkableElement(fieldContents, cssClass, el));
    return fieldContents;
  }

  @Override
  public Set<Long> findAnnotatedImageIds(String content) {
    Set<Long> imageIds = new LinkedHashSet<>();
    String selector =
        "img." + IMAGE_DROPPED_CLASS_NAME + "[data-type=" + DATA_TYPE_ANNOTATION + "]";
    for (Element el : Jsoup.parse(content).select(selector)) {
      parseImageId(el.attr("id")).ifPresent(imageIds::add);
    }
    return imageIds;
  }

  /* The image id from a composite "fieldId-imageId" element id, if it has exactly that form. */
  private static Optional<Long> parseImageId(String elementId) {
    Matcher matcher = COMPOSITE_ID.matcher(elementId);
    if (matcher.matches()) {
      return Optional.of(Long.parseLong(matcher.group(1)));
    }
    log.warn("unparsable annotated image id: {}", elementId);
    return Optional.empty();
  }

  private void convertToLinkableElement(FieldContents fieldContents, String cssClass, Element el) {
    FieldElementConverter cv = fieldParserFactory.getConverterForClass(cssClass);
    cv.jsoup2LinkableElement(fieldContents, el);
  }

  private String getClassAttributeValue(Element el) {
    return el.attr("class").toLowerCase();
  }

  @Override
  public boolean hasChemElement(Long rsChemElementId, String content) {
    FieldElementIterator it = new FieldElementIterator(content);
    while (it.hasNext()) {
      Element el = it.next();
      String elementClassLowerCase = getClassAttributeValue(el);
      if (isChemElementImg(elementClassLowerCase, el)
          && rsChemElementId.equals(Long.parseLong(el.attr("id")))) {
        return true;
      }
    }
    return false;
  }

  private boolean isChemElementImg(String elementClassLowerCase, Element el) {
    return elementClassLowerCase.contains(FieldParserConstants.CHEM_IMG_CLASSNAME.toLowerCase())
        && el.hasAttr("id");
  }

  @Override
  public Elements getNonRSpaceImages(String fieldHTML) {
    Document d = Jsoup.parse(fieldHTML);
    Elements els = d.getAllElements();
    for (String classname : FieldParserConstants.getRSpaceImageCSSClasses()) {
      els = els.select("img:not(." + classname + ")");
    }
    return els;
  }

  @Override
  public FieldContentDelta findFieldElementChanges(Field originalField, Field newCopyField) {
    return findFieldElementChanges(originalField.getFieldData(), newCopyField.getFieldData());
  }

  @Override
  public FieldContentDelta findFieldElementChanges(String originalData, String newData) {
    FieldContents original = findFieldElementsInContent(originalData);
    FieldContents newContents = findFieldElementsInContent(newData);
    FieldContentDelta delta = original.computeDelta(newContents);
    return delta;
  }
}
