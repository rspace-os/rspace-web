import { pick, uniq } from "es-toolkit";
import { action, computed, makeObservable, observable } from "mobx";
import * as v from "valibot";
import {
  type FieldDisplayRule,
  FieldDisplayRuleSchema,
  operatorRequiresValues,
  sameDisplayRule,
  serializeDisplayRule,
} from "@/modules/common/field-rules/displayRule";
import { isBlankOption, withoutOptions } from "@/modules/common/field-rules/ruleOptions";
import i18n from "@/modules/common/i18n";
import { formatList } from "@/modules/common/i18n/listFormat";
import { allAreValid, IsInvalid, IsValid, type ValidationResult } from "../../components/ValidatingSubmitButton";
import type { GalleryFile } from "../../eln/gallery/useGalleryListing";
import { UnparsableString } from "../../util/error";
import { isBlankToServer } from "../../util/StringUtils";
import type { URL as URLType } from "../../util/types";
import type { Attachment } from "../definitions/Attachment";
import type { GlobalId, Id } from "../definitions/BaseRecord";
import type { Field, FieldLink, FieldType, Option, OptionValue } from "../definitions/Field";
import type { Sample } from "../definitions/Sample";
import { truncateIsoTimestamp } from "../definitions/Units";
import { type AttachmentJson, newAttachment, newExistingAttachment, newGalleryAttachment } from "./AttachmentModel";
import { apiStringToFieldType, type FieldType as FieldTypeSymbol } from "./FieldTypes";
import { sourceKindOfType } from "./fieldVisibility";
import type InventoryBaseRecord from "./InventoryBaseRecord";

const formatOption = (option: OptionValue): Option => ({
  value: option,
  label: option,
  editing: false,
});

export function hasOptions(type: FieldType): boolean {
  return type === "radio" || type === "choice";
}

export type FieldModelAttrs = {
  id?: number;
  globalId?: GlobalId;
  name?: string;
  type: FieldType;
  content?: string | null;
  selectedOptions: Array<OptionValue> | null;
  definition?: {
    options: Array<OptionValue>;
  } | null;
  initial?: boolean;
  editing?: boolean;
  columnIndex: number | null;
  attachment: AttachmentJson | null;
  mandatory: boolean;
  allowedRelationTypes?: Array<string> | null;
  link?: FieldLink | null;
  fieldKey?: string | null;
  displayRule?: FieldDisplayRule | null;
};

function parseRule(json: unknown): FieldDisplayRule | null {
  if (json === null || json === undefined) return null;
  const result = v.safeParse(FieldDisplayRuleSchema, json);
  if (result.success) return result.output;
  // a rule that cannot be read must not hide data, so the field is always shown
  console.warn("Ignoring an unreadable display rule", json);
  return null;
}

/** Clears the display rules that read `field`, before it is removed. */
export function clearRulesReading(field: Field | undefined): void {
  if (!(field instanceof FieldModel)) return;
  for (const dependent of field.dependentFields) dependent.setDisplayRule(null);
}

/** Why a template was not sent: a display condition is still unfinished. */
export class DisplayConditionIncomplete extends Error {}

/**
 * Throws a DisplayConditionIncomplete with the reason the first of `fields`
 * gives for not saving its display condition, so a template is never sent
 * while a condition is unfinished.
 */
export function assertDisplayConditionsComplete(fields: ReadonlyArray<Field>): void {
  allAreValid(fields.map((field) => field.validateDisplayCondition()))
    .mapError(([first]) => new DisplayConditionIncomplete(first?.message))
    .elseThrow();
}

export default class FieldModel implements Field {
  // @ts-expect-error Initialised by setAttributes
  id: Id;
  // @ts-expect-error Initialised by setAttributes
  globalId: GlobalId | null;
  // @ts-expect-error Initialised by setAttributes
  type: FieldType;
  // @ts-expect-error Initialised by setAttributes
  name: string;
  // @ts-expect-error Initialised by setAttributes
  content: string | number | Date;
  // @ts-expect-error Initialised by setAttributes
  selectedOptions: Array<string> | null;
  definition: object = {};
  initial: boolean = false;
  editing: boolean = false;
  deleteFieldRequest: boolean = false;
  deleteFieldOnSampleUpdate: boolean = false;
  options: Array<Option> = [];
  // @ts-expect-error Initialised by setAttributes
  owner: Sample;
  // @ts-expect-error Initialised by setAttributes
  columnIndex: number;
  // @ts-expect-error Initialised by setAttributes
  attachment: Attachment | null;
  originalAttachment: Attachment | null = null;
  // @ts-expect-error Initialised by setAttributes
  mandatory: boolean;
  error: boolean;
  linkEditInProgress: boolean = false;
  /** Set by the template editor while this field's display condition is unfinished or invalid. */
  displayConditionIncomplete: boolean = false;
  /** Link template fields: permitted DataCite relation types; empty = all allowed. */
  allowedRelationTypes: Array<string> = [];
  /** Link fields: the single link value, or null when unset. */
  link: FieldLink | null = null;
  fieldKey: string | null = null;
  displayRule: FieldDisplayRule | null = null;
  /** The rule the field had when loaded, so a save sends the rule only when this edit changed it. */
  private readonly loadedRule: FieldDisplayRule | null;

