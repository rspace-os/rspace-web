package com.researchspace.service;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.security.sasl.AuthenticationException;

/** Handles persistence /retrieval of media content. */
public interface MediaManager {

  /**
   * Saves a file to File Store and creates a new EcatImage entity for it. <br>
   * Delegates to {{@link #saveMediaFile(InputStream, Long, String, String, Long, Folder, String,
   * User)}.
   *
   * @param originalFileName
   * @param inputStream A new, unread InputStream to the image to be saved.
   * @param user
   * @param targetFolder (optional) if null, the Gallery Images folder is used
   * @return EcatImage
   * @throws IOException
   */
  EcatImage saveNewImage(
      String originalFileName, InputStream inputStream, User user, Folder targetFolder)
      throws IOException;

  /**
   * For importing from archives, preserving original information
   *
   * @param originalFileName
   * @param inputStream
   * @param user
   * @param targetFolder
   * @param override
   * @return
   * @throws IOException
   */
  EcatImage saveNewImage(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException;

  /**
   * Saves provided pngBase64Image as a new Gallery image linked to the original image.
   *
   * @param sourceImage gallery image that was a source for edition
   * @param uiBase64Image image bytes in Base64 encoding, prefixed with content-type specification
   * @param user
   * @return new saved EcatImage
   * @throws IOException
   */
  EcatImage saveEditedImage(EcatImage sourceImage, String uiBase64Image, User user)
      throws IOException;

  /**
   * Saves a file to File Store and creates a new EcatVideo entity for it. <br>
   * Delegates to {{@link #saveMediaFile(InputStream, Long, String, String, Long, Folder, String,
   * User)}.
   *
   * @param originalFileName
   * @param inputStream
   * @param user
   * @param targetFolder (optional) if null, the Gallery Video folder is used
   * @param override nullable
   * @return EcatVideo
   * @throws IOException
   */
  EcatVideo saveNewVideo(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException;

  /**
   * Saves a file to File Store and creates a new EcatAudio entity for it. <br>
   * Delegates to {{@link #saveMediaFile(InputStream, Long, String, String, Long, Folder, String,
   * User)}.
   *
   * @param originalFileName
   * @param inputStream
   * @param user
   * @param targetFolder (optional) if null, the Gallery Audio folder is used
   * @param override nullable
   * @return EcatAudio
   * @throws IOException
   */
  EcatAudio saveNewAudio(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException;

  EcatDocumentFile saveNewDMP(
      String originalFileName, InputStream inputStream, User user, ImportOverride override)
      throws IOException;

  /**
   * Saves a file to File Store and creates a new EcatChemistry entity for it. <br>
   * Delegates to {{@link #saveMediaFile(InputStream, Long, String, String, Long, Folder, String,
   * User)}.
   *
   * @param originalFileName
   * @param inputStream
   * @param user
   * @param targetFolder (optional) if null, the Gallery Chemistry folder is used
   * @return EcatAudio
   * @throws IOException
   */
  EcatChemistryFile saveNewChemFile(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException;

  /**
   * Saves a file to File Store and creates a new EcatDocumentFile entity for it. <br>
   * Delegates to {{@link #saveMediaFile(InputStream, Long, String, String, Long, Folder, String,
   * User)}.
   *
   * @param originalFileName
   * @param inputStream
   * @param user
   * @param targetFolder (optional) if null, the Gallery Documents/Misc folder is used
   * @return EcatDocumentFile
   * @throws IOException
   */
  EcatDocumentFile saveNewDocument(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException;

  /**
   * Saves input stream as a file in File Store Gallery, and creates (or updates) EcatMediaFile
   * entity. When creating new entity the type is decided based on file extension. <br>
   * The method normally closes the input stream passed in.
   *
   * @param mediaFileId (optional) id of EcatMediaFile this upload should update. If provided,
   *     method will call {@link #updateMediaFile(Long, InputStream, String, User, String)}.
   * @param inputStream
   * @param displayName
   * @param originalFileName
   * @param fieldId (optional) id of Field to which this upload is attached
   * @param targetFolder (optional) id of Folder where the file should be saved
   * @param caption (optional) can be empty string or null
   * @param user
   * @return subclass of EcatMediaFile (decided on file extension)
   * @throws IOException
   */
  EcatMediaFile saveMediaFile(
      InputStream inputStream,
      Long mediaFileId,
      String displayName,
      String originalFileName,
      Long fieldId,
      Folder targetFolder,
      String caption,
      User user)
      throws IOException;

  /**
   * Saves input stream as a new version of existing EcatMediaFile.
   *
   * @param mediaFileId id of EcatMediaFile this upload should update
   * @param inputStream
   * @param updatedFileName
   * @param user
   * @param lockId (optional) if current operation owns edit lock on the file, it can pass it
   * @return
   * @throws IOException
   */
  EcatMediaFile updateMediaFile(
      Long mediaFileId, InputStream inputStream, String updatedFileName, User user, String lockId)
      throws IOException;

  /**
   * Method to insert a new comment.
   *
   * @param fieldId
   * @param comment
   * @return
   * @throws {@link AuthenticationException} if not resource access not authorized
   */
  EcatComment insertEcatComment(String fieldId, String comment, User user);

  /**
   * Method used to add a comment in another comment.
   *
   * @param fieldId
   * @param commentId
   * @param comment
   * @return EcatComment
   * @throws Exception
   */
  EcatComment addEcatComment(String fieldId, String commentId, String comment, User user);

  /**
   * Updates an existing sketch, or creates a new one.
   *
   * @param annotations
   * @param imageBase64
   * @param sketchId can be "", in which case a new sketch is created
   * @param fieldId
   * @param record
   * @param subject
   * @return EcatImageAnnotation
   * @throws IOException
   */
  EcatImageAnnotation saveSketch(
      String annotations,
      String imageBase64,
      String sketchId,
      long fieldId,
      Record record,
      User subject)
      throws IOException;

  /**
   * @param annotations
   * @param imageBase64
   * @param fieldId
   * @param record
   * @param imageId
   * @return EcatImageAnnotation
   * @throws IOException
   */
  EcatImageAnnotation saveImageAnnotation(
      String annotations,
      String imageBase64,
      long fieldId,
      Record record,
      long imageId,
      User subject)
      throws IOException;

  /**
   * Method used to save a chemistry element on the database. We use this method on the archive
   * import process.
   *
   * @param inputStream : to get the information about the image (getDataImage or getData)
   * @param chem : JSON string is used to populate the chem sketcher widgets.
   * @param format ChemElements format type.
   * @param fieldId : it's the id of parent field.
   * @param format ChemElements format type.
   * @return RSChemElement
   * @throws Exception
   */
  RSChemElement importChemElement(
      InputStream inputStream, String chem, ChemElementsFormat format, long fieldId, Record record)
      throws IOException;

  /**
   * Method used to save a sketch element on the database. We use this methods on the archiving
   * process.
   *
   * @param inputStream : to get the information about the image (getDataImage or getData)
   * @param annotations : JSON string is used to populate the sketcher widgets.
   * @param fieldId : it's the id of parent field.
   * @param record
   * @return EcatImageAnnotation
   * @throws IOException if input stream does not close
   */
  EcatImageAnnotation importSketch(
      InputStream inputStream, String annotations, long fieldId, Record record) throws IOException;

  /**
   * Method used to save an image annotation element on the database. We use this method on the
   * archiving process.
   *
   * @param inputStream : to get the information about the image (getDataImage or getData)
   * @param annotations : JSON string is used to populate the sketcher widgets.
   * @param fieldId : it's the id of parent field.
   * @return EcatImageAnnotation
   * @throws Exception
   */
  EcatImageAnnotation importImageAnnotation(
      InputStream inputStream, String annotations, long fieldId, Record record, long imageId)
      throws IOException;

  /**
   * Gets an {@link EcatImage} by ID
   *
   * @param imageId
   * @param user
   * @param includeImageBytes boolean whether to lazy load image bytes or not
   * @return An {@link EcatImage}
   * @throws AuthenticationException if user not permitted to access the image.
   */
  EcatImage getImage(Long imageId, User user, boolean includeImageBytes);

  /**
   * Creates or updates an {@link RSMath} element
   *
   * @param svg The SVG of the Math object
   * @param fieldId
   * @param latex
   * @param mathId
   * @param subject
   * @return the created/updated RSMath object
   */
  RSMath saveMath(String svg, long fieldId, String latex, Long mathId, User subject);

  /**
   * Import Math element ( from XML import )
   *
   * @param inputStream
   * @param latex
   * @param fieldId
   * @param record
   * @return
   * @throws IOException
   */
  RSMath importMathElement(InputStream inputStream, String latex, long fieldId, Record record)
      throws IOException;

  /**
   * Gets IDs of linked documents
   *
   * @param mediaFileId
   * @return
   */
  List<RecordInformation> getIdsOfLinkedDocuments(Long mediaFileId);

  /**
   * @return lock handler used by the manager
   */
  MediaFileLockHandler getLockHandler();

  EcatMediaFile saveMediaFile(
      InputStream inputStream,
      Long mediaFileId,
      String displayName,
      String originalFileName,
      Long fieldId,
      Folder targetFolder,
      String caption,
      User user,
      ImportOverride override)
      throws IOException;
}
