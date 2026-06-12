import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

vi.mock("../InventoryInfoDialog", () => ({
  default: ({
    open,
    globalId,
    versionPin,
  }: {
    open: boolean;
    globalId: string;
    versionPin?: number | null;
  }) =>
    open ? (
      <div
        data-testid="inventory-info-dialog"
        data-globalid={globalId}
        data-version-pin={versionPin == null ? "" : String(versionPin)}
      />
    ) : null,
}));

vi.mock("../ElnRecordInfoDialog", () => ({
  default: ({
    open,
    globalId,
    versionPin,
  }: {
    open: boolean;
    globalId: string;
    versionPin?: number | null;
  }) =>
    open ? (
      <div
        data-testid="eln-info-dialog"
        data-globalid={globalId}
        data-version-pin={versionPin == null ? "" : String(versionPin)}
      />
    ) : null,
}));

const mockUseLinkTargetSummary = vi.hoisted(() => vi.fn());
vi.mock("../useLinkTargetSummary", () => ({
  default: (globalId: string) => mockUseLinkTargetSummary(globalId) as unknown,
}));

import LinkField from "../LinkField";

const baseLink = {
  relationType: "IsCalibratedBy",
  targetGlobalId: "SA42",
  versionPin: null as number | null,
};

function renderField(props: Partial<React.ComponentProps<typeof LinkField>> = {}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <LinkField
        name="Calibration cert"
        link={baseLink}
        onOpen={vi.fn()}
        onEdit={vi.fn()}
        editable={true}
        {...props}
      />
    </ThemeProvider>,
  );
}

