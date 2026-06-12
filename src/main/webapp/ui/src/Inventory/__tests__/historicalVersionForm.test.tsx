/*
 * @vitest-environment jsdom
 */
import { afterEach, describe, expect, test, vi } from "vitest";
// not an auto-mock: a side-effect polyfill that must run before tinymce loads
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/matchMedia";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ThemeProvider } from "@mui/material/styles";
import { IsValid } from "../../components/ValidatingSubmitButton";
import type { InventoryRecord } from "../../stores/definitions/InventoryRecord";
import { makeMockContainer } from "../../stores/models/__tests__/ContainerModel/mocking";
import { personAttrs } from "../../stores/models/__tests__/PersonModel/mocking";
import { makeMockRootStore } from "../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../stores/stores-context";
import materialTheme from "../../theme";
import ContainerForm from "../Container/Form";
import SynchroniseFormSections from "../components/Stepper/SynchroniseFormSections";

class ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

vi.mock("../Container/Content/Content", () => ({
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
vi.mock("../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
    query: () => ({}),
  },
}));
vi.mock("../../stores/stores/RootStore", () => ({
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

function renderContainerForm(activeResult: InventoryRecord) {
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
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SynchroniseFormSections>
          <ContainerForm />
        </SynchroniseFormSections>
      </storesContext.Provider>
    </ThemeProvider>,
  );
}

describe("Container Form and historical versions", () => {
  window.ResizeObserver = ResizeObserver;
  window.scrollTo = vi.fn();

  afterEach(cleanup);

  test("the Locations and Content section is hidden for a historical version", () => {
    renderContainerForm(
      makeMockContainer({
        owner: personAttrs(),
        version: 1,
        historicalVersion: true,
      }),
    );

    // locations are not audited, so a snapshot has no contents to show
    expect(screen.queryByRole("heading", { name: /locations and content/i })).not.toBeInTheDocument();
    // the sticky alert explains why
    expect(screen.getByText(/contents are not part of/i)).toBeInTheDocument();
  });

  test("the Locations and Content section is shown for a live container", () => {
    renderContainerForm(makeMockContainer({ owner: personAttrs() }));

    expect(screen.getByRole("heading", { name: /locations and content/i })).toBeInTheDocument();
  });
});
