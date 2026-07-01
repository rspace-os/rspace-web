import { afterEach, beforeEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { render, screen, waitFor, within } from "@/__tests__/customQueries";
import { stubAppChrome } from "@/__tests__/helpers/appChrome";
import axios from "@/common/axios";
import { ImportDialogStory } from "./ImportDialog.story";

const mockAxios = new MockAdapter(axios);

const aspirin = {
  name: "Aspirin",
  pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
  smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
  formula: "C9H8O4",
  pubchemId: "2244",
  pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
  cas: "50-78-2",
};

const paracetamol = {
  name: "Paracetamol",
  pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=1983&t=l",
  smiles: "CC(=O)NC1=CC=C(O)C=C1",
  formula: "C8H9NO2",
  pubchemId: "1983",
  pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/1983",
  cas: "103-90-2",
};

const compoundWithEmptyCas = {
  name: "Compound with Empty CAS",
  pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=5555&t=l",
  smiles: "C1=CC=CC=C1",
  formula: "C6H6",
  pubchemId: "5555",
  pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/5555",
  cas: "",
};

/**
 * Mirrors the spec's `router.route("/api/v1/pubchem/search", ...)` stub: the
 * response is selected based on the `searchTerm` in the POST body.
 */
function stubEndpoints() {
  mockAxios.reset();
  stubAppChrome(mockAxios, {
    visibleTabs: { published: false, system: false },
  });
  mockAxios.onPost("/api/v1/pubchem/search").reply((config) => {
    const body = (config.data as string | undefined) ?? "{}";
    const requestData = JSON.parse(body) as {
      searchTerm?: string;
    };
    const searchTerm = requestData.searchTerm;
    if (searchTerm === "error") {
      return [500, { message: "There was an error" }];
    }
    if (searchTerm === "multiple") {
      return [200, [aspirin, paracetamol]];
    }
    if (searchTerm === "nocas") {
      return [200, [compoundWithEmptyCas]];
    }
    if (searchTerm === "noresults") {
      return [200, []];
    }
    return [200, [aspirin]];
  });
  // Any other request fired during the AppBar bootstrap should resolve cleanly
  // rather than reject and produce console noise.
  mockAxios.onAny().reply(200, {});
}

async function performSearch(user: ReturnType<typeof userEvent.setup>, term: string) {
  const searchInput = screen.getByRole("textbox");
  // Paste the whole term in one step. This controlled MUI input shares its form
  // with a Select adornment, so pasting is more reliable here than per-key typing.
  await user.clear(searchInput);
  await user.click(searchInput);
  await user.paste(term);
  await user.click(screen.getByRole("button", { name: "common:actions.search" }));
}

describe("ImportDialog", () => {
  beforeEach(() => {
    stubEndpoints();
  });

  afterEach(() => {
    mockAxios.reset();
  });

  test("Renders correctly", () => {
    render(<ImportDialogStory />);
    expect(screen.getByRole("dialog")).toBeVisible();
  });

  test("Should be a dialog header banner", async () => {
    render(<ImportDialogStory />);
    const dialogHeader = await screen.findByRole("banner", {
      name: "common:appBar.dialogHeader",
    });
    expect(dialogHeader).toBeVisible();
    expect(dialogHeader).toHaveTextContent("apps:tinyMce.pubchem.dialog.title");
  });

  test("Should have a title", () => {
    render(<ImportDialogStory />);
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAccessibleName("common:pubchemImport.title");
    const title = within(dialog).getByRole("heading", { level: 3 });
    expect(title).toBeVisible();
    expect(title).toHaveTextContent("common:pubchemImport.title");
  });

  test("Should have a close button", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBe(null);
    });
  });

  test("Should have a search input", () => {
    render(<ImportDialogStory />);
    expect(screen.getByRole("textbox")).toBeVisible();
  });

  test("Should have a search type selector", () => {
    render(<ImportDialogStory />);
    expect(screen.getByRole("combobox", { name: "apps:tinyMce.pubchem.dialog.searchTypeLabel" })).toBeVisible();
  });

  test("The API endpoint is called when a search is performed", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "aspirin");
    const searchResults = await screen.findByRole("region", {
      name: "apps:tinyMce.pubchem.dialog.searchResults",
    });
    await waitFor(() => {
      expect(searchResults).toHaveTextContent(/Aspirin/);
    });
  });

  test("searchType is passed in API call", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    // choose SMILES as the search type
    await user.click(screen.getByRole("combobox", { name: "apps:tinyMce.pubchem.dialog.searchTypeLabel" }));
    await user.click(screen.getByRole("option", { name: "apps:tinyMce.pubchem.dialog.searchTypes.smiles" }));
    await performSearch(user, "aspirin");
    await waitFor(() => {
      const searchRequest = mockAxios.history.post.find(
        ({ url, data }) =>
          url === "/api/v1/pubchem/search" && ((data as string | undefined) ?? "").includes('"searchType":"SMILES"'),
      );
      expect(searchRequest).toBeDefined();
    });
  });

  test("Should auto-select a compound when there is only one result", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "aspirin");
    const checkbox = await screen.findByRole("checkbox", {
      name: "apps:tinyMce.pubchem.dialog.selectCompoundAria",
    });
    expect(checkbox).toBeChecked();
    /*
     * The vast majority of the time there will only be one result, so
     * auto-selecting it is a small usability improvement that reduces
     * the friction to inserting compounds, especially when paired with
     * the slash menu command.
     */
  });

  test("Should not auto-select compounds when there are multiple results", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "multiple");
    await waitFor(() => {
      expect(screen.getAllByRole("checkbox", { name: "apps:tinyMce.pubchem.dialog.selectCompoundAria" }).length).toBe(
        2,
      );
    });
    const checkboxes = screen.getAllByRole("checkbox", {
      name: "apps:tinyMce.pubchem.dialog.selectCompoundAria",
    });
    for (const checkbox of checkboxes) {
      expect(checkbox).not.toBeChecked();
    }
  });

  test("Should toggle compound selection when clicked twice", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "multiple");
    const firstCheckbox = (
      await screen.findAllByRole("checkbox", {
        name: "apps:tinyMce.pubchem.dialog.selectCompoundAria",
      })
    )[0];
    await user.click(firstCheckbox);
    await waitFor(() => {
      expect(firstCheckbox).toBeChecked();
    });
    await user.click(firstCheckbox);
    await waitFor(() => {
      expect(firstCheckbox).not.toBeChecked();
    });
  });

  test("Should validate compound selection", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "multiple");
    await screen.findAllByRole("checkbox", { name: "apps:tinyMce.pubchem.dialog.selectCompoundAria" });

    // clicking import without selecting any compounds shows a validation warning
    await user.click(screen.getByRole("button", { name: "common:pubchemImport.importSelected" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toBeVisible();
    expect(alert).toHaveTextContent("apps:tinyMce.pubchem.dialog.validation.selectCompound");

    // pressing escape dismisses the validation warning
    await user.keyboard("{Escape}");

    // selecting a compound clears the warning
    await user.click(
      (await screen.findAllByRole("checkbox", { name: "apps:tinyMce.pubchem.dialog.selectCompoundAria" }))[0],
    );
    await waitFor(() => {
      expect(screen.queryByRole("alert")).toBe(null);
    });
  });

  test("An error when searching should result in an alert toast", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "error");
    const alert = await screen.findByRole("alert");
    expect(alert).toBeVisible();
    expect(alert).toHaveTextContent(/There was an error/);
  });

  test("Should reset state when dialog is closed and reopened", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "multiple");
    await user.click((await screen.findAllByRole("checkbox", { name: /select/i }))[0]);

    // close the dialog
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBe(null);
    });

    // reopen the dialog via the story's Open button
    await user.click(screen.getByRole("button", { name: /open/i }));
    await screen.findByRole("dialog");

    // search input should be present and empty, with no results
    const searchInput = screen.getByRole("textbox");
    expect(searchInput).toBeVisible();
    expect(searchInput).toHaveValue("");

    const resultsSection = screen.getByRole("region", {
      name: "apps:tinyMce.pubchem.dialog.searchResults",
    });
    expect(resultsSection).toBeVisible();
    expect(screen.queryAllByRole("checkbox", { name: /select/i })).toHaveLength(0);
  });

  test("Should not display CAS Number when it is empty", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "nocas");

    const compoundCard = await screen.findByRole("region", {
      name: "Compound with Empty CAS",
    });
    expect(compoundCard).toBeVisible();

    const terms = within(compoundCard).getAllByRole("term");
    expect(terms.some((t) => (t.textContent ?? "") === "apps:tinyMce.pubchem.pubchemId")).toBe(true);
    expect(terms.some((t) => (t.textContent ?? "") === "apps:tinyMce.pubchem.formula")).toBe(true);
    expect(terms.some((t) => (t.textContent ?? "") === "apps:tinyMce.pubchem.casNumber")).toBe(false);
  });

  test("Should hide error message when typing after a no-results search", async () => {
    const user = userEvent.setup();
    render(<ImportDialogStory />);
    await performSearch(user, "noresults");
    expect(await screen.findByText("apps:tinyMce.pubchem.dialog.emptyState.noneFound")).toBeVisible();

    // modifying the search term hides the no-results message
    await user.type(screen.getByRole("textbox"), "modified");
    await waitFor(() => {
      expect(screen.queryByText("apps:tinyMce.pubchem.dialog.emptyState.noneFound")).toBe(null);
    });
  });
});
