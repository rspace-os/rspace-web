import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { I18nextProvider } from "react-i18next";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, within } from "@/__tests__/customQueries";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import type { WorkspaceRecordInformation } from "@/modules/workspace/schema";
import materialTheme from "../../../../../theme";

const getLinkedByRecords = vi.fn();
const getPublicLink = vi.fn();
const useReferencingInventoryItems = vi.fn();

vi.mock("@/modules/workspace/linkedRecords", () => ({
  getLinkedByRecords: (...args: Array<unknown>) => getLinkedByRecords(...args) as unknown,
}));
vi.mock("@/modules/workspace/publicLink", () => ({
  getPublicLink: (...args: Array<unknown>) => getPublicLink(...args) as unknown,
}));
vi.mock("@/eln/gallery/useReferencingInventoryItems", () => ({
  default: (...args: Array<unknown>) => useReferencingInventoryItems(...args) as unknown,
}));

import DocumentSections from "../DocumentSections";

const baseInfo: WorkspaceRecordInformation = {
  id: 123,
  oid: { idString: "SD123" },
  name: "My experiment",
  type: "Structured Document",
  ownerFullName: "Ada Lovelace",
  creationDateWithClientTimezoneOffset: "2026-05-01 10:00 +0000",
  modificationDateWithClientTimezoneOffset: "2026-05-02 10:00 +0000",
  version: 4,
  status: "VIEW_MODE",
  signatureStatus: "UNSIGNED",
  tags: "alpha,beta",
  path: "/Workspace/Project",
  templateFormName: "Basic Document",
  templateFormId: { idString: "FM7" },
  templateName: "My template",
  templateOid: "SD200",
  linkedByCount: 2,
  shared: false,
  implicitlyShared: false,
};

beforeEach(() => {
  getLinkedByRecords.mockReset();
  getPublicLink.mockReset();
  useReferencingInventoryItems.mockReset();
  // Sensible defaults; individual tests override.
  getLinkedByRecords.mockResolvedValue({ readable: [], privateByOwner: [] });
  getPublicLink.mockResolvedValue(null);
  useReferencingInventoryItems.mockReturnValue({
    items: [],
    loading: false,
    errorMessage: null,
  });
});

afterEach(cleanup);

function renderDoc(props: Partial<React.ComponentProps<typeof DocumentSections>> = {}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <DocumentSections info={baseInfo} isNotebook={false} {...props} />
    </ThemeProvider>,
  );
}

// The public-link sentence is rendered via TransRichText, embedding the <a> in the
// translated copy; cimode returns the raw key with no <a> tag to embed, so these
// two tests render against the real English bundle instead.
async function renderDocWithRealI18n(props: Partial<React.ComponentProps<typeof DocumentSections>> = {}) {
  const i18n = await createTestI18n({ inventory: inventoryEn }, "inventory");
  return render(
    <I18nextProvider i18n={i18n}>
      <ThemeProvider theme={materialTheme}>
        <DocumentSections info={baseInfo} isNotebook={false} {...props} />
      </ThemeProvider>
    </I18nextProvider>,
  );
}

