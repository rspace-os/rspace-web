import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import type { FieldType as FieldModelType } from "../../../definitions/Field";
import type { ImportRecordType } from "../../../stores/ImportStore";
import { apiStringToFieldType, type FieldType, FieldTypes } from "../../FieldTypes";
import ImportModel, { ColumnFieldMap, Fields } from "../../ImportModel";
import { makeMockInstrumentTemplate } from "../InstrumentTemplateModel/mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: true },
    uiStore: {
      setDirty: vi.fn(),
      unsetDirty: vi.fn(),
      addAlert: vi.fn(),
    },
    trackingStore: { trackEvent: vi.fn() },
    peopleStore: { currentUser: null },
  }),
}));

const makeMapping = (
  model: ImportModel,
  recordType: ImportRecordType,
  {
    field = Fields.name,
    fieldName = "Name",
    columnName = "Column",
    selected = true,
    fieldType = FieldTypes.plain_text as FieldType,
    columnsWithoutBlankValue,
    blankColumns = [] as string[],
  }: {
    field?: symbol;
    fieldName?: string;
    columnName?: string;
    selected?: boolean;
    fieldType?: FieldType;
    columnsWithoutBlankValue?: string[];
    blankColumns?: string[];
  } = {},
): ColumnFieldMap =>
  new ColumnFieldMap({
    recordType,
    selected,
    columnName,
    field,
    fieldName,
    fieldType,
    quantityUnitId: null,
    options: null,
    fieldChangeCallback: () => {},
    isNameUnique: (c) => model.isNameUnique(c),
    columnsWithoutBlankValue: columnsWithoutBlankValue ?? [columnName],
    blankColumns,
  });

const makeInstrumentsNameMapping = (model: ImportModel): ColumnFieldMap =>
  makeMapping(model, "INSTRUMENTS", {
    field: Fields.name,
    fieldName: "Name",
    columnName: "ItemName",
    columnsWithoutBlankValue: ["ItemName"],
  });

const instrumentFieldAttrs = (name: string, overrides: { type?: FieldModelType; mandatory?: boolean } = {}) => ({
  name,
  type: overrides.type ?? ("string" as FieldModelType),
  columnIndex: 1,
  selectedOptions: null,
  definition: null,
  attachment: null,
  mandatory: overrides.mandatory ?? false,
});

describe("isNameUnique", () => {
  /*
   * Before this fix, isNameUnique used this.mappingsByRecordType (the active
   * tab's mappings) instead of looking up mappings by the ColumnFieldMap's own
   * recordType. That meant a duplicate name on SAMPLES would falsely make an
   * INSTRUMENTS mapping non-unique, and vice-versa.
   */
  test("a duplicate field name in a different record type does not affect uniqueness", () => {
    const model = new ImportModel("INSTRUMENTS");
    const instA = makeMapping(model, "INSTRUMENTS", { fieldName: "Sensor Type", columnName: "colA" });
    const sampleA = makeMapping(model, "SAMPLES", { fieldName: "Sensor Type", columnName: "colB" });
    runInAction(() => {
      model.instrumentsMappings = [instA];
      model.samplesMappings = [sampleA];
    });
    expect(model.isNameUnique(instA)).toBe(true);
  });

  test("returns false when two INSTRUMENTS mappings share the same field name", () => {
    const model = new ImportModel("INSTRUMENTS");
    const instA = makeMapping(model, "INSTRUMENTS", { fieldName: "Serial", columnName: "colA" });
    const instB = makeMapping(model, "INSTRUMENTS", { fieldName: "Serial", columnName: "colB" });
    runInAction(() => {
      model.instrumentsMappings = [instA, instB];
    });
    expect(model.isNameUnique(instA)).toBe(false);
  });
});

describe("transformInstrumentTemplateInfoForSubmission", () => {
  test("throws when instrumentTemplateInfo is null", () => {
    const model = new ImportModel("INSTRUMENTS");
    expect(() => model.transformInstrumentTemplateInfoForSubmission()).toThrow("InstrumentTemplateInfo is null");
  });

  test("result is JSON serialisable", () => {
    const model = new ImportModel("INSTRUMENTS");
    const nameMapping = makeInstrumentsNameMapping(model);
    runInAction(() => {
      model.instrumentTemplateInfo = { name: "template", fields: [{ name: "ItemName", type: "string" }] };
      model.instrumentTemplateName = "My Template";
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "ItemName",
          columnName: "ItemName",
        }),
        nameMapping,
      ];
    });
    expect(() => JSON.stringify(model.transformInstrumentTemplateInfoForSubmission())).not.toThrow();
  });

  test("only selected custom-field mappings appear in the result, with the template name applied", () => {
    const model = new ImportModel("INSTRUMENTS");
    /*
     * fieldName must match the templateInfo field name so that the lookup inside
     * transformInstrumentTemplateInfoForSubmission can pair each mapping with its
     * template field definition.
     */
    const selectedCustom = makeMapping(model, "INSTRUMENTS", {
      field: Fields.custom,
      fieldName: "Serial",
      columnName: "Serial",
    });
    const deselectedCustom = makeMapping(model, "INSTRUMENTS", {
      field: Fields.custom,
      fieldName: "Location",
      columnName: "Location",
      selected: false,
    });
    runInAction(() => {
      model.instrumentTemplateInfo = {
        name: "template",
        fields: [
          { name: "Serial", type: "string" },
          { name: "Location", type: "string" },
        ],
      };
      model.instrumentTemplateName = "Production Template";
      model.instrumentsMappings = [selectedCustom, deselectedCustom];
    });
    const result = model.transformInstrumentTemplateInfoForSubmission();
    expect(result.name).toBe("Production Template");
    expect(result.fields).toHaveLength(1);
    expect((result.fields[0] as { name: string }).name).toBe("Serial");
  });
});

