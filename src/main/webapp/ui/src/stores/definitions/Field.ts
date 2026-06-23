import type { ValidationResult } from "../../components/ValidatingSubmitButton";
import type { GalleryFile } from "../../eln/gallery/useGalleryListing";
import type { Attachment } from "./Attachment";
import type { BaseRecord } from "./BaseRecord";

export type FieldType =
  | "choice"
  | "date"
  | "number"
  | "radio"
  | "string"
  | "text"
  | "uri"
  | "time"
  | "reference"
  | "attachment"
  | "link";

export type OptionValue = string;

/**
 * The value of a link field: a single link to another Inventory item or ELN record, with a
 * DataCite relationship type and an optional pinned version.
 */
export type FieldLink = {
  relationType: string;
  targetGlobalId: string;
  versionPin: number | null;
};

export type Option = {
  value: OptionValue;
  label: string;
  editing: boolean;
};

/**
 * Samples and Templates have fields, which are effectively key-value pairs
 * with a type. The type determines how the field is rendered in the UI, and
 * what kind of data can be stored in it.
 */
export interface Field extends BaseRecord {
  attachment: Attachment | null;
  originalAttachment: Attachment | null;
  mandatory: boolean;
  columnIndex: number;
  type: FieldType;
  error: boolean;
  /**
   * Link fields only: set while the link editor is open/unapplied. Blocks the record-level Save
   * via {@link validate} without being an `error`, so opening the editor does not trip the inline
   * "Invalid value" message or the section-header error.
   */
  linkEditInProgress: boolean;
  content: string | number | Date;
  selectedOptions: Array<string> | null;
  options: Array<Option>;

  /**
   * For link template fields: the whitelist of permitted DataCite relation types. An empty array
   * means all relation types are allowed.
   */
  allowedRelationTypes: Array<string>;

  /** For link fields: the single link value, or null when unset. */
  link: FieldLink | null;

  readonly paramsForBackend: object;
  readonly hasContent: boolean;
  readonly renderContentAsString: string;

  validate(): ValidationResult;
  setAttributesDirty(attributes: Record<string, unknown>): void;
  setAttachment(file: File | GalleryFile): void;
  setError(error: boolean): void;
  setLinkEditInProgress(value: boolean): void;
}
