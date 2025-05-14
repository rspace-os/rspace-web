import ApiService from "../../common/InvApiService";
import { match, toTitleCase, filterObject } from "../../util/Util";
import * as ArrayUtils from "../../util/ArrayUtils";
import { filenameExceptExtension } from "../../util/files";
import { showToastWhilstPending } from "../../util/alerts";
import RsSet from "../../util/set";
import StateMachine from "../../util/stateMachine";
import getRootStore from "../stores/RootStore";
import MemoisedFactory from "./Factory/MemoisedFactory";
import { mkAlert } from "../contexts/Alert";
import {
  FieldTypes,
  fieldTypeToApiString,
  apiStringToFieldType,
  compatibleFieldTypes,
  type FieldType,
} from "./FieldTypes";
import { type ImportRecordType } from "../stores/ImportStore";
import TemplateModel from "./TemplateModel";
import {
  computed,
  action,
  observable,
  makeObservable,
  runInAction,
} from "mobx";
import { parseString } from "../../util/parsers";
import { type Field as TemplateField } from "../definitions/Field";
import { pick } from "../../util/unsafeUtils";
import Result from "../../util/result";
import { getErrorMessage } from "../../util/error";
import * as Parsers from "../../util/parsers";

export const Fields: { [fieldName: string]: symbol } = {
  name: Symbol.for("NAME"),
  description: Symbol.for("DESCRIPTION"),
  expiry_date: Symbol.for("EXPIRY DATE"),
  quantity: Symbol.for("QUANTITY"),
  source: Symbol.for("SOURCE"),
  tags: Symbol.for("TAGS"),
  import_identifier: Symbol.for("IMPORT ID"),
  parent_container_global_id: Symbol.for("PARENT CONTAINER GLOBAL ID"),
  parent_container_import_id: Symbol.for("PARENT CONTAINER IMPORT ID"),
  parent_sample_global_id: Symbol.for("PARENT SAMPLE GLOBAL ID"),
  parent_sample_import_id: Symbol.for("PARENT SAMPLE IMPORT ID"),
  custom: Symbol.for("CUSTOM"),
  none: Symbol.for("IGNORE"),
};

type Field = (typeof Fields)[keyof typeof Fields];

// returns null for custom and none
export const getTypeOfField: (field: Field) => FieldType | null = match([
  [(f) => f === Fields.name, FieldTypes.plain_text],
  [(f) => f === Fields.description, FieldTypes.formatted_text],
  [(f) => f === Fields.expiry_date, FieldTypes.date],
  [(f) => f === Fields.tags, FieldTypes.plain_text],
  [(f) => f === Fields.source, FieldTypes.radio],
  [(f) => f === Fields.quantity, FieldTypes.plain_text],
  [(f) => f === Fields.import_identifier, FieldTypes.plain_text],
  [(f) => f === Fields.parent_container_global_id, FieldTypes.plain_text],
  [(f) => f === Fields.parent_container_import_id, FieldTypes.plain_text],
  [(f) => f === Fields.parent_sample_global_id, FieldTypes.plain_text],
  [(f) => f === Fields.parent_sample_import_id, FieldTypes.plain_text],
  [() => true, null],
]);

const transitionMapping: { [state: string]: Set<string> } = {
  initial: new Set(["parsing", "submitting"]),
  //   clean state, the page has just loaded (or "clear file" button has been pressed)
  parsing: new Set(["parsed", "parsingFailed"]),
  //   selected file is sent to server for parsing; awaiting response
  parsingFailed: new Set(["initial", "parsing"]),
  //   file could not be parsed, user must select a new file
  parsed: new Set(["initial", "nameSelected", "nameRequired", "parsing"]),
  //   file parsed successfully, user must select name field, or can choose a different file
  nameRequired: new Set(["initial", "nameRequiredAndSelected", "parsing"]),
  //   if the user tries to submit without a name mapping then, and only then, do we show an error
  nameRequiredAndSelected: new Set([
    "initial",
    "nameRequired",
    "submitting",
    "parsing",
  ]),
  /*
   *   all good to submit, unselecting goes back to "required" error state
   * transition to "initial" may occur when already parsed csv file is cleared
   * transition to "nameSelected" may occur when import of multiple groups fails
   */
  nameSelected: new Set([
    "initial",
    "submitting",
    "parsed",
    "parsing",
    "nameSelected",
  ]),
  //   all good to submit, unselecting name goes back to parsed where error is not shown
  submitting: new Set(["nameSelected", "successfullySubmitted"]),
  //   server processes the submission
  successfullySubmitted: new Set(["initial", "parsing"]),
  //   and the success state where the user can start over with a new file
};
export type State = keyof typeof transitionMapping;

const DEFAULT_NEW_TEMPLATE_NAME = "New template";

