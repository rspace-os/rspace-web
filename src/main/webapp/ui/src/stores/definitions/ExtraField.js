//@flow

import { type Id, type GlobalId } from "./BaseRecord";
import { type InventoryRecord } from "./InventoryRecord";
import { type ValidationResult } from "../../components/ValidatingSubmitButton";

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

export type ExtraFieldType = "Text" | "Number";

export type ExtraFieldAttrs = {
  id: Id,
  globalId: ?GlobalId,
  name: string,
  lastModified: ?string,
  type: "text" | "number",
  content: string,
  parentGlobalId: ?GlobalId,
  editable?: boolean,
  editing?: boolean,
  initial?: boolean,
  newFieldRequest?: boolean,
};

/*
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

  setAttributes({}): void;
  setAttributesDirty({}): void;
  setEditing(boolean): void;

  /*
   * Flag that CAN be set
   */
  invalidInput: boolean;
  setInvalidInput(boolean): void;

  +hasContent: boolean;
  +isValid: ValidationResult;
}
