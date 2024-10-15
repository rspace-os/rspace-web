/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../__mocks__/matchMedia";
import React, { useState } from "react";
import {
  render,
  cleanup,
  screen,
  act,
  waitFor,
  within,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import ExportDialog, { type ExportSelection } from "../ExportDialog";
import fc from "fast-check";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import CREATE_QUICK_EXPORT_PLAN from "./createQuickExportPlan";
import each from "jest-each";
import { type UseState } from "../../util/types";
import Alerts from "../../components/Alerts/Alerts";
import { sleep } from "../../util/Util";

window.fetch = jest.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve({}),
  })
);

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const arbUserSelection = fc.record<{
  type: "user",
  username: string,
  exportIds: Array<string>,
}>({
  type: fc.constant("user"),
  username: fc.string({ minLength: 1 }),
  exportIds: fc.constant([]),
});

const arbGroupSelection = fc.record<{
  type: "group",
  groupId: string,
  groupName: string,
  exportIds: Array<string>,
}>({
  type: fc.constant("group"),
  groupId: fc.string({ minLength: 1 }),
  groupName: fc.string({ minLength: 1 }),
  exportIds: fc.constant([]),
});

const arbDocumentSelection = (args: { max?: number } = {}) =>
  fc.integer({ min: 1, max: args.max ?? 20 }).chain((n) =>
    fc.record<{
      type: "selection",
      exportTypes: Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">,
      exportNames: Array<string>,
      exportIds: Array<string>,
    }>({
      type: fc.constant("selection"),
      exportTypes: fc.array(
        fc.constantFrom("MEDIA_FILE", "NOTEBOOK", "NORMAL", "FOLDER"),
        { minLength: n, maxLength: n }
      ),
      exportNames: fc.array(fc.string({ minLength: 1 }), {
        minLength: n,
        maxLength: n,
      }),
      exportIds: fc.array(fc.string({ minLength: 1 }), {
        minLength: n,
        maxLength: n,
      }),
    })
  );

