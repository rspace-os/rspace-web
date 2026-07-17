import { describe, expect, test, vi } from "vitest";
import FieldModel from "../../FieldModel";
import { fieldAttrs } from "../FieldModel/mocking";
import { makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentModel.overrideFields", () => {
  test("replaces all existing fields with new FieldModel instances", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(
      fieldAttrs({ type: "text", name: "Serial Number", content: "SN-001", columnIndex: 1 }),
      instrument,
    );
    instrument.overrideFields([source]);
    expect(instrument.fields).toHaveLength(1);
    expect(instrument.fields[0].name).toBe("Serial Number");
  });

  test("preserves field name and type", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(
      fieldAttrs({ type: "number", name: "Frequency", content: "50", columnIndex: 1 }),
      instrument,
    );
    instrument.overrideFields([source]);
    expect(instrument.fields[0].name).toBe("Frequency");
    expect(instrument.fields[0].type).toBe("number");
  });

  test("columnIndex is not set by the constructor (must be assigned directly after creation)", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(fieldAttrs({ type: "text", name: "A", columnIndex: 3 }), instrument);
    instrument.overrideFields([source]);
    const fm = instrument.fields[0] as FieldModel;
    // FieldModel.constructor uses pick() which omits columnIndex; it is only set via direct assignment
    expect(fm.columnIndex).toBeUndefined();
  });

  test("clears fields when called with an empty array", () => {
    const instrument = makeMockInstrument({
      fields: [
        {
          id: 1,
          globalId: "IF1",
          name: "Serial",
          type: "text",
          content: null,
          selectedOptions: null,
          definition: null,
          columnIndex: 1,
          attachment: null,
          mandatory: false,
        },
      ],
    });
    instrument.overrideFields([]);
    expect(instrument.fields).toHaveLength(0);
  });

  test("handles multiple fields and preserves order", () => {
    const instrument = makeMockInstrument();
    const f1 = new FieldModel(fieldAttrs({ type: "text", name: "First", columnIndex: 1 }), instrument);
    const f2 = new FieldModel(fieldAttrs({ type: "number", name: "Second", columnIndex: 2 }), instrument);
    const f3 = new FieldModel(fieldAttrs({ type: "date", name: "Third", columnIndex: 3 }), instrument);
    instrument.overrideFields([f1, f2, f3]);
    expect(instrument.fields).toHaveLength(3);
    expect(instrument.fields[0].name).toBe("First");
    expect(instrument.fields[1].name).toBe("Second");
    expect(instrument.fields[2].name).toBe("Third");
  });

  test("creates new FieldModel instances (not the same objects as input)", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(fieldAttrs({ type: "text", name: "Serial", columnIndex: 1 }), instrument);
    instrument.overrideFields([source]);
    expect(instrument.fields[0]).not.toBe(source);
    expect(instrument.fields[0]).toBeInstanceOf(FieldModel);
  });

  test("preserves mandatory flag", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(
      fieldAttrs({ type: "text", name: "Required", columnIndex: 1, mandatory: true }),
      instrument,
    );
    instrument.overrideFields([source]);
    const fm = instrument.fields[0] as FieldModel;
    expect(fm.mandatory).toBe(true);
  });

  test("attachment is falsy on copied fields when no attachment provided", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(fieldAttrs({ type: "text", name: "Note", columnIndex: 1 }), instrument);
    instrument.overrideFields([source]);
    const fm = instrument.fields[0] as FieldModel;
    // FieldModel constructor only calls setAttributes with attachment when it is truthy;
    // a null attachment in attrs means the attachment property is left unset (undefined)
    expect(fm.attachment).toBeFalsy();
  });

  test("preserves allowedRelationTypes on link fields", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(
      fieldAttrs({
        type: "link",
        name: "Related Instrument",
        columnIndex: 1,
        allowedRelationTypes: ["IsDerivedFrom", "IsPartOf"],
      }),
      instrument,
    );
    instrument.overrideFields([source]);
    const fm = instrument.fields[0] as FieldModel;
    expect(fm.allowedRelationTypes).toEqual(["IsDerivedFrom", "IsPartOf"]);
  });

  test("preserves radio field options", () => {
    const instrument = makeMockInstrument();
    const source = new FieldModel(
      fieldAttrs({
        type: "radio",
        name: "Status",
        columnIndex: 1,
        definition: { options: ["Active", "Inactive", "Retired"] },
        selectedOptions: ["Active"],
      }),
      instrument,
    );
    instrument.overrideFields([source]);
    const fm = instrument.fields[0] as FieldModel;
    expect(fm.options.map((o) => o.value)).toEqual(["Active", "Inactive", "Retired"]);
  });
});
