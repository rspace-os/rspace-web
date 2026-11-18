import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import { afterEach, beforeEach, describe, expect, onTestFinished, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import DefaultSearch from "../DefaultSearch";
import { RECORD_LIST_SHOWN, SEARCH_INPUT_ID, VIEW_MODE_BUTTON_ID } from "../workspaceGlobals";
import { toSearchQuery } from "../workspaceSearchUrl";

const KEY = "WORKSPACE_DEFAULT_SEARCH";

const searchSettings = {
  searchMode: true,
  options: ["name"],
  terms: ["Zebrafish"],
  advancedSearch: false,
  operator: "AND",
  sharedFilter: false,
  favoritesFilter: false,
  viewableItemsFilter: false,
  templatesFilter: false,
  ontologiesFilter: false,
  orderBy: null,
  sortOrder: "DESC",
};
const ZEBRAFISH = toSearchQuery(searchSettings);
const BKMKTAG = toSearchQuery({ ...searchSettings, options: ["tag"], terms: ["bkmktag"] });

let savedPreferences: Array<Record<string, { value: unknown }>>;

/** Each read returns the next of `preferences`, and the last one after that. FAILED_READ fails that read. */
const FAILED_READ = "failed read";
function givenStoredPreferences(...preferences: Array<Record<string, unknown> | typeof FAILED_READ>) {
  let reads = 0;
  server.use(
    http.get("/userform/ajax/preference", () => {
      const next = preferences[Math.min(reads++, preferences.length - 1)];
      return next === FAILED_READ ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(next);
    }),
    http.post("/userform/ajax/preference", async ({ request }) => {
      const form = await request.formData();
      savedPreferences.push(JSON.parse(form.get("value") as string));
      return HttpResponse.json({});
    }),
  );
}

function givenWorkspace(settings: Partial<typeof searchSettings>) {
  Object.assign(window, { workspaceSettings: { ...searchSettings, ...settings } });
}

const runButton = () => screen.queryByRole("button", { name: "workspace:toolbar.defaultSearch.run" });

describe("DefaultSearch", () => {
  beforeEach(() => {
    savedPreferences = [];
    const slot = document.createElement("span");
    slot.id = "defaultSearchSlot";
    document.body.appendChild(slot);
  });

  afterEach(() => {
    document.getElementById("defaultSearchSlot")?.remove();
    delete (window as { workspaceSettings?: unknown }).workspaceSettings;
  });

  test("with no default there is no toolbar button, and a search offers to save", async () => {
    givenStoredPreferences({});
    givenWorkspace({});
    render(<DefaultSearch />);
    expect(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" })).toBeVisible();
    expect(runButton()).not.toBeInTheDocument();
  });

  test("outside search mode the search bar slot is empty", async () => {
    givenStoredPreferences({ [KEY]: { value: ZEBRAFISH } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);
    await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" });
    expect(document.getElementById("defaultSearchSlot")).toBeEmptyDOMElement();
  });

  test("saving with no default stores the search and keeps the other preferences", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({ OTHER: { value: "kept" } });
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));

    expect(await screen.findByText("workspace:toolbar.defaultSearch.isDefault")).toBeVisible();
    expect(runButton()).toBeVisible();
    await vi.waitFor(() => expect(savedPreferences).toHaveLength(1));
    expect(savedPreferences[0][KEY].value).toBe(ZEBRAFISH);
    expect(savedPreferences[0].OTHER.value).toBe("kept");
  });

  test("saving over a different default asks first, and cancelling keeps the old default", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/bkmktag/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));

    expect(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" })).toBeVisible();
    expect(savedPreferences).toHaveLength(0);
  });

  test("confirming the replacement stores the new default", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: "workspace:toolbar.defaultSearch.replaceConfirm",
      }),
    );

    expect(await screen.findByText("workspace:toolbar.defaultSearch.isDefault")).toBeVisible();
    await vi.waitFor(() => expect(savedPreferences).toHaveLength(1));
    expect(savedPreferences[0][KEY].value).toBe(ZEBRAFISH);
  });

  test("saving asks first when another tab has since stored a default", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({}, { [KEY]: { value: BKMKTAG } });
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/bkmktag/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));

    expect(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" })).toBeVisible();
    expect(savedPreferences).toHaveLength(0);
  });

  test("removing asks first when another tab has since stored a different default", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({ [KEY]: { value: ZEBRAFISH } }, { [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" }));
    await user.click(await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/bkmktag/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));

    expect(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" })).toBeVisible();
    expect(savedPreferences).toHaveLength(0);
  });

  test("saving with the keyboard moves focus to the run button and announces it", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({});
    givenWorkspace({});
    const { baseElement } = render(<DefaultSearch />);

    (await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" })).focus();
    await user.keyboard("{Enter}");

    await vi.waitFor(() => expect(runButton()).toHaveFocus());
    expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.saved");
    await expectAccessible(baseElement);
  });

  test("removing with the keyboard moves focus to the search box and announces it", async () => {
    const user = userEvent.setup();
    const searchBox = document.createElement("input");
    searchBox.id = SEARCH_INPUT_ID;
    searchBox.setAttribute("aria-label", "search");
    document.body.appendChild(searchBox);
    onTestFinished(() => searchBox.remove());
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    const { baseElement } = render(<DefaultSearch />);

    (await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" })).focus();
    await user.keyboard("{Enter}");
    await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" });
    await expectAccessible(baseElement);
    await user.keyboard("{Enter}");

    await vi.waitFor(() => expect(searchBox).toHaveFocus());
    expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.removed");
  });

  test("removing with the keyboard in tree view, with no search box, moves focus to the View mode button", async () => {
    const user = userEvent.setup();
    const viewModeButton = document.createElement("button");
    viewModeButton.id = VIEW_MODE_BUTTON_ID;
    viewModeButton.textContent = "View mode";
    document.body.appendChild(viewModeButton);
    onTestFinished(() => viewModeButton.remove());
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    (await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" })).focus();
    await user.keyboard("{Enter}");
    await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" });
    await user.keyboard("{Enter}");

    await vi.waitFor(() => expect(viewModeButton).toHaveFocus());
    expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.removed");
  });

  test("removing a default another tab already removed announces it and moves focus to the search box", async () => {
    const user = userEvent.setup();
    const searchBox = document.createElement("input");
    searchBox.id = SEARCH_INPUT_ID;
    document.body.appendChild(searchBox);
    onTestFinished(() => searchBox.remove());
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } }, {});
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" }));
    await user.click(await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" }));

    await vi.waitFor(() => expect(searchBox).toHaveFocus());
    expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.removed");
    expect(savedPreferences).toHaveLength(0);
  });

  test("saving a search another tab already saved announces it and moves focus to the run button", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({}, { [KEY]: { value: ZEBRAFISH } });
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));

    await vi.waitFor(() => expect(runButton()).toHaveFocus());
    expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.saved");
    expect(savedPreferences).toHaveLength(0);
  });

  test("a save the server rejects keeps the old state and says it failed", async () => {
    const user = userEvent.setup();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    onTestFinished(() => warn.mockRestore());
    givenStoredPreferences({});
    server.use(http.post("/userform/ajax/preference", () => new HttpResponse(null, { status: 500 })));
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));

    await vi.waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.saveFailed"),
    );
    expect(screen.getByRole("button", { name: "workspace:toolbar.defaultSearch.save" })).toBeEnabled();
    expect(runButton()).not.toBeInTheDocument();
  });

  test("a remove the server rejects keeps the default and says it failed", async () => {
    const user = userEvent.setup();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    onTestFinished(() => warn.mockRestore());
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    server.use(http.post("/userform/ajax/preference", () => new HttpResponse(null, { status: 500 })));
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" }));
    await user.click(await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" }));

    await vi.waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.removeFailed"),
    );
    expect(runButton()).toBeEnabled();
  });

  test("a save whose check of the stored default fails stores nothing and says it failed", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({}, FAILED_READ, { [KEY]: { value: BKMKTAG } });
    givenWorkspace({});
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" }));

    await vi.waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.saveFailed"),
    );
    expect(savedPreferences).toHaveLength(0);
  });

  test("a remove whose check of the stored default fails keeps it and says it failed", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({ [KEY]: { value: ZEBRAFISH } }, FAILED_READ, { [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" }));
    await user.click(await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" }));

    await vi.waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("workspace:toolbar.defaultSearch.removeFailed"),
    );
    expect(savedPreferences).toHaveLength(0);
    expect(runButton()).toBeEnabled();
  });

  test("the buttons are disabled until the server has saved", async () => {
    const user = userEvent.setup();
    const pending: { finishSaving?: () => void } = {};
    givenStoredPreferences({});
    server.use(
      http.post(
        "/userform/ajax/preference",
        () =>
          new Promise<Response>((resolve) => {
            pending.finishSaving = () => resolve(HttpResponse.json({}));
          }),
      ),
    );
    givenWorkspace({});
    render(<DefaultSearch />);

    const save = await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" });
    await user.click(save);

    await vi.waitFor(() => expect(pending.finishSaving).toBeDefined());
    expect(save).toBeDisabled();
    expect(screen.queryByText("workspace:toolbar.defaultSearch.isDefault")).not.toBeInTheDocument();
    pending.finishSaving?.();
    expect(await screen.findByText("workspace:toolbar.defaultSearch.isDefault")).toBeVisible();
  });

  test("clicking Default search opens the stored search", async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch navigate={navigate} />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" }));

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(`/workspace?${BKMKTAG}`));
  });

  test("clicking Default search opens the default another tab has since saved", async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } }, { [KEY]: { value: ZEBRAFISH } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch navigate={navigate} />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" }));

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(`/workspace?${ZEBRAFISH}`));
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  test("clicking Default search opens the loaded default when the check fails", async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } }, FAILED_READ);
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch navigate={navigate} />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" }));

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(`/workspace?${BKMKTAG}`));
  });

  test("clicking Default search after another tab removed it opens nothing and hides the button", async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } }, {});
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch navigate={navigate} />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" }));

    await vi.waitFor(() => expect(runButton()).not.toBeInTheDocument());
    expect(navigate).not.toHaveBeenCalled();
  });

  test("removing the default stores null and hides the button", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.options" }));
    await user.click(await screen.findByRole("menuitem", { name: "workspace:toolbar.defaultSearch.remove" }));

    await vi.waitFor(() => expect(runButton()).not.toBeInTheDocument());
    await vi.waitFor(() => expect(savedPreferences).toHaveLength(1));
    expect(savedPreferences[0][KEY].value).toBeNull();
  });

  test("a stored term with markup shows as text in the tooltip", async () => {
    const user = userEvent.setup();
    givenStoredPreferences({
      [KEY]: { value: toSearchQuery({ ...searchSettings, terms: ["<script>alert(1)</script>"] }) },
    });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch />);

    await user.hover(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" }));

    expect((await screen.findByRole("tooltip")).textContent).toContain("<script>alert(1)</script>");
    expect(document.querySelector("script")).toBeNull();
  });

  test.each([
    ["no default", {}],
    ["a default", { [KEY]: { value: BKMKTAG } }],
  ])("is accessible with %s", async (_, preferences) => {
    givenStoredPreferences(preferences);
    givenWorkspace({});
    const { baseElement } = render(<DefaultSearch />);
    await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" });
    await expectAccessible(baseElement);
  });

  test("a search that found nothing still offers to save", async () => {
    givenStoredPreferences({});
    givenWorkspace({ terms: ["nothing-matches-this"] });
    render(<DefaultSearch />);
    expect(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" })).toBeVisible();
  });

  test("the bar follows a new search shown by the legacy page", async () => {
    givenStoredPreferences({ [KEY]: { value: ZEBRAFISH } });
    givenWorkspace({});
    render(<DefaultSearch />);
    expect(await screen.findByText("workspace:toolbar.defaultSearch.isDefault")).toBeVisible();

    givenWorkspace({ options: ["tag"], terms: ["bkmktag"] });
    window.dispatchEvent(new CustomEvent(RECORD_LIST_SHOWN));

    expect(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.save" })).toBeVisible();
  });
});
