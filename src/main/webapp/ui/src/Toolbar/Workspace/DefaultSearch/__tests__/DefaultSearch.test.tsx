import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import DefaultSearch from "../DefaultSearch";
import { RECORD_LIST_SHOWN } from "../workspaceGlobals";
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

function givenStoredPreferences(preferences: Record<string, unknown>) {
  server.use(
    http.get("/userform/ajax/preference", () => HttpResponse.json(preferences)),
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

  test("clicking Default search opens the stored search", async () => {
    const user = userEvent.setup();
    const navigate = vi.fn();
    givenStoredPreferences({ [KEY]: { value: BKMKTAG } });
    givenWorkspace({ searchMode: false });
    render(<DefaultSearch navigate={navigate} />);

    await user.click(await screen.findByRole("button", { name: "workspace:toolbar.defaultSearch.run" }));

    expect(navigate).toHaveBeenCalledWith(`/workspace?${BKMKTAG}`);
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
    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible();
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
