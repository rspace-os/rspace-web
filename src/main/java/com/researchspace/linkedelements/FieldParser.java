package com.researchspace.linkedelements;

import com.researchspace.model.field.Field;
import org.jsoup.select.Elements;

/**
 * Parses and gets all the elements it can find on a textfield.
 *
 * <p>URL structure of the elements on a text field: If changing the syntax, alter the example
 * fields as well.
 *
 * <p>Image: <img id="fieldId-imageId" class="imageDropped inlineImageThumbnail"
 * src="/images/getImage/parentId-imageId/timestamp"/> Video: <img id="fieldId-videoId"
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
 * src="/images/commentIcon.gif" alt="image" /> LinkedRecord: <a id="linkedRecordId"
 * data-globalid="linkedRecordGlobalId" class="linkedRecord mceNonEditable"
 * href="/globalId/linkedRecordGlobalId" data-name="recordName">recordName</a>
 */
public interface FieldParser {

  /**
   * Extract all the elements present in the content
   *
   * @param content content where the elements should be searched for
   * @return an object with all the elements found within the content
   */
  FieldContents findFieldElementsInContent(String content);

  /**
   * Boolean test for whether a text field contains a link to the specified chem element id
   *
   * @param rsChemElementId
   * @return <code>true</code> if a link to the chem element is present, <code>false</code>
   *     otherwise
   */
  boolean hasChemElement(Long rsChemElementId, String content);

  /**
   * Gets images that don't have a known RSpace class, i.e., those that are <em>not</em> in
   * FieldParseConstants#RSPACE_IMAGE_CSSCLASSES
   *
   * @param fieldHTML
   * @return
   */
  Elements getNonRSpaceImages(String fieldHTML);

  /**
   * Finds added or removed elements between two fields.
   *
   * @param original the original field
   * @param newVersion the modified field
   * @return a {@link FieldContentDelta} of the changes
   * @throws IllegalArgumentException if the 2 fields don't have the same database ID
   */
  FieldContentDelta findFieldElementChanges(Field original, Field newVersion);

  /**
   * @see findFieldElementChanges(Field original, Field newVersion)
   */
  FieldContentDelta findFieldElementChanges(String originalData, String newData);

  /**
   * Parses field looking for IFieldLinkableElements identified by the <code>cssClass</code>, which
   * must be one of 'cssClassesToParse' array
   *
   * @param fieldContents
   * @param content
   * @param cssClass
   * @return the updated FieldContents
   */
  FieldContents findFieldElementsInContentForCssClass(
      FieldContents fieldContents, String content, String cssClass);
}
