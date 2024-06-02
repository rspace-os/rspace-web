package com.researchspace.document.importer;

import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RecordManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;

/** Helper class with common methods for external-importer services */
@Slf4j
public abstract class AbstractExternalDocImporter {

  private @Autowired MediaManager mediaMgr;
  private @Autowired RichTextUpdater richTextUpdater;
  private @Autowired FieldManager fieldMgr;
  protected @Autowired RecordManager recMgr;
  protected @Autowired FolderManager folderMgr;

  /**
   * Given a newly created structured document, with HTML content containing links to locally-stored
   * files, identified by Elements <code>aResourceLinks</code>, this method will obtain and import
   * the files, save them as EcatMedia entities, then update the links to be standard RSpace
   * attachment links. <br>
   * Also sets up mediaFile link to associate the field with the media item.
   *
   * <p>
   *
   * @param creator
   * @param jsoupDoc
   * @param aResourceLinks
   * @param imageFolder
   * @param strucDoc
   * @param fileLocator A lambda to get the source reference from a JSoup Element
   * @return the modified StructuredDocument
   * @throws FileNotFoundException
   * @throws IOException if linked files cannot be obtained, or temporary files cannot be written
   */
  protected StructuredDocument updateFieldContent(
      User creator,
      Document jsoupDoc,
      Elements aResourceLinks,
      Folder imageFolder,
      StructuredDocument strucDoc,
      Function<Element, String> fileLocator) {

    Field textField = strucDoc.getFields().get(0);

    Map<File, EcatMediaFile> seenMap = new HashMap<>();

    for (Element resourceEl : aResourceLinks) {
      String htmlFileRef = fileLocator.apply(resourceEl);

      File attachmentFile = new File(htmlFileRef);
      if (!attachmentFile.exists()) {
        log.warn("File {} does not exist, cannot import to RSpace ", htmlFileRef);
        continue;
      }
      try (FileInputStream fis = new FileInputStream(attachmentFile)) {
        String displayName = FilenameUtils.getName(htmlFileRef);
        EcatMediaFile ecatMedia = null;
        // don't save duplicates
        if (!seenMap.containsKey(attachmentFile)) {
          ecatMedia =
              mediaMgr.saveMediaFile(
                  fis, null, displayName, displayName, null, imageFolder, null, creator);
          seenMap.put(attachmentFile, ecatMedia);
          fieldMgr.addMediaFileLink(ecatMedia.getId(), creator, textField.getId(), true);
        } else {
          ecatMedia = seenMap.get(attachmentFile);
        }
        replaceCurrImageTagWithRSpaceImgTag(textField, resourceEl, ecatMedia);
      } catch (IOException e) {
        log.warn("File referenced by {} could not be saved to RSpace, skipping", htmlFileRef, e);
      }
    }
    textField.setFieldData(jsoupDoc.body().html());
    // changes propagated to fields in single transaction for audit trail
    return recMgr.save(strucDoc, creator).asStrucDoc();
  }

  private void replaceCurrImageTagWithRSpaceImgTag(
      Field textField, Element aResourceTag, EcatMediaFile ecatMedia) {
    String replacementHtml = "";
    Element replaceElement = null;
    if (ecatMedia.isImage()) {
      replacementHtml =
          richTextUpdater.generateRawImageElement((EcatImage) ecatMedia, textField.getId() + "");
      Document docx = Jsoup.parse(replacementHtml, "");
      replaceElement = (docx.getElementsByTag("img").first());
    } else if (ecatMedia.isEcatDocument()) {
      replacementHtml = richTextUpdater.generateURLString((EcatDocumentFile) ecatMedia);
      replaceElement = generateReplaceElement(replacementHtml);
    } else if (ecatMedia.isAudio()) {
      replacementHtml = richTextUpdater.generateURLString((EcatAudio) ecatMedia, textField.getId());
      replaceElement = generateReplaceElement(replacementHtml);
    } else if (ecatMedia.isVideo()) {
      replacementHtml = richTextUpdater.generateURLString((EcatVideo) ecatMedia, textField.getId());
      replaceElement = generateReplaceElement(replacementHtml);
    }
    // we may have removed duplicate images from the DOM, in which case one or both of these may be
    // null
    if (aResourceTag != null && replaceElement != null && aResourceTag.hasParent()) {
      aResourceTag.replaceWith(replaceElement);
    }
  }

  private Element generateReplaceElement(String replacementHtml) {
    Document docx = Jsoup.parse(replacementHtml, "");
    Element replaceElement = attachmentDiv(docx);
    return replaceElement;
  }

  private Element attachmentDiv(Document docx) {
    return docx.getElementsByClass("attachmentDiv").first();
  }
}