const invalidFieldNames = [
  "",
  "Description",
  "Expiry Date",
  "Name",
  "Preview Image",
  "Source",
  "Tags",
  // 'Quantity' name is allowed for now, field uses 'Total Quantity'
];

type ColumnName = string;

export class ColumnFieldMap {
  selected: boolean;
  columnName: ColumnName;
  field: Field;
  fieldName: string;
  fieldType: FieldType;
  chosenFieldType: FieldType;
  quantityUnitId: number | null;
  options: Array<string> | null;
  fieldChangeCallback: (oldField: Field, newField: Field) => void;
  isNameUnique: (columnFieldMap: ColumnFieldMap) => boolean;
  recordType: ImportRecordType;
  columnsWithoutBlankValue: Array<ColumnName>;

  constructor({
    selected,
    columnName,
    field,
    fieldName,
    fieldType,
    quantityUnitId,
    options,
    fieldChangeCallback,
    isNameUnique,
    recordType,
    columnsWithoutBlankValue,
  }: {
    selected: boolean;
    columnName: ColumnName;
    field: Field;
    fieldName: string;
    fieldType: FieldType;
    quantityUnitId: number | null;
    options: Array<string> | null;
    fieldChangeCallback: (oldField: Field, newField: Field) => void;
    isNameUnique: (columnFieldMap: ColumnFieldMap) => boolean;
    recordType: ImportRecordType;
    columnsWithoutBlankValue: Array<ColumnName>;
  }) {
    makeObservable(this, {
      selected: observable,
      columnName: observable,
      field: observable,
      fieldName: observable,
      fieldType: observable,
      chosenFieldType: observable,
      quantityUnitId: observable,
      options: observable,
      recordType: observable,
      toggleSelected: action,
      setField: action,
      updateField: action,
      setChosenFieldType: action,
      setFieldName: action,
      allValidTypes: computed,
      validFieldName: computed,
      valid: computed,
      fieldsByRecordType: computed,
    });
    this.selected = selected;
    this.recordType = recordType;
    this.columnName = columnName;
    this.field = field;
    this.fieldName = fieldName;
    this.fieldType = fieldType;
    this.chosenFieldType = fieldType;
    this.options = options;
    this.fieldChangeCallback = fieldChangeCallback;
    this.isNameUnique = isNameUnique;
    this.quantityUnitId = quantityUnitId;
    this.columnsWithoutBlankValue = columnsWithoutBlankValue;
  }

  toggleSelected(value: boolean = !this.selected) {
    this.selected = value;
  }

  setField(newField: Field) {
    this.field = newField;
  }

  updateField(newField: Field) {
    const oldField = this.field;
    this.setField(newField);
    this.fieldChangeCallback(oldField, newField);
  }

  setChosenFieldType(fieldType: FieldType) {
    this.chosenFieldType = fieldType;
  }

  setFieldName(value: string) {
    this.fieldName = value;
  }

  get allValidTypes(): Array<FieldType> {
    return compatibleFieldTypes(this.fieldType);
  }

  // returns false for custom; caller should handle
  typeIsCompatibleWithField(field: Field): boolean {
    return (
      this.allValidTypes.includes(getTypeOfField(field)) ||
      field === Fields.none // make none selectable (for containers)
    );
  }

  isCompatibleWithField(field: Field): boolean {
    if (field === Fields.source) {
      return (
        this.fieldType === FieldTypes.radio &&
        Boolean(this.options) &&
        new RsSet(this.options).isSubsetOf(
          new RsSet(["LAB_CREATED", "VENDOR_SUPPLIED", "OTHER"])
        )
      );
    }
    if (field === Fields.name) {
      return (
        this.columnsWithoutBlankValue.includes(this.columnName) &&
        this.typeIsCompatibleWithField(field)
      );
    }
    return this.typeIsCompatibleWithField(field);
  }

  get validFieldName(): boolean {
    return (
      // apply no restrictions for non-samples fields name validity
      this.recordType !== "SAMPLES" ||
      (!invalidFieldNames.includes(this.fieldName.trim()) &&
        this.isNameUnique(this))
    );
  }

  get valid(): boolean {
    return this.field !== Fields.custom || this.validFieldName;
  }

  get fieldsByRecordType(): { [string]: symbol } {
    const exclusions = {
      SAMPLES: new Set([
        "parent_sample_global_id",
        "parent_sample_import_id",
        "none",
      ]),
      CONTAINERS: new Set([
        "expiry_date",
        "quantity",
        "source",
        "parent_sample_global_id",
        "parent_sample_import_id",
        "custom",
      ]),
      SUBSAMPLES: new Set([
        "expiry_date",
        "source",
        "import_identifier",
        "custom",
      ]),
    };
    return filterObject((key) => !exclusions[this.recordType].has(key), Fields);
  }
}

