/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import axios from "@/common/axios";
import {render, screen, fireEvent} from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import ExternalWorkflowInvocations from "../ExternalWorkflowInvocations";
const mockAxios = new MockAdapter(axios);
const GalaxyDataSummary = {
  "rspaceFieldName" : "Data",
  "galaxyHistoryName" : "RSPACE_Untitled document_SD375v3_Data_FD229379",
  "galaxyHistoryId" : "473f68f2250fb0ff",
  "galaxyDataNames" : "Galaxy1-_anaphase_1750407920234.jpg__1753183694203.jpg",
  "galaxyInvocationName" : 'Invocation Name',
  "galaxyInvocationStatus" : 'FAILED',
  "galaxyInvocationId" : null,
  "galaxyBaseUrl" : "https://usegalaxy.eu",
  "createdOn" : 1752834698272
}
describe("Renders with table of  data ", () => {
  beforeEach(() => {
    mockAxios
    .onGet("/apps/galaxy/galaxyDataExists/1")
    .reply(200, true);
    mockAxios
    .onGet("/apps/galaxy/getSummaryGalaxyDataForRSpaceField/1")
    .reply(200, [GalaxyDataSummary]);
  });

  it("displays WorkFlow Data table headers", async () => {
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false}/>);
    expect(await screen.findByRole("button")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button"));
    expect(await screen.findByText(/Galaxy WorkFlow Data/i)).toBeInTheDocument();
    expect(await screen.findByText("Data Uploaded")).toBeInTheDocument();
    expect(await screen.findByText("Container/Galaxy History")).toBeInTheDocument();
    expect(await screen.findByText("Invocation")).toBeInTheDocument();
    expect(await screen.findByText("Invocation Status")).toBeInTheDocument();
    expect(await screen.findByText("Invocation Created")).toBeInTheDocument();
  });
  it("displays WorkFlow Data ", async () => {
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false}/>);
    expect(await screen.findByRole("button")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button"));
    expect(await screen.findByText(/Galaxy WorkFlow Data/i)).toBeInTheDocument();
    const gridCells = screen.getAllByRole("gridcell");
    expect(gridCells[0]).toHaveTextContent('Galaxy1-_anaphase_1750407920234.jpg__1753183694203.jpg');
    expect(gridCells[1]).toHaveTextContent('RSPACE_Untitled document_SD375v3_Data_FD229379');
    expect(gridCells[2]).toHaveTextContent('Invocation Name');
    expect(gridCells[3]).toHaveTextContent('FAILED');
    expect(gridCells[4]).toHaveTextContent(new Date(GalaxyDataSummary.createdOn).toLocaleString());
  });

});
describe("Handles errors", () => {
  beforeEach(() => {
    mockAxios
    .onGet("/apps/galaxy/galaxyDataExists/1")
    .reply(200, true);
  });
  it("displays error message if 404 returned", async () => {
    mockAxios
    .onGet("/apps/galaxy/getSummaryGalaxyDataForRSpaceField/1")
    .reply(404, []);
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false}/>);
    expect(await screen.findByText("Error")).toBeInTheDocument();
    expect(
        await screen.findByText(/Unable to retrieve any relevant results./i)
    ).toBeInTheDocument();
  });
  it("displays error message if 403 returned", async () => {
    mockAxios
    .onGet("/apps/galaxy/getSummaryGalaxyDataForRSpaceField/1")
    .reply(403, []);
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false}/>);
    expect(await screen.findByText("Error")).toBeInTheDocument();
    expect(
        await screen.findByText(/Invalid Galaxy API Key Please re-enter your API Key on the Apps page/i)
    ).toBeInTheDocument();
  });

  it("displays error message if 500 returned", async () => {
    mockAxios
    .onGet("/apps/galaxy/getSummaryGalaxyDataForRSpaceField/1")
    .reply(500, []);
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false}/>);
    expect(await screen.findByText("Error")).toBeInTheDocument();
    expect(
        await screen.findByText(/Unknown issue, please investigate whether your Galaxy Server is running/i)
    ).toBeInTheDocument();
  })
});
