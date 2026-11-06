/*
 * @vitest-environment jsdom
 */
import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { server } from "../../../__tests__/mswServer";
import { IsValid } from "../../../components/ValidatingSubmitButton";
import { makeMockTemplate } from "../../../stores/models/__tests__/TemplateModel/mocking";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../stores/stores-context";
import materialTheme from "../../../theme";
import SynchroniseFormSections from "../../components/Stepper/SynchroniseFormSections";
import TemplateForm from "../Form";

vi.mock("../../components/Fields/AccessPermissions", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../../components/Fields/Image", () => ({
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
    uiStore: {
      addAlert: () => {},
      setPageNavigationConfirmation: () => {},
      setDirty: () => {},
    },
    unitStore: {
      getUnit: () => ({ id: 1, label: "items", category: "dimensionless", description: "" }),
    },
  }),
}));

class ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

function renderTemplateForm() {
  const activeResult = makeMockTemplate();
  activeResult.editing = true;
  activeResult.lastEditInput = new Date();
  activeResult.currentlyEditableFields.add("fields");
  const rootStore = makeMockRootStore({
    searchStore: {
      activeResult,
      search: {
        activeResult,
        processingContextActions: false,
        fetcher: { basketSearch: false },
        batchEditableInstance: { loading: false, submittable: IsValid() },
      },
    },
    unitStore: {
      getUnit: () => ({ id: 1, label: "items", category: "dimensionless", description: "" }),
      unitsOfCategory: () => [],
    },
  });
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <SynchroniseFormSections>
          <TemplateForm />
        </SynchroniseFormSections>
      </storesContext.Provider>
    </ThemeProvider>,
  );
}

describe("Template Form", () => {
  window.ResizeObserver = ResizeObserver;
  window.scrollTo = vi.fn();

  test("the collapsed Custom Fields section counts each new field left with a blank name", async () => {
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", () => HttpResponse.json({})),
    );
    const user = userEvent.setup();
    renderTemplateForm();
    const section = screen.getByRole("region", { name: "inventory:formSections.customFields" });
    const collapsedErrorCount = async () => {
      await user.click(within(section).getByRole("button", { name: "inventory:formSections.collapseSection" }));
      const expand = await within(section).findByRole("button", { name: "inventory:formSections.expandSection" });
      return expand.textContent;
    };
    const expandSection = () =>
      user.click(within(section).getByRole("button", { name: "inventory:formSections.expandSection" }));

    await expandSection();
    const addNewField = within(section).getByRole("button", { name: "inventory:fields.extraFields.addNewField" });
    await user.click(addNewField);
    await user.click(addNewField);
    expect(await collapsedErrorCount()).toBe("2");

    await expandSection();
    const [deleteFirst] = within(section).getAllByRole("button", {
      name: "inventory:fields.templateFields.customField.deleteNewField",
    });
    await user.click(deleteFirst);
    expect(await collapsedErrorCount()).toBe("1");
  });
});
