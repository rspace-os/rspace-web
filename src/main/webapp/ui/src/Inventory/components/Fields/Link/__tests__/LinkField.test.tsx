import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import materialTheme from "../../../../../theme";

vi.mock("../InventoryInfoDialog", () => ({
  default: ({ open, globalId, versionPin }: { open: boolean; globalId: string; versionPin?: number | null }) =>
    open ? (
      <div
        data-testid="inventory-info-dialog"
        data-globalid={globalId}
        data-version-pin={versionPin == null ? "" : String(versionPin)}
      />
    ) : null,
}));

vi.mock("../ElnRecordInfoDialog", () => ({
  default: ({ open, globalId, versionPin }: { open: boolean; globalId: string; versionPin?: number | null }) =>
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
      <LinkField name="Calibration cert" link={baseLink} onEdit={vi.fn()} editable={true} {...props} />
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

  it("shows a bold inline field name and no 'Link' heading", () => {
    renderField();
    // the literal "Link" heading was removed; only the field name is shown
    expect(screen.queryByText(/^link$/i)).not.toBeInTheDocument();
    const name = screen.getByText(/^calibration cert$/i);
    // subtitle1 is the larger variant; the name renders bold within the link row
    expect(name).toHaveClass("MuiTypography-subtitle1");
    /* eslint-disable testing-library/no-node-access -- asserting the name sits inline in the link row */
    expect(name.closest('[data-test-id="LinkField-row"]')).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access */
  });

  it("renders the inline Edit action when onEdit is provided (template Link fields, which have no settings cog)", async () => {
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

  it("omits the inline Edit button when onEdit is not provided (custom field Link cards are edited via the settings cog)", () => {
    renderField({ editable: true, onEdit: undefined });
    expect(screen.queryByRole("button", { name: /edit link/i })).not.toBeInTheDocument();
  });

  it("renders the Open action as a static link inline with the link chips", () => {
    renderField();

    // a single consistent, target-qualified Open action ("Open SA42"), never a
    // separate "Open in Inventory" variant. It is a real anchor (role link) for
    // accessibility, not a scripted button.
    const openLink = screen.getByRole("link", { name: /^open sa42$/i });
    expect(openLink).toHaveAttribute("href", "/globalId/SA42");
    expect(openLink).toHaveAttribute("target", "_blank");
    expect(screen.queryByRole("link", { name: /open in inventory/i })).not.toBeInTheDocument();

    /* eslint-disable testing-library/no-node-access -- asserting the Open link sits in the same row as the link chips */
    expect(openLink.closest('[data-test-id="LinkField-row"]')).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access */
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
      readable: true,
    });
    renderField({ link: { ...baseLink, targetGlobalId: "SD5" } });
    expect(screen.getByText(/target deleted/i)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /^open /i })).not.toBeInTheDocument();
  });

  it("shows the Target deleted pill but keeps Open for a deleted inventory target", () => {
    // deleted inventory items live on in the trash and their viewer works,
    // so Open stays available alongside the pill
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SA42",
      name: "binned sample",
      type: "SAMPLE",
      deleted: true,
      readable: true,
    });
    renderField();
    expect(screen.getByText(/target deleted/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^open /i })).toBeInTheDocument();
  });

  it("shows the No access pill and removes Open for an unreadable ELN target", () => {
    // the server redacts targets the viewer cannot read (unshared, never
    // shared, nonexistent, or hard-deleted by another owner all look alike);
    // opening such an ELN target could only produce an error page
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SD5",
      name: null,
      type: null,
      deleted: false,
      readable: false,
    });
    renderField({ link: { ...baseLink, targetGlobalId: "SD5" } });
    expect(screen.getByText(/no access/i)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /^open /i })).not.toBeInTheDocument();
    // an unreadable target has nothing to show, so info and the version-pin
    // clock are greyed out; Edit stays as the repair path (retarget or remove)
    expect(screen.getByRole("button", { name: /show info for sd5/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /pin version for sd5/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /edit link/i })).toBeInTheDocument();
  });

  it("explains the No access pill with a permission tooltip", async () => {
    const user = userEvent.setup();
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SD5",
      name: null,
      type: null,
      deleted: false,
      readable: false,
    });
    renderField({ link: { ...baseLink, targetGlobalId: "SD5" } });

    await user.hover(screen.getByText(/no access/i));

    // no "no longer": viewers who never had access see this pill too
    expect(await screen.findByText(/you do not have permission to view this item/i)).toBeInTheDocument();
  });

  it("renders a normal card with Open for an unreadable inventory target", () => {
    // every logged-in user keeps the limited-read view of inventory items,
    // so Open still lands somewhere useful and no pill is warranted
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SA42",
      name: null,
      type: null,
      deleted: false,
      readable: false,
    });
    renderField();
    expect(screen.queryByText(/no access/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^open /i })).toBeInTheDocument();
  });

  it("renders no pill and keeps Open while the target state is unknown", () => {
    // the summary fetch may be in flight or have failed; the card behaves as
    // it did before the summary existed rather than flashing a wrong state
    mockUseLinkTargetSummary.mockReturnValue(null);
    renderField();
    expect(screen.queryByText(/target deleted/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^open /i })).toBeInTheDocument();
  });

  it("greys out the clock in view mode: pin changes happen in the link editor", () => {
    // the version pin is edited like every other link property: click Edit,
    // change it in the editor, and commit with Update. The view-mode clock is
    // a disabled affordance only.
    renderField({ editable: true });

    expect(screen.getByRole("button", { name: /pin version for sa42/i })).toBeDisabled();
  });

  it("hides the clock entirely when the record is not editable", () => {
    renderField({ editable: false });

    expect(screen.queryByRole("button", { name: /pin version for sa42/i })).not.toBeInTheDocument();
  });

  it("links Open to the versioned viewer for a pinned inventory target", () => {
    renderField({
      link: { ...baseLink, targetGlobalId: "SA42", versionPin: 3 },
    });

    // pinned inventory links to the read-only versioned viewer (RSDEV-1141 route),
    // not the live record.
    const openLink = screen.getByRole("link", { name: /^open /i });
    expect(openLink).toHaveAttribute("href", "/inventory/sample/42?version=3");
    expect(openLink).toHaveAttribute("target", "_blank");
  });

  it("keeps Open enabled when no versionPin is set", () => {
    renderField({ link: { ...baseLink, versionPin: null } });
    expect(screen.getByRole("link", { name: /^open /i })).toHaveAttribute("href", "/globalId/SA42");
  });

  it("shows a clock icon for version-pinning only when editable", () => {
    const { rerender } = renderField({ editable: true });
    expect(screen.getByRole("button", { name: /pin.*version|version.*history/i })).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkField name="Calibration cert" link={baseLink} onEdit={vi.fn()} editable={false} />
      </ThemeProvider>,
    );
    expect(screen.queryByRole("button", { name: /pin.*version|version.*history/i })).not.toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: /show info for sa42/i }));
    expect(screen.getByTestId("inventory-info-dialog")).toHaveAttribute("data-version-pin", "3");
  });

  it("hides the version-pin clock for notebook (NB) targets even when editable", () => {
    renderField({
      editable: true,
      link: { ...baseLink, targetGlobalId: "NB5" },
    });
    expect(screen.queryByRole("button", { name: /pin version for/i })).not.toBeInTheDocument();
  });

  it("hides the version-pin clock for gallery (GL) targets even when editable", () => {
    renderField({
      editable: true,
      link: { ...baseLink, targetGlobalId: "GL9" },
    });
    expect(screen.queryByRole("button", { name: /pin version for/i })).not.toBeInTheDocument();
  });

  it("shows the version-pin clock for document (SD) targets when editable", () => {
    renderField({
      editable: true,
      link: { ...baseLink, targetGlobalId: "SD3" },
    });
    expect(screen.getByRole("button", { name: /pin version for sd3/i })).toBeInTheDocument();
  });

  it("labels the open action 'Open <globalId>' for ELN targets too", () => {
    renderField({ link: { ...baseLink, targetGlobalId: "SD3" } });
    expect(screen.getByRole("link", { name: /^open sd3$/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /open in inventory/i })).not.toBeInTheDocument();
  });

  it("links a version-pinned document (SD) target to its versioned globalId route", () => {
    renderField({ link: { ...baseLink, targetGlobalId: "SD3", versionPin: 2 } });
    expect(screen.getByRole("link", { name: /^open /i })).toHaveAttribute("href", "/globalId/SD3v2");
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
    expect(screen.queryByTestId("inventory-info-dialog")).not.toBeInTheDocument();
  });

  it("still shows the info button for inventory targets", () => {
    renderField({ link: { ...baseLink, targetGlobalId: "SA42" } });
    expect(screen.getByRole("button", { name: /show info for sa42/i })).toBeInTheDocument();
  });

  it("renders a type icon inside the target chip for each Inventory prefix", () => {
    /* eslint-disable testing-library/no-node-access, testing-library/no-container -- inspecting MUI Chip icon child */
    const { rerender, container } = renderField({
      link: { ...baseLink, targetGlobalId: "SA42" },
    });
    const sampleChip = container.querySelector('[data-test-id="LinkField-target"]');
    expect(sampleChip?.querySelector("svg")).toBeInTheDocument();

    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkField name="x" link={{ ...baseLink, targetGlobalId: "IC9" }} onEdit={vi.fn()} editable={true} />
      </ThemeProvider>,
    );
    const containerChip = container.querySelector('[data-test-id="LinkField-target"]');
    expect(containerChip?.querySelector("svg")).toBeInTheDocument();
    /* eslint-enable testing-library/no-node-access, testing-library/no-container */
  });
});