describe("DocumentSections (structured document)", () => {
  it("renders the core metadata table rows", () => {
    renderDoc();
    expect(screen.getByText("My experiment")).toBeInTheDocument();
    expect(screen.getByText("Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByText("/Workspace/Project")).toBeInTheDocument();
    // version
    expect(screen.getByText("4")).toBeInTheDocument();
    // status maps to a friendly label
    expect(screen.getByText("inventory:fields.link.documentSections.statusLabels.viewMode")).toBeInTheDocument();
    // signature status maps to a friendly label
    expect(screen.getByText("inventory:fields.link.documentSections.signatureLabels.unsigned")).toBeInTheDocument();
    // tags are comma-spaced
    expect(screen.getByText(/alpha, beta/i)).toBeInTheDocument();
  });

  it("renders the self link to /globalId/SD123", () => {
    renderDoc();
    const selfLink = screen.getByRole("link", { name: /SD123/ });
    expect(selfLink).toHaveAttribute("href", "/globalId/SD123");
  });

  it("renders form id and template links", () => {
    renderDoc();
    const formLink = screen.getByRole("link", { name: /FM7/ });
    expect(formLink).toHaveAttribute("href", "/globalId/FM7");
    const templateLink = screen.getByRole("link", { name: /my template/i });
    expect(templateLink).toHaveAttribute("href", "/globalId/SD200");
  });

  it("lazily loads the linked-by docs when 'Show linked docs' is clicked", async () => {
    getLinkedByRecords.mockResolvedValue({
      readable: [{ globalId: "SD11", name: "Doc one", ownerFullName: "Ada" }],
      privateByOwner: [{ ownerFullName: "Grace Hopper", count: 2 }],
    });
    const user = userEvent.setup();
    renderDoc();

    expect(screen.getByText("inventory:fields.link.documentSections.linkedBy.linkedByCount")).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "inventory:fields.link.documentSections.linkedBy.showLinked" }),
    );

    expect(getLinkedByRecords).toHaveBeenCalledWith(123);
    expect(await screen.findByRole("link", { name: /SD11/ })).toHaveAttribute("href", "/globalId/SD11");
    expect(screen.getByText("inventory:fields.link.documentSections.linkedBy.privateDocs")).toBeInTheDocument();
  });

  it("renders related inventory items from the referencing-items hook", () => {
    useReferencingInventoryItems.mockReturnValue({
      items: [
        {
          globalId: "SA1",
          name: "Buffer",
          type: "SAMPLE",
          relationType: "IsPartOf",
          permalinkHref: "/globalId/SA1",
          linkableRecord: {},
        },
      ],
      loading: false,
      errorMessage: null,
    });
    renderDoc();
    expect(useReferencingInventoryItems).toHaveBeenCalledWith("SD123");
    const invLink = screen.getByRole("link", { name: /SA1/ });
    expect(invLink).toHaveAttribute("href", "/globalId/SA1");
    expect(screen.getByText(/buffer/i)).toBeInTheDocument();
  });

  it("shows sharing status when the document is shared", () => {
    renderDoc({
      info: {
        ...baseInfo,
        shared: true,
        sharedGroupsAndAccess: { "Lab Group": "READ" },
      },
    });
    expect(screen.getByText("inventory:fields.link.documentSections.sharing.withGroup")).toBeInTheDocument();
  });

  it("shows the public link when the document is published", async () => {
    getPublicLink.mockResolvedValue("abc-123");
    await renderDocWithRealI18n();
    expect(getPublicLink).toHaveBeenCalledWith("SD123");
    const publicLink = await screen.findByRole("link", { name: "public link" });
    expect(publicLink).toHaveAttribute("href", expect.stringContaining("/public/publishedView/document/abc-123"));
  });

  it("routes an unpublished entry in a published notebook to the notebook public view", async () => {
    // The publiclink endpoint returns <parentLink>?initialRecordToDisplay=<docId>
    // when the document itself is unpublished but its parent notebook is published;
    // the ELN routes these to /notebook/, not /document/ (recordInfoPanel.js).
    getPublicLink.mockResolvedValue("parent-link?initialRecordToDisplay=123");
    await renderDocWithRealI18n();
    const publicLink = await screen.findByRole("link", { name: "public link" });
    expect(publicLink).toHaveAttribute(
      "href",
      expect.stringContaining("/public/publishedView/notebook/parent-link?initialRecordToDisplay=123"),
    );
    expect(
      screen.getByText(
        (_content, element) => element?.textContent === "This document is in a published notebook: public link",
      ),
    ).toBeInTheDocument();
  });
});

describe("DocumentSections (version-pinned SD)", () => {
  function renderPinned(props: Partial<React.ComponentProps<typeof DocumentSections>> = {}) {
    // getRecordInformation?version=N returns a VERSIONED oid (getOidWithVersion()),
    // so the fixture mirrors production; the component strips the suffix for the
    // "latest" link and reconstructs the versioned id for the Unique Id row.
    return renderDoc({
      info: { ...baseInfo, oid: { idString: "SD599v3" }, version: 3 },
      pinnedVersion: 3,
      ...props,
    });
  }

  function renderPinnedWithRealI18n(props: Partial<React.ComponentProps<typeof DocumentSections>> = {}) {
    return renderDocWithRealI18n({
      info: { ...baseInfo, oid: { idString: "SD599v3" }, version: 3 },
      pinnedVersion: 3,
      ...props,
    });
  }

  it("shows the 'may not be the latest version' header with the version and doc id as plain text (no link) when pinned", async () => {
    await renderPinnedWithRealI18n();
    const note = screen.getByRole("note");
    expect(note).toHaveTextContent(
      "The information below describes version 3 of a document SD599, which may not be the latest version.",
    );
    // The document id in the warning is plain text, not a link to the latest version.
    expect(within(note).queryByRole("link")).not.toBeInTheDocument();
  });

  it("shows the versioned global id in the Unique Id row when pinned", () => {
    renderPinned();
    const uniqueIdLink = screen.getByRole("link", { name: "SD599v3" });
    expect(uniqueIdLink).toHaveAttribute("href", "/globalId/SD599v3");
  });
});

describe("DocumentSections (notebook)", () => {
  const notebookInfo: WorkspaceRecordInformation = {
    ...baseInfo,
    oid: { idString: "NB55" },
    id: 55,
    name: "Lab notebook",
    type: "Notebook",
    templateFormId: null,
    templateName: null,
    templateOid: null,
  };

  function renderNotebook() {
    return render(
      <ThemeProvider theme={materialTheme}>
        <DocumentSections info={notebookInfo} isNotebook={true} />
      </ThemeProvider>,
    );
  }

  it("does not render the form/template rows for notebooks", () => {
    renderNotebook();
    expect(screen.queryByRole("link", { name: /FM7/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/form id/i)).not.toBeInTheDocument();
  });

  it("uses notebook wording in the sharing/publication section", async () => {
    getPublicLink.mockResolvedValue(null);
    renderNotebook();
    expect(await screen.findByText("inventory:fields.link.documentSections.sharing.notPublished")).toBeInTheDocument();
  });
});
