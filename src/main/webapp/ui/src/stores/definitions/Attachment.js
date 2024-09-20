//@flow

import { type Record } from "./Record";
import { type URL } from "../../util/types";
import { type GlobalId } from "./BaseRecord";

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

/*
 * Some records have an associated or set of associated attachments, which
 * facilitate indirect references to files stored in RSpace. The UI then
 * provides the means to upload, preview, and download those attached files.
 * This interface specifies the mechanisms exposed by the attachment model code
 * for use by the UI code. Some special behaviour is defined for Chemistry
 * files.
 *
 * Attachments are themselves a type of record, with a Global Id prefix of
 * "IF", whose job is to associate a file with another Inventory record; either
 * a Sample/Container/Subsample/Template via their "Attachments" field or via
 * the Field record of samples and templates where the field type is
 * "attachment".
 */
export interface Attachment extends Record {
  imageLink: ?URL;
  loadingImage: boolean;
  loadingString: boolean;
  chemicalString: string;

  /*
   * Indicates that the user has chosen to remove the Attachment, but that
   * action has not yet been saved to the server. UI code using this interface
   * MUST use this flag to provide a visual indication that this action has
   * been completed.
   */
  removed: boolean;

  getFile(): Promise<File>;

  /*
   * This method MUST set the removed flag and MAY perform any other cleanup.
   */
  remove(): void;

  /*
   * Implementations SHOULD trigger a download of the file that requires no
   * further user interaction to complete. Code calling this method should
   * take this into account and only call this method once the user has made
   * a clear action to download this file as some attachments may be large
   * and unintentional downloads are likely to be annoying.
   */
  download(): Promise<void>;

  /*
   * Operate on the chemical preview of the attached file.
   */
  createChemicalPreview(): Promise<void>;
  revokeChemicalPreview(): void;

  /*
   * Fetches the associated files and prepares a URL, imageLink, that can be
   * used by the UI to display a preview of the attached file, where one is
   * available. If a file has not been set then implementations of this
   * interface SHOULD throw.
   */
  setImageLink(): Promise<void>;

  /*
   * This method gives the UI the ability to indicate that it is done with a
   * the download link to the file. Implementations of this interface MAY
   * perform clean up at this point and consumers of this interface MUST NOT
   * assume that imageLink will continue to be available. Nonetheless, it is
   * best practice to call this method as early as possible to minimise
   * excess memory usage.
   */
  revokeAuthenticatedLink(URL): void;

  +isChemicalFile: boolean;
  +chemistrySupported: boolean;
  +previewSupported: boolean;

  /**
   * Save any changes to the attachment; uploading new attachments, or deletion
   * ones that have been marked for removal. Is called after edits to a
   * sample/container/subsample/template have been saved, and is called for
   * each attachment of those records and for each field of type attachment,
   * if a sample or template is being saved.
   *
   * @param parentGlobalId This is the Global Id of the record with which the
   *                       attachment is associated: this could be a
   *                       container/sample/subsample/template or it could be a
   *                       field of a sample or template that is of type
   *                       attachment. When saving new attachments the server
   *                       needs to know where to attach them.
   * @return Promise resolves when the network activity has finished.
   */
  save(parentGlobalId: GlobalId): Promise<void>;
}