export default class Import {
  /*
   * The CSV files to import, per recordType.
   * Can be POSTed indivudually or together.
   */
  containersFile: File | null = null;
  samplesFile: File | null = null;
  subSamplesFile: File | null = null;

  /*
   * When parsing the CSV file, the server may suggest a different field name
   * from that of the CSV's column name (ie when column name would match a default template field name).
   */
  fieldNameForColumnName: { [string]: string };

  /*
   * Determines if we are creating a new template or importing using an
   * existing one.
   */
  createNewTemplate: boolean;

  /*
   * The name chosen by the user for their new template. Only applicable when
   * `createNewTemplate` is true, otherwise unused.
   */
  templateName: string;

  /*
   * When creating a new template, the server will respond with the definition
   * of a suggested template. The user has the opportunity to then modify this
   * definition in whichever way the UI exposes this object.
   */
  templateInfo: TemplateModel | null;

  /*
   * When importing using an existing template, this is the template chosen by
   * the user. Only the `id` is passed to the server and so no modifications
   * should be permitted by the UI.
   */
  template: TemplateModel | null;

  /*
   * The column-to-field mappings, per recordType.
   * Can be POSTed indivudually or together.
   */
  containersMappings: Array<ColumnFieldMap> = [];
  samplesMappings: Array<ColumnFieldMap> = [];
  subSamplesMappings: Array<ColumnFieldMap> = [];

  /*
   * A Finite State Automata (State Machine) that models the steps of the
   * import process, ensuring that the several steps are performed in order.
   */
  state: StateMachine<keyof typeof transitionMapping>;

  /*
   * this is required for handling the import process:
   * custom conversions, POST request, results, UI tabs etc.
   */
  recordType: ImportRecordType;

  constructor(recordType: ImportRecordType) {
    makeObservable(this, {
      containersFile: observable,
      samplesFile: observable,
      subSamplesFile: observable,
      containersMappings: observable,
      samplesMappings: observable,
      subSamplesMappings: observable,
      templateName: observable,
      templateInfo: observable,
      template: observable,
      createNewTemplate: observable,
      state: observable,
      recordType: observable,
      validateMappings: observable,
      resetMappingsByRecordType: action,
      resetAllMappings: action,
      resetAllLoadedFiles: action,
      setCreateNewTemplate: action,
      setCurrentRecordType: action,
      setTemplateName: action,
      setTemplate: action,
      setFile: action,
      clearFile: action,
      setDefaultUnitId: action,
      toggleSelection: action,
      transformTemplateInfoForSubmission: action,
      updateRecordType: action,
      nameFieldIsSelected: computed,
      quantityFieldIsSelected: computed,
      validTemplateName: computed,
      anyParentSamplesFieldIsSelected: computed,
      unconvertedFieldIsSelected: computed,
      parentContainersImportIdUndefined: computed,
      parentSamplesImportIdUndefined: computed,
      containersSubmittable: computed,
      samplesSubmittable: computed,
      subSamplesSubmittable: computed,
      importSubmittable: computed,
      fileErrorMessage: computed,
      fileByRecordType: computed,
      importMatchesExistingTemplate: computed,
      isContainersImport: computed,
      isSamplesImport: computed,
      isSubSamplesImport: computed,
      labelByRecordType: computed,
      mappingsByRecordType: computed,
      someFileSubmitted: computed,
      fileByRecordTypeLoaded: computed,
    });
    this.recordType = recordType;
    this.templateName = DEFAULT_NEW_TEMPLATE_NAME;
    this.templateInfo = null;
    this.state = new StateMachine(transitionMapping, "initial", (x) => x, null);
    this.createNewTemplate = true;
  }

  setCurrentRecordType(value: ImportRecordType) {
    this.recordType = value;
  }

  setTemplateName(value: string) {
    this.templateName = value;
  }

  setCreateNewTemplate(value: boolean) {
    this.createNewTemplate = value;
  }

  setTemplate(template: TemplateModel | null) {
    this.template = template;
    if (template) void template.fetchAdditionalInfo();
  }

  setFile(file: File | null) {
    if (this.isSamplesImport) {
      this.samplesFile = file;
    } else if (this.isContainersImport) {
      this.containersFile = file;
    } else {
      this.subSamplesFile = file;
    }

    if (this.isSamplesImport && this.samplesFile) {
      this.setTemplateName(
        `New template from ${filenameExceptExtension(this.samplesFile.name)}`
      );
    }

    getRootStore().uiStore.setDirty(() => {
      getRootStore().uiStore.unsetDirty();
      return Promise.resolve();
    });

    if (file) void this.parseCsvFile();
  }

  resetMappingsByRecordType() {
    if (this.isSamplesImport) {
      this.samplesMappings = [];
    } else if (this.isContainersImport) {
      this.containersMappings = [];
    } else {
      this.subSamplesMappings = [];
    }
  }

