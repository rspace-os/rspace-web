//@flow

import { type ValidationResult } from "../../components/ValidatingSubmitButton";
import { type Progress } from "../../util/progress";

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

/*
 * Maps any given type to an optional string, which when coupled with $ObjMap,
 * allows for any object to be mapped to one with the same keys but where the
 * values are optional strings.
 */
export type OptionalString = (mixed) => ?string;

/*
 * Objects and classes that implement this interface can be edited by the user
 * and those changes can then be persisted on the server. By providing a
 * generic interface components can be reused across the application wherever
 * data is is being edited and then stored, regardless of what the actual data
 * is.
 */
export interface Editable {
  /*
   * This property MUST be true whilst communication with the server regarding
   * data associated with this object is ongoing, such as submitting changes or
   * refreshing the data known to the client.
   */
  loading: boolean;

  /*
   * This property is to be used for displaying a progress bar during lengthly
   * uploads of large amounts of data.
   *
   * Note: Axios's provides an `onUploadProgress` callback function
   * which returns the total and progress bytes from which a percentage can be
   * calculated.
   */
  uploadProgress: Progress;

  /*
   * This computed value MUST state whether the user is permitted to save the
   * record in its current state. It is the enforcement mechanism for all
   * invariants that must be enforced on the client-side before submission.
   */
  +submittable: ValidationResult;

  /*
   * When called, all edits SHOULD be discarded.
   */
  cancel(): Promise<void>;

  /*
   * When called, changes MUST be persisted, e.g. by submitting them to the
   * server.
   */
  update(): Promise<void>;
}

/*
 * Objects that implement this interface describe data, organised into distinct
 * fields, that the user is able to edit.
 *
 * It is paramaterised by a plain object that describes the names and types of
 * the fields that are editable.
 */
export interface HasEditableFields<Fields> {
  /*
   * Given the name of one of the editable fields, this method MUST return
   * whether the user is currently allowed to edit the field.
   */
  isFieldEditable($Keys<Fields>): boolean;

  /*
   * This computed property MUST return the current value of the editable
   * fields.
   */
  +fieldValues: Fields;

  /*
   * Given an object, mapping names of editables fields to values of their
   * respective types, this MUST update their value; such that `fieldValues` is
   * updated to the new value. It MUST be idempotent. It SHOULD also set a
   * dirty flag which is used to enable the save button.
   *
   * Despite best effort to find some way to type this argument something akin
   * to `Partial<Fields>`, it seems impossible to get this to correctly type.
   * Rather than add supression at every call site, it seems cleaner to make the
   * parameter be any-typed.
   */
  setFieldsDirty(any): void;

  /*
   * When true, the user MUST be given the option of not editing each field,
   * with the current value retained. When false, the user MUST not be
   * prevented from eding each field; however the current value MUST be
   * retained if they take no action.
   */
  +canChooseWhichToEdit: boolean;

  /*
   * Given the name of the one of the editable fields and a boolean, this
   * method MUST attempt to the set the editable status of the given field. It
   * MAY fail and have no effect if there are addition factors causing the
   * field to be uneditable.
   */
  setFieldEditable($Keys<Fields>, boolean): void;

  /*
   * This computed property SHOULD provide the option to provide an alternative
   * string, for each field, to be shown when there is currently no value. What
   * it means for a field to have no value is specific to the type of the field
   * and the component used to render it. Returning null or undefined for any
   * field will result in the default string being shown and any implementation
   * choosing not to support this option MUST return an empty object.
   */
  +noValueLabel: {[ key in keyof Fields]: ?string};
}

/*
 * Some pieces of data associated with a given record are shown as fields of a
 * form but cannot be edited by the user.
 */
export interface HasUneditableFields<Fields> {
  /*
   * This computed property MUST return the current value of the uneditable
   * fields.
   */
  +fieldValues: Fields;

  /*
   * This computed property SHOULD provide the option to provide an alternative
   * string, for each field, to be shown when there is currently no value. What
   * it means for a field to have no value is specific to the type of the field
   * and the component used to render it. Returning null or undefined for any
   * field will result in the default string being shown and any implementation
   * choosing not to support this option MUST return an empty object.
   */
  +noValueLabel: {[ key in keyof Fields]: ?string};
}
