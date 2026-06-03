import { afterEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

vi.mock("../InventoryInfoDialog", () => ({
  default: ({ open, globalId }: { open: boolean; globalId: string }) =>
    open ? (
      <div data-testid="inventory-info-dialog" data-globalid={globalId} />
    ) : null,
}));

vi.mock("../EnElnRecordInfoDialog", () => ({
  default: ({ open, globalId }: { open: boolean; globalId: string }) =>
    open ? (
      <div data-testid="eln-info-dialog" data-globalid={globalId} />
    ) : null,
}));

vi.mock("../VersionLockDialog", () => ({
  default: ({
    open,
    globalId,
    currentVersionPin,
    onConfirm,
  }: {
    open: boolean;
    globalId: string;
    currentVersionPin: number | null;
    onConfirm: (v: number | null) => void;
  }) =>
    open ? (
      <div
        data-testid="version-lock-dialog"
        data-globalid={globalId}
        data-current-pin={currentVersionPin == null ? "" : String(currentVersionPin)}
      >
        <button
          type="button"
          data-testid="version-lock-dialog-confirm-7"
          onClick={() => onConfirm(7)}
        >
          confirm 7
        </button>
        <button
          type="button"
          data-testid="version-lock-dialog-confirm-latest"
          onClick={() => onConfirm(null)}
        >
          confirm latest
        </button>
      </div>
    ) : null,
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
        targetDeleted={false}
        onOpen={vi.fn()}
        onEdit={vi.fn()}
        onPeek={vi.fn()}
        editable={true}
        {...props}
      />
    </ThemeProvider>,
  );
}

describe("LinkField", () => {
  afterEach(cleanup);

  it("renders relation type, target global id, and name", () => {
    renderField();
    expect(screen.getByText(/calibration cert/i)).toBeInTheDocument();
    expect(screen.getByText(/iscalibratedby/i)).toBeInTheDocument();
    expect(screen.getByText(/sa42/i)).toBeInTheDocument();
  });

  it("calls onPeek when the card body is clicked", async () => {
    const onPeek = vi.fn();
    const user = userEvent.setup();
    renderField({ onPeek });

    await user.click(screen.getByText(/calibration cert/i));
    expect(onPeek).toHaveBeenCalledTimes(1);
  });

  it("renders 'Open in Inventory' as a secondary action", async () => {
    const onOpen = vi.fn();
    const user = userEvent.setup();
    renderField({ onOpen });

    await user.click(screen.getByRole("button", { name: /open in inventory/i }));
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("renders pinned version label when versionPin is set", () => {
    renderField({ link: { ...baseLink, versionPin: 4 } });
    expect(screen.getByText(/v4|pinned to v4/i)).toBeInTheDocument();
  });

  it("shows 'Target deleted' badge and hides 'Open in Inventory' when the target is deleted", () => {
    renderField({ targetDeleted: true });
    expect(screen.getByText(/target deleted/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /open in inventory/i })).not.toBeInTheDocument();
  });

  it("opens the VersionLockDialog when the clock icon is clicked", async () => {
    const user = userEvent.setup();
    renderField({ editable: true });

    const clockBtn = screen.getByRole("button", {
      name: /pin version for sa42/i,
    });
    await user.click(clockBtn);

    const dialog = screen.getByTestId("version-lock-dialog");
    expect(dialog).toHaveAttribute("data-globalid", "SA42");
    expect(dialog).toHaveAttribute("data-current-pin", "");
  });

  it("calls onVersionPinChange with the picked version when the dialog confirms", async () => {
    const onVersionPinChange = vi.fn();
    const user = userEvent.setup();
    renderField({ editable: true, onVersionPinChange });

    await user.click(
      screen.getByRole("button", { name: /pin version for sa42/i }),
    );
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));

    expect(onVersionPinChange).toHaveBeenCalledWith(7);
  });

  it("calls onVersionPinChange with null when the dialog confirms latest", async () => {
    const onVersionPinChange = vi.fn();
    const user = userEvent.setup();
    renderField({
      editable: true,
      onVersionPinChange,
      link: { ...baseLink, versionPin: 4 },
    });

    await user.click(
      screen.getByRole("button", { name: /pin version for sa42/i }),
    );
    await user.click(screen.getByTestId("version-lock-dialog-confirm-latest"));

    expect(onVersionPinChange).toHaveBeenCalledWith(null);
  });

  it("passes the current versionPin to the dialog when one is set", async () => {
    const user = userEvent.setup();
    renderField({
      editable: true,
      link: { ...baseLink, versionPin: 7 },
    });

    await user.click(
      screen.getByRole("button", { name: /pin version for sa42/i }),
    );
    expect(screen.getByTestId("version-lock-dialog")).toHaveAttribute(
      "data-current-pin",
      "7",
    );
  });

  it("disables Open in Inventory when versionPin is set, with an explanatory tooltip", async () => {
    renderField({ link: { ...baseLink, versionPin: 7 } });

    const openButton = screen.getByRole("button", {
      name: /open in inventory/i,
    });
    expect(openButton).toBeDisabled();

    // tooltip is rendered into MUI's portal on hover; presence of the title
    // text in the document is sufficient to confirm wiring
    const user = userEvent.setup();
    // eslint-disable-next-line testing-library/no-node-access -- a disabled button does not fire hover; hover its wrapping span instead
    await user.hover(openButton.parentElement!);
    expect(
      await screen.findByText(/version-specific view is not yet supported/i),
    ).toBeInTheDocument();
  });

  it("keeps Open in Inventory enabled when no versionPin is set", () => {
    renderField({ link: { ...baseLink, versionPin: null } });
    expect(
      screen.getByRole("button", { name: /open in inventory/i }),
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
          targetDeleted={false}
          onOpen={vi.fn()}
          onEdit={vi.fn()}
          onPeek={vi.fn()}
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

  it("labels the open action 'Open' (not 'Open in Inventory') for ELN targets", () => {
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
    expect(sampleChip?.querySelector("svg")).not.toBeNull();

    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkField
          name="x"
          link={{ ...baseLink, targetGlobalId: "IC9" }}
          targetDeleted={false}
          onOpen={vi.fn()}
          onEdit={vi.fn()}
          onPeek={vi.fn()}
          editable={true}
        />
      </ThemeProvider>,
    );
    const containerChip = container.querySelector(
      '[data-test-id="LinkField-target"]',
    );
    expect(containerChip?.querySelector("svg")).not.toBeNull();
    /* eslint-enable testing-library/no-node-access, testing-library/no-container */
  });
});