  resetAllMappings() {
    this.containersMappings = [];
    this.samplesMappings = [];
    this.subSamplesMappings = [];
  }

  resetAllLoadedFiles() {
    this.samplesFile = null;
    this.containersFile = null;
    this.subSamplesFile = null;
  }

  clearFile() {
    this.setFile(null);
    this.resetMappingsByRecordType();
    if (this.isSamplesImport) {
      this.setTemplateName(DEFAULT_NEW_TEMPLATE_NAME);
    }
    this.state.transitionTo("initial");
  }

  setDefaultUnitId(id: number = 3) {
    if (this.templateInfo) {
      this.templateInfo.defaultUnitId = id;
    }
  }

  get isContainersImport(): boolean {
    return this.recordType === "CONTAINERS";
  }

  get isSamplesImport(): boolean {
    return this.recordType === "SAMPLES";
  }

  get isSubSamplesImport(): boolean {
    return this.recordType === "SUBSAMPLES";
  }

  get nameFieldIsSelected(): boolean {
    return this.mappingsByRecordType.some(
      (m) => m.field === Fields.name && m.selected
    );
  }

  get quantityFieldIsSelected(): boolean {
    return this.samplesMappings.some(
      (m) => m.field === Fields.quantity && m.selected
    );
  }

  get validTemplateName(): boolean {
    return Boolean(this.templateName) && this.templateName.length <= 255;
  }

  get anyParentSamplesFieldIsSelected(): boolean {
    return this.subSamplesMappings.some(
      (m) =>
        (m.field === Fields.parent_sample_import_id ||
          m.field === Fields.parent_sample_global_id) &&
        m.selected
    );
  }

  get unconvertedFieldIsSelected(): boolean {
    return this.mappingsByRecordType.some(
      (m) => m.field === Fields.none && m.selected
    );
  }

  /**
   * frontend Parent Container check (for all types) and Parent Sample check (for subsamples only)
   * check that containersMappings / samplesMappings exist and have a compatible mapping "Import identifier" selected, otherwise warn user.
   * id values will be cross-checked by the backend
   */

  get parentContainersImportIdUndefined(): boolean {
    return this.mappingsByRecordType.some(
      (m) => m.field === Fields.parent_container_import_id && m.selected
    )
      ? !this.containersMappings.some(
          (m) => m.field === Fields.import_identifier && m.selected
        )
      : false;
  }

  get parentSamplesImportIdUndefined(): boolean {
    return this.subSamplesMappings.some(
      (m) => m.field === Fields.parent_sample_import_id && m.selected
    )
      ? !this.samplesMappings.some(
          (m) => m.field === Fields.import_identifier && m.selected
        )
      : false;
  }

  /**
   * validation is per type (specific mappings needed)
   * but only for the common checks (relevant to all types).
   * type-specific checks should be in each type submittable computed
   */
  validateMappings(mappings: Array<ColumnFieldMap>): boolean {
    const allMappingsAreValid: boolean = mappings.every((m) => m.valid);

    const allFieldNamesAreUnique: boolean = ArrayUtils.allAreUnique(
      mappings.map((m) => m.fieldName)
    );

    const nameFieldIsSelected: boolean = mappings.some(
      (m) =>
        m.field === Fields.name &&
        m.selected &&
        m.columnsWithoutBlankValue.includes(m.columnName)
    );

    const parentContainersImportIdUndefined: boolean = mappings.some(
      (m) => m.field === Fields.parent_container_import_id && m.selected
    )
      ? !this.containersMappings.some(
          (m) => m.field === Fields.import_identifier && m.selected
        )
      : false;

    return (
      allMappingsAreValid &&
      allFieldNamesAreUnique &&
      nameFieldIsSelected &&
      !parentContainersImportIdUndefined
    );
  }

  get fileByRecordType(): File | null {
    if (this.isSamplesImport) {
      return this.samplesFile;
    }
    if (this.isContainersImport) {
      return this.containersFile;
    }
    return this.subSamplesFile;
  }

  get someFileSubmitted(): boolean {
    return (
      Boolean(this.containersFile) ||
      Boolean(this.samplesFile) ||
      Boolean(this.subSamplesFile)
    );
  }

  get fileByRecordTypeLoaded(): boolean {
    return this.isContainersImport
      ? Boolean(this.containersFile)
      : this.isSamplesImport
      ? Boolean(this.samplesFile)
      : Boolean(this.subSamplesFile);
  }

  get labelByRecordType(): string {
    return toTitleCase(this.recordType);
  }

  get mappingsByRecordType(): Array<ColumnFieldMap> {
    return this.isContainersImport
      ? this.containersMappings
      : this.isSamplesImport
      ? this.samplesMappings
      : this.subSamplesMappings;
  }

