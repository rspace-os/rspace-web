/*
 */
import Galaxy, {AttachedRecords} from "../Galaxy";
import React from "react";
import axios from "@/common/axios";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import "@testing-library/jest-dom/vitest";
import MockAdapter from "axios-mock-adapter";
import {act} from "react-dom/test-utils";
import { type SpyInstance, describe, it, test, expect, vi, beforeEach } from "vitest";

const mockAxios = new MockAdapter(axios);
// simulating the actual events fired in plugin.min.js code for galaxy upload button
const activeEditorMock = {
  storedEventName: "",
  storedFunction: () => {
  },
  on: function (eventName: string, targetFunction: () => {}) {
    this.storedEventName = eventName;
    this.storedFunction = targetFunction;
  },
  handleEvent: async function (eventName: string) {
    if (eventName === this.storedEventName) {
      this.storedFunction();
    }
  }
};
const tinymceMock = {activeEditor: activeEditorMock};
Object.defineProperty(parent, "tinymce", {value: tinymceMock});
const attachedRecords: Array<AttachedRecords> = [{
  id: 'attachedID',
  html: document.createElement('div')
}]
const createdGalaxyHistory = {
  "id": "f8e722da311b8793",
  "name": "RSPACE_Untitled document_SD375v4_Data_FD229379_1",
}
let windowParentPostMessageSpy: SpyInstance;
describe("Galaxy Upload Data tests ", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    windowParentPostMessageSpy = vi.spyOn(window.parent, "postMessage")
    mockAxios.onGet("/integration/integrationInfo").reply(200, {
      data: {
        name: "GALAXY",
        displayName: "Galaxy",
        available: true,
        enabled: true,
        oauthConnected: false,
        "options": {
          "GALAXY_CONFIGURED_SERVERS": [{
            "alias": "galaxy eu server",
            "url": "https://usegalaxy.eu"
          }, {
            "alias": "galaxy us server",
            "url": "https://usegalaxy.org"
          }],
          "7": {
            "GALAXY_URL": "https://usegalaxy.org",
            "GALAXY_ALIAS": "galaxy us server",
            "GALAXY_APIKEY": "USKEY"
          },
          "8": {
            "GALAXY_URL": "https://usegalaxy.eu",
            "GALAXY_APIKEY": "EUKEY",
            "GALAXY_ALIAS": "galaxy eu server"
          }
        }
      },
      error: null,
      success: true,
      errorMsg: null,
    });
  });
  describe("displays attached data with select checkboxes ", () => {
    it("renders radio button server choice with eu as default ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={[]}/>);
      const radios = await screen.findAllByRole("radio", {
        name: /galaxy eu server/,
      });
      expect(radios[0]).toBeInTheDocument();
    });
    it("displays empty table when there is no attached data ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={[]}/>);
      const columnHeadings = await screen.findAllByRole("columnheader");
      expect(columnHeadings[1]).toHaveTextContent('File');
    });
    it("displays attached data ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}/>);
      const gridCells = screen.getAllByRole("gridcell");
      expect(gridCells[1]).toContainHTML(attachedRecords[0].html.outerHTML);
    });
    it("initial rendering posts no data selected ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}/>);
      await screen.findAllByRole("radio", { name: /galaxy eu server/ });
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "no-data-selected"}, "*");
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "data-selected"}, "*");
    });
    it("selecting data posts  data selected ", async () => {
      const user = userEvent.setup();
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}/>);
      await screen.findAllByRole("radio", { name: /galaxy eu server/ });
      const checkBoxes = await screen.getAllByRole("checkbox", {
        name: /select row/i,
      });
      const checkBox = checkBoxes[0];
      await user.click(checkBox);
      await waitFor(() => {
        expect(checkBox).toBeChecked();
      });
    });
  });
  describe("uploads data to Galaxy ", () => {
    beforeEach(async () => {
      mockAxios
      .onPost("/apps/galaxy/setUpDataInGalaxyFor")
      .reply(200, createdGalaxyHistory);
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}/>);
      await screen.findAllByRole("radio", { name: /galaxy eu server/ });
      const checkBox = await screen.getAllByRole("checkbox")[0];
      fireEvent.click(checkBox);

    });

    it("posts no-data selected while uploading", async () => {

      windowParentPostMessageSpy.mockClear();
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "no-data-selected"}, "*");
      await act(async () => {
        await activeEditorMock.handleEvent("galaxy-used");
      });
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "no-data-selected"}, "*");
    });
    it("once upload complete displays link to new Galaxy History", async () => {

      await act(async () => {
        await activeEditorMock.handleEvent("galaxy-used");
      });
      expect((await screen.findAllByText(/Your new history can be viewed here/i))[0]).toBeInTheDocument();
      expect((await screen.findAllByRole('link', {name: 'RSPACE_Untitled document_SD375v4_Data_FD229379_1'}))[0])
      .toHaveAttribute('href', 'https://usegalaxy.org/histories/view?id=f8e722da311b8793');
    });
    it("once upload complete events are dispatched", async () => {
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "uploading-complete"}, "*");
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "enableClose"}, "*");
      await act(async () => {
        await activeEditorMock.handleEvent("galaxy-used");
      });
      expect((await screen.findAllByText(/Your new history can be viewed here/i))[0]).toBeInTheDocument();
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "uploading-complete"}, "*");
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "enableClose"}, "*");
    });
  });

  describe("Handles errors", () => {
    beforeEach(async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}/>);
      const checkBox = await screen.getAllByRole("checkbox")[0];
      fireEvent.click(checkBox);

    });
    it("displays error message if 403 returned", async () => {
      mockAxios
      .onPost("/apps/galaxy/setUpDataInGalaxyFor")
      .reply(403, []);
      await act(async () => {
        await activeEditorMock.handleEvent("galaxy-used");
      });
      expect((await screen.findAllByText("Error"))[0]).toBeInTheDocument();
      expect(
          await screen.findByText(/Invalid Galaxy API Key Please re-enter your API Key on the Apps page/i)
      ).toBeInTheDocument();
    });

    it("displays error message if 500 returned", async () => {
      mockAxios
      .onPost("/apps/galaxy/setUpDataInGalaxyFor")
      .reply(500, []);
      await act(async () => {
        await activeEditorMock.handleEvent("galaxy-used");
      });
      expect((await screen.findAllByText("Error"))[0]).toBeInTheDocument();
      expect(await screen.findByRole("alert")).toBeInTheDocument();
    });
  });
});
