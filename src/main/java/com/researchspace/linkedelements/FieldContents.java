package com.researchspace.linkedelements;

import static org.apache.commons.collections4.CollectionUtils.subtract;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.record.RecordInformation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class FieldContents {

  public static final List<Class<? extends IFieldLinkableElement>> FIELD_ELEMENT_CLASSES =
      List.of(
          EcatAudio.class,
          EcatVideo.class,
          EcatImage.class,
          EcatDocumentFile.class,
          EcatChemistryFile.class,
          RSChemElement.class,
          RSMath.class,
          EcatComment.class,
          RecordInformation.class,
          NfsElement.class);

  private Set<Thumbnail> thumbnails;
  private FieldElementLinkPairs<EcatAudio> audios = new FieldElementLinkPairs<>(EcatAudio.class);
  private FieldElementLinkPairs<EcatVideo> videos = new FieldElementLinkPairs<>(EcatVideo.class);
  private FieldElementLinkPairs<EcatImageAnnotation> imageAnnotations =
      new FieldElementLinkPairs<>(EcatImageAnnotation.class);
  private FieldElementLinkPairs<EcatImageAnnotation> sketches =
      new FieldElementLinkPairs<>(EcatImageAnnotation.class);
  private FieldElementLinkPairs<EcatImage> images = new FieldElementLinkPairs<>(EcatImage.class);
  private FieldElementLinkPairs<EcatDocumentFile> attachments =
      new FieldElementLinkPairs<>(EcatDocumentFile.class);
  private FieldElementLinkPairs<EcatChemistryFile> chemistryFiles =
      new FieldElementLinkPairs<>(EcatChemistryFile.class);
  private FieldElementLinkPairs<RSChemElement> chemElements =
      new FieldElementLinkPairs<>(RSChemElement.class);
  private FieldElementLinkPairs<EcatComment> comments =
      new FieldElementLinkPairs<>(EcatComment.class);
  private FieldElementLinkPairs<RSMath> mathElements = new FieldElementLinkPairs<>(RSMath.class);
  private FieldElementLinkPairs<RecordInformation> linkedRecords =
      new FieldElementLinkPairs<>(RecordInformation.class);
  private FieldElementLinkPairs<NfsElement> linkedNfs =
      new FieldElementLinkPairs<>(NfsElement.class);

  private List<FieldElementLinkPairs<? extends IFieldLinkableElement>> allLinks =
      new ArrayList<FieldElementLinkPairs<? extends IFieldLinkableElement>>();

  public FieldContents() {
    thumbnails = new HashSet<>();
    allLinks =
        TransformerUtils.toList(
            audios,
            videos,
            imageAnnotations,
            sketches,
            images,
            attachments,
            chemElements,
            chemistryFiles,
            comments,
            mathElements,
            linkedRecords,
            linkedNfs);
  }

  /**
   * Gets all links for all the linked item types
   *
   * @return
   */
  public List<String> getAllStringLinks() {
    return allLinks.stream().flatMap(link -> link.getLinks().stream()).collect(Collectors.toList());
  }

  public Set<Thumbnail> getThumbs() {
    return thumbnails;
  }

  public FieldElementLinkPairs<EcatImageAnnotation> getSketches() {
    return sketches;
  }

  public boolean hasSketches() {
    return getSketches().size() > 0;
  }

  public boolean addSketch(EcatImageAnnotation sketch, String link) {
    return sketches.add(new FieldElementLinkPair<EcatImageAnnotation>(sketch, link));
  }

  public FieldElementLinkPairs<EcatImageAnnotation> getImageAnnotations() {
    return imageAnnotations;
  }

  public boolean hasImageAnnotations() {
    return getImageAnnotations().size() > 0;
  }

  public boolean addImageAnnotation(EcatImageAnnotation annot, String link) {
    return imageAnnotations.add(new FieldElementLinkPair<EcatImageAnnotation>(annot, link));
  }

  public FieldElementLinkPairs<EcatChemistryFile> getChemistryFiles() {
    return chemistryFiles;
  }

  public boolean hasChemistryFiles() {
    return getChemistryFiles().size() > 0;
  }

  public boolean addChemistryFile(EcatChemistryFile chemistryFile, String link) {
    return chemistryFiles.add(new FieldElementLinkPair<EcatChemistryFile>(chemistryFile, link));
  }

  /**
   * retrieves RecordInformation pairs about internal links pointing to records on the same instance
   */
  public FieldElementLinkPairs<RecordInformation> getLinkedRecordsWithRelativeUrl() {
    return getLinkedRecords(true);
  }

  /**
   * retrieves RecordInformation pairs about internal links pointing to records on a different
   * instance
   */
  public FieldElementLinkPairs<RecordInformation> getLinkedRecordsWithNonRelativeUrl() {
    return getLinkedRecords(false);
  }

  /* returns either relative or absolute internal links (the latter normally point to external instance) */
  private FieldElementLinkPairs<RecordInformation> getLinkedRecords(boolean onlyRelativeUrls) {
    List<FieldElementLinkPair<RecordInformation>> relativeInternalLink =
        linkedRecords.getPairs().stream()
            .filter(pair -> onlyRelativeUrls == isRelativeUrl(pair.getLink()))
            .collect(Collectors.toList());
    FieldElementLinkPairs<RecordInformation> result =
        new FieldElementLinkPairs<>(RecordInformation.class);
    result.addAll(relativeInternalLink);
    return result;
  }

  public boolean isRelativeUrl(String url) {
    return url != null && url.startsWith("/");
  }

  /**
   * Convenience method to retrieve all images., attachments, AV links
   *
   * @return
   */
  public FieldElementLinkPairs<EcatMediaFile> getAllMediaFiles() {
    FieldElementLinkPairs<EcatMediaFile> mediaLinks =
        new FieldElementLinkPairs<>(EcatMediaFile.class);
    transform(getElements(EcatDocumentFile.class), mediaLinks);
    transform(getElements(EcatAudio.class), mediaLinks);
    transform(getElements(EcatVideo.class), mediaLinks);
    transform(getElements(EcatImage.class), mediaLinks);
    transform(getElements(EcatChemistryFile.class), mediaLinks);
    return mediaLinks;
  }

  /**
   * Convenience method to retrieve a selection of media images., attachments, AV links
   *
   * @return
   */
  public FieldElementLinkPairs<EcatMediaFile> getMediaElements(
      Class<? extends EcatMediaFile>... clazzes) {
    FieldElementLinkPairs<EcatMediaFile> mediaLinks =
        new FieldElementLinkPairs<>(EcatMediaFile.class);
    for (Class<? extends EcatMediaFile> clazz : clazzes) {
      transform(getElements(clazz), mediaLinks);
    }
    return mediaLinks;
  }

  private void transform(
      FieldElementLinkPairs<? extends EcatMediaFile> attachments2,
      FieldElementLinkPairs<EcatMediaFile> mediaLinks) {
    attachments2.getPairs().stream()
        .forEach(
            pair ->
                mediaLinks.add(
                    new FieldElementLinkPair<EcatMediaFile>(pair.getElement(), pair.getLink())));
  }

  private void transform2Element(
      FieldElementLinkPairs<? extends IFieldLinkableElement> attachments2,
      FieldElementLinkPairs<IFieldLinkableElement> mediaLinks) {
    attachments2.getPairs().stream()
        .forEach(
            pair ->
                mediaLinks.add(
                    new FieldElementLinkPair<IFieldLinkableElement>(
                        pair.getElement(), pair.getLink())));
  }

  /**
   * Convenience method to return all links to RSpace resources
   *
   * @return
   */
  public FieldElementLinkPairs<IFieldLinkableElement> getAllLinks() {
    FieldElementLinkPairs<IFieldLinkableElement> mediaLinks =
        new FieldElementLinkPairs<>(IFieldLinkableElement.class);
    transform2Element(getAllMediaFiles(), mediaLinks);
    transform2Element(getElements(RSChemElement.class), mediaLinks);
    transform2Element(getSketches(), mediaLinks);
    transform2Element(getElements(RSMath.class), mediaLinks);
    transform2Element(getImageAnnotations(), mediaLinks);
    transform2Element(getElements(EcatComment.class), mediaLinks);
    transform2Element(getElements(RecordInformation.class), mediaLinks);
    transform2Element(getElements(NfsElement.class), mediaLinks);
    return mediaLinks;
  }

  public <T extends IFieldLinkableElement> FieldElementLinkPairs<T> getElements(Class<T> clazz) {
    return (FieldElementLinkPairs<T>)
        allLinks.stream().filter(pair -> pair.supportsClass(clazz)).findFirst().get();
  }

  public <T extends IFieldLinkableElement> boolean hasElements(Class<T> clazz) {
    return getElements(clazz).size() > 0;
  }

  public <T extends IFieldLinkableElement> boolean addElement(
      T element, String link, Class<T> clazz) {
    return getElements(clazz).add(new FieldElementLinkPair<T>(element, link));
  }

  /**
   * Calculates what elements have been added to, or removed from <code>newContents</code> relative
   * to this object's contents
   *
   * @param newContents
   * @return
   */
  public FieldContentDelta computeDelta(FieldContents newContents) {
    FieldContents added = new FieldContents();
    FieldContents removed = new FieldContents();
    doCalculateDeltas(newContents, added, removed);

    FieldContentDelta delta = new FieldContentDelta(added, removed);
    return delta;
  }

  private void doCalculateDeltas(
      FieldContents newContents, FieldContents added, FieldContents removed) {
    for (Class clazz : FIELD_ELEMENT_CLASSES) {
      getDeltas(newContents, added, removed, clazz);
    }
    // these are separate as they are the same class so can't be distinguished by class type.
    getSketchAnnotationDeltas(newContents, added, removed);
    getImageAnnotationDeltas(newContents, added, removed);
  }

  private void getSketchAnnotationDeltas(
      FieldContents newContents, FieldContents added, FieldContents removed) {
    Collection<FieldElementLinkPair<EcatImageAnnotation>> removedItems =
        subtract(getSketches().getPairs(), newContents.getSketches().getPairs());
    Collection<FieldElementLinkPair<EcatImageAnnotation>> addedItems =
        subtract(newContents.getSketches().getPairs(), getSketches().getPairs());
    added.getSketches().addAll(addedItems);
    removed.getSketches().addAll(removedItems);
  }

  private void getImageAnnotationDeltas(
      FieldContents newContents, FieldContents added, FieldContents removed) {
    Collection<FieldElementLinkPair<EcatImageAnnotation>> removedItems =
        subtract(getImageAnnotations().getPairs(), newContents.getImageAnnotations().getPairs());
    Collection<FieldElementLinkPair<EcatImageAnnotation>> addedItems =
        subtract(newContents.getImageAnnotations().getPairs(), getImageAnnotations().getPairs());
    added.getImageAnnotations().addAll(addedItems);
    removed.getImageAnnotations().addAll(removedItems);
  }

  private <T extends IFieldLinkableElement> void getDeltas(
      FieldContents newContents, FieldContents added, FieldContents removed, Class<T> clazz) {
    Collection<FieldElementLinkPair<T>> removedItems =
        subtract(getElements(clazz).getPairs(), newContents.getElements(clazz).getPairs());
    Collection<FieldElementLinkPair<T>> addedItems =
        subtract(newContents.getElements(clazz).getPairs(), getElements(clazz).getPairs());
    added.getElements(clazz).addAll(addedItems);
    removed.getElements(clazz).addAll(removedItems);
  }

  /**
   * Boolean test for whether this field elements object has any elements in any categories.
   *
   * @return
   */
  public boolean hasAnyElements() {
    return allLinks.stream().mapToInt(l -> l.size()).sum() > 0;
  }

  public void addThumbnail(Thumbnail thumb) {
    thumbnails.add(thumb);
  }
}
