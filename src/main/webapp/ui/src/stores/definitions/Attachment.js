//@flow

import { type Record } from "./Record";
import { type URL } from "../../util/types";

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
 * for use by the UI code.
 *
 * Some special behaviour is defined for Chemistry files.
 */
export interface Attachment extends Record {
  imageLink: ?URL;
  loadingImage: boolean;
  loadingString: boolean;
  chemicalString: string;
  file: ?File;

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
}