  constructor(_params: FieldModelAttrs, owner: InventoryBaseRecord) {
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
      linkEditInProgress: observable,
      displayConditionIncomplete: observable,
      allowedRelationTypes: observable,
      link: observable,
      fieldKey: observable,
      displayRule: observable.ref,
      setAttributesDirty: action,
      setDisplayRule: action,
      renameOption: action,
      removeOption: action,
      changeType: action,
      setAttributes: action,
      setEditing: action,
      setError: action,
      setLinkEditInProgress: action,
      setDisplayConditionIncomplete: action,
      fieldType: computed,
      optionsAreUnique: computed,
      hasContent: computed,
    });

    let params = pick(_params, [
      "id",
      "globalId",
      "attachment",
      "content",
      "selectedOptions",
      "type",
      "name",
      "initial",
      "mandatory",
      "allowedRelationTypes",
      "link",
    ]) as unknown as object & {
      content: string | number | Date;
      type: FieldType;
      initial: boolean;
      attachment?: AttachmentJson;
      owner: InventoryBaseRecord;
      options: Array<OptionValue> | Array<Option>;
      allowedRelationTypes?: Array<string> | null;
      link?: FieldLink | null;
    };

    params.initial = params.initial ?? false;
    params.allowedRelationTypes = params.allowedRelationTypes ?? [];
    params.link = params.link ?? null;

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
      } else if (typeof params.content === "string" && params.content.length === 5) {
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
        throw new UnparsableString(params.content as string, "Invalid time format");
      }
    }

    if (hasOptions(params.type)) {
      params.options = (params.options as Array<OptionValue>).map(formatOption);
    }

    if (params.type === "number") {
      if (Number.isNaN(params.content as number)) params.content = "";
      if (params.content !== "") {
        params.content = parseFloat(params.content as string);
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
          // @ts-expect-error owner is set by the setAttributes call above
          this.owner.id ? this.permalinkURL : "",
          (a: Attachment) => {
            if (!this.originalAttachment) this.originalAttachment = a;
            this.setAttributesDirty({});
          },
        ),
      });
    this.fieldKey = _params.fieldKey ?? null;
    // attrs built from API JSON carry the rule unparsed whatever their type says, so it is parsed here
    this.displayRule = parseRule(_params.displayRule as unknown);
    this.loadedRule = this.displayRule;
    this.error = false;
  }

  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
  setAttributesDirty(attrs: {}) {
    this.owner.setAttributesDirty({});
    this.setAttributes(attrs);
  }

  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
  setAttributes(attrs: {}) {
    Object.assign(this, attrs);
  }

  setEditing(editing: boolean) {
    this.editing = editing;
  }

  setError(error: boolean) {
    this.error = error;
  }

  setLinkEditInProgress(value: boolean) {
    this.linkEditInProgress = value;
  }

  setDisplayConditionIncomplete(value: boolean) {
    this.displayConditionIncomplete = value;
  }

  validateDisplayCondition(): ValidationResult {
    if (!this.displayConditionIncomplete) return IsValid();
    return IsInvalid(
      i18n.t("inventory:template.displayRules.conditionIncomplete", {
        field: this.name || i18n.t("common:fieldEditor.card.unnamed"),
      }),
    );
  }

  get fieldType(): FieldTypeSymbol {
    return apiStringToFieldType(this.type);
  }

  get optionsAreUnique(): boolean {
    const optionValues = this.options.map((o) => o.value);
    return new Set(optionValues).size === optionValues.length;
  }

  /** Siblings in the same template or record that are still in use. */
  private get liveSiblings(): Array<FieldModel> {
    const fields = (this.owner.fields ?? []) as Array<FieldModel>;
    return fields.filter((field) => field !== this && !field.deleteFieldRequest);
  }

  /** Fields whose display rule reads this field. */
  get dependentFields(): Array<FieldModel> {
    const key = this.fieldKey;
    if (key === null) return [];
    return this.liveSiblings.filter((field) => field.displayRule?.sourceFieldKey === key);
  }

  /** Fields whose display rule names this option of this field; text and number values are not options. */
  fieldsUsingOption(option: string): Array<FieldModel> {
    return this.dependentFields.filter(
      ({ displayRule }) =>
        displayRule && operatorRequiresValues(displayRule.operator) && displayRule.values.includes(option),
    );
  }

  setDisplayRule(displayRule: FieldDisplayRule | null): void {
    this.setAttributesDirty({ displayRule });
  }

  /**
   * The default selection once option `previous` has been renamed to
   * `replacement`, or removed when `replacement` is null. Nothing changes while
   * another option still offers `previous`; a blank replacement is dropped.
   */
  private selectionAfter(
    options: ReadonlyArray<Option>,
    previous: string,
    replacement: string | null,
  ): Array<string> | null {
    const selected = this.selectedOptions;
    if (!selected?.includes(previous) || options.some((option) => option.value === previous)) return selected;
    if (replacement === null || isBlankOption(replacement)) return selected.filter((value) => value !== previous);
    // the default is held by value, so typing a value another option already has moves it to that option
    return uniq(selected.map((value) => (value === previous ? replacement : value)));
  }

  /** Renames an option still being typed, carrying a default that selected it. */
  renameOption(index: number, value: string): void {
    const previous = this.options[index]?.value;
    if (previous === undefined) return;
    const options = this.options.toSpliced(index, 1, { value, label: value, editing: true });
    this.setAttributesDirty({ options, selectedOptions: this.selectionAfter(options, previous, value) });
  }

  /**
   * Removes an option, and with it the option from the default selection and
   * from any display rule that names it, in the same change; a rule left
   * without options is cleared.
   */
  removeOption(index: number): void {
    const removed = this.options[index]?.value;
    if (removed === undefined) return;
    const options = this.options.toSpliced(index, 1);
    if (!options.some((option) => option.value === removed)) {
      for (const dependent of this.fieldsUsingOption(removed)) {
        if (dependent.displayRule) dependent.setDisplayRule(withoutOptions(dependent.displayRule, [removed]));
      }
    }
    this.setAttributesDirty({ options, selectedOptions: this.selectionAfter(options, removed, null) });
  }

  /**
   * Changes the type of a field that has not been saved, resetting its value.
   * If a display rule would read the new type differently (another source
   * kind), the rules that read this field are cleared in the same change.
   */
  changeType(type: FieldType): void {
    if (sourceKindOfType(type) !== sourceKindOfType(this.type)) {
      for (const dependent of this.dependentFields) dependent.setDisplayRule(null);
    }
    this.setAttributesDirty({
      type,
      content: hasOptions(type) ? [] : type === "number" ? 0 : "",
    });
  }

  validate(): ValidationResult {
    const condition = this.validateDisplayCondition();
    if (!condition.isOk) return condition;
    // a hidden field keeps its value, and the server still checks a changed value's format (RPD-339)
    if (this.owner.isFieldHidden?.(this)) {
      if (!this.error) return IsValid();
      return IsInvalid(i18n.t("inventory:fields.hiddenFieldInvalid", { field: this.name }));
    }
    if (this.name === "") return IsInvalid("Names for custom fields cannot be empty.");
    if (this.name.length > 50) return IsInvalid("Names for custom fields cannot exceed 50 characters.");
    if (this.type === "radio") {
      if (this.options.length === 0) return IsInvalid("Custom radio fields must have at least one option.");
      if (this.options.some((o) => o.value === "")) return IsInvalid("Options of custom radio fields cannot be empty.");
      if (!this.optionsAreUnique) return IsInvalid("Options of custom radio fields must all be distinct.");
    }
    if (this.type === "choice") {
      if (this.options.length === 0) return IsInvalid("Custom choice fields must have at least one option.");
      if (this.options.some((o) => o.value === ""))
        return IsInvalid("Options of custom choice fields cannot be empty.");
      if (!this.optionsAreUnique) return IsInvalid("Options of custom choice fields must all be distinct.");
    }
    const required = this.owner.requiresValue ? this.owner.requiresValue(this) : this.mandatory;
    if (required && this.owner.enforceMandatoryFields && !this.hasContent)
      return IsInvalid(`The mandatory custom field "${this.name}" must have a valid value.`);
    if (this.type === "link" && this.linkEditInProgress)
      return IsInvalid(`Apply or discard the link changes in "${this.name}" before saving.`);
    if (this.error) return IsInvalid(`There is an error with the custom field, "${this.name}".`);
    return IsValid();
  }

  /**
   * Whether the field holds a value a mandatory field accepts, as the server's
   * `isValidValueForMandatoryField` reads it: text that is not blank
   * (`InventoryEntityField`), and for a link a Filestore target or a Global ID
   * that is not blank, with a relation type (`InventoryLinkField`).
   */
  get hasContent(): boolean {
    if (hasOptions(this.type)) return Boolean(this.selectedOptions?.length);
    // a number stored blank parses to NaN, and blank is no value to the server
    if (this.type === "number") return this.content !== "" && !Number.isNaN(this.content);
    if (this.type === "link") {
      const link = this.link;
      if (!link) return false;
      const hasTarget = Boolean(link.filestoreTarget) || !isBlankToServer(link.targetGlobalId ?? "");
      return hasTarget && !isBlankToServer(link.relationType ?? "");
    }
    if (typeof this.content === "string") return !isBlankToServer(this.content);
    return Boolean(this.content);
  }

  get renderContentAsString(): string {
    if (hasOptions(this.type)) return formatList(this.selectedOptions ?? [], i18n.resolvedLanguage ?? i18n.language);
    return this.content.toString() ?? "";
  }

  setAttachment(file: File | GalleryFile): void {
    if (this.attachment) this.attachment.remove();

    this.setAttributesDirty({
      attachment:
        file instanceof File
          ? newAttachment(file, this.owner.id ? this.permalinkURL : "", (attachment) => {
              if (!this.originalAttachment) this.originalAttachment = attachment;
              this.setAttributesDirty({});
            })
          : newGalleryAttachment(file, (attachment) => {
              if (!this.originalAttachment) this.originalAttachment = attachment;
              this.setAttributesDirty({});
            }),
    });
  }

  get permalinkURL(): URLType | null {
    return this.owner.permalinkURL;
  }

  /** The field for saving its own record: a new record sends every rule, a saved one only the rules this edit changed. */
  get paramsForBackend(): object {
    return this.backendParams(this.owner.id ? "update" : "create");
  }

  /** The field for a new record built from this one's, such as a template made from a sample; it sends every rule. */
  get paramsForNewRecord(): object {
    return this.backendParams("create");
  }

  private backendParams(request: "create" | "update"): object {
    const ret: typeof this & { newFieldRequest?: boolean } = { ...this };
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
        // a date the picker could not read is held as an invalid Date, which is never sent
        if (this.content instanceof Date) ret.content = truncateIsoTimestamp(this.content, "date").orElse("");
        ret.content = ret.content ?? "";
        break;
    }
    const keys = [
      "id",
      "name",
      "type",
      "definition",
      "newFieldRequest",
      "deleteFieldRequest",
      "deleteFieldOnSampleUpdate",
      "columnIndex",
      "mandatory",
    ];
    if (ret.type === "link") {
      // link fields carry their permitted relation-type whitelist and the
      // chosen link value; their data column is unused, so no content is sent
      // (the backend's mandatory check rejects empty content)
      keys.push("allowedRelationTypes", "link");
    } else {
      keys.push(hasOptions(ret.type) ? "selectedOptions" : "content");
    }
    const params: Record<string, unknown> = pick(ret, keys as Array<keyof typeof ret>);
    if (this.fieldKey !== null) params.fieldKey = this.fieldKey;
    // a field being deleted sends no rule, so the request never depends on rules of deleted fields
    if (this.deleteFieldRequest) return params;
    /*
     * The API reads an absent rule as unchanged. Updating a record, a saved
     * field sends its rule only when this edit changed it, as null when
     * cleared, so a tab opened before someone else changed the rule does not
     * put the old rule back.
     */
    if (request === "update" && this.id && sameDisplayRule(this.displayRule, this.loadedRule)) return params;
    if (this.displayRule !== null) params.displayRule = JSON.parse(serializeDisplayRule(this.displayRule));
    else if (this.loadedRule !== null) params.displayRule = null;
    return params;
  }
}
