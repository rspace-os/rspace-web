import fc from "fast-check";
import { describe, expect, test, vi } from "vitest";
import { arrayOfSameElements } from "../../../../util/__tests__/helpers";
import type { FieldModelAttrs } from "../../FieldModel";
import { makeMockInstrumentTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

const fieldData: Array<FieldModelAttrs> = [
  {
    id: 1,
    globalId: "IF1",
    name: "Serial",
    type: "text",
    content: "SN-001",
    columnIndex: 1,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 2,
    globalId: "IF2",
    name: "Frequency",
    type: "number",
    content: "50",
    columnIndex: 2,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 3,
    globalId: "IF3",
    name: "Calibration Date",
    type: "date",
    content: "2024-01-01",
    columnIndex: 3,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 4,
    globalId: "IF4",
    name: "Notes",
    type: "text",
    content: "Default notes",
    columnIndex: 4,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 5,
    globalId: "IF5",
    name: "Mode",
    type: "radio",
    content: "",
    columnIndex: 5,
    definition: { options: ["Auto", "Manual"] },
    selectedOptions: [],
    attachment: null,
    mandatory: false,
  },
];

describe("action: moveField", () => {
  describe("Property Tests", () => {
    test("Moving to top should be idempotent.", () => {
      fc.assert(
        fc.property(fc.nat(fieldData.length - 1), (i) => {
          const template = makeMockInstrumentTemplate({ fields: fieldData });
          const field = template.fields[i];
          template.moveField(field, 0);
          const afterOnce = [...template.fields];
          template.moveField(field, 0);
          const afterTwice = [...template.fields];
          expect(arrayOfSameElements(afterOnce, afterTwice)).toBe(true);
        }),
      );
    });

    test("Moving to bottom should be idempotent.", () => {
      fc.assert(
        fc.property(fc.nat(fieldData.length - 1), (i) => {
          const template = makeMockInstrumentTemplate({ fields: fieldData });
          const field = template.fields[i];
          template.moveField(field, template.fields.length - 1);
          const afterOnce = [...template.fields];
          template.moveField(field, template.fields.length - 1);
          const afterTwice = [...template.fields];
          expect(arrayOfSameElements(afterOnce, afterTwice)).toBe(true);
        }),
      );
    });

    test("Re-ordering never changes count.", () => {
      fc.assert(
        fc.property(
          fc.nat(fieldData.length - 1).chain((maxLength) => fc.array(fc.tuple(fc.nat(maxLength), fc.nat(maxLength)))),
          (changes) => {
            const template = makeMockInstrumentTemplate({ fields: fieldData });
            for (const [fieldIndex, positionIndex] of changes) {
              const field = template.fields[fieldIndex];
              template.moveField(field, positionIndex);
            }
            expect(template.fields.length).toBe(fieldData.length);
          },
        ),
      );
    });
  });

  describe("Unit Tests", () => {
    test("Moving a field to the top places it at index 0.", () => {
      const template = makeMockInstrumentTemplate({ fields: fieldData });
      const lastField = template.fields[template.fields.length - 1];
      template.moveField(lastField, 0);
      expect(template.fields[0]).toBe(lastField);
    });

    test("Moving a field to the bottom places it at the last index.", () => {
      const template = makeMockInstrumentTemplate({ fields: fieldData });
      const firstField = template.fields[0];
      template.moveField(firstField, template.fields.length - 1);
      expect(template.fields[template.fields.length - 1]).toBe(firstField);
    });

    test("Using index -1 moves a field to the last position.", () => {
      const template = makeMockInstrumentTemplate({ fields: fieldData });
      const firstField = template.fields[0];
      template.moveField(firstField, -1);
      expect(template.fields[template.fields.length - 1]).toBe(firstField);
    });

    test("columnIndex values are updated to reflect new positions after a move.", () => {
      const template = makeMockInstrumentTemplate({ fields: fieldData });
      const lastField = template.fields[template.fields.length - 1];
      template.moveField(lastField, 0);
      template.fields.forEach((f, i) => {
        expect(f.columnIndex).toBe(i + 1);
      });
    });

    test("throws when newIndex is out of bounds.", () => {
      const template = makeMockInstrumentTemplate({ fields: fieldData });
      const field = template.fields[0];
      expect(() => template.moveField(field, fieldData.length)).toThrow();
    });

    test("throws when the field does not belong to the template.", () => {
      const template = makeMockInstrumentTemplate({ fields: fieldData });
      const other = makeMockInstrumentTemplate({ fields: fieldData });
      const foreignField = other.fields[0];
      expect(() => template.moveField(foreignField, 0)).toThrow();
    });
  });
});
