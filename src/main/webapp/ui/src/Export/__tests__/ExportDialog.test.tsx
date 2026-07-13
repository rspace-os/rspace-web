import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/muiTransitions";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import fc from "fast-check";
import type React from "react";
import { useState } from "react";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import axios from "@/common/axios";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import workspaceEn from "@/modules/common/i18n/locales/en-US/workspace.json";
import Alerts from "../../components/Alerts/Alerts";
import type { ExportSelection } from "../common";
import ExportDialog from "../ExportDialog";
import CREATE_QUICK_EXPORT_PLAN from "./createQuickExportPlan.json";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

vi.mock("@/modules/raid/queries", () => ({
  useRaidIntegrationInfoAjaxQuery: () => ({
    data: { success: true, data: { enabled: false } },
  }),
}));

vi.mock("@/modules/share/queries", () => ({
  useCommonGroupsShareListingQuery: () => ({ data: new Map() }),
}));

window.fetch = vi.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve({}),
  } as Response),
);

const mockAxios = new MockAdapter(axios);
const arbUserSelection = fc.record<{
  type: "user";
  username: string;
  exportIds: Array<string>;
}>({
  type: fc.constant("user"),
  username: fc.string({ minLength: 1 }),
  exportIds: fc.constant([]),
});
const arbGroupSelection = fc.record<{
  type: "group";
  groupId: string;
  groupName: string;
  exportIds: Array<string>;
}>({
  type: fc.constant("group"),
  groupId: fc.string({ minLength: 1 }),
  groupName: fc.string({ minLength: 1 }),
  exportIds: fc.constant([]),
});
const arbDocumentSelection = (args: { max?: number } = {}) =>
  fc.integer({ min: 1, max: args.max ?? 20 }).chain((n) =>
    fc.record<{
      type: "selection";
      exportTypes: Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">;
      exportNames: Array<string>;
      exportIds: Array<string>;
    }>({
      type: fc.constant("selection"),
      exportTypes: fc.array(fc.constantFrom("MEDIA_FILE", "NOTEBOOK", "NORMAL", "FOLDER"), {
        minLength: n,
        maxLength: n,
      }),
      exportNames: fc.array(fc.string({ minLength: 1 }), {
        minLength: n,
        maxLength: n,
      }),
      exportIds: fc.array(fc.string({ minLength: 1 }), {
        minLength: n,
        maxLength: n,
      }),
    }),
  );
type RenderExportDialogArgs = {
  allowFileStores?: boolean;
  RealI18nWrapper?: React.ComponentType<{ children: React.ReactNode }>;
};

function renderExportDialog({ allowFileStores, RealI18nWrapper }: RenderExportDialogArgs = {}): {
  setProps: (props: { selection: ExportSelection; open: boolean }) => void;
} {
  // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
  let setProps;
  const Wrapper = () => {
    const [open, setOpen] = useState(false);
    const [selection, setSelection] = useState<ExportSelection>({
      type: "selection",
      exportTypes: [],
      exportNames: [],
      exportIds: [],
    });
    setProps = ({ selection: s, open: o }: { selection: ExportSelection; open: boolean }) => {
      setSelection(s);
      setOpen(o);
    };
    const dialog = (
      <Alerts>
        <ExportDialog exportSelection={selection} open={open} allowFileStores={allowFileStores ?? false} />
      </Alerts>
    );
    return RealI18nWrapper ? <RealI18nWrapper>{dialog}</RealI18nWrapper> : dialog;
  };
  render(<Wrapper />);
  if (!setProps) throw new Error("setProps is not initialised");
  return { setProps };
}

async function renderExportDialogWithRealI18n({ allowFileStores }: { allowFileStores?: boolean } = {}) {
  const RealI18nWrapper = await createRealI18nWrapper({
    resources: { common: commonEn, workspace: workspaceEn },
    defaultNS: "workspace",
  });
  return renderExportDialog({ allowFileStores, RealI18nWrapper });
}

/*
 * In cimode, i18n renders the untranslated key (e.g.
 * "workspace:export.format.pdf.pageSize.a4") rather than the real page size
 * text, so tests must match against the key's lowercase suffix rather than
 * the API's uppercase pageSize value ("A4"/"LETTER").
 */
