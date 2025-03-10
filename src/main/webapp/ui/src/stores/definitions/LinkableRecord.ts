import { type BaseRecord, type RecordIconData } from "./BaseRecord";
import { type URL } from "../../util/types";

/**
 * This interface is for records that have a dedicated page for displaying
 * information about them. As such they can be referenced as a link in order to
 * provide easy navigation. This includes Inventory records like containers and
 * samples and it includes ELN records link documents and folders. It does not
 * include records like Lists of Materials, attachments, or form fields as
 * whilst they may have a name and unique ID they do not have a dedicated web
 * page.
 */
export interface LinkableRecord extends BaseRecord, RecordIconData {
  /**
   * This is the URL that displays information about this record. It will
   * typically be of the form
   * `https://${instance}.researchspace.com/globalId/${this.globalId}`, but not
   * always. Inventory records use a different format
   * (`https://${instance}.researchspace.com/inventory/${this.recordType}/${this.id}`)
   * so as to avoid a page navigation as everything under the `/inventory` path
   * is rendered as a Single Page Application.
   *
   * Unsaved records will not yet have a permalinkURL as they do not have an ID
   */
  readonly permalinkURL: URL | null;
}
