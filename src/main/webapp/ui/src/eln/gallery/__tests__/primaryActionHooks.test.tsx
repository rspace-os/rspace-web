import { renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import { galleryFile } from "../components/VersionHistoryDialog.story";
import { HistoricalGalleryFile } from "../historicalGalleryFile";
import usePrimaryAction, { useDocumentPreviewOfGalleryFile } from "../primaryActionHooks";
import { CollaboraContext } from "../useCollabora";
import type { GalleryFile } from "../useGalleryListing";

/*
 * The primary action is the second way to reach an editor, alongside the Actions menu. It is
 * reached by double-clicking a tile, by the InfoPanel's action button, and from the tree and
 * carousel views, so a refusal that only the menu honours is not a refusal at all.
 *
 * Both integrations are injected through their contexts rather than mocked over the network:
 * useCollabora and useOfficeOnline read a context first and only fetch when it is empty.
 */
function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <DeploymentPropertyContext.Provider
      value={
        new Map<string, unknown>([
          ["collabora.wopi.enabled", true],
          ["msoffice.wopi.enabled", true],
          ["snapgene.available", "ALLOWED"],
          ["conversion.enabled", true],
        ])
      }
    >
      <CollaboraContext.Provider value={{ supportedExts: new Set(["docx"]) }}>{children}</CollaboraContext.Provider>
    </DeploymentPropertyContext.Provider>
  );
}

const primaryActionFor = (file: GalleryFile) => renderHook(() => usePrimaryAction(), { wrapper }).result.current(file);

const docx = (overrides: Partial<GalleryFile> = {}) =>
  galleryFile({ name: "protocol.docx", extension: "docx", ...overrides });

const dna = (overrides: Partial<GalleryFile> = {}) =>
  galleryFile({ name: "plasmid.dna", extension: "dna", ...overrides });

const pinnedVersionOf = (file: GalleryFile) =>
  new HistoricalGalleryFile({
    file,
    version: 1,
    size: 918,
    modificationDate: new Date("2026-06-11T09:30:00Z"),
    name: file.name,
    description: null,
  });

describe("usePrimaryAction", () => {
  beforeEach(() => {
    /* useOfficeOnline has no context to inject through, so its fetch is stubbed instead */
    server.use(http.get("/officeOnline/supportedExts", () => HttpResponse.json({})));
  });

  test("offers Collabora editing for a live document", () => {
    // the control: without this the pinned assertion below could pass for the wrong reason
    const action = primaryActionFor(docx());

    expect(action.orElseGet(() => ({ tag: "none" }))).toEqual({
      tag: "collabora",
      url: "/collaboraOnline/GL42/edit",
    });
  });

  test("refuses to edit a past version, which would edit the live document", () => {
    /*
     * HistoricalGalleryFile delegates globalId unversioned by design, so the Collabora URL it
     * would produce points at the live document. Editing from a page labelled "locked for
     * editing" would silently modify current content.
     */
    const action = primaryActionFor(pinnedVersionOf(docx()));

    expect(action.isError).toBe(true);
  });

  test("offers a SnapGene preview for a live DNA file", () => {
    const action = primaryActionFor(dna());

    expect(action.orElseGet(() => ({ tag: "none" })).tag).toBe("snapgene");
  });

  test("refuses to preview a past version, which would show live content", () => {
    /*
     * /molbiol/dna/png takes a bare id with no version, so the preview would render today's
     * sequence beneath a "version 1" badge. No preview is better than the wrong one.
     */
    const action = primaryActionFor(pinnedVersionOf(dna()));

    expect(action.isError).toBe(true);
  });

  test("offers conversion preview for every supported document format", () => {
    const previewDocument = renderHook(() => useDocumentPreviewOfGalleryFile(), { wrapper }).result.current;

    for (const extension of [
      "csv",
      "doc",
      "docx",
      "md",
      "odt",
      "rtf",
      "txt",
      "xls",
      "xlsx",
      "ods",
      "pdf",
      "ppt",
      "pptx",
      "odp",
    ]) {
      const preview = previewDocument(galleryFile({ name: `document.${extension}`, extension }));

      expect(preview.isOk, extension).toBe(true);
    }
  });
});
