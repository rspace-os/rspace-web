import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import type { FieldType as FieldModelType } from "../../../definitions/Field";
import { FieldTypes } from "../../FieldTypes";
import ImportModel, { ColumnFieldMap, Fields } from "../../ImportModel";
import { makeMockTemplate } from "../TemplateModel/mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    uiStore: { setDirty: vi.fn(), unsetDirty: vi.fn(), addAlert: vi.fn() },
    unitStore: { assertValidUnitId: () => {} },
  }),
}));

const matchWithBlankColumnFor = (type: FieldModelType, mandatory = false, blankColumns = ["Collected"]) => {
  const model = new ImportModel("SAMPLES");
  runInAction(() => {
    model.createNewTemplate = false;
    model.template = makeMockTemplate({
      fields: [
        {
          name: "Collected",
          type,
          columnIndex: 1,
          selectedOptions: null,
          definition: null,
          attachment: null,
          mandatory,
        },
      ],
    });
    model.samplesMappings = [
      new ColumnFieldMap({
        recordType: "SAMPLES",
        selected: true,
        columnName: "Collected",
        field: Fields.custom,
        fieldName: "Collected",
        fieldType: FieldTypes.plain_text,
        quantityUnitId: null,
        options: null,
        fieldChangeCallback: () => {},
        isNameUnique: (c) => model.isNameUnique(c),
        columnsWithoutBlankValue: [],
        blankColumns,
      }),
    ];
  });
  return model.importMatchesExistingTemplate;
};

describe("importMatchesExistingTemplate", () => {
  test.each(["date", "uri", "number", "time", "reference"] as const)(
    "returns matches:true when a column with no value in any row fills a %s field",
    (type) => {
      expect(matchWithBlankColumnFor(type)).toEqual({ matches: true });
    },
  );

  test("returns matches:false when a column blank in only some rows holds text for a date field", () => {
    expect(matchWithBlankColumnFor("date", false, [])).toEqual({
      matches: false,
      reason: "inventory:import.columnMapping.templateColumnTypeMismatch",
    });
  });

  test("still refuses a column with no value in any row for an attachment field", () => {
    expect(matchWithBlankColumnFor("attachment")).toEqual({
      matches: false,
      reason: "inventory:import.columnMapping.templateColumnTypeMismatch",
    });
  });

  test("still refuses a column with no value in any row for a mandatory field", () => {
    expect(matchWithBlankColumnFor("date", true)).toEqual({
      matches: false,
      reason: "inventory:import.columnMapping.templateRequiredValueMissing",
    });
  });
});