describe("instrumentsSubmittable", () => {
  test("is false when no instruments file is loaded", () => {
    const model = new ImportModel("INSTRUMENTS");
    expect(model.instrumentsSubmittable).toBe(false);
  });

  test("is false in new-template mode when instrumentTemplateInfo is absent", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentsFile = new File([""], "test.csv");
      model.instrumentsMappings = [makeInstrumentsNameMapping(model)];
      // instrumentCreateNewTemplate is true by default; instrumentTemplateInfo is null
    });
    expect(model.instrumentsSubmittable).toBe(false);
  });

  test("is true in new-template mode with templateInfo, a valid name, and valid mappings", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentsFile = new File([""], "test.csv");
      model.instrumentTemplateInfo = { name: "template", fields: [] };
      model.instrumentTemplateName = "My Instrument Template";
      model.instrumentsMappings = [makeInstrumentsNameMapping(model)];
    });
    expect(model.instrumentsSubmittable).toBe(true);
  });

  test("is false in existing-template mode when no template is selected", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentsFile = new File([""], "test.csv");
      model.instrumentCreateNewTemplate = false;
      model.instrumentsMappings = [makeInstrumentsNameMapping(model)];
      // instrumentTemplate remains null
    });
    expect(model.instrumentsSubmittable).toBe(false);
  });

  test("is true in existing-template mode with a fully matching template", () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({
      fields: [instrumentFieldAttrs("Serial")],
    });
    runInAction(() => {
      model.instrumentsFile = new File([""], "test.csv");
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
      model.instrumentTemplateName = "Existing Template";
      model.instrumentsMappings = [
        makeInstrumentsNameMapping(model),
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Serial",
          columnName: "Serial",
          fieldType: FieldTypes.plain_text,
          columnsWithoutBlankValue: ["ItemName", "Serial"],
        }),
      ];
    });
    expect(model.instrumentsSubmittable).toBe(true);
  });

  test("is true for a PIDINST CSV whose date, link and web address columns are blank in every row", () => {
    // [template field name, template field type, mandatory, blank in every row]
    const pidinstFields: Array<[string, FieldModelType, boolean, boolean]> = [
      ["Owner", "string", true, false],
      ["Manufacturer", "string", true, false],
      ["Model", "string", false, false],
      ["Instrument type", "string", false, false],
      ["Commissioned", "date", false, false],
      ["Decommissioned", "date", false, true],
      ["Measurement technique", "link", false, true],
      ["Measured quantity", "string", false, false],
      ["Calibration", "link", false, true],
      ["Last calibrated", "date", false, true],
      ["Landing page", "uri", false, true],
      ["Alternate Identifier", "string", false, false],
    ];
    const blankColumns = pidinstFields.filter(([, , , blank]) => blank).map(([name]) => name);
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentsFile = new File([""], "instruments.csv");
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplateName = "Instrument (PIDINST 1.0)";
      model.instrumentTemplate = makeMockInstrumentTemplate({
        fields: pidinstFields.map(([name, type, mandatory]) => instrumentFieldAttrs(name, { type, mandatory })),
      });
      model.instrumentsMappings = [
        makeInstrumentsNameMapping(model),
        ...pidinstFields.map(([name, type, , blank]) =>
          makeMapping(model, "INSTRUMENTS", {
            field: Fields.custom,
            fieldName: name,
            columnName: name,
            fieldType: blank ? FieldTypes.plain_text : apiStringToFieldType(type),
            columnsWithoutBlankValue: blank ? [] : [name],
            blankColumns,
          }),
        ),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toEqual({ matches: true });
    expect(model.instrumentsSubmittable).toBe(true);
  });
});

