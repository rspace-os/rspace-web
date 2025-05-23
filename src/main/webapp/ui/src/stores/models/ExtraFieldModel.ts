import { action, observable, computed, makeObservable } from "mobx";
import Result from "./InventoryBaseRecord";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import {
  type ExtraFieldAttrs,
  type ExtraFieldType,
  type ExtraField,
} from "../definitions/ExtraField";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import {
  IsValid,
  IsInvalid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";

export default class ExtraFieldModel implements ExtraField {
  // @ts-expect-error Set by the call to setAttributes
  id: Id;
  // @ts-expect-error Set by the call to setAttributes
  globalId: GlobalId;
  // @ts-expect-error Set by the call to setAttributes
  parentGlobalId: GlobalId;
  // @ts-expect-error Set by the call to setAttributes
  type: ExtraFieldType;
  // @ts-expect-error Set by the call to setAttributes
  name: string;
  // @ts-expect-error Set by the call to setAttributes
  content: string;
  // @ts-expect-error Set by the call to setAttributes
  editable: boolean;
  // @ts-expect-error Set by the call to setAttributes
  editing: boolean;
  // @ts-expect-error Set by the call to setAttributes
  newFieldRequest: boolean;
  // @ts-expect-error Set by the call to setAttributes
  deleteFieldRequest: boolean;
  // @ts-expect-error Set by the call to setAttributes
  lastModified: string;
  // @ts-expect-error Set by the call to setAttributes
  initial: boolean;
  // @ts-expect-error Set by the call to setAttributes
  owner: InventoryRecord;
  invalidInput: boolean;

  constructor(attrs: ExtraFieldAttrs, owner: Result) {
    makeObservable(this, {
      type: observable,
      name: observable,
      content: observable,
      editable: observable,
      editing: observable,
      newFieldRequest: observable,
      deleteFieldRequest: observable,
      lastModified: observable,
      initial: observable,
      owner: observable,
      invalidInput: observable,
      setAttributesDirty: action,
      setAttributes: action,
      isValid: computed,
      hasContent: computed,
    });

    this.setAttributes({
      ...attrs,
      owner,
      type: attrs.type === "text" ? "Text" : "Number",
    });
    this.invalidInput = false;
  }

  setAttributesDirty(attrs: Record<string, unknown>) {
    this.owner.setAttributesDirty({});
    this.setAttributes(attrs);
  }

  setAttributes(attrs: Record<string, unknown>) {
    Object.assign(this, attrs);
  }

  setEditing(editing: boolean) {
    this.editing = editing;
  }

  setInvalidInput(invalidInput: boolean) {
    this.invalidInput = invalidInput;
  }

  get isValid(): ValidationResult {
    if (!this.name) return IsInvalid("Names of extra fields cannot be empty.");
    if (this.name.length > 255)
      return IsInvalid("Names of extra fields cannot exceed 255 characters.");
    if (this.type === "Text") {
      if (typeof this.content !== "string")
        return IsInvalid(
          "The content of textual extra fields must be a string."
        );
      if (this.content.length > 250)
        return IsInvalid(
          "The content of textual extra fields cannot exceed 250 characters."
        );
      return IsValid();
    }
    if (this.type === "Number") {
      if (this.invalidInput)
        return IsInvalid(
          "The content of numberical extra fields must be a valid number."
        );
      return IsValid();
    }
    return IsInvalid("Invalid field type");
  }

  get hasContent(): boolean {
    return Boolean(this.content);
  }
}
