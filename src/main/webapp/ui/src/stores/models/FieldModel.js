// @flow

import { action, observable, computed, makeObservable } from "mobx";
import * as ArrayUtils from "../../util/ArrayUtils";
import {
  type AttachmentJson,
  newAttachment,
  newGalleryAttachment,
  newExistingAttachment,
} from "./AttachmentModel";
import {
  apiStringToFieldType,
  type FieldType as FieldTypeSymbol,
} from "./FieldTypes";
import Result from "./Result";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import { type URL as URLType } from "../../util/types";
import { type Sample } from "../definitions/Sample";
import { type Attachment } from "../definitions/Attachment";
import { UnparsableString } from "../../util/error";
import {
  type Field,
  type FieldType,
  type OptionValue,
  type Option,
} from "../definitions/Field";
import { pick } from "../../util/unsafeUtils";
import {
  IsInvalid,
  IsValid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";
import { type GalleryFile } from "../../eln/gallery/useGalleryListing";

const formatOption = (option: OptionValue): Option => ({
  value: option,
  label: option,
  editing: false,
});

export function hasOptions(type: FieldType): boolean {
  return type === "radio" || type === "choice";
}

export type FieldModelAttrs = {|
  id?: number,
  globalId?: GlobalId,
  name?: string,
  type: FieldType,
  content?: ?string,
  selectedOptions: ?Array<OptionValue>,
  definition?: ?{
    options: Array<OptionValue>,
  },
  initial?: boolean,
  editing?: boolean,
  columnIndex: ?number,
  attachment: ?AttachmentJson,
  mandatory: boolean,
|};

export default class FieldModel implements Field {
  id: Id;
  globalId: ?GlobalId;
  type: FieldType;
  name: string;
  content: string | number;
  selectedOptions: ?Array<string>;
  definition: {} = {};
  initial: boolean = false;
  editing: boolean = false;
  deleteFieldRequest: boolean = false;
  deleteFieldOnSampleUpdate: boolean = false;
  options: Array<Option> = [];
  owner: Sample;
  columnIndex: number;
  attachment: ?Attachment;
  originalAttachment: ?Attachment = null;
  mandatory: boolean;
  error: boolean;

  constructor(_params: FieldModelAttrs, owner: Result) {
    makeObservable(this, {
      id: observable,
      globalId: observable,
      type: observable,
      name: observable,
      content: observable,
      definition: observable,
      initial: observable,
      editing: observable,
      deleteFieldRequest: observable,
      deleteFieldOnSampleUpdate: observable,
      options: observable,
      owner: observable,
      columnIndex: observable,
      selectedOptions: observable,
      attachment: observable,
      mandatory: observable,
      error: observable,
      setAttributesDirty: action,
      setAttributes: action,
      setEditing: action,
      setError: action,
      fieldType: computed,
      optionsAreUnique: computed,
      hasContent: computed,
    });

    let params = pick(
      "id",
      "globalId",
      "attachment",
      "content",
      "selectedOptions",
      "files",
      "type",
      "name",
      "initial",
      "mandatory"
    )(_params);

    params.initial = params.initial ?? false;

    if (_params.definition) {
      params = {
        ...params,
        ..._params.definition,
      };
    }

    params.content = params.content ?? "";

    if (params.type === "time") {
      if (params.content === "") {
        // do nothing; empty string is valid value when not set
      } else if (
        typeof params.content === "string" &&
        params.content.length === 5
      ) {
        // as "hh:mm"
        const date = new Date();
        const [h, m] = params.content.split(":").map((n) => Number(n));
        date.setHours(h);
        date.setMinutes(m);
        params.content = date;
        /*
         * Note that whilst this does result in the content being set to a full
         * date object, we only use the time component throughout the UI.
         */
      } else {
        throw new UnparsableString(params.content, "Invalid time format");
      }
    }

    if (hasOptions(params.type)) {
      params.options = params.options.map(formatOption);
    }

    if (params.type === "number") {
      if (isNaN(params.content)) params.content = "";
      if (params.content !== "") {
        params.content = parseFloat(params.content);
      }
    }

    params.owner = owner;
    const attachment = params.attachment;
    delete params.attachment;
    this.setAttributes(params);

    // have to do this setAttributes after because this.permalinkURL needs to be set
    if (attachment)
      this.setAttributes({
        attachment: newExistingAttachment(
          attachment,
          this.owner.id ? this.permalinkURL : "",
          (a) => {
            if (!this.originalAttachment) this.originalAttachment = a;
            this.setAttributesDirty({});
          }
        ),
      });
    this.error = false;
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

  setError(error: boolean) {
    this.error = error;
  }

  get fieldType(): FieldTypeSymbol {
    return apiStringToFieldType(this.type);
  }

  get optionsAreUnique(): boolean {
    return ArrayUtils.allAreUnique(this.options.map((o) => o.value));
  }

  validate(): ValidationResult {
    if (this.name === "")
      return IsInvalid("Names for custom fields cannot be empty.");
    if (this.name.length > 50)
      return IsInvalid("Names for custom fields cannot exceed 50 characters.");
    if (this.type === "radio") {
      if (this.options.length === 0)
        return IsInvalid("Custom radio fields must have at least one option.");
      if (this.options.some((o) => o.value === ""))
        return IsInvalid("Options of custom radio fields cannot be empty.");
      if (!this.optionsAreUnique)
        return IsInvalid(
          "Options of custom radio fields must all be distinct."
        );
    }
    if (this.type === "choice") {
      if (this.options.length === 0)
        return IsInvalid("Custom choice fields must have at least one option.");
      if (this.options.some((o) => o.value === ""))
        return IsInvalid("Options of custom choice fields cannot be empty.");
      if (!this.optionsAreUnique)
        return IsInvalid(
          "Options of custom choice fields must all be distinct."
        );
    }
    if (this.mandatory && this.owner.enforceMandatoryFields && !this.hasContent)
      return IsInvalid(
        `The mandatory custom field "${this.name}" must have a valid value.`
      );
    if (this.error)
      return IsInvalid(
        `There is an error with the custom field, "${this.name}".`
      );
    return IsValid();
  }

  get hasContent(): boolean {
    if (hasOptions(this.type)) return Boolean(this.selectedOptions?.length);
    if (this.type === "number") return this.content !== "";
    return Boolean(this.content);
  }

  get renderContentAsString(): string {
    if (hasOptions(this.type)) return (this.selectedOptions ?? []).join(", ");
    return this.content.toString() ?? "";
  }

  setAttachment(file: File | GalleryFile): void {
    if (this.attachment) this.attachment.remove();

    this.setAttributesDirty({
      attachment:
        file instanceof File
          ? newAttachment(
              file,
              this.owner.id ? this.permalinkURL : "",
              (attachment) => {
                if (!this.originalAttachment)
                  this.originalAttachment = attachment;
                this.setAttributesDirty({});
              }
            )
          : newGalleryAttachment(file, (attachment) => {
              if (!this.originalAttachment)
                this.originalAttachment = attachment;
              this.setAttributesDirty({});
            }),
    });
  }

  get permalinkURL(): ?URLType {
    return this.owner.permalinkURL;
  }

  get paramsForBackend(): { ... } {
    const ret: any = { ...this };
    if (!ret.id) ret.newFieldRequest = true;

    switch (ret.type) {
      case "radio":
      case "choice":
        if (typeof this.name === "string")
          ret.definition = {
            options: this.options.map((o) => o.value),
          };
        break;
      case "time":
        if (this.content instanceof Date)
          ret.content = this.content.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
            hour12: false,
          });
        ret.content = ret.content ?? "";
        break;
      case "date":
        ret.content = ret.content ?? "";
        break;
    }
    return pick(
      "id",
      "name",
      hasOptions(ret.type) ? "selectedOptions" : "content",
      "type",
      "definition",
      "newFieldRequest",
      "deleteFieldRequest",
      "deleteFieldOnSampleUpdate",
      "columnIndex",
      "mandatory"
    )(ret);
  }
}
