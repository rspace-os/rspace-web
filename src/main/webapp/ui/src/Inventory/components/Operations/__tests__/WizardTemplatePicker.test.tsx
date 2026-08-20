import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import WizardTemplatePicker from "../WizardTemplatePicker";

// A controllable stand-in for the Search model: `results` feeds the Autocomplete options and
// `performInitialSearch` records the (server) queries the picker fires. The picker is `observer`, but
// `results` is set before render so the initial render already sees it (no reactive update needed).
const state = vi.hoisted(() => ({
  results: [] as Array<{ id: number; name: string; globalId: string }>,
  loading: false,
}));
const performInitialSearch = vi.hoisted(() => vi.fn((_args: unknown) => Promise.resolve()));

vi.mock("@/stores/models/Search", () => ({
  default: class {
    get results() {
      return state.results;
    }
    fetcher = {
      performInitialSearch,
      get loading() {
        return state.loading;
      },
    };
  },
}));
vi.mock("@/stores/models/Factory/AlwaysNewFactory", () => ({ default: class {} }));

beforeEach(() => {
  performInitialSearch.mockClear();
  state.results = [
    { id: 5, name: "Cells", globalId: "IT5" },
    { id: 7, name: "Buffer", globalId: "IT7" },
  ];
  state.loading = false;
});

describe("WizardTemplatePicker", () => {
  it("fetches an initial list of templates when it opens", () => {
    render(<WizardTemplatePicker setTemplate={vi.fn()} />);
    expect(performInitialSearch).toHaveBeenCalled();
  });

  it("shows each template as an option with its name and its global id as plain (non-link) text", async () => {
    const user = userEvent.setup();
    render(<WizardTemplatePicker setTemplate={vi.fn()} />);
    await user.click(screen.getByRole("combobox"));
    const listbox = screen.getByRole("listbox");
    expect(within(listbox).getByText("Cells")).toBeInTheDocument();
    expect(within(listbox).getByText("IT5")).toBeInTheDocument();
    // the clickable open-in-new-window pill is gone: the option carries no link
    expect(within(listbox).queryByRole("link")).not.toBeInTheDocument();
  });

  it("reports the chosen template through setTemplate", async () => {
    const setTemplate = vi.fn();
    const user = userEvent.setup();
    render(<WizardTemplatePicker setTemplate={setTemplate} />);
    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByRole("option", { name: /Buffer/ }));
    expect(setTemplate).toHaveBeenCalledWith(expect.objectContaining({ id: 7, name: "Buffer" }));
  });

  it("re-queries the server as the user types and never treats typed text as a new template", async () => {
    const setTemplate = vi.fn();
    const user = userEvent.setup();
    render(<WizardTemplatePicker setTemplate={setTemplate} />);
    await user.type(screen.getByRole("combobox"), "Buf");
    await waitFor(() => expect(performInitialSearch).toHaveBeenCalledWith(expect.objectContaining({ query: "Buf" })));
    // typing alone selects nothing: the field is not free-solo
    expect(setTemplate).not.toHaveBeenCalled();
  });

  it("does not query the backend for a single character (it enforces a 2-character minimum)", async () => {
    const user = userEvent.setup();
    render(<WizardTemplatePicker setTemplate={vi.fn()} />);
    performInitialSearch.mockClear(); // ignore the initial-list fetch from mount
    await user.type(screen.getByRole("combobox"), "C");
    // wait past the debounce, then assert no 1-character query term was ever sent
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(performInitialSearch).not.toHaveBeenCalledWith(expect.objectContaining({ query: "C" }));
  });

  it("pre-fills the currently-selected template's name so a reopened picker starts on it", () => {
    render(<WizardTemplatePicker setTemplate={vi.fn()} selectedTemplateId={5} selectedTemplateName="Cells" />);
    expect(screen.getByRole("combobox")).toHaveValue("Cells");
  });
});
