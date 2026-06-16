import { describe, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen, within } from "@testing-library/react";
import type React from "react";
import { assertConsistentOrderOfLists } from "@/__tests__/assertConsistentOrderOfLists";
import { IsValid } from "../../components/ValidatingSubmitButton";
import type { InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { makeMockContainer } from "../../stores/models/__tests__/ContainerModel/mocking";
import { personAttrs } from "../../stores/models/__tests__/PersonModel/mocking";
import { makeMockSample } from "../../stores/models/__tests__/SampleModel/mocking";
import { makeMockSubSample } from "../../stores/models/__tests__/SubSampleModel/mocking";
import { makeMockTemplate } from "../../stores/models/__tests__/TemplateModel/mocking";
import { makeMockRootStore } from "../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../stores/stores-context";
import materialTheme from "../../theme";
import RsSet from "../../util/set";
import ContainerBatchForm from "../Container/BatchForm";
import ContainerForm from "../Container/Form";
import ContainerNewRecordForm from "../Container/NewRecordForm";
import SynchroniseFormSections from "../components/Stepper/SynchroniseFormSections";
import MixedBatchForm from "../Mixed/BatchForm";
import SampleBatchForm from "../Sample/BatchForm";
import SampleForm from "../Sample/Form";
import SampleNewRecordForm from "../Sample/NewRecordForm";
import SubSampleBatchForm from "../Subsample/BatchForm";
import SubSampleForm from "../Subsample/Form";
import TemplateForm from "../Template/Form";
import TemplateNewRecordForm from "../Template/NewRecordForm";
import InstrumentForm from "../Instrument/Form";
import InstrumentTemplateForm from "../InstrumentTemplate/Form";
import InstrumentTemplateNewRecordForm from "../InstrumentTemplate/NewRecordForm";

class ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

vi.mock("../Sample/Content/SubsampleListing", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../Container/Content/Content", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../Template/Fields/SamplesList", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../components/Fields/Attachments/Attachments", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../components/Fields/ExtraFields/ExtraFields", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../components/ContextMenu/ContextMenu", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../Sample/Fields/Template/Template", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../Sample/Fields/Quantity", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
    query: () => ({}),
  },
}));

vi.mock("../../stores/stores/getRootStore", () => ({
  default: () => ({
    searchStore: {
      savedSearches: [{ name: "Dummy saved search", query: "foo" }],
    },
    uiStore: {
      addAlert: () => {},
    },
    unitStore: {
      getUnit: () => ({
        id: 1,
        label: "items",
        category: "dimensionless",
        description: "",
      }),
    },
  }),
}));
vi.mock("../../components/Ketcher/KetcherDialog", () => ({
  default: vi.fn(() => <div></div>),
}));
// Cast to any to avoid TypeScript errors with the mock implementation
window.fetch = vi.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(),
    headers: new Headers(),
    redirected: false,
    statusText: "OK",
    type: "basic",
    url: "",
    clone: () => ({}) as Response,
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    blob: () => Promise.resolve(new Blob()),
    formData: () => Promise.resolve(new FormData()),
    text: () => Promise.resolve(""),
    body: null,
    bodyUsed: false,
  } as Response),
);
type MakeRootStoreArgs = {
  activeResult: InventoryRecord | null;
  batchEditingRecords?: Array<InventoryRecord>;
};
function makeRootStore({ activeResult, batchEditingRecords }: MakeRootStoreArgs) {
  return makeMockRootStore({
    searchStore: {
      activeResult,
      fetcher: {
        generateNewQuery: () => "foo",
      },
      search: {
        batchEditingRecords,
        activeResult,
        processingContextActions: false,
        fetcher: {
          basketSearch: false,
        },
        batchEditableInstance: { loading: false, submittable: IsValid() },
      },
    },
    unitStore: {
      getUnit: () => ({
        id: 1,
        label: "items",
        category: "dimensionless",
        description: "",
      }),
      unitsOfCategory: () => [],
    },
  });
}
function getSectionNames(reactComponent: React.ReactNode, rootStore: MakeRootStoreArgs): Array<string> {
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={makeRootStore(rootStore)}>
        <SynchroniseFormSections>{reactComponent}</SynchroniseFormSections>
      </storesContext.Provider>
    </ThemeProvider>,
  );
  const sectionNames = screen
    .getAllByRole("region")
    .map((r) => within(r).getByRole("heading", { level: 3 }).textContent);
  cleanup();
  return sectionNames;
}
describe("Form Section Order", () => {
  test("Across all of the forms, all of the sections should be in a consistent order.", () => {
    window.ResizeObserver = ResizeObserver;

    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    window.scrollTo = vi.fn() as any;
    assertConsistentOrderOfLists(
      new Map([
        [
          "Container Form",
          getSectionNames(<ContainerForm />, {
            activeResult: makeMockContainer({
              owner: personAttrs(),
            }),
          }),
        ],
        [
          "Container New Form",
          getSectionNames(<ContainerNewRecordForm />, {
            activeResult: makeMockContainer({ id: null, globalId: null }),
          }),
        ],
        [
          "Container Batch Edit Form",
          getSectionNames(<ContainerBatchForm records={new RsSet([makeMockContainer()])} />, {
            activeResult: null,
            batchEditingRecords: [makeMockContainer()],
          }),
        ],
        [
          "Sample Form",
          getSectionNames(<SampleForm />, {
            activeResult: makeMockSample({
              owner: personAttrs(),
            }),
          }),
        ],
        [
          "Sample New Form",
          getSectionNames(<SampleNewRecordForm />, {
            activeResult: makeMockSample({ id: null, globalId: null }),
          }),
        ],
        [
          "Sample Batch Edit Form",
          getSectionNames(<SampleBatchForm records={new RsSet([makeMockSample()])} />, {
            activeResult: null,
            batchEditingRecords: [makeMockSample()],
          }),
        ],
        [
          "Subsample Form",
          getSectionNames(<SubSampleForm />, {
            activeResult: makeMockSubSample({
              owner: personAttrs(),
            }),
          }),
        ],
        [
          "Subsample Batch Edit Form",
          getSectionNames(<SubSampleBatchForm records={new RsSet([makeMockSubSample()])} />, {
            activeResult: null,
            batchEditingRecords: [makeMockSubSample()],
          }),
        ],
        [
          "Template Form",
          getSectionNames(<TemplateForm />, {
            activeResult: makeMockTemplate({
              owner: personAttrs(),
            }),
          }),
        ],
        [
          "Template New Form",
          getSectionNames(<TemplateNewRecordForm />, {
            activeResult: makeMockTemplate({ id: null, globalId: null }),
          }),
        ],
        [
          "Instrument Form",
          getSectionNames(<InstrumentForm />, {
            activeResult: makeMockInstrument({
              owner: personAttrs(),
            }),
          }),
        ],
        [
          "Instrument Template Form",
          getSectionNames(<InstrumentTemplateForm />, {
            activeResult: makeMockInstrumentTemplate({
              owner: personAttrs(),
            }),
          }),
        ],
        [
          "Instrument Template New Form",
          getSectionNames(<InstrumentTemplateNewRecordForm />, {
            activeResult: makeMockInstrumentTemplate({ id: null, globalId: null }),
          }),
        ],
        [
          "Mixed Batch Edit Form",
          getSectionNames(<MixedBatchForm records={new RsSet([makeMockSubSample(), makeMockContainer()])} />, {
            activeResult: null,
            batchEditingRecords: [makeMockSubSample(), makeMockContainer()],
          }),
        ],
      ]),
    );
  });
});