  get containersSubmittable(): boolean {
    return this.containersFile
      ? this.validateMappings(this.containersMappings)
      : false;
  }

  get samplesSubmittable(): boolean {
    const validNewTemplate: boolean =
      Boolean(this.templateInfo) && this.validTemplateName;

    const validExistingTemplate: boolean =
      Boolean(this.template) &&
      this.validTemplateName &&
      Boolean(this.importMatchesExistingTemplate?.matches);

    const templateIsValid: boolean = this.createNewTemplate
      ? validNewTemplate
      : validExistingTemplate;

    return this.samplesFile
      ? this.validateMappings(this.samplesMappings) && templateIsValid
      : false;
  }

  get subSamplesSubmittable(): boolean {
    return this.subSamplesFile
      ? this.validateMappings(this.subSamplesMappings) &&
          this.anyParentSamplesFieldIsSelected &&
          !this.parentSamplesImportIdUndefined
      : false;
  }

  get importSubmittable(): boolean {
    return (
      this.containersSubmittable ||
      this.samplesSubmittable ||
      this.subSamplesSubmittable
    );
  }

  toggleSelection() {
    const allAlreadySelected = this.mappingsByRecordType.every(
      (m) => m.selected
    );
    this.mappingsByRecordType.map((m) => m.toggleSelected(!allAlreadySelected));
  }

  async parseCsvFile(): Promise<void> {
    const state = this.state;
    state.transitionTo("parsing", () => ({}));

    const fieldChangeHandler = (oldField: Field, newField: Field) => {
      if (oldField === Fields.name && newField !== Fields.name) {
        state.assertCurrentState(
          new RsSet(["nameSelected", "nameRequiredAndSelected"])
        );
        if (state.isCurrentState("nameSelected")) {
          state.transitionTo("parsed");
        } else {
          state.transitionTo("nameRequired");
        }
      }
      if (oldField !== Fields.name && newField === Fields.name) {
        state.assertCurrentState(new RsSet(["parsed", "nameRequired"]));
        if (state.isCurrentState("parsed")) {
          state.transitionTo("nameSelected");
        } else {
          state.transitionTo("nameRequiredAndSelected");
        }
      }
    };

    // matching column names to Fields key names
    const nameToMatch = (name: string) => {
      const nameKey = name
        .substring(name[0] === "_" ? 1 : 0)
        .replace(/ /g, "_")
        .toLowerCase();
      // supporting one alternative, otherwise exact matches
      return nameKey === "import_id" ? nameKey.concat("entifier") : nameKey;
    };

    try {
      const params = new FormData();
      if (!this.someFileSubmitted) {
        throw new Error("No file set");
      } else {
        params.append("recordType", this.recordType);
        params.append("file", this.fileByRecordType);

        const { data } = await ApiService.post<unknown>(
          "/import/parseFile",
          params
        );

        if (this.isSamplesImport) {
          if (
            !(
              Boolean(data?.fieldNameForColumnName) &&
              typeof data.fieldNameForColumnName === "object"
            )
          )
            throw new Error("Field name for column name mapping data missing.");

          runInAction(() => {
            this.templateInfo = data.templateInfo;

            this.fieldNameForColumnName = data.fieldNameForColumnName;

            this.samplesMappings = data.templateInfo.fields.map(
              ({ name, type }): ColumnFieldMap => {
                const originalColumnName = Object.keys(
                  data.fieldNameForColumnName
                ).find((key) => this.fieldNameForColumnName[key] === name);

                const newFieldMap = new ColumnFieldMap({
                  recordType: this.recordType,
                  selected: true,
                  columnName: originalColumnName,
                  field: Fields.custom,
                  fieldName: name,
                  fieldType: apiStringToFieldType(type),
                  quantityUnitId:
                    data.quantityUnitForColumn[originalColumnName] ?? null,
                  options:
                    data.radioOptionsForColumn[originalColumnName] ?? null,
                  fieldChangeCallback: fieldChangeHandler,
                  isNameUnique: (c) => this.isNameUnique(c),
                  columnsWithoutBlankValue: data.columnsWithoutBlankValue,
                });

                // after creation: auto-select conversion in suitable cases
                const fieldsByRecordType = newFieldMap.fieldsByRecordType;
                if (
                  newFieldMap.isCompatibleWithField(
                    fieldsByRecordType[nameToMatch(name)]
                  )
                ) {
                  newFieldMap.setField(fieldsByRecordType[nameToMatch(name)]);
                }
                return newFieldMap;
              }
            );
          });
        } else {
          // same process for containers and subsamples
          const defaultType = "string";
          runInAction(() => {
            const mappings = data.columnNames.map((columnName) => {
              const newFieldMap = new ColumnFieldMap({
                recordType: this.recordType,
                selected: true,
                columnName,
                field: Fields.none,
                fieldName: columnName,
                fieldType: apiStringToFieldType(defaultType),
                quantityUnitId: null,
                options: null,
                fieldChangeCallback: fieldChangeHandler,
                isNameUnique: (c) => this.isNameUnique(c),
                columnsWithoutBlankValue: data.columnsWithoutBlankValue,
              });
              /*
               * after creation: auto-select conversion in suitable cases
               * or unselect / don't assign if there is no match (as movables have no custom fields)
               */
              const fieldsByRecordType = newFieldMap.fieldsByRecordType;
              if (
                newFieldMap.isCompatibleWithField(
                  fieldsByRecordType[nameToMatch(columnName)]
                )
              ) {
                newFieldMap.setField(
                  fieldsByRecordType[nameToMatch(columnName)]
                );
              } else {
                newFieldMap.setField(Fields.none);
                newFieldMap.selected = false;
              }
              return newFieldMap;
            });
            if (this.isContainersImport) {
              this.containersMappings = mappings;
            } else {
              this.subSamplesMappings = mappings;
            }
          });
        }
        state.transitionTo("parsed");
        // update state following name auto-mapping
        if (this.nameFieldIsSelected) state.transitionTo("nameSelected");
      }
    } catch (error) {
      console.error("parsing failed", error);
      const {
        response: { data },
      } = error;
      this.state.transitionTo("parsingFailed", () => ({
        fileErrorMessage: data.message,
      }));
      this.resetAllMappings();
    }
  }

