import type { ValidationResult } from "../../components/ValidatingSubmitButton";
import type { GlobalId, Id } from "./BaseRecord";
import type { InventoryRecord } from "./InventoryRecord";

export type ExtraFieldType = "Text" | "Number" | "Link";

export type ExtraInventoryLink = {
  relationType: string;
  targetGlobalId: string;
  versionPin: number | null;
};

export type ExtraFieldAttrs = {
  id: Id;
  globalId: GlobalId | null;
  name: string;
  lastModified: string | null;
  type: "text" | "number" | "link";
  content: string;
  parentGlobalId: GlobalId | null;
  editable?: boolean;
  editing?: boolean;
  initial?: boolean;
  newFieldRequest?: boolean;
  link?: ExtraInventoryLink | null;
};

/**
 * ExtraFields are an user-editable key-value pair of information. Associated
 * with an InventoryRecord, they allow the user to store pieces of information
 * in an unstructured manner.
 */
export interface ExtraField {
  id: Id;
  name: string; // the key of the key-value pair
  type: ExtraFieldType;
  content: string; // the value of the key-value pair
  owner: InventoryRecord;
  initial: boolean;

  /*
   * For Link-typed fields, the inventory/ELN link payload; null for non-Link
   * fields (and for a Link field that has not yet been given a target).
   */
  readonly link: ExtraInventoryLink | null;

  /*
   * Client-side only. True when this field was copied from a template, false
   * when the user added it manually. Never sent to the backend.
   */
  fromTemplate: boolean;

  /*
   * When set, subsequent API calls to store modifications MUST ensure that the
   * extra field is deleted.
   */
  deleteFieldRequest: boolean;

  /*
   * When set, subsequent API calls to store the extra field MUST ensure that
   * the server creates a new extra field and is not attempting to edit an
   * existing one.
   */
  newFieldRequest: boolean;

  /*
   * editing means that the user can modify the content, and editable means
   * that the user can edit the name and type
   */
  editing: boolean;
  editable: boolean;

  // biome-ignore lint/correctness/noEmptyPattern: initial biome migration
  setAttributes({}): void;
  // biome-ignore lint/correctness/noEmptyPattern: initial biome migration
  setAttributesDirty({}): void;
  setEditing(editing: boolean): void;

  /*
   * Flag that CAN be set
   */
  invalidInput: boolean;
  setInvalidInput(invalidInput: boolean): void;

  readonly hasContent: boolean;
  readonly isValid: ValidationResult;
}
