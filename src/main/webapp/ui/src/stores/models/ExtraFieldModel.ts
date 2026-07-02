import { action, computed, makeObservable, observable } from "mobx";
import i18n from "@/modules/common/i18n";
import { IsInvalid, IsValid, type ValidationResult } from "../../components/ValidatingSubmitButton";
import type { GlobalId, Id } from "../definitions/BaseRecord";
import type { ExtraField, ExtraFieldAttrs, ExtraFieldType, ExtraInventoryLink } from "../definitions/ExtraField";
import type { InventoryRecord } from "../definitions/InventoryRecord";
import type InventoryBaseRecord from "./InventoryBaseRecord";

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
  link: ExtraInventoryLink | null = null;

  constructor(attrs: ExtraFieldAttrs, owner: InventoryBaseRecord) {
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
      link: observable,
      setAttributesDirty: action,
      setAttributes: action,
      setEditing: action,
      setInvalidInput: action,
      isValid: computed,
      hasContent: computed,
    });

    this.setAttributes({
      ...attrs,
      owner,
      type: typeNameFromApi(attrs.type),
      link: attrs.link ?? null,
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
    // An open editor holds mid-edit values (name, type, and for Link fields the
    // link payload) in local editor state that the model does not have yet, until
    // Apply/Update commits them. Saving the record now would silently discard the
    // uncommitted edit, so Save stays greyed for ANY open field editor until the
    // edit is committed or abandoned (RSDEV-1201).
    if (this.editing && !this.deleteFieldRequest) {
      return IsInvalid(
        this.initial
          ? i18n.t("inventory:fields.extraFields.validation.newFieldBeingAdded")
          : i18n.t("inventory:fields.extraFields.validation.fieldBeingEdited", { name: this.name }),
      );
    }
    if (!this.name) return IsInvalid(i18n.t("inventory:fields.extraFields.validation.emptyName"));
    if (this.name.length > 255) return IsInvalid(i18n.t("inventory:fields.extraFields.validation.nameTooLong"));
    if (this.type === "Text") {
      if (typeof this.content !== "string")
        return IsInvalid(i18n.t("inventory:fields.extraFields.validation.textContentMustBeString"));
      if (this.content.length > 250)
        return IsInvalid(i18n.t("inventory:fields.extraFields.maxCharacters", { max: 250 }));
      return IsValid();
    }
    if (this.type === "Number") {
      if (this.invalidInput) return IsInvalid(i18n.t("inventory:fields.extraFields.validNumber"));
      return IsValid();
    }
    if (this.type === "Link") {
      if (this.invalidInput)
        return IsInvalid(i18n.t("inventory:fields.extraFields.validation.linkTargetRequired", { name: this.name }));
      // an absent payload is the legitimate "No link set" empty state (the
      // backend allows payload-less Link extra-fields), so it must not block
      // record-level Save; only a half-set payload is invalid
      if (!this.link) return IsValid();
      if (!this.link.relationType || !this.link.targetGlobalId) {
        return IsInvalid(i18n.t("inventory:fields.extraFields.validation.linkRelationAndTargetRequired"));
      }
      return IsValid();
    }
    return IsInvalid(i18n.t("inventory:fields.extraFields.validation.invalidFieldType"));
  }

  get hasContent(): boolean {
    if (this.type === "Link") {
      return Boolean(this.link?.targetGlobalId);
    }
    return Boolean(this.content);
  }
}

function typeNameFromApi(t: "text" | "number" | "link"): ExtraFieldType {
  if (t === "text") return "Text";
  if (t === "number") return "Number";
  return "Link";
}