function renderExportDialog({
  allowFileStores,
}: { allowFileStores?: boolean } = {}): { setProps: (any) => void } {
  let setProps;
  const Wrapper = () => {
    const [open, setOpen] = useState(false);
    const [selection, setSelection]: UseState<ExportSelection> = useState({
      type: "selection",
      exportTypes: [],
      exportNames: [],
      exportIds: [],
    });
    setProps = ({
      selection: s,
      open: o,
    }: {
      selection: ExportSelection,
      open: boolean,
    }) => {
      setSelection(s);
      setOpen(o);
    };
    return (
      <Alerts>
        <ExportDialog
          exportSelection={selection}
          open={open}
          allowFileStores={allowFileStores ?? false}
        />
      </Alerts>
    );
  };
  render(<Wrapper />);
  if (!setProps) throw new Error("setProps is not initialised");
  return { setProps };
}

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
      />
    );

    expect(true).toBe(true);
  });

  describe("Validations should be enforced.", () => {
    describe("Exporting with filestores links", () => {
      test("but without being logged in should show a warning.", async () => {
        mockAxios
          .onPost("/nfsExport/ajax/createQuickExportPlan")
          .reply(200, { ...CREATE_QUICK_EXPORT_PLAN });

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

        act(() => {
          screen
            .getByRole("radio", { name: /^.ZIP bundle containing .HTML files/ })
            .click();
        });

        act(() => {
          screen
            .getByRole("checkbox", { name: "Include filestore links" })
            .click();
        });

        act(() => {
          screen.getByRole("button", { name: "Next" }).click();
        });

        await waitFor(() => {
          expect(
            screen.getByText(
              "Should linked RSpace documents be included in export?"
            )
          ).toBeVisible();
        });

        act(() => {
          screen.getByRole("button", { name: "Next" }).click();
        });

        await waitFor(() => {
          expect(
            screen.getByText(
              "Exported content contains 1 filestore link from 1 File System."
            )
          ).toBeVisible();
        });

        act(() => {
          screen.getByRole("button", { name: "Export" }).click();
        });

        await waitFor(() => {
          expect(
            within(screen.getByRole("dialog")).getByText(
              "You are not logged into all required File Systems and some filestore links won't be exported. Do you want to proceed without logging in?"
            )
          ).toBeVisible();
        });
      });

      test("but without scanning should show a warning.", async () => {
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

        act(() => {
          screen
            .getByRole("radio", { name: /^.ZIP bundle containing .HTML files/ })
            .click();
        });

        act(() => {
          screen
            .getByRole("checkbox", { name: "Include filestore links" })
            .click();
        });

        act(() => {
          screen.getByRole("button", { name: "Next" }).click();
        });

        await waitFor(() => {
          expect(
            screen.getByText(
              "Should linked RSpace documents be included in export?"
            )
          ).toBeVisible();
        });

        act(() => {
          screen.getByRole("button", { name: "Next" }).click();
        });

        await waitFor(() => {
          expect(
            screen.getByText(
              "Exported content contains 1 filestore link from 1 File System."
            )
          ).toBeVisible();
        });

        await waitFor(() => {
          expect(
            screen.getByText(
              "You are logged into all File Systems referenced by filestore links."
            )
          ).toBeVisible();
        });

        await act(() => {
          screen.getByRole("button", { name: "Export" }).click();
        });

        await waitFor(() => {
          expect(
            within(screen.getByRole("dialog")).getByText(
              "You have not performed links availability scan. We strongly recommend running it, as it will report any potential problems with accessing filestore link. Do you want to proceed without running the scan?"
            )
          ).toBeVisible();
        });
      });
    });
  });

  describe("Controlled vocabulary terms", () => {
    // passes on its own, fails when run together
    test("Tags should be pre-populated from the tags on the documents", async () => {
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
            ZENODO_USER_TOKEN:
              "************************************************************",
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

      fireEvent.click(await screen.findByRole("radio", { name: /PDF/ }));
      fireEvent.click(
        screen.getByRole("checkbox", { name: /Export to a repository/ })
      );
      fireEvent.click(screen.getByRole("button", { name: /Next/ }));
      expect(await screen.findByRole("textbox", { name: /File name/ }));
      fireEvent.click(screen.getByRole("button", { name: /Next/ }));
      expect(await screen.findByRole("radio", { name: /Zenodo/ }));

      await sleep(1000);

      expect(screen.getByRole("button", { name: /BT-20/ })).toBeVisible();
    });
  });
  describe("Completing the export, makes the right call to /export/ajax/exportArchive", () => {
    mockAxios.onGet("/repository/ajax/repo/uiConfig").reply(200, []);
    mockAxios
      .onGet("/export/ajax/defaultPDFConfig")
      .reply(200, { data: { pageSize: "A4" } });
    mockAxios
      .onPost("/export/ajax/exportArchive")
      .reply(
        200,
        "Your export generation request has been submitted to the server. RSpace will notify you when the export is ready."
      );
    test("allVersions is set by the version switch.", async () => {
      await fc.assert(
        fc
          .asyncProperty(fc.boolean(), async (setAllVersionsSwitch) => {
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

            act(() => {
              screen
                .getByRole("radio", {
                  name: /^.ZIP bundle containing .XML files/,
                })
                .click();
            });

            if (setAllVersionsSwitch) {
              await act(async () => {
                (
                  await screen.findByRole("checkbox", {
                    name: /^Check to include all previous versions of your documents/,
                  })
                ).click();
              });
            }

            act(() => {
              screen.getByRole("button", { name: "Next" }).click();
            });

            await waitFor(() => {
              expect(
                screen.getByRole("button", { name: "Export" })
              ).toBeVisible();
            });

            fireEvent.click(screen.getByRole("button", { name: "Export" }));

            await waitFor(() => {
              expect(screen.getByText(/submitted to the server/)).toBeVisible();
            });

            expect(mockAxios.history.post.length).toBe(1);
            expect(
              JSON.parse(mockAxios.history.post[0].data).exportConfig
                .allVersions
            ).toBe(setAllVersionsSwitch);
            expect(
              JSON.parse(mockAxios.history.post[0].data).exportConfig
                .archiveType
            ).toBe("xml");
          })
          .afterEach(cleanup),
        { numRuns: 1 }
      );
    });
    test("Default page size checkbox should be sent.", async () => {
      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { data: { pageSize: "A4" } });
      mockAxios
        .onPost("/export/ajax/export")
        .reply(
          200,
          "Your export generation request has been submitted to the server. RSpace will notify you when the export is ready."
        );
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

      fireEvent.click(await screen.findByRole("radio", { name: /^.DOC/ }));
      fireEvent.click(screen.getByRole("button", { name: "Next" }));
      await waitFor(() => expect(screen.getByRole("combobox")).toBeVisible());
      fireEvent.mouseDown(screen.getByRole("combobox"));
      fireEvent.click(screen.getByRole("option", { name: "Letter" }));
      fireEvent.click(
        screen.getByRole("checkbox", { name: "Set LETTER as default." })
      );
      mockAxios.resetHistory();

      fireEvent.click(screen.getByRole("button", { name: "Export" }));
      await waitFor(() => {
        expect(screen.getByText(/submitted to the server/)).toBeVisible();
      });

      expect(mockAxios.history.post.length).toBe(1);
      expect(
        JSON.parse(mockAxios.history.post[0].data).exportConfig
          .setPageSizeAsDefault
      ).toBe(true);
      expect(
        JSON.parse(mockAxios.history.post[0].data).exportConfig.pageSize
      ).toBe("LETTER");
    });
  });

  describe("When the dialog is rendered it is passed a selection.", () => {
    mockAxios.onGet("/repository/ajax/repo/uiConfig").reply(200, []);
    describe("On the second page of the dialog the name field should be set accordingly.", () => {
      mockAxios
        .onGet("/export/ajax/defaultPDFConfig")
        .reply(200, { data: { pageSize: "A4" } });
      describe("When the selected export type is PDF", () => {
        test("and the selection is a set of documents.", async () => {
          await fc.assert(
            fc
              .asyncProperty(
                arbDocumentSelection().filter(({ exportTypes }) =>
                  exportTypes.every((t) => t === "NORMAL" || t === "NOTEBOOK")
                ),
                async (selection) => {
                  const { setProps } = renderExportDialog();
                  act(() => {
                    setProps({ open: true, selection });
                  });
                  fireEvent.click(screen.getByRole("radio", { name: /^PDF/ }));
                  act(() => {
                    screen.getByRole("button", { name: "Next" }).click();
                  });

                  await waitFor(() => {
                    expect(screen.getByRole("textbox")).toBeVisible();
                  });

                  expect(screen.getByRole("textbox")).toHaveValue(
                    selection.exportNames[0].trimStart()
                  );
                }
              )
              .afterEach(cleanup),
            { numRuns: 1 }
          );
        });
        test("and the selection is a group.", async () => {
          await fc.assert(
            fc
              .asyncProperty(arbGroupSelection, async (selection) => {
                const { setProps } = renderExportDialog();
                act(() => {
                  setProps({ open: true, selection });
                });
                fireEvent.click(screen.getByRole("radio", { name: /^PDF/ }));
                act(() => {
                  screen.getByRole("button", { name: "Next" }).click();
                });

                await waitFor(() => {
                  expect(screen.getByRole("textbox")).toBeVisible();
                });

                expect(screen.getByRole("textbox")).toHaveValue(
                  selection.groupName + " - all work"
                );
              })
              .afterEach(cleanup),
            { numRuns: 1 }
          );
        });
        test("and the selection is a user.", async () => {
          await fc.assert(
            fc
              .asyncProperty(arbUserSelection, async (selection) => {
                const { setProps } = renderExportDialog();
                act(() => {
                  setProps({ open: true, selection });
                });
                fireEvent.click(screen.getByRole("radio", { name: /^PDF/ }));
                act(() => {
                  screen.getByRole("button", { name: "Next" }).click();
                });

                await waitFor(() => {
                  expect(screen.getByRole("textbox")).toBeVisible();
                });

                expect(screen.getByRole("textbox")).toHaveValue(
                  selection.username + " - all work"
                );
              })
              .afterEach(cleanup),
            { numRuns: 1 }
          );
        });
      });
      describe("When the selected export type is DOC", () => {
        test("and the selection is a single document.", async () => {
          mockAxios
            .onGet("deploymentproperties/ajax/property")
            .reply(200, true);
          await fc.assert(
            fc
              .asyncProperty(
                arbDocumentSelection({ max: 1 }).filter(({ exportTypes }) =>
                  exportTypes.every((t) => t === "NORMAL")
                ),
                async (selection) => {
                  const { setProps } = renderExportDialog();
                  act(() => {
                    setProps({ open: true, selection });
                  });

                  fireEvent.click(
                    await screen.findByRole("radio", { name: /^.DOC/ })
                  );

                  act(() => {
                    screen.getByRole("button", { name: "Next" }).click();
                  });

                  await waitFor(() => {
                    expect(screen.getByRole("textbox")).toBeVisible();
                  });

                  expect(screen.getByRole("textbox")).toHaveValue(
                    selection.exportNames[0].trimStart()
                  );
                }
              )
              .afterEach(cleanup),
            { numRuns: 1 }
          );
        });
      });
    });
    describe("The page size displayed on the second page should be set by a call to /defaultPDFConfig", () => {
      each(["A4", "LETTER"]).test("PDF export: pageSize = %s", (pageSize) => {
        fc.assert(
          fc
            .asyncProperty(
              arbDocumentSelection().filter(({ exportTypes }) =>
                exportTypes.every((t) => t === "NORMAL" || t === "NOTEBOOK")
              ),
              async (selection) => {
                mockAxios
                  .onGet("/export/ajax/defaultPDFConfig")
                  .reply(200, { data: { pageSize } });

                const { setProps } = renderExportDialog();
                act(() => {
                  setProps({ open: true, selection });
                });
                fireEvent.click(
                  await screen.findByRole("radio", { name: /^.DOC/ })
                );
                act(() => {
                  screen.getByRole("button", { name: "Next" }).click();
                });
                await waitFor(() => {
                  expect(
                    screen.getByRole("button", { name: pageSize })
                  ).toBeVisible();
                });
              }
            )
            .afterEach(cleanup),
          { numRuns: 1 }
        );
      });
      each(["A4", "LETTER"]).test("DOC export: pageSize = %s", (pageSize) => {
        fc.assert(
          fc
            .asyncProperty(
              arbDocumentSelection({ max: 1 }).filter(({ exportTypes }) =>
                exportTypes.every((t) => t === "NORMAL")
              ),
              async (selection) => {
                mockAxios
                  .onGet("/export/ajax/defaultPDFConfig")
                  .reply(200, { data: { pageSize } });

                const { setProps } = renderExportDialog();
                act(() => {
                  setProps({ open: true, selection });
                });
                fireEvent.click(
                  await screen.findByRole("radio", { name: /^.DOC/ })
                );
                act(() => {
                  screen.getByRole("button", { name: "Next" }).click();
                });
                await waitFor(() => {
                  expect(
                    screen.getByRole("button", { name: pageSize })
                  ).toBeVisible();
                });
              }
            )
            .afterEach(cleanup),
          { numRuns: 1 }
        );
      });
    });
  });
});
