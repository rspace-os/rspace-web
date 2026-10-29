/*
 * @vitest-environment jsdom
 */
import { describe, expect, test, vi } from "vitest";
// not an auto-mock: a side-effect polyfill that must run before tinymce loads
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { IsValid } from "../../../components/ValidatingSubmitButton";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { makeMockInstrument } from "../../../stores/models/__tests__/InstrumentModel/mocking";
import { personAttrs } from "../../../stores/models/__tests__/PersonModel/mocking";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../stores/stores-context";
import materialTheme from "../../../theme";
import SynchroniseFormSections from "../../components/Stepper/SynchroniseFormSections";
import InstrumentForm from "../Form";
import InstrumentNewRecordForm from "../NewRecordForm";

class ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

vi.mock("../../components/Fields/Attachments/Attachments", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../../components/Fields/ExtraFields/ExtraFields", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../../components/ContextMenu/ContextMenu", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../Fields/InstrumentTemplateField", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../Fields/TemplateFields", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
    query: () => ({}),
  },
}));
vi.mock("../../../stores/stores/getRootStore", () => ({
  default: () => ({
    searchStore: {
      savedSearches: [{ name: "Dummy saved search", query: "foo" }],
    },
    uiStore: {
      addAlert: () => {},
    },
  }),
}));

function renderForm(FormComponent: () => ReactNode, activeResult: InventoryRecord) {
  const rootStore = makeMockRootStore({
    searchStore: {
      activeResult,
      fetcher: {
        generateNewQuery: () => "foo",
      },
      search: {
        activeResult,
        processingContextActions: false,
        fetcher: {
          basketSearch: false,
        },
        batchEditableInstance: { loading: false, submittable: IsValid() },
      },
    },
  });
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SynchroniseFormSections>
          <FormComponent />
        </SynchroniseFormSections>
      </storesContext.Provider>
    </ThemeProvider>,
  );
}

describe("Instrument forms' Access Permissions section", () => {
  window.ResizeObserver = ResizeObserver;
  window.scrollTo = vi.fn();

  test("is shown when viewing an instrument with full access", () => {
    renderForm(InstrumentForm, makeMockInstrument({ owner: personAttrs() }));

    expect(screen.getByRole("region", { name: "inventory:formSections.accessPermissions" })).toBeInTheDocument();
  });

  test("is not shown when viewing an instrument with limited access", () => {
    renderForm(InstrumentForm, makeMockInstrument({ owner: personAttrs(), permittedActions: ["LIMITED_READ"] }));

    expect(screen.queryByRole("region", { name: "inventory:formSections.accessPermissions" })).not.toBeInTheDocument();
  });

  test("is shown when creating a new instrument", () => {
    renderForm(InstrumentNewRecordForm, makeMockInstrument({ id: null, globalId: null }));

    expect(screen.getByRole("region", { name: "inventory:formSections.accessPermissions" })).toBeInTheDocument();
  });
});