describe("importInstrumentMatchesExistingTemplate", () => {
  test("is null when the user is creating a new template", () => {
    const model = new ImportModel("INSTRUMENTS");
    // instrumentCreateNewTemplate defaults to true
    expect(model.importInstrumentMatchesExistingTemplate).toBeNull();
  });

  test("returns matches:false when no template is selected", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
    });
    expect(model.importInstrumentMatchesExistingTemplate).toMatchObject({ matches: false });
  });

  test("returns matches:false when the custom field count differs from the template field count", () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({
      fields: [instrumentFieldAttrs("Serial"), instrumentFieldAttrs("Location")],
    });
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
      // one custom mapping selected; template has two fields
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Serial",
          columnName: "Serial",
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toMatchObject({ matches: false });
  });

  test("returns matches:false when a CSV column name does not match the template field name", () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({
      fields: [instrumentFieldAttrs("TemplateFieldName")],
    });
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "AnyName",
          columnName: "CsvColumnName", // differs from template field name
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toMatchObject({ matches: false });
  });

  test("returns matches:false when the CSV column type is incompatible with the template field type", () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({
      fields: [instrumentFieldAttrs("Serial", { type: "reference" })],
    });
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
      // date FieldType is not compatible with reference
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Serial",
          columnName: "Serial",
          fieldType: FieldTypes.date,
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toMatchObject({ matches: false });
  });

  test("returns matches:false when a mandatory template field has missing data in the CSV", () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({
      fields: [instrumentFieldAttrs("Serial", { mandatory: true })],
    });
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Serial",
          columnName: "Serial",
          columnsWithoutBlankValue: [], // "Serial" column has blank values
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toMatchObject({ matches: false });
  });

  test("returns matches:true when field count, names, types, and mandatory data all align", () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({
      fields: [instrumentFieldAttrs("Serial")],
    });
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Serial",
          columnName: "Serial", // matches template field name
          fieldType: FieldTypes.plain_text, // compatible with "string"
          columnsWithoutBlankValue: ["Serial"], // no blank values
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toEqual({ matches: true });
  });

  test.each(["date", "uri", "number", "time", "reference", "attachment"] as const)(
    "returns matches:true when a column with no value in any row fills a %s field",
    (type) => {
      const model = new ImportModel("INSTRUMENTS");
      runInAction(() => {
        model.instrumentCreateNewTemplate = false;
        model.instrumentTemplate = makeMockInstrumentTemplate({
          fields: [instrumentFieldAttrs("Decommissioned", { type })],
        });
        model.instrumentsMappings = [
          makeMapping(model, "INSTRUMENTS", {
            field: Fields.custom,
            fieldName: "Decommissioned",
            columnName: "Decommissioned",
            fieldType: FieldTypes.plain_text,
            columnsWithoutBlankValue: [],
            blankColumns: ["Decommissioned"],
          }),
        ];
      });
      expect(model.importInstrumentMatchesExistingTemplate).toEqual({ matches: true });
    },
  );

  test("returns matches:false when a column blank in only some rows holds text for a date field", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = makeMockInstrumentTemplate({
        fields: [instrumentFieldAttrs("Decommissioned", { type: "date" })],
      });
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Decommissioned",
          columnName: "Decommissioned",
          fieldType: FieldTypes.plain_text,
          columnsWithoutBlankValue: [],
          blankColumns: [],
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toEqual({
      matches: false,
      reason: "inventory:import.columnMapping.templateColumnTypeMismatch",
    });
  });

  test("still refuses a column with no value in any row for a mandatory field", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = makeMockInstrumentTemplate({
        fields: [instrumentFieldAttrs("Commissioned", { type: "date", mandatory: true })],
      });
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Commissioned",
          columnName: "Commissioned",
          fieldType: FieldTypes.plain_text,
          columnsWithoutBlankValue: [],
          blankColumns: ["Commissioned"],
        }),
      ];
    });
    expect(model.importInstrumentMatchesExistingTemplate).toEqual({
      matches: false,
      reason: "inventory:import.columnMapping.templateRequiredValueMissing",
    });
  });
});

describe("setFile", () => {
  test("auto-populates instrumentTemplateName from the CSV filename", () => {
    const model = new ImportModel("INSTRUMENTS");
    vi.spyOn(ImportModel.prototype, "parseCsvFile").mockImplementation(async () => {});
    const initialName = model.instrumentTemplateName;
    model.setFile(new File([""], "my-instruments.csv", { type: "text/csv" }));
    expect(model.instrumentTemplateName).not.toBe(initialName);
  });
});

describe("link-typed columns", () => {
  test("a link column submits as type link when creating a new template", () => {
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentTemplateInfo = {
        name: "template",
        fields: [{ name: "Calibrated by", type: "link" }],
      };
      model.instrumentTemplateName = "Links";
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Calibrated by",
          columnName: "Calibrated by",
          fieldType: FieldTypes.link,
        }),
      ];
    });

    const result = model.transformInstrumentTemplateInfoForSubmission();

    expect((result.fields[0] as { type: string }).type).toBe("link");
  });

  test("a link column maps onto an existing template's link field", () => {
    // the compatibility gate is acceptsTemplateFieldType vs the template's own field type:
    // a link column has to be accepted by a template field declared "link"
    const model = new ImportModel("INSTRUMENTS");
    runInAction(() => {
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = makeMockInstrumentTemplate({
        fields: [instrumentFieldAttrs("Calibrated by", { type: "link" as FieldModelType })],
      });
      model.instrumentsMappings = [
        makeMapping(model, "INSTRUMENTS", {
          field: Fields.custom,
          fieldName: "Calibrated by",
          columnName: "Calibrated by",
          fieldType: FieldTypes.link,
          columnsWithoutBlankValue: ["Calibrated by"],
        }),
      ];
    });

    expect(model.importInstrumentMatchesExistingTemplate).toEqual({ matches: true });
  });
});