const PAGE_SIZE_KEY_SUFFIX: Record<string, string> = { A4: "a4", LETTER: "letter" };

describe("ExportDialog", () => {
  mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
  test("Should be renderable", () => {
    render(
      <ExportDialog
        exportSelection={{
          type: "selection",
          exportTypes: [],
          exportNames: [],
          exportIds: [],
        }}
        open={false}
        allowFileStores={true}
      />,
    );
    expect(true).toBe(true);
  });
  describe("Validations should be enforced.", () => {
    describe("Exporting with filestores links", () => {
      test("but without being logged in should show a warning.", async () => {
        const user = userEvent.setup();
        mockAxios
          .onPost("/nfsExport/ajax/createQuickExportPlan")

          .reply(200, { ...CREATE_QUICK_EXPORT_PLAN });
        const { setProps } = await renderExportDialogWithRealI18n({ allowFileStores: true });
        act(() => {
          setProps({
            open: true,
            selection: {
              type: "selection",
              exportTypes: ["NORMAL"],
              exportNames: ["foo"],
              exportIds: ["1"],
            },
          });
        });
        await user.click(
          screen.getByRole("radio", {
            name: (name) => name.startsWith(".ZIP bundle containing .HTML files"),
          }),
        );
        await user.click(screen.getByRole("checkbox", { name: "Include filestore links" }));

        await user.click(screen.getByRole("button", { name: "Next" }));
        await waitFor(() => {
          expect(screen.getByText("Should linked RSpace documents be included in export?")).toBeVisible();
        });

        await user.click(screen.getByRole("button", { name: "Next" }));
        await waitFor(() => {
          expect(screen.getByText("Exported content contains 1 filestore link from 1 File System.")).toBeVisible();
        });

        await user.click(screen.getByRole("button", { name: "Export" }));
        await waitFor(() => {
          expect(
            within(screen.getByRole("dialog")).getByText(
              "You are not logged into all required File Systems and some filestore links won't be exported. Do you want to proceed without logging in?",
            ),
          ).toBeVisible();
        });
      });
      test("but without scanning should show a warning.", async () => {
        const user = userEvent.setup();
        /*
         * Here, we're setting loggedAs on the mocked samba file system because
         * the scanning warning is only shown if the user is logged in,
         * otherwise the logged in warning above takes precedence.
         */
        mockAxios.onPost("/nfsExport/ajax/createQuickExportPlan").reply(200, {
          ...CREATE_QUICK_EXPORT_PLAN,
          foundFileSystems: [
            {
              ...CREATE_QUICK_EXPORT_PLAN.foundFileSystems[0],
              loggedAs: "sambatest",
            },
          ],
        });
        const { setProps } = await renderExportDialogWithRealI18n({ allowFileStores: true });
        act(() => {
          setProps({
            open: true,
            selection: {
              type: "selection",
              exportTypes: ["NORMAL"],
              exportNames: ["foo"],
              exportIds: ["1"],
            },
          });
        });
        await user.click(
          screen.getByRole("radio", {
            name: (name) => name.startsWith(".ZIP bundle containing .HTML files"),
          }),
        );
        await user.click(screen.getByRole("checkbox", { name: "Include filestore links" }));

        await user.click(screen.getByRole("button", { name: "Next" }));
        await waitFor(() => {
          expect(screen.getByText("Should linked RSpace documents be included in export?")).toBeVisible();
        });

        await user.click(screen.getByRole("button", { name: "Next" }));
        await waitFor(() => {
          expect(screen.getByText("Exported content contains 1 filestore link from 1 File System.")).toBeVisible();
        });
        await waitFor(() => {
          expect(screen.getByText("You are logged into all File Systems referenced by filestore links.")).toBeVisible();
        });

        await user.click(screen.getByRole("button", { name: "Export" }));
        await waitFor(() => {
          expect(
            within(screen.getByRole("dialog")).getByText(
              "You have not performed links availability scan. We strongly recommend running it, as it will report any potential problems with accessing filestore link. Do you want to proceed without running the scan?",
            ),
          ).toBeVisible();
        });
      });
    });
  });
  describe("Controlled vocabulary terms", () => {
    // passes on its own, fails when run together
    test("Tags should be pre-populated from the tags on the documents", async () => {
      const user = userEvent.setup();
      mockAxios.onGet("/repository/ajax/repo/uiConfig").reply(200, [
        {
          repoName: "app.zenodo",
          subjects: [],
          license: {
            licenseRequired: true,
            otherLicensePermitted: false,
            licenses: [
              {
                licenseDefinition: {
                  url: "https://creativecommons.org/publicdomain/zero/1.0/",
                  name: "CC-0",
                },
                defaultLicense: null,
              },
            ],
          },
          otherProperties: [],
          linkedDMPs: null,
          options: {
            ZENODO_USER_TOKEN: "************************************************************",
          },
          displayName: "Zenodo",
        },
      ]);
      mockAxios.onPost("/export/ajax/exportRecordTagsPdfsAndDocs").reply(200, {
        data: [
          "BT-20 cell__RSP_EXTONT_URL_DELIM__http:__rspactags_forsl____rspactags_forsl__purl.obolibrary.org__rspactags_forsl__obo__rspactags_forsl__BTO_0001466__RSP_EXTONT_NAME_DELIM__test__RSP_EXTONT_VERSION_DELIM__1",
        ],
        error: null,
        success: true,
        errorMsg: null,
      });
      const { setProps } = renderExportDialog();
      act(() => {
        setProps({
          open: true,
          selection: {
            type: "selection",
            exportTypes: ["NORMAL"],
            exportNames: ["foo"],
            exportIds: ["1"],
          },
        });
      });

      await user.click(
        await screen.findByRole("radio", {
          name: (name) => name.startsWith("workspace:export.format.chooser.formats.pdfHeading"),
        }),
      );
      await user.click(screen.getByRole("checkbox", { name: "workspace:export.format.chooser.exportToRepository" }));

      await user.click(screen.getByRole("button", { name: "common:actions.next" }));
      await screen.findByRole("textbox", { name: "workspace:export.format.pdf.name" });
      await user.click(screen.getByRole("button", { name: "common:actions.next" }));
      await screen.findByRole("radio", { name: "Zenodo" });
      expect(await screen.findByRole("button", { name: "BT-20 cell" })).toBeVisible();
    });
  });
  describe("Completing the export, makes the right call to /export/ajax/exportArchive", () => {
    mockAxios.onGet("/repository/ajax/repo/uiConfig").reply(200, []);
    mockAxios.onGet("/export/ajax/defaultPDFConfig").reply(200, { data: { pageSize: "A4" } });
    mockAxios
      .onPost("/export/ajax/exportArchive")
      .reply(
        200,
        "Your export generation request has been submitted to the server. RSpace will notify you when the export is ready.",
      );
    test("allVersions is set by the version switch.", async () => {
      const user = userEvent.setup();
      await fc.assert(
        fc.asyncProperty(fc.boolean(), async (setAllVersionsSwitch) => {
          mockAxios.resetHistory();
          const { setProps } = renderExportDialog({ allowFileStores: true });
          act(() => {
            setProps({
              open: true,
              selection: {
                type: "selection",
                exportTypes: ["NORMAL"],
                exportNames: ["foo"],
                exportIds: ["1"],
              },
            });
          });
          await user.click(
            screen.getByRole("radio", {
              name: (name) => name.startsWith("workspace:export.format.chooser.formats.xmlHeading"),
            }),
          );
          if (setAllVersionsSwitch) {
            await user.click(
              await screen.findByRole("checkbox", {
                name: "workspace:export.format.chooser.includeAllVersions",
              }),
            );
          }
          const nextButton = screen.getByRole("button", { name: "common:actions.next" });
          await waitFor(() => expect(nextButton).toBeEnabled());
          await user.click(nextButton);
          const exportButton = await screen.findByRole("button", {
            name: "common:actions.export",
          });
          await user.click(exportButton);
          await waitFor(() => {
            expect(
              screen.getByText(
                "Your export generation request has been submitted to the server. RSpace will notify you when the export is ready.",
              ),
            ).toBeVisible();
          });
          expect(mockAxios.history.post.length).toBe(1);
          expect(JSON.parse(mockAxios.history.post[0].data).exportConfig.allVersions).toBe(setAllVersionsSwitch);
          expect(JSON.parse(mockAxios.history.post[0].data).exportConfig.archiveType).toBe("xml");
        }),
        { numRuns: 1 },
      );
    });
    test("Default page size checkbox should be sent.", async () => {
      mockAxios.onGet("/export/ajax/defaultPDFConfig").reply(200, { data: { pageSize: "A4" } });
      mockAxios
        .onPost("/export/ajax/export")
        .reply(
          200,
          "Your export generation request has been submitted to the server. RSpace will notify you when the export is ready.",
        );
      const user = userEvent.setup();
      const { setProps } = await renderExportDialogWithRealI18n();
      act(() => {
        setProps({
          open: true,
          selection: {
            type: "selection",
            exportTypes: ["NORMAL"],
            exportNames: ["foo"],
            exportIds: ["1"],
          },
        });
      });
      await user.click(await screen.findByRole("radio", { name: (name) => name.startsWith(".DOC file") }));
      await user.click(screen.getByRole("button", { name: "Next" }));
      await waitFor(() => expect(screen.getByRole("combobox")).toBeVisible());
      await user.click(screen.getByRole("combobox"));
      await user.click(screen.getByRole("option", { name: "Letter" }));
      await user.click(screen.getByRole("checkbox", { name: "Set LETTER as default." }));

      mockAxios.resetHistory();
      await user.click(screen.getByRole("button", { name: "Export" }));
      await waitFor(() => {
        expect(
          screen.getByText(
            "Your export generation request has been submitted to the server. RSpace will notify you when the export is ready.",
          ),
        ).toBeVisible();
      });
      expect(mockAxios.history.post.length).toBe(1);
      expect(JSON.parse(mockAxios.history.post[0].data).exportConfig.setPageSizeAsDefault).toBe(true);
      expect(JSON.parse(mockAxios.history.post[0].data).exportConfig.pageSize).toBe("LETTER");
    });
  });
  describe("When the dialog is rendered it is passed a selection.", () => {
    mockAxios.onGet("/repository/ajax/repo/uiConfig").reply(200, []);
    describe("On the second page of the dialog the name field should be set accordingly.", () => {
      mockAxios.onGet("/export/ajax/defaultPDFConfig").reply(200, { data: { pageSize: "A4" } });
      describe("When the selected export type is PDF", () => {
        test("and the selection is a set of documents.", async () => {
          const user = userEvent.setup();
          await fc.assert(
            fc.asyncProperty(
              arbDocumentSelection().filter(({ exportTypes }) =>
                exportTypes.every((t) => t === "NORMAL" || t === "NOTEBOOK"),
              ),
              async (selection) => {
                const { setProps } = renderExportDialog();
                act(() => {
                  setProps({ open: true, selection });
                });
                await user.click(
                  screen.getByRole("radio", {
                    name: (name) => name.startsWith("workspace:export.format.chooser.formats.pdfHeading"),
                  }),
                );
                await user.click(screen.getByRole("button", { name: "common:actions.next" }));
                await waitFor(() => {
                  expect(screen.getByRole("textbox")).toBeVisible();
                });
                expect(screen.getByRole("textbox")).toHaveValue(selection.exportNames[0].trimStart());
              },
            ),
            { numRuns: 1 },
          );
        });
        test("and the selection is a group.", async () => {
          const user = userEvent.setup();
          await fc.assert(
            fc.asyncProperty(arbGroupSelection, async (selection) => {
              const { setProps } = await renderExportDialogWithRealI18n();
              act(() => {
                setProps({ open: true, selection });
              });
              await user.click(screen.getByRole("radio", { name: (name) => name.startsWith("PDF file") }));

              await user.click(screen.getByRole("button", { name: "Next" }));
              await waitFor(() => {
                expect(screen.getByRole("textbox")).toBeVisible();
              });
              expect(screen.getByRole("textbox")).toHaveValue(`${selection.groupName} - all work`);
            }),
            { numRuns: 1 },
          );
        });
        test("and the selection is a user.", async () => {
          const user = userEvent.setup();
          await fc.assert(
            fc.asyncProperty(arbUserSelection, async (selection) => {
              const { setProps } = await renderExportDialogWithRealI18n();
              act(() => {
                setProps({ open: true, selection });
              });
              await user.click(screen.getByRole("radio", { name: (name) => name.startsWith("PDF file") }));

              await user.click(screen.getByRole("button", { name: "Next" }));
              await waitFor(() => {
                expect(screen.getByRole("textbox")).toBeVisible();
              });
              expect(screen.getByRole("textbox")).toHaveValue(`${selection.username} - all work`);
            }),
            { numRuns: 1 },
          );
        });
      });
      describe("When the selected export type is DOC", () => {
        test("and the selection is a single document.", async () => {
          const user = userEvent.setup();
          mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
          await fc.assert(
            fc.asyncProperty(
              arbDocumentSelection({ max: 1 }).filter(({ exportTypes }) => exportTypes.every((t) => t === "NORMAL")),
              async (selection) => {
                const { setProps } = renderExportDialog();
                act(() => {
                  setProps({ open: true, selection });
                });
                await user.click(
                  await screen.findByRole("radio", {
                    name: (name) => name.startsWith("workspace:export.format.chooser.formats.docHeading"),
                  }),
                );
                await user.click(screen.getByRole("button", { name: "common:actions.next" }));
                await waitFor(() => {
                  expect(screen.getByRole("textbox")).toBeVisible();
                });
                expect(screen.getByRole("textbox")).toHaveValue(selection.exportNames[0].trimStart());
              },
            ),
            { numRuns: 1 },
          );
        });
      });
    });
    describe("The page size displayed on the second page should be set by a call to /defaultPDFConfig", () => {
      test.each(["A4", "LETTER"])("PDF export: pageSize = %s", async (pageSize) => {
        const user = userEvent.setup();
        await fc.assert(
          fc.asyncProperty(
            arbDocumentSelection().filter(({ exportTypes }) =>
              exportTypes.every((t) => t === "NORMAL" || t === "NOTEBOOK"),
            ),
            async (selection) => {
              mockAxios
                .onGet("/export/ajax/defaultPDFConfig")

                .reply(200, { data: { pageSize } });
              const { setProps } = renderExportDialog();
              act(() => {
                setProps({ open: true, selection });
              });
              await user.click(
                await screen.findByRole("radio", {
                  name: (name) => name.startsWith("workspace:export.format.chooser.formats.pdfHeading"),
                }),
              );
              await user.click(screen.getByRole("button", { name: "common:actions.next" }));
              await waitFor(() => {
                const pageSizeLabel = screen.getByText("workspace:export.format.pdf.pageFormatLabel");
                if (!pageSizeLabel.parentElement) throw new Error("pageSizeLabel has no parent");
                expect(within(pageSizeLabel.parentElement).getByRole("combobox")).toHaveTextContent(
                  `workspace:export.format.pdf.pageSize.${PAGE_SIZE_KEY_SUFFIX[pageSize]}`,
                );
              });
            },
          ),
          { numRuns: 1 },
        );
      });
      test.each(["A4", "LETTER"])("DOC export: pageSize = %s", async (pageSize) => {
        const user = userEvent.setup();
        await fc.assert(
          fc.asyncProperty(
            arbDocumentSelection({ max: 1 }).filter(({ exportTypes }) => exportTypes.every((t) => t === "NORMAL")),
            async (selection) => {
              mockAxios
                .onGet("/export/ajax/defaultPDFConfig")

                .reply(200, { data: { pageSize } });
              const { setProps } = renderExportDialog();
              act(() => {
                setProps({ open: true, selection });
              });
              await user.click(
                await screen.findByRole("radio", {
                  name: (name) => name.startsWith("workspace:export.format.chooser.formats.docHeading"),
                }),
              );
              await user.click(screen.getByRole("button", { name: "common:actions.next" }));
              await waitFor(() => {
                expect(screen.getByRole("combobox")).toHaveTextContent(
                  `workspace:export.format.word.pageSize.${PAGE_SIZE_KEY_SUFFIX[pageSize]}`,
                );
              });
            },
          ),
          { numRuns: 1 },
        );
      });
    });
  });
});
