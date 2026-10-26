import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen } from "@testing-library/react";
import type { AxiosResponse } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";

import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import InvApiService from "../../../../../common/InvApiService";
import materialTheme from "../../../../../theme";

vi.mock("../../../../../common/InvApiService", () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock("../../../../../stores/models/Factory/AlwaysNewFactory", () => {
  return {
    default: class TestFactory {
      newRecord(data: Record<string, unknown>) {
        return data as unknown;
      }
    },
  };
});

vi.mock("../../../MoreInfoSidebar/SidebarBody", () => ({
  default: ({ record }: { record: { globalId: string; created: string } }) => (
    <div data-testid="sidebar-body" data-globalid={record.globalId} data-created={record.created} />
  ),
}));

import InventoryInfoDialog from "../InventoryInfoDialog";

describe("InventoryInfoDialog", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not render when closed", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open={false} globalId="SA42" onClose={vi.fn()} />
      </ThemeProvider>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("fetches the target by global id and renders the sidebar body for it", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockImplementation((url) => {
      if (url === "samples/42") {
        return Promise.resolve({
          data: {
            id: 42,
            globalId: "SA42",
            type: "SAMPLE",
            name: "A sample",
            created: "2026-05-01T10:00:00Z",
            lastModified: "2026-05-02T10:00:00Z",
            extraFields: [],
            subSamples: [],
            quantity: { numericValue: 1, unitId: 3 },
            permittedActions: ["READ"],
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.reject(new Error(`unexpected url ${String(url)}`));
    });

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA42" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    const body = await screen.findByTestId("sidebar-body");
    expect(body).toHaveAttribute("data-globalid", "SA42");
    expect(body).toHaveAttribute("data-created", "2026-05-01T10:00:00Z");
    expect(vi.mocked(apiGet)).toHaveBeenCalledWith("samples/42");
  });

  it("fetches the pinned version snapshot when versionPin is set", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockImplementation((url) => {
      if (url === "samples/42/versions/3") {
        return Promise.resolve({
          data: {
            id: 42,
            globalId: "SA42",
            type: "SAMPLE",
            name: "A sample (v3)",
            version: 3,
            created: "2026-05-01T10:00:00Z",
            lastModified: "2026-05-03T10:00:00Z",
            extraFields: [],
            subSamples: [],
            quantity: { numericValue: 1, unitId: 3 },
            permittedActions: ["READ"],
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.reject(new Error(`unexpected url ${String(url)}`));
    });

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA42" versionPin={3} onClose={vi.fn()} />
      </ThemeProvider>,
    );

    const body = await screen.findByTestId("sidebar-body");
    expect(body).toHaveAttribute("data-globalid", "SA42");
    expect(vi.mocked(apiGet)).toHaveBeenCalledWith("samples/42/versions/3");
  });

  it("fetches an instrument target by global id (IN prefix)", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockImplementation((url) => {
      if (url === "instruments/43") {
        return Promise.resolve({
          data: {
            id: 43,
            globalId: "IN43",
            type: "INSTRUMENT",
            name: "A microscope",
            created: "2026-05-01T10:00:00Z",
            lastModified: "2026-05-02T10:00:00Z",
            extraFields: [],
            permittedActions: ["READ"],
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      }
      return Promise.reject(new Error(`unexpected url ${String(url)}`));
    });

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="IN43" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    const body = await screen.findByTestId("sidebar-body");
    expect(body).toHaveAttribute("data-globalid", "IN43");
    expect(vi.mocked(apiGet)).toHaveBeenCalledWith("instruments/43");
  });

  it("shows a historic-version note (matching the ELN dialog) when versionPin is set", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue({
      data: {
        id: 42,
        globalId: "SA42",
        type: "SAMPLE",
        name: "A sample (v1)",
        version: 1,
        created: "2026-05-01T10:00:00Z",
        lastModified: "2026-05-01T10:00:00Z",
        extraFields: [],
        subSamples: [],
        quantity: { numericValue: 1, unitId: 3 },
        permittedActions: ["READ"],
      },
      status: 200,
      statusText: "OK",
      headers: {},
      config: {},
    } as AxiosResponse);

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA42" versionPin={1} onClose={vi.fn()} />
      </ThemeProvider>,
    );

    await screen.findByTestId("sidebar-body");
    expect(screen.getByRole("note")).toHaveTextContent("fields.link.infoDialog.versionNote");
  });

  it("shows no historic-version note when the link is not pinned", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockResolvedValue({
      data: {
        id: 42,
        globalId: "SA42",
        type: "SAMPLE",
        name: "A sample",
        created: "2026-05-01T10:00:00Z",
        lastModified: "2026-05-02T10:00:00Z",
        extraFields: [],
        subSamples: [],
        quantity: { numericValue: 1, unitId: 3 },
        permittedActions: ["READ"],
      },
      status: 200,
      statusText: "OK",
      headers: {},
      config: {},
    } as AxiosResponse);

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA42" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    await screen.findByTestId("sidebar-body");
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });

  it("ignores a stale in-flight response when the target changes before it resolves", async () => {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    let resolveStale: (r: AxiosResponse) => void = () => {};
    const stalePending = new Promise<AxiosResponse>((res) => {
      resolveStale = res;
    });
    const okResponse = (globalId: string): AxiosResponse =>
      ({
        data: {
          globalId,
          type: "SAMPLE",
          name: globalId,
          created: "2026-05-01T10:00:00Z",
        },
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      }) as AxiosResponse;
    vi.mocked(apiGet).mockImplementation((url) => {
      if (url === "samples/42") return stalePending; // never resolves until told
      if (url === "subSamples/7") return Promise.resolve(okResponse("SS7"));
      return Promise.reject(new Error(`unexpected url ${String(url)}`));
    });

    const { rerender } = render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA42" onClose={vi.fn()} />
      </ThemeProvider>,
    );
    // switch to a new target before the SA42 request resolves
    rerender(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SS7" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    const body = await screen.findByTestId("sidebar-body");
    expect(body).toHaveAttribute("data-globalid", "SS7");

    // the stale SA42 response now arrives; the cancellation guard must drop it
    // so it cannot overwrite the current SS7 result
    resolveStale(okResponse("SA42"));
    await Promise.resolve();
    await Promise.resolve();

    expect(screen.getByTestId("sidebar-body")).toHaveAttribute("data-globalid", "SS7");
  });

  function rejectGetWith(status: number) {
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const apiGet = InvApiService.get;
    vi.mocked(apiGet).mockRejectedValue(
      Object.assign(new Error(`Request failed with status code ${status}`), {
        isAxiosError: true,
        response: { status },
      }),
    );
  }

  it("explains a 404 on the pinned snapshot in words rather than axios' message", async () => {
    rejectGetWith(404);

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA6" versionPin={7} onClose={vi.fn()} />
      </ThemeProvider>,
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toMatchTextContent(/fields\.link\.infoDialog\.versionNotFound/);
    expect(alert).not.toHaveTextContent("Request failed");
  });

  it("names the pinned version and the target without its version suffix", async () => {
    rejectGetWith(404);

    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA6v7" versionPin={7} onClose={vi.fn()} />
      </ThemeProvider>,
      { resources: { inventory: inventoryEn }, defaultNS: "inventory" },
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Version 7 of SA6 cannot be found. It may never have existed, or its history may have been removed.",
    );
  });

  it("keeps the raw message for a 404 on an unpinned target", async () => {
    rejectGetWith(404);

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA6" onClose={vi.fn()} />
      </ThemeProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Request failed with status code 404");
  });

  it("keeps the raw message for any other failure on a pinned snapshot", async () => {
    rejectGetWith(500);

    render(
      <ThemeProvider theme={materialTheme}>
        <InventoryInfoDialog open globalId="SA6" versionPin={7} onClose={vi.fn()} />
      </ThemeProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Request failed with status code 500");
  });
});
