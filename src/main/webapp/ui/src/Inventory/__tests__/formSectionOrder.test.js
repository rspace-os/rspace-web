/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../__mocks__/createObjectURL";
import "../../../__mocks__/matchMedia";
import React, { type Node } from "react";
import { render, cleanup, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import RsSet from "../../util/set";
import ContainerForm from "../Container/Form";
import ContainerNewRecordForm from "../Container/NewRecordForm";
import ContainerBatchForm from "../Container/BatchForm";
import SampleForm from "../Sample/Form";
import SampleNewRecordForm from "../Sample/NewRecordForm";
import SampleBatchForm from "../Sample/BatchForm";
import TemplateForm from "../Template/Form";
import TemplateNewRecordForm from "../Template/NewRecordForm";
import SubSampleForm from "../Subsample/Form";
import SubSampleBatchForm from "../Subsample/BatchForm";
import MixedBatchForm from "../Mixed/BatchForm";
import { makeMockRootStore } from "../../stores/stores/__tests__/RootStore/mocking";
import { makeMockContainer } from "../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSample } from "../../stores/models/__tests__/SampleModel/mocking";
import { makeMockTemplate } from "../../stores/models/__tests__/TemplateModel/mocking";
import { makeMockSubSample } from "../../stores/models/__tests__/SubSampleModel/mocking";
import { storesContext } from "../../stores/stores-context";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import SynchroniseFormSections from "../components/Stepper/SynchroniseFormSections";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { assertConsistentOrderOfLists } from "../../__tests__/assertConsistentOrderOfLists";
import { personAttrs } from "../../stores/models/__tests__/PersonModel/mocking";
import { IsValid } from "../../components/ValidatingSubmitButton";

class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

jest.mock("../Sample/Content/SubsampleListing", () =>
  jest.fn(() => <div></div>)
);
jest.mock("../Container/Content/Content", () => jest.fn(() => <div></div>));
jest.mock("../Template/Fields/SamplesList", () => jest.fn(() => <div></div>));
jest.mock("../components/Fields/Attachments/Attachments", () =>
  jest.fn(() => <div></div>)
);
jest.mock("../components/Fields/ExtraFields/ExtraFields", () =>
  jest.fn(() => <div></div>)
);
jest.mock("../components/ContextMenu/ContextMenu", () =>
  jest.fn(() => <div></div>)
);
jest.mock("../Sample/Fields/Template/Template", () =>
  jest.fn(() => <div></div>)
);
jest.mock("../Sample/Fields/Quantity", () => jest.fn(() => <div></div>));
jest.mock("../../common/InvApiService", () => ({
  get: () => ({}),
  query: () => ({}),
}));
jest.mock("../../stores/stores/RootStore", () => () => ({
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
}));
jest.mock("../../components/Ketcher/KetcherDialog", () =>
  jest.fn(() => <div></div>)
);

window.fetch = jest.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(),
  })
);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

type MakeRootStoreArgs = {|
  activeResult: ?InventoryRecord,
  batchEditingRecords?: Array<InventoryRecord>,
|};

function makeRootStore({
  activeResult,
  batchEditingRecords,
}: MakeRootStoreArgs) {
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

function getSectionNames(
  reactComponent: Node,
  rootStore: MakeRootStoreArgs
): Array<string> {
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={makeRootStore(rootStore)}>
        <SynchroniseFormSections>{reactComponent}</SynchroniseFormSections>
      </storesContext.Provider>
    </ThemeProvider>
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
    window.scrollTo = () => {};

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
          getSectionNames(
            <ContainerBatchForm records={new RsSet([makeMockContainer()])} />,
            { activeResult: null, batchEditingRecords: [makeMockContainer()] }
          ),
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
          getSectionNames(
            <SampleBatchForm records={new RsSet([makeMockSample()])} />,
            { activeResult: null, batchEditingRecords: [makeMockSample()] }
          ),
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
          getSectionNames(
            <SubSampleBatchForm records={new RsSet([makeMockSubSample()])} />,
            { activeResult: null, batchEditingRecords: [makeMockSubSample()] }
          ),
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
          "Mixed Batch Edit Form",
          getSectionNames(
            <MixedBatchForm
              records={new RsSet([makeMockSubSample(), makeMockContainer()])}
            />,
            {
              activeResult: null,
              batchEditingRecords: [makeMockSubSample(), makeMockContainer()],
            }
          ),
        ],
      ])
    );
  });
});
