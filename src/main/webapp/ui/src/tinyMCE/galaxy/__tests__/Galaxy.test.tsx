/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import Galaxy, {AttachedRecords} from "../Galaxy";
import React from "react";
import axios from "@/common/axios";
import {fireEvent, render, screen} from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import {act} from "react-dom/test-utils";
const mockAxios = new MockAdapter(axios);
// simulating the actual events fired in plugin.min.js code for galaxy upload button
const activeEditorMock = {
  storedEventName:"",
  storedFunction: ()=>{},
  on: function (eventName: string, targetFunction: ()=>{}) {
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
Object.defineProperty(parent, "tinymce", { value: tinymceMock });
const attachedRecords : Array<AttachedRecords> = [{
  id: 'attachedID',
  html: document.createElement('div')
}]
const createdGalaxyHistory = {
  "id" : "f8e722da311b8793",
  "name" : "RSPACE_Untitled document_SD375v4_Data_FD229379_1",
}
let windowParentPostMessageSpy: jest.SpyInstance;
describe("Galaxy Upload Data tests ", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    windowParentPostMessageSpy = jest.spyOn(window.parent, "postMessage")
  });
  describe("displays attached data with select checkboxes ", () => {

    it("displays empty table when there is no attached data ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={[]}
                     galaxy_web_url={"galaxy_web_url"}/>);
      const columnHeadings = await screen.findAllByRole("columnheader");
      expect(columnHeadings[1]).toHaveTextContent('File');
    });
    it("displays attached data ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}
                     galaxy_web_url={"galaxy_web_url"}/>);
      const gridCells = screen.getAllByRole("gridcell");
      expect(gridCells[1]).toContainHTML(attachedRecords[0].html.outerHTML);
    });
    it("initial rendering posts no data selected ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}
                     galaxy_web_url={"galaxy_web_url"}/>);
      const checkBox = await screen.getAllByRole("checkbox")[0];
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "no-data-selected"}, "*");
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "data-selected"}, "*");
    });
    it("selecting data posts  data selected ", async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}
                     galaxy_web_url={"galaxy_web_url"}/>);
      const checkBox = await screen.getAllByRole("checkbox")[0];
      fireEvent.click(checkBox);
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "data-selected"}, "*");
    });
  });
  describe("uploads data to Galaxy ", () => {
    beforeEach(async () => {
      mockAxios
      .onPost("/apps/galaxy/setUpDataInGalaxyFor")
      .reply(200, createdGalaxyHistory);
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}
                     galaxy_web_url={"galaxy_web_url"}/>);
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
      expect(await screen.findByText(/Your new history can be viewed here/i)).toBeInTheDocument();
      expect(await screen.findByRole('link', {name: 'RSPACE_Untitled document_SD375v4_Data_FD229379_1'}))
      .toHaveAttribute('href', 'galaxy_web_url/histories/view?id=f8e722da311b8793');
    });
    it("once upload complete events are dispatched", async () => {
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "uploading-complete"}, "*");
      expect(windowParentPostMessageSpy).not.toHaveBeenCalledWith({"mceAction": "enableClose"}, "*");
      await act(async () => {
        await activeEditorMock.handleEvent("galaxy-used");
      });
      expect(await screen.findByText(/Your new history can be viewed here/i)).toBeInTheDocument();
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "uploading-complete"}, "*");
      expect(windowParentPostMessageSpy).toHaveBeenCalledWith({"mceAction": "enableClose"}, "*");
    });
  });

  describe("Handles errors", () => {
    beforeEach(async () => {
      render(<Galaxy fieldId="1" recordId="2" attachedFileInfo={attachedRecords}
                     galaxy_web_url={"galaxy_web_url"}/>);
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
      expect(await screen.findByText("Error")).toBeInTheDocument();
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
      expect(await screen.findByText("Error")).toBeInTheDocument();
      expect(
          await screen.findByText(/Unknown issue, please investigate whether your Galaxy Server is running/i)
      ).toBeInTheDocument();
    });
  });
});
