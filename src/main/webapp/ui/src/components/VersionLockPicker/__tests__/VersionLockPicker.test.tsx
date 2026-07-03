import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import materialTheme from "../../../theme";
import VersionLockPicker, { LATEST_SELECTION, type VersionRecord } from "../VersionLockPicker";

const revisions: VersionRecord[] = [
  { version: 1, revisionId: 11, modificationDate: "2024-01-01T00:00:00Z" },
  { version: 2, revisionId: 22, modificationDate: "2024-02-01T00:00:00Z" },
  { version: 3, revisionId: 33, modificationDate: "2024-03-01T00:00:00Z" },
];

function renderComponent(props: {
  currentSelection: number | typeof LATEST_SELECTION;
  onChange: (s: number | typeof LATEST_SELECTION) => void;
}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <VersionLockPicker
        recordId={42}
        currentSelection={props.currentSelection}
        fetchVersions={() => Promise.resolve(revisions)}
        onChange={props.onChange}
      />
    </ThemeProvider>,
  );
}

describe("VersionLockPicker", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders a row for each revision and a 'latest' row", async () => {
    renderComponent({ currentSelection: LATEST_SELECTION, onChange: vi.fn() });
    const versionRows = await screen.findAllByText("common:versionLockPicker.versionValue");

    expect(versionRows).toHaveLength(revisions.length);
    versionRows.forEach((row) => {
      expect(row).toBeInTheDocument();
    });
    expect(screen.getByText("common:versionLockPicker.latest")).toBeInTheDocument();
  });

  it("calls onChange with the version number when a row is selected", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderComponent({ currentSelection: LATEST_SELECTION, onChange });

    const row = (await screen.findAllByText("common:versionLockPicker.versionValue"))[1];
    await user.click(row);

    expect(onChange).toHaveBeenCalledWith(2);
  });

  it("calls onChange with LATEST_SELECTION when the latest row is selected", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderComponent({ currentSelection: 1, onChange });

    const latestRow = await screen.findByText("common:versionLockPicker.latest");
    await user.click(latestRow);

    expect(onChange).toHaveBeenCalledWith(LATEST_SELECTION);
  });

  it("marks the current selection as the checked radio", async () => {
    renderComponent({ currentSelection: 2, onChange: vi.fn() });

    // wait for rows to load
    await screen.findAllByText("common:versionLockPicker.versionValue");
    const radios = screen.getAllByRole("radio");
    const checkedRadios = radios.filter((r) => (r as HTMLInputElement).checked);
    expect(checkedRadios).toHaveLength(1);
  });

  it("degrades to the latest-only view when fetchVersions rejects", async () => {
    // the fetchVersions contract does not promise to never reject; a failing
    // versions endpoint must leave the picker in a known state (no version
    // rows) without an unhandled promise rejection escaping the component
    const unhandled: unknown[] = [];
    const onUnhandled = (reason: unknown) => {
      unhandled.push(reason);
    };
    process.on("unhandledRejection", onUnhandled);
    try {
      render(
        <ThemeProvider theme={materialTheme}>
          <VersionLockPicker
            recordId={42}
            currentSelection={LATEST_SELECTION}
            fetchVersions={() => Promise.reject(new Error("versions endpoint down"))}
            onChange={vi.fn()}
          />
        </ThemeProvider>,
      );

      expect(await screen.findByText("common:versionLockPicker.latest")).toBeInTheDocument();
      // let the rejection cross a macrotask boundary so node's
      // unhandled-rejection detection has run
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(screen.queryByText(/version \d/i)).not.toBeInTheDocument();
      expect(unhandled).toHaveLength(0);
    } finally {
      process.off("unhandledRejection", onUnhandled);
    }
  });
});