  get fileErrorMessage(): string {
    this.state.assertCurrentState("parsingFailed");
    return this.state.data.fileErrorMessage;
  }

  fieldIsChosen(field: Field): boolean {
    return this.mappingsByRecordType.some((m) => m.field === field);
  }

  transformTemplateInfoForSubmission(): {
    fields: Array<any>;
    name: string;
  } {
    if (!this.createNewTemplate)
      return pick("id")(this.template) as { id: TemplateModel["id"] };
    if (!this.templateInfo) throw new Error("TemplateInfo is null");
    const templateFieldWithMappings: Array<{
      field: TemplateField;
      mapping: ColumnFieldMap | null;
    }> = ArrayUtils.zipWith<
      TemplateField,
      ColumnFieldMap | null,
      { field: TemplateField; mapping: ColumnFieldMap | null }
    >(
      this.templateInfo.fields,
      this.templateInfo.fields.map(
        ({ name }) =>
          this.samplesMappings.find((f) => f.fieldName === name) ?? null
      ),
      (f, m) => ({ field: f, mapping: m })
    );
    const processedFields = templateFieldWithMappings
      .filter(({ mapping }) => Boolean(mapping))
      .filter(({ mapping: { selected } }) => selected)
      .filter(({ mapping: { field } }) => field === Fields.custom)
      .map(({ field, mapping: { fieldName, chosenFieldType, options } }) => ({
        ...field,
        name: fieldName,
        type: fieldTypeToApiString(chosenFieldType),
        definition:
          chosenFieldType === FieldTypes.radio ||
          chosenFieldType === FieldTypes.choice
            ? {
                options,
                multiple: false,
              }
            : null,
      }));
    const newUnitId = this.samplesMappings.find(
      ({ selected, field }) => field === Fields.quantity && selected
    )?.quantityUnitId;
    if (this.quantityFieldIsSelected && newUnitId) {
      this.setDefaultUnitId(newUnitId);
    }

    return {
      ...this.templateInfo,
      fields: processedFields,
      name: this.templateName,
    };
  }

  findField(
    mappings: Array<ColumnFieldMap>,
    field: Field
  ): ColumnFieldMap | undefined {
    return mappings.find((f) => f.field === field);
  }

  findParsedColumnName(
    mappings: Array<ColumnFieldMap>,
    field: Field
  ): string | undefined | null {
    const columnName = this.findField(mappings, field)?.columnName;
    return columnName ?? null;
  }

