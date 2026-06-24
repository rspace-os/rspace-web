import { act, render, screen } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import { DeploymentPropertyContext } from "../../hooks/api/useDeploymentProperty";
import { mkValidator } from "../../util/Validator";
import FormatChoice from "../FormatChoice";

const mockAxios = new MockAdapter(axios);
beforeEach(() => {
  vi.clearAllMocks();
  mockAxios.reset();
});
describe("FormatChoice", () => {
  describe("Repository switch", () => {
    test("When the repo/uiConfig endpoint returns an empty list, the switch should be disabled.", async () => {
      mockAxios.onGet("/repository/ajax/repo/uiConfig").reply(200, []);
      render(
        <FormatChoice
          exportSelection={{
            type: "selection",
            exportTypes: [],
            exportNames: [],
            exportIds: [],
          }}
          exportConfigUpdate={() => {}}
          archiveType={""}
          allowFileStores={false}
          repoSelected={false}
          fileStoresSelected={false}
          allVersions={false}
          updateFileStores={() => {}}
          validator={mkValidator()}
        />,
      );
      expect(
        await screen.findByRole("checkbox", {
          name: "export.format.chooser.noRepoSetup",
        }),
      ).toBeDisabled();
    });
    test("When the repo/uiConfig endpoint returns a repository, the switch should be enabled.", async () => {
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
          linkedDMPs: [
            {
              dmpUserInternalId: 1,
              dmpTitle: "saved one distibution",
              dmpId: "e6c97551-f074-4b88-9779-5bdc4c13c694",
            },
          ],
          options: {
            ZENODO: "",
          },
          displayName: "Zenodo",
        },
      ]);
      render(
        <FormatChoice
          exportSelection={{
            type: "selection",
            exportTypes: [],
            exportNames: [],
            exportIds: [],
          }}
          exportConfigUpdate={() => {}}
          archiveType={""}
          allowFileStores={false}
          repoSelected={false}
          fileStoresSelected={false}
          allVersions={false}
          updateFileStores={() => {}}
          validator={mkValidator()}
        />,
      );
      expect(await screen.findByRole("checkbox", { name: "export.format.chooser.exportToRepository" })).toBeEnabled();
    });
  });
  describe("Export as Word .doc is dependent on the document conversion lib aspose.", () => {
    test("When aspose is enabled, .doc export option is available", async () => {
      mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
      await act(() =>
        render(
          <DeploymentPropertyContext.Provider value={new Map()}>
            <FormatChoice
              exportSelection={{
                type: "selection",
                exportTypes: ["NORMAL"],
                exportNames: ["foo"],
                exportIds: ["1"],
              }}
              exportConfigUpdate={() => {}}
              archiveType={""}
              allowFileStores={false}
              repoSelected={false}
              fileStoresSelected={false}
              allVersions={false}
              updateFileStores={() => {}}
              validator={mkValidator()}
            />
          </DeploymentPropertyContext.Provider>,
        ),
      );
      const wordElement = screen.getByText("export.format.chooser.formats.docHeading");
      expect(wordElement).toBeInTheDocument();
    });
    test("When aspose is disabled, .doc export option isn't present", async () => {
      mockAxios.onGet("deploymentproperties/ajax/property").reply(200, false);
      await act(() =>
        render(
          <DeploymentPropertyContext.Provider value={new Map()}>
            <FormatChoice
              exportSelection={{
                type: "selection",
                exportTypes: ["NORMAL"],
                exportNames: ["foo"],
                exportIds: ["1"],
              }}
              exportConfigUpdate={() => {}}
              archiveType={""}
              allowFileStores={false}
              repoSelected={false}
              fileStoresSelected={false}
              allVersions={false}
              updateFileStores={() => {}}
              validator={mkValidator()}
            />
          </DeploymentPropertyContext.Provider>,
        ),
      );
      const wordElement = screen.queryAllByText("export.format.chooser.formats.docHeading");
      expect(wordElement.length).toBe(0);
    });
  });
  /*
   * There are various conditions that must be met for the export to a Word
   * file to be allowed, with each condition displaying a justification when it
   * causes the radio option to be disabled.
   */
  describe("Export as Word .doc format is correctly restricted.", () => {
    test("When more than one document is selected, .doc export should be denied.", async () => {
      mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
      await act(() =>
        render(
          <DeploymentPropertyContext.Provider value={new Map()}>
            <FormatChoice
              exportSelection={{
                type: "selection",
                exportTypes: ["NORMAL", "NORMAL"],
                exportNames: ["foo", "bar"],
                exportIds: ["1", "2"],
              }}
              exportConfigUpdate={() => {}}
              archiveType={""}
              allowFileStores={false}
              repoSelected={false}
              fileStoresSelected={false}
              allVersions={false}
              updateFileStores={() => {}}
              validator={mkValidator()}
            />
          </DeploymentPropertyContext.Provider>,
        ),
      );
      expect(
        screen.getByRole("radio", {
          name: "export.format.chooser.formats.docHeading export.format.chooser.wordErrors.multiple",
        }),
      ).toBeDisabled();
    });
    test("When the selected document is a folder, .doc export should be denied.", async () => {
      mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
      await act(() =>
        render(
          <DeploymentPropertyContext.Provider value={new Map()}>
            <FormatChoice
              exportSelection={{
                type: "selection",
                exportTypes: ["FOLDER"],
                exportNames: ["foo"],
                exportIds: ["1"],
              }}
              exportConfigUpdate={() => {}}
              archiveType={""}
              allowFileStores={false}
              repoSelected={false}
              fileStoresSelected={false}
              allVersions={false}
              updateFileStores={() => {}}
              validator={mkValidator()}
            />
          </DeploymentPropertyContext.Provider>,
        ),
      );
      expect(
        screen.getByRole("radio", {
          name: "export.format.chooser.formats.docHeading export.format.chooser.wordErrors.folder",
        }),
      ).toBeDisabled();
    });
    test("When the selected document is a notebook, .doc export should be denied.", async () => {
      mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
      await act(() =>
        render(
          <DeploymentPropertyContext.Provider value={new Map()}>
            <FormatChoice
              exportSelection={{
                type: "selection",
                exportTypes: ["NOTEBOOK"],
                exportNames: ["foo"],
                exportIds: ["1"],
              }}
              exportConfigUpdate={() => {}}
              archiveType={""}
              allowFileStores={false}
              repoSelected={false}
              fileStoresSelected={false}
              allVersions={false}
              updateFileStores={() => {}}
              validator={mkValidator()}
            />
          </DeploymentPropertyContext.Provider>,
        ),
      );
      expect(
        screen.getByRole("radio", {
          name: "export.format.chooser.formats.docHeading export.format.chooser.wordErrors.notebook",
        }),
      ).toBeDisabled();
    });
    test("When the selected document is a media file, .doc export should be denied.", async () => {
      mockAxios.onGet("deploymentproperties/ajax/property").reply(200, true);
      await act(() =>
        render(
          <DeploymentPropertyContext.Provider value={new Map()}>
            <FormatChoice
              exportSelection={{
                type: "selection",
                exportTypes: ["MEDIA_FILE"],
                exportNames: ["foo"],
                exportIds: ["1"],
              }}
              exportConfigUpdate={() => {}}
              archiveType={""}
              allowFileStores={false}
              repoSelected={false}
              fileStoresSelected={false}
              allVersions={false}
              updateFileStores={() => {}}
              validator={mkValidator()}
            />
          </DeploymentPropertyContext.Provider>,
        ),
      );
      expect(
        screen.getAllByRole("radio", {
          name: "export.format.chooser.formats.docHeading export.format.chooser.wordErrors.allMedia",
        })[0],
      ).toBeDisabled();
    });
  });
  describe("Export as .pdf format is correctly restricted.", () => {
    test("When the selected documents are all media files, .pdf export should be denied.", async () => {
      await act(() =>
        render(
          <FormatChoice
            exportSelection={{
              type: "selection",
              exportTypes: ["MEDIA_FILE", "MEDIA_FILE"],
              exportNames: ["foo", "bar"],
              exportIds: ["1", "2"],
            }}
            exportConfigUpdate={() => {}}
            archiveType={""}
            allowFileStores={false}
            repoSelected={false}
            fileStoresSelected={false}
            allVersions={false}
            updateFileStores={() => {}}
            validator={mkValidator()}
          />,
        ),
      );
      expect(
        screen.getAllByRole("radio", {
          name: "export.format.chooser.formats.pdfHeading export.format.chooser.formats.pdfUnavailable",
          // selecting first such radio because second will be for .doc export
        })[0],
      ).toBeDisabled();
    });
  });
});
