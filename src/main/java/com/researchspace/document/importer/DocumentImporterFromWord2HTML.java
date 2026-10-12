package com.researchspace.document.importer;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FieldManager;
import com.researchspace.service.MediaContentMismatchException;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class DocumentImporterFromWord2HTML implements RSpaceDocumentCreator {

  private @Autowired RecordManager recMgr;
  private @Autowired MessageSourceUtils messages;
  private @Autowired MediaManager mediaMgr;
  private @Autowired RichTextUpdater richTextUpdater;
  private @Autowired FieldManager fieldMgr;
  private @Autowired IPermissionUtils permUtils;

  private Logger log = LoggerFactory.getLogger(getClass());

  public BaseRecord create(
      ContentProvider provider,
      Folder targetFolder,
      Folder imageFolder,
      String docName,
      User creator)
      throws IOException {
    assertTargetImageFolder(imageFolder, creator);
    log.info(
        "Creating RSpace doc in {}-({}) using content in {}",
        targetFolder.getName(),
        targetFolder.getId(),
        provider.getContentFolder().getName());
    File contentFolder = provider.getContentFolder();
    StructuredDocument strucDoc =
        extractAndAddImages(provider, targetFolder, imageFolder, docName, creator, contentFolder);
    return strucDoc;
  }

  StructuredDocument extractAndAddImages(
      ContentProvider provider,
      Folder targetFolder,
      Folder imageFolder,
      String docName,
      User creator,
      File contentFolder)
      throws IOException, FileNotFoundException {
    Document doc = Jsoup.parse(provider.getTextFieldSource(), null, "");
    Elements images = doc.getElementsByTag("img");

    StructuredDocument strucDoc = recMgr.createBasicDocument(targetFolder.getId(), creator);
    strucDoc.setName(docName);
    strucDoc = recMgr.save(strucDoc, creator).asStrucDoc();
    updateFieldContent(creator, contentFolder, doc, images, imageFolder, strucDoc);
    return strucDoc;
  }

  private void assertTargetImageFolder(Folder imageFolder, User u) {
    if (imageFolder != null) {
      permUtils.assertIsPermitted(imageFolder, PermissionType.READ, u, "import image");
      if (imageFolder.getShortestPathToParent(this::isImagesMediaFolder).isEmpty()) {
        throw new IllegalArgumentException(
            "Image folder must be a subfolder of Gallery Image folder but was id: "
                + imageFolder.getId());
      }
    }
  }

  private boolean isImagesMediaFolder(BaseRecord parent) {
    return MediaUtils.IMAGES_MEDIA_FLDER_NAME.equals(parent.getName())
        && parent.hasType(RecordType.SYSTEM);
  }

  private StructuredDocument updateFieldContent(
      User creator,
      File contentFolder,
      Document doc,
      Elements images,
      Folder imageFolder,
      StructuredDocument strucDoc)
      throws FileNotFoundException, IOException {

    Field textField = strucDoc.getFields().get(0);
    importImages(creator, contentFolder, images, imageFolder, strucDoc);
    textField.setFieldData(doc.body().html());
    return recMgr.save(strucDoc, creator).asStrucDoc();
  }

  private void importImages(
      User creator,
      File contentFolder,
      Elements images,
      Folder imageFolder,
      StructuredDocument strucDoc)
      throws IOException {
    Field textField = strucDoc.getFields().get(0);
    for (Element img : images) {
      String src = img.attr("src");
      String wordStyle = img.attr("style");
      File imageFile = new File(contentFolder, src);

      String displayName = strucDoc.getName() + "-" + src;
      try (FileInputStream fis = new FileInputStream(imageFile)) {
        EcatImage savedImage = mediaMgr.saveNewImage(displayName, fis, creator, imageFolder);
        replaceCurrImageTagWithRSpaceImgTag(textField, img, wordStyle, savedImage);
        fieldMgr.addMediaFileLink(savedImage.getId(), creator, textField.getId(), true);
      } catch (MediaContentMismatchException e) {
        log.warn("Image {} could not be saved to RSpace, skipping: {}", src, e.getMessage());
      }
    }
  }

  private void replaceCurrImageTagWithRSpaceImgTag(
      Field textField, Element img, String wordStyle, EcatImage savedImage) {
    String imgStr = richTextUpdater.generateRawImageElement(savedImage, textField.getId() + "");
    Document docx = Jsoup.parse(imgStr, "", Parser.xmlParser());
    Element tag = docx.getElementsByTag("img").get(0);
    tag.attr("style", wordStyle + tag.attr("style"));
    img.replaceWith(tag);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public BaseRecord replace(
      Long toReplaceId, ContentProvider provider, String origDocName, User user)
      throws IOException, DocumentAlreadyEditedException {
    StructuredDocument document = recMgr.getRecordWithFields(toReplaceId, user).asStrucDoc();
    permUtils.assertIsPermitted(
        document,
        PermissionType.WRITE,
        user,
        messages.getMessage("workspace.word.import.updateAction"));
    if (!document.isBasicDocument()) {
      throw new IllegalArgumentException(
          messages.getMessage("workspace.word.import.basicDocumentRequired"));
    }
    if (document.isSigned()
        || document.isDeleted()
        || document.isDeletedForUser(user)
        || document.isDeletedForUser(document.getOwner())
        || !document.isEditable()) {
      throw new IllegalArgumentException(
          messages.getMessage("workspace.word.import.targetNotEditable"));
    }
    Document html = Jsoup.parse(provider.getTextFieldSource(), null, "");
    // Flush pending autosaves before replacing the content.
    // This also checks whether another user is editing the document.
    recMgr.saveStructuredDocument(toReplaceId, user.getUsername(), false, null);
    importImages(user, provider.getContentFolder(), html.getElementsByTag("img"), null, document);
    // Use the regular edit path to sanitize HTML and synchronize attachment/internal links.
    recMgr.saveTemporaryDocument(document.getFields().get(0), user, html.body().html());
    recMgr.saveStructuredDocument(toReplaceId, user.getUsername(), false, null);
    return document;
  }
}