  makeMappingsObject(mappings: Array<ColumnFieldMap>): {
    [columnName: string]: string;
  } {
    const name = this.findParsedColumnName(mappings, Fields.name);
    if (!name) throw new Error("Name is a required field");
    const desc = this.findParsedColumnName(mappings, Fields.description);
    const eDate = this.findParsedColumnName(mappings, Fields.expiry_date);
    const tags = this.findParsedColumnName(mappings, Fields.tags);
    const source = this.findParsedColumnName(mappings, Fields.source);
    const quantity = this.findParsedColumnName(mappings, Fields.quantity);
    const importId = this.findParsedColumnName(
      mappings,
      Fields.import_identifier
    );
    const pContainerGlobalId = this.findParsedColumnName(
      mappings,
      Fields.parent_container_global_id
    );
    const pContainerImportId = this.findParsedColumnName(
      mappings,
      Fields.parent_container_import_id
    );
    const pSampleGlobalId = this.findParsedColumnName(
      mappings,
      Fields.parent_sample_global_id
    );
    const pSampleImportId = this.findParsedColumnName(
      mappings,
      Fields.parent_sample_import_id
    );

    const result = {
      [name]: "name",
      ...(desc ? { [desc]: "description" } : {}),
      ...(eDate ? { [eDate]: "expiry date" } : {}),
      ...(tags ? { [tags]: "tags" } : {}),
      ...(source ? { [source]: "source" } : {}),
      ...(quantity ? { [quantity]: "quantity" } : {}),
      ...(importId ? { [importId]: "import identifier" } : {}),
      ...(pContainerGlobalId
        ? { [pContainerGlobalId]: "parent container global id" }
        : {}),
      ...(pContainerImportId
        ? { [pContainerImportId]: "parent container import id" }
        : {}),
      ...(pSampleGlobalId
        ? { [pSampleGlobalId]: "parent sample global id" }
        : {}),
      ...(pSampleImportId
        ? { [pSampleImportId]: "parent sample import id" }
        : {}),

      ...Object.fromEntries(
        mappings
          .filter(({ selected }) => !selected)
          .map(({ columnName }) => [columnName, null])
      ),
    };
    return result;
  }

  async importFiles() {
    this.state.transitionTo("submitting");
    const { peopleStore, uiStore } = getRootStore();

    try {
      const params = new FormData();

      if (this.containersFile && this.containersSubmittable)
        params.append("containersFile", this.containersFile);
      if (this.samplesFile && this.samplesSubmittable)
        params.append("samplesFile", this.samplesFile);
      if (this.subSamplesFile && this.subSamplesSubmittable)
        params.append("subSamplesFile", this.subSamplesFile);

      params.append(
        "importSettings",
        // make different fieldMappings object (per type)
        JSON.stringify({
          containerSettings: this.containersSubmittable
            ? {
                fieldMappings: this.makeMappingsObject(this.containersMappings),
              }
            : null,
          sampleSettings: this.samplesSubmittable
            ? {
                templateInfo: this.transformTemplateInfoForSubmission(),
                fieldMappings: this.makeMappingsObject(this.samplesMappings),
              }
            : null,
          subSampleSettings: this.subSamplesSubmittable
            ? {
                fieldMappings: this.makeMappingsObject(this.subSamplesMappings),
              }
            : null,
        })
      );

      const {
        // renaming status property to prevent duplication.
        data: {
          status: importStatus,
          sampleResults,
          containerResults,
          subSampleResults,
        },
      } = await showToastWhilstPending(
        `Importing Records...`,
        ApiService.post<unknown>("/import/importFiles", params)
      );

      // multiple results can be returned and should be handled per type
      const resultsGroups = [
        containerResults,
        sampleResults,
        subSampleResults,
      ].filter((rg) => rg);

      resultsGroups.forEach((group) => {
        const { results, type, status, templateResult } = group;
        const labelByResultsType = `${toTitleCase(type)}s`;

        if (status !== "COMPLETED") {
          this.state.transitionTo("nameSelected");
          if (type === "SAMPLE") results.concat(templateResult);
          uiStore.addAlert(
            mkAlert({
              message: `Could not import invalid ${labelByResultsType} data.`,
              variant: "error",
              details: results
                .map(({ error }, i) => [error, i])
                .filter(([error]) => error)
                .flatMap(([error, i]) =>
                  error.errors.map((e) => ({
                    title: `Row ${i + 1}`,
                    help: e,
                    variant: "error",
                    record: null,
                  }))
                ),
              retryFunction: () => this.importFiles(),
            })
          );
        } else {
          uiStore.unsetDirty();
          // in group PREVALIDATED case, don't create records and alerts
          const factory = new MemoisedFactory();
          uiStore.addAlert(
            mkAlert({
              message: `${labelByResultsType} successfully imported.`,
              variant: "success",
              isInfinite: true,
              details: results
                .map(({ record }) => {
                  const newRecord = factory.newRecord(record);
                  newRecord.populateFromJson(factory, record, null);
                  return newRecord;
                })
                .map((record) => ({
                  title: `Imported "${record.name}".`,
                  variant: "success",
                  record,
                })),
            })
          );
        }
      });
      if (importStatus === "COMPLETED") {
        this.state.transitionTo("successfullySubmitted");
        if (peopleStore.currentUser) void peopleStore.currentUser.getBench();
        this.resetAllLoadedFiles();
        this.resetAllMappings();
        this.setTemplateName(DEFAULT_NEW_TEMPLATE_NAME);
      }
    } catch (error) {
      const gatewayTimeout =
        Parsers.objectPath(["response", "status"], error)
          .flatMap(Parsers.isNumber)
          .orElse(-1) === 504;
      this.state.transitionTo("nameSelected");
      uiStore.addAlert(
        mkAlert({
          title: gatewayTimeout
            ? "Something went wrong but the import may have completed."
            : "Something went wrong and some records were not imported.",
          message: getErrorMessage(error, "Unknown reason."),
          variant: gatewayTimeout ? "warning" : "error",
        })
      );
      console.error("Problem on import.", error);
    }
  }

