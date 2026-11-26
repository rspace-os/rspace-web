import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/resizeObserver";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import galleryCatalog from "@/modules/common/i18n/locales/en-US/gallery.json";
import materialTheme from "@/theme";
import { FilestoreLinkedDocumentsPanel } from "../components/LinkedDocumentsPanel";
import { Filestore, RemoteFile } from "../useGalleryListing";

const ENDPOINT = "/api/v1/gallery/filestores/42/linkedDocuments";

// an SFTP filestore, so an S3-only gate anywhere on the path would show up here
const filestore = new Filestore({
  id: 42,
  name: "My Filestore",
  filesystemId: 10,
  filesystemName: "My Filesystem",
  filesystemType: "SFTP",
  canWrite: false,
  ownerName: "gallery:unknownOwner",
});

const file = new RemoteFile({
  nfsId: null,
  name: "run.dat",
  folder: false,
  fileSize: 1024,
  modificationDate: undefined,
  path: [filestore],
  logicPath: "42:/XRD/run.dat",
  token: "",
  ownerName: "gallery:unknownOwner",
});

function withQueryClient(ui: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </ThemeProvider>
  );
}

describe("FilestoreLinkedDocumentsPanel", () => {
  it("lists readable documents by name with a Global ID link to the document", async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json([{ id: 11, globalId: "SD11", name: "Doc one", ownerFullName: "Ada Lovelace" }]),
      ),
    );

    render(withQueryClient(<FilestoreLinkedDocumentsPanel file={file} />));

    expect(await screen.findByText("Doc one")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "gallery:linkedDocumentsPanel.heading" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "gallery:linkedDocumentsPanel.columns.name" })).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: "gallery:linkedDocumentsPanel.columns.globalId" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "SD11" })).toHaveAttribute("href", "/globalId/SD11");
  });

  it("summarises documents the viewer cannot read per owner, in English", async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json([{ ownerFullName: "Grace Hopper" }, { ownerFullName: "Grace Hopper" }]),
      ),
    );

    await renderWithRealI18n(withQueryClient(<FilestoreLinkedDocumentsPanel file={file} />), {
      resources: { gallery: galleryCatalog },
      defaultNS: "gallery",
    });

    expect(await screen.findByText("2 private docs belonging to Grace Hopper")).toBeInTheDocument();
    expect(screen.queryByText("No Linked Documents")).not.toBeInTheDocument();
  });

  it("reports a failed request inside the grid and keeps the heading", async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json({ message: "boom" }, { status: 500 })));

    render(withQueryClient(<FilestoreLinkedDocumentsPanel file={file} />));

    expect(await screen.findByText("gallery:linkedDocumentsPanel.loadFailed")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "gallery:linkedDocumentsPanel.heading" })).toBeInTheDocument();
    expect(screen.queryByText("gallery:linkedDocumentsPanel.noRows")).not.toBeInTheDocument();
  });

  it("is what a filestore item's Info panel shows, while the filestore node shows nothing", async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json([])));

    render(withQueryClient(file.linkedDocuments));

    expect(await screen.findByText("gallery:linkedDocumentsPanel.noRows")).toBeInTheDocument();
    expect(filestore.linkedDocuments).toBeNull();
  });
});
