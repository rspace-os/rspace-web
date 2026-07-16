import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import type React from "react";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import Alerts from "@/components/Alerts/Alerts";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import SidecarDialog from "./SidecarDialog";

const mockAxios = new MockAdapter(axios);
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

const FILESTORE_ID = 42;
const PREVIEW = {
  filename: "experiments.sidecar.yaml",
  content: "schemaVersion: ltds-datacite4.3\ntitle:\n  value: experiments\n",
};
const previewUrl = `/api/v1/gallery/filestores/${FILESTORE_ID}/sidecar/preview`;
const saveUrl = `/api/v1/gallery/filestores/${FILESTORE_ID}/sidecar`;

function renderDialog(props?: Partial<React.ComponentProps<typeof SidecarDialog>>) {
  const onClose = vi.fn();
  const refreshListing = vi.fn(() => Promise.resolve());
  render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Alerts>
          <SidecarDialog
            open
            onClose={onClose}
            filestoreId={FILESTORE_ID}
            folderPath="experiments"
            refreshListing={refreshListing}
            {...props}
          />
        </Alerts>
      </ThemeProvider>
    </QueryClientProvider>,
  );
  return { onClose, refreshListing };
}

beforeEach(() => {
  mockAxios.reset();
});
afterEach(() => {
  cleanup();
  mockAxios.reset();
});

describe("SidecarDialog", () => {
  test("composes and shows the metadata preview on open, writing nothing", async () => {
    mockAxios.onPost(previewUrl).reply(200, PREVIEW);
    renderDialog();

    expect(await screen.findByText(PREVIEW.filename)).toBeVisible();
    expect(await screen.findByDisplayValue(/ltds-datacite4\.3/)).toBeVisible();

    // Preview only: the save endpoint must not be hit.
    expect(mockAxios.history.post.map((r) => r.url)).not.toContain(saveUrl);
    // Request describes the current browse folder.
    expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({ path: "experiments" });
  });

  test("saving posts to the save endpoint, refreshes the listing, alerts, and closes", async () => {
    mockAxios.onPost(previewUrl).reply(200, PREVIEW);
    mockAxios.onPost(saveUrl).reply(201, PREVIEW);
    const user = userEvent.setup();
    const { onClose, refreshListing } = renderDialog();

    await screen.findByText(PREVIEW.filename);
    await user.click(screen.getByRole("button", { name: /gallery:sidecar\.save/i }));

    await waitFor(() => {
      const saveCalls = mockAxios.history.post.filter((r) => r.url === saveUrl);
      expect(saveCalls).toHaveLength(1);
      expect(JSON.parse(saveCalls[0].data)).toEqual({ path: "experiments" });
    });
    expect(refreshListing).toHaveBeenCalled();
    // The success toast portals to the body; while the (test-controlled) dialog stays open the
    // modal marks it aria-hidden, so assert by text rather than the alert role.
    expect(await screen.findByText("gallery:sidecar.saveSuccess")).toBeInTheDocument();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  test("shows an error and keeps Save disabled when the preview fails", async () => {
    mockAxios.onPost(previewUrl).reply(500);
    renderDialog();

    expect(await screen.findByText(/gallery:sidecar\.previewFailed/)).toBeVisible();
    expect(screen.getByRole("button", { name: /gallery:sidecar\.save/i })).toBeDisabled();
    expect(mockAxios.history.post.map((r) => r.url)).not.toContain(saveUrl);
  });
});
