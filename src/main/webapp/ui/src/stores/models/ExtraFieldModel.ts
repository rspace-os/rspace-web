import { action, observable, computed, makeObservable } from "mobx";
import Result from "./Result";
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
  id: Id;
  globalId: GlobalId;
  parentGlobalId: GlobalId;
  type: ExtraFieldType;
  name: string;
  content: string;
  editable: boolean;
  editing: boolean;
  newFieldRequest: boolean;
  deleteFieldRequest: boolean;
  lastModified: string;
  initial: boolean;
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

  setAttributesDirty(attrs: {}) {
    this.owner.setAttributesDirty({});
    this.setAttributes(attrs);
  }

  setAttributes(attrs: {}) {
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