  isNameUnique(columnFieldMap: ColumnFieldMap): boolean {
    const mappings = new RsSet(this.mappingsByRecordType);
    mappings.delete(columnFieldMap);
    return !mappings
      .map((m) => m.fieldName.trim())
      .has(columnFieldMap.fieldName.trim());
  }

  /*
   * When importing samples into an existing template (this.template), the
   * columns of the CSV file must match the fields of the template. Once the
   * built-in fields have been assigned (name, description, etc.), the
   * remaining selected fields (the mappings with a .field of Fields.custom and
   * are selected) should be the same in number as the CSV columns, have the
   * same name, and have compatible types. If any of these checks fails then
   * this computed returns false. It returns null if the user is creating a new
   * template.
   */
  get importMatchesExistingTemplate():
    | { matches: false; reason: string }
    | { matches: true }
    | null {
    if (this.createNewTemplate) return null;
    if (!this.template)
      return { matches: false, reason: "Template has not been selected" };
    const template = this.template;

    const customFields = this.mappingsByRecordType.filter(
      ({ field, selected }) => field === Fields.custom && selected
    );
    if (customFields.length !== template.fields.length)
      return {
        matches: false,
        reason: `Number of custom columns (${customFields.length}) does not equal the number of template fields (${template.fields.length}).`,
      };

    const namePairs = ArrayUtils.zipWith(
      customFields,
      template.fields,
      ({ columnName }, { name: fieldName }) => [columnName, fieldName]
    );
    if (namePairs.some(([c, f]) => c !== f)) {
      const help = namePairs
        .filter(([c, f]) => c !== f)
        .map(([c, f]) => `"${c}" is not the same as "${f}"`)
        .join(", ");
      return { matches: false, reason: `The names don't match: ${help}.` };
    }

    const notMatchingByType = new Set(
      ArrayUtils.zipWith(
        customFields,
        template.fields,
        ({ allValidTypes, columnName }, { type: fieldType }) =>
          allValidTypes.includes(apiStringToFieldType(fieldType.toLowerCase()))
            ? null
            : columnName
      )
    );
    notMatchingByType.delete(null);
    if (notMatchingByType.size > 0) {
      const help = [...notMatchingByType].join(", ");
      return {
        matches: false,
        reason: `Some of the data in some  columns does not match the type of the respective template field. Please check the values in these columns: ${help}`,
      };
    }

    const columnsWithMissingData = ArrayUtils.zipWith(
      customFields,
      template.fields,
      ({ columnsWithoutBlankValue, columnName }, { mandatory }) =>
        mandatory && !columnsWithoutBlankValue.includes(columnName)
          ? columnName
          : null
    ).filter(Boolean);
    if (columnsWithMissingData.length) {
      return {
        matches: false,
        reason: `Some columns have missing required data where the template's field mandates a value. In the CSV file, please provide a value for every cell in these columns or modify the template fields to no longer require a value. These columns are: ${columnsWithMissingData.join(
          ", "
        )}.`,
      };
    }

    return { matches: true };
  }

  // group all byType computeds
  byRecordType(
    prop: "label" | "fileLoaded" | "file" | "mappings" | "resetMappings"
  ): unknown {
    const byType = {
      label: this.labelByRecordType,
      fileLoaded: this.fileByRecordTypeLoaded,
      file: this.fileByRecordType,
      mappings: this.mappingsByRecordType,
      resetMappings: () => this.resetMappingsByRecordType(),
    };
    return byType[prop];
  }

  /*
   * Whenever the URL changes, this method should be called to update the
   * current type of record being imported of the current instance of this
   * class.
   *
   * @throws If `recordType` is either missing from the passed URLSearchParams
   *         or is an invalid string
   */
  updateRecordType(urlSearchParams: URLSearchParams): void {
    const recordTypeParam = urlSearchParams.get("recordType");
    if (!recordTypeParam)
      throw new Error("recordType URL arg is missing but required");

    const recordType: ImportRecordType = Result.first(
      parseString("SAMPLES", recordTypeParam),
      parseString("CONTAINERS", recordTypeParam),
      parseString("SUBSAMPLES", recordTypeParam)
    ).orElseGet(() => {
      throw new Error(
        `Could not parse URL arg, invaid value: "${recordTypeParam}".`
      );
    });

    this.setCurrentRecordType(recordType);
  }
}