describe("LinkField", () => {
  afterEach(cleanup);

  beforeEach(() => {
    // default: target state unknown (loading/unresolvable) -> no pill, Open on
    mockUseLinkTargetSummary.mockReset();
    mockUseLinkTargetSummary.mockReturnValue(null);
  });

  it("renders relation type, target global id, and name", () => {
    renderField();
    expect(screen.getByText(/calibration cert/i)).toBeInTheDocument();
    expect(screen.getByText(/iscalibratedby/i)).toBeInTheDocument();
    expect(screen.getByText(/sa42/i)).toBeInTheDocument();
  });


  it("shows a bold inline 'Link' heading and bold inline field name", () => {
    renderField();
    const heading = screen.getByText(/^link$/i);
    const name = screen.getByText(/^calibration cert$/i);
    // subtitle1 is the larger variant; both render bold within the link row
    expect(heading).toHaveClass("MuiTypography-subtitle1");
    expect(name).toHaveClass("MuiTypography-subtitle1");
    /* eslint-disable testing-library/no-node-access -- asserting both sit inline in the link row */
    expect(heading.closest('[data-test-id="LinkField-row"]')).toBeInTheDocument();
    expect(name.closest('[data-test-id="LinkField-row"]')).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access */
  });

  it("renders the Edit action inline", async () => {
    const onEdit = vi.fn();
    const user = userEvent.setup();
    renderField({ editable: true, onEdit });

    const editButton = screen.getByRole("button", { name: /edit link/i });
    /* eslint-disable testing-library/no-node-access -- asserting Edit sits inline in the link row */
    expect(editButton.closest('[data-test-id="LinkField-row"]')).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access */

    await user.click(editButton);
    expect(onEdit).toHaveBeenCalledTimes(1);
  });

  it("renders a plain Open action inline with the link chips", async () => {
    const onOpen = vi.fn();
    const user = userEvent.setup();
    renderField({ onOpen });

    // a single consistent label: "Open", never "Open in Inventory"
    const openButton = screen.getByRole("button", { name: /^open$/i });
    expect(
      screen.queryByRole("button", { name: /open in inventory/i }),
    ).not.toBeInTheDocument();

    /* eslint-disable testing-library/no-node-access -- asserting the Open button sits in the same row as the link chips */
    expect(openButton.closest('[data-test-id="LinkField-row"]')).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access */

    await user.click(openButton);
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("renders pinned version label when versionPin is set", () => {
    renderField({ link: { ...baseLink, versionPin: 4 } });
    expect(screen.getByText(/v4|pinned to v4/i)).toBeInTheDocument();
  });

  it("shows the Target deleted pill and blocks Open for a deleted ELN target", () => {
    // a deleted ELN record's open route only produces an error page, so the
    // card must not offer Open
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SD5",
      name: "gone doc",
      type: "DOCUMENT",
      deleted: true,
    });
    renderField({ link: { ...baseLink, targetGlobalId: "SD5" } });
    expect(screen.getByText(/target deleted/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^open$/i })).not.toBeInTheDocument();
  });

  it("shows the Target deleted pill but keeps Open for a deleted inventory target", () => {
    // deleted inventory items live on in the trash and their viewer works,
    // so Open stays available alongside the pill
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SA42",
      name: "binned sample",
      type: "SAMPLE",
      deleted: true,
    });
    renderField();
    expect(screen.getByText(/target deleted/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^open$/i })).toBeEnabled();
  });

  it("renders no pill and keeps Open while the target state is unknown", () => {
    // the summary fetch may be in flight or have failed; the card behaves as
    // it did before the summary existed rather than flashing a wrong state
    mockUseLinkTargetSummary.mockReturnValue(null);
    renderField();
    expect(screen.queryByText(/target deleted/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^open$/i })).toBeEnabled();
  });

  it("greys out the clock in view mode: pin changes happen in the link editor", () => {
    // the version pin is edited like every other link property: click Edit,
    // change it in the editor, and commit with Update. The view-mode clock is
    // a disabled affordance only.
    renderField({ editable: true });

    expect(
      screen.getByRole("button", { name: /pin version for sa42/i }),
    ).toBeDisabled();
  });

  it("hides the clock entirely when the record is not editable", () => {
    renderField({ editable: false });

    expect(
      screen.queryByRole("button", { name: /pin version for sa42/i }),
    ).not.toBeInTheDocument();
  });

  it("enables Open for a pinned inventory target and navigates to the versioned viewer", async () => {
    const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
    const onOpen = vi.fn();
    const user = userEvent.setup();
    renderField({
      link: { ...baseLink, targetGlobalId: "SA42", versionPin: 3 },
      onOpen,
    });

    const openButton = screen.getByRole("button", {
      name: /^open$/i,
    });
    expect(openButton).toBeEnabled();
    await user.click(openButton);

    // pinned inventory opens the read-only versioned viewer (RSDEV-1141 route),
    // not the live record, and does not fall back to the parent onOpen handler.
    expect(openSpy).toHaveBeenCalledWith(
      "/inventory/sample/42?version=3",
      "_blank",
    );
    expect(onOpen).not.toHaveBeenCalled();
    openSpy.mockRestore();
  });

  it("keeps Open enabled when no versionPin is set", () => {
    renderField({ link: { ...baseLink, versionPin: null } });
    expect(
      screen.getByRole("button", { name: /^open$/i }),
    ).toBeEnabled();
  });

  it("shows a clock icon for version-pinning only when editable", () => {
    const { rerender } = renderField({ editable: true });
    expect(
      screen.getByRole("button", { name: /pin.*version|version.*history/i }),
    ).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkField
          name="Calibration cert"
          link={baseLink}
          onOpen={vi.fn()}
          onEdit={vi.fn()}
          editable={false}
        />
      </ThemeProvider>,
    );
    expect(
      screen.queryByRole("button", { name: /pin.*version|version.*history/i }),
    ).not.toBeInTheDocument();
  });

  it("renders an info button next to the chip that opens InventoryInfoDialog for the target", async () => {
    const user = userEvent.setup();
    renderField();

    const infoButton = screen.getByRole("button", {
      name: /show info for sa42/i,
    });
    await user.click(infoButton);

    const dialog = screen.getByTestId("inventory-info-dialog");
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute("data-globalid", "SA42");
  });

  it("passes the link's versionPin to the inventory record-info dialog", async () => {
    const user = userEvent.setup();
    renderField({ link: { ...baseLink, targetGlobalId: "SA42", versionPin: 3 } });
    await user.click(
      screen.getByRole("button", { name: /show info for sa42/i }),
    );
    expect(screen.getByTestId("inventory-info-dialog")).toHaveAttribute(
      "data-version-pin",
      "3",
    );
  });

  it("hides the version-pin clock for notebook (NB) targets even when editable", () => {
    renderField({
      editable: true,
      link: { ...baseLink, targetGlobalId: "NB5" },
    });
    expect(
      screen.queryByRole("button", { name: /pin version for/i }),
    ).not.toBeInTheDocument();
  });

  it("hides the version-pin clock for gallery (GL) targets even when editable", () => {
    renderField({
      editable: true,
      link: { ...baseLink, targetGlobalId: "GL9" },
    });
    expect(
      screen.queryByRole("button", { name: /pin version for/i }),
    ).not.toBeInTheDocument();
  });

  it("shows the version-pin clock for document (SD) targets when editable", () => {
    renderField({
      editable: true,
      link: { ...baseLink, targetGlobalId: "SD3" },
    });
    expect(
      screen.getByRole("button", { name: /pin version for sd3/i }),
    ).toBeInTheDocument();
  });

  it("labels the open action 'Open' for ELN targets too", () => {
    renderField({ link: { ...baseLink, targetGlobalId: "SD3" } });
    expect(screen.getByRole("button", { name: /^open$/i })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /open in inventory/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps Open enabled for a version-pinned document (SD) target", () => {
    renderField({ link: { ...baseLink, targetGlobalId: "SD3", versionPin: 2 } });
    expect(
      screen.getByRole("button", { name: /^open$/i }),
    ).toBeEnabled();
  });

  it("passes the link's versionPin to the ELN record-info dialog", async () => {
    const user = userEvent.setup();
    renderField({ link: { ...baseLink, targetGlobalId: "SD3", versionPin: 2 } });
    const infoButton = screen.getByRole("button", {
      name: /show info for sd3/i,
    });
    await user.click(infoButton);
    const dialog = screen.getByTestId("eln-info-dialog");
    expect(dialog).toHaveAttribute("data-version-pin", "2");
  });

  it("shows the info button for ELN targets and opens the ELN record-info dialog (not the inventory dialog)", async () => {
    const user = userEvent.setup();
    renderField({ link: { ...baseLink, targetGlobalId: "SD3" } });

    const infoButton = screen.getByRole("button", {
      name: /show info for sd3/i,
    });
    await user.click(infoButton);

    const dialog = screen.getByTestId("eln-info-dialog");
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute("data-globalid", "SD3");
    expect(
      screen.queryByTestId("inventory-info-dialog"),
    ).not.toBeInTheDocument();
  });

  it("still shows the info button for inventory targets", () => {
    renderField({ link: { ...baseLink, targetGlobalId: "SA42" } });
    expect(
      screen.getByRole("button", { name: /show info for sa42/i }),
    ).toBeInTheDocument();
  });

  it("renders a type icon inside the target chip for each Inventory prefix", () => {
    /* eslint-disable testing-library/no-node-access, testing-library/no-container -- inspecting MUI Chip icon child */
    const { rerender, container } = renderField({
      link: { ...baseLink, targetGlobalId: "SA42" },
    });
    const sampleChip = container.querySelector(
      '[data-test-id="LinkField-target"]',
    );
    expect(sampleChip?.querySelector("svg")).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkField
          name="x"
          link={{ ...baseLink, targetGlobalId: "IC9" }}
          onOpen={vi.fn()}
          onEdit={vi.fn()}
          editable={true}
        />
      </ThemeProvider>,
    );
    const containerChip = container.querySelector(
      '[data-test-id="LinkField-target"]',
    );
    expect(containerChip?.querySelector("svg")).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access, testing-library/no-container */
  });
});
