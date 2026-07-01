import { describe, expect, test, vi } from "vitest";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import InstrumentModel from "../../InstrumentModel";
import { instrumentAttrs, makeMockInstrument } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

describe("InstrumentModel constructor", () => {
  test("recordType is 'instrument'", () => {
    const instrument = makeMockInstrument();
    expect(instrument.recordType).toBe("instrument");
  });

  test("fields defaults to empty array when no fields provided", () => {
    const instrument = makeMockInstrument();
    expect(instrument.fields).toHaveLength(0);
  });

  test("template defaults to null", () => {
    const instrument = makeMockInstrument();
    expect(instrument.template).toBeNull();
  });

  test("templateId is set from attrs", () => {
    const instrument = makeMockInstrument({ templateId: 42 });
    expect(instrument.templateId).toBe(42);
  });

  test("templateId defaults to null when absent", () => {
    const instrument = makeMockInstrument();
    expect(instrument.templateId).toBeNull();
  });

  test("templateVersion is set from attrs", () => {
    const instrument = makeMockInstrument({ templateVersion: 3 });
    expect(instrument.templateVersion).toBe(3);
  });

  test("templateVersion defaults to null when absent", () => {
    const instrument = makeMockInstrument();
    expect(instrument.templateVersion).toBeNull();
  });

  test("fields are parsed from attrs.fields", () => {
    const instrument = makeMockInstrument({
      fields: [
        {
          id: 10,
          globalId: "IF10",
          name: "Volume",
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
    expect(instrument.fields).toHaveLength(1);
    expect(instrument.fields[0].name).toBe("Volume");
  });

  test("multiple fields are all parsed from attrs.fields", () => {
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
        {
          id: 2,
          globalId: "IF2",
          name: "Calibration Date",
          type: "date",
          content: null,
          selectedOptions: null,
          definition: null,
          columnIndex: 2,
          attachment: null,
          mandatory: false,
        },
      ],
    });
    expect(instrument.fields).toHaveLength(2);
  });

  test("iconName is 'instrument'", () => {
    const instrument = makeMockInstrument();
    expect(instrument.iconName).toBe("instrument");
  });

  test("cardTypeLabel is 'inventory:recordTypes.instrument.singular'", () => {
    const instrument = makeMockInstrument();
    expect(instrument.cardTypeLabel).toBe("inventory:recordTypes.instrument.singular");
  });

  test("is usable in Lists of Materials", () => {
    const instrument = makeMockInstrument();
    expect(instrument.usableInLoM).toBe(true);
  });

  test("supportsBatchEditing returns false", () => {
    const instrument = makeMockInstrument();
    expect(instrument.supportsBatchEditing).toBe(false);
  });

  test("two instances created by the same factory are independent objects", () => {
    const factory = new AlwaysNewFactory();
    const a = new InstrumentModel(factory, instrumentAttrs({ name: "A" }));
    const b = new InstrumentModel(factory, instrumentAttrs({ name: "B" }));
    expect(a).not.toBe(b);
    expect(a.name).toBe("A");
    expect(b.name).toBe("B");
  });
});
