import { ThemeProvider } from "@mui/material/styles";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import Alerts from "@/components/Alerts/Alerts";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import SidecarFileDialog from "./SidecarFileDialog";

const FILESTORE_ID = 42;
const PREVIEW = {
  filename: "experiments.sidecar.yaml",
  content: "schemaVersion: ltds-datacite4.3\ntitle:\n  value: experiments\n",
};
const previewUrl = `/api/v1/gallery/filestores/${FILESTORE_ID}/sidecarFile/preview`;
const saveUrl = `/api/v1/gallery/filestores/${FILESTORE_ID}/sidecarFile`;

// Request bodies captured by the MSW handlers, per endpoint.
let previewBodies: Array<unknown>;
let saveBodies: Array<unknown>;

function handlePreview(status: number) {
  return http.post(previewUrl, async ({ request }) => {
    previewBodies.push(await request.json());
    return status === 200 ? HttpResponse.json(PREVIEW) : new HttpResponse(null, { status });
  });
}
function handleSave(status: number) {
  return http.post(saveUrl, async ({ request }) => {
    saveBodies.push(await request.json());
    return HttpResponse.json(PREVIEW, { status });
  });
}

function renderDialog(props?: Partial<React.ComponentProps<typeof SidecarFileDialog>>) {
  const onClose = vi.fn();
  const refreshListing = vi.fn(() => Promise.resolve());
  const { baseElement } = render(
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Alerts>
        <SidecarFileDialog
          open
          onClose={onClose}
          filestoreId={FILESTORE_ID}
          folderPath="experiments"
          refreshListing={refreshListing}
          {...props}
        />
      </Alerts>
    </ThemeProvider>,
  );
  return { onClose, refreshListing, baseElement };
}

beforeEach(() => {
  previewBodies = [];
  saveBodies = [];
});
afterEach(cleanup);

describe("SidecarFileDialog", () => {
  test("composes and shows the metadata preview on open, writing nothing", async () => {
    server.use(handlePreview(200), handleSave(201));
    renderDialog();

    expect(await screen.findByText(PREVIEW.filename)).toBeVisible();
    // Regex: the field's value is the whole YAML document; match the schema line within it.
    expect(await screen.findByDisplayValue(/ltds-datacite4\.3/)).toBeVisible();

    // Preview only: the save endpoint must not be hit.
    expect(saveBodies).toHaveLength(0);
    // Request describes the current browse folder.
    expect(previewBodies).toEqual([{ path: "experiments" }]);
  });

  test("saving posts to the save endpoint, refreshes the listing, alerts, and closes", async () => {
    server.use(handlePreview(200), handleSave(201));
    const user = userEvent.setup();
    const { onClose, refreshListing } = renderDialog();

    await screen.findByText(PREVIEW.filename);
    await user.click(screen.getByRole("button", { name: "gallery:sidecarFile.save" }));

    // Synchronize on the user-visible result, not on the request firing.
    // The success toast portals to the body; while the (test-controlled) dialog stays open the
    // modal marks it aria-hidden, so assert by text rather than the alert role.
    expect(await screen.findByText("gallery:sidecarFile.saveSuccess")).toBeInTheDocument();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(refreshListing).toHaveBeenCalled();
    expect(saveBodies).toEqual([{ path: "experiments" }]);
  });

  test("has no accessibility violations once the preview is shown", async () => {
    server.use(handlePreview(200), handleSave(201));
    const { baseElement } = renderDialog();

    await screen.findByText(PREVIEW.filename);
    await expectAccessible(baseElement);
  });

  test("shows an error and keeps Save disabled when the preview fails", async () => {
    server.use(handlePreview(500), handleSave(201));
    renderDialog();

    expect(await screen.findByText("gallery:sidecarFile.previewFailed")).toBeVisible();
    expect(screen.getByRole("button", { name: "gallery:sidecarFile.save" })).toBeDisabled();
    expect(saveBodies).toHaveLength(0);
  });
});
