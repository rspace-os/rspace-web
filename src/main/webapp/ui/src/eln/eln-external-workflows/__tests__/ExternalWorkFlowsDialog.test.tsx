import { fireEvent, render, screen } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, test } from "vitest";
import axios from "@/common/axios";

import ExternalWorkflowInvocations, { type InvocationsAndDataCount } from "../ExternalWorkflowInvocations";

const mockAxios = new MockAdapter(axios);
const GalaxyDataSummary = {
  rspaceFieldName: "Data",
  galaxyHistoryName: "RSPACE_Untitled document_SD375v3_Data_FD229379",
  galaxyHistoryId: "473f68f2250fb0ff",
  galaxyDataNames: [{ fileName: "Galaxy1-_anaphase_1750407920234.jpg__1753183694203.jpg", id: "1" }],
  galaxyInvocationName: "Invocation Name",
  galaxyInvocationStatus: "FAILED",
  galaxyInvocationId: null,
  galaxyBaseUrl: "https://usegalaxy.eu",
  createdOn: 1752834698272,
};
const GalaxyInvocationsAndDataCount: InvocationsAndDataCount = {
  dataCount: 2,
  invocationCount: 1,
};
describe("Renders with table of  data", () => {
  beforeEach(() => {
    mockAxios.onGet("/apps/galaxy/galaxyDataExists/1").reply(200, true);
    mockAxios.onGet("/apps/galaxy/getSummaryGalaxyDataForRSpaceField/1").reply(200, [GalaxyDataSummary]);
    mockAxios.onGet("/apps/galaxy/getGalaxyInvocationCountForRSpaceField/1").reply(200, GalaxyInvocationsAndDataCount);
  });

  test("displays WorkFlow Data table headers", async () => {
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false} />);
    const toggleButton = await screen.findByRole("button", {
      name: /externalWorkflows.showWorkflowsAria/i,
    });
    expect(toggleButton).toBeEnabled();
    fireEvent.click(toggleButton);
    expect(await screen.findByText("apps:externalWorkflows.dialogTitle")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.columns.dataUploaded")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.columns.container")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.columns.invocation")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.columns.status")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.columns.created")).toBeInTheDocument();
  });
  test("displays WorkFlow Data", async () => {
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false} />);
    const toggleButton = await screen.findByRole("button", {
      name: /externalWorkflows.showWorkflowsAria/i,
    });
    expect(toggleButton).toBeEnabled();
    expect(toggleButton).toBeInTheDocument();
    fireEvent.click(toggleButton);
    expect(await screen.findByText("apps:externalWorkflows.dialogTitle")).toBeInTheDocument();
    const gridCells = screen.getAllByRole("gridcell");
    expect(gridCells[0]).toHaveTextContent("Galaxy1-_anaphase_1750407920234.jpg__1753183694203.jpg");
    expect(gridCells[1]).toHaveTextContent("RSPACE_Untitled document_SD375v3_Data_FD229379");
    expect(gridCells[2]).toHaveTextContent("Invocation Name");
    expect(gridCells[3]).toHaveTextContent("FAILED");
    expect(gridCells[4]).toHaveTextContent(new Date(GalaxyDataSummary.createdOn).toLocaleString());
  });
});
describe("Handles errors", () => {
  beforeEach(() => {
    mockAxios.onGet("/apps/galaxy/galaxyDataExists/1").reply(200, true);
  });
  test("displays error message if 404 returned", async () => {
    mockAxios.onGet("/apps/galaxy/getGalaxyInvocationCountForRSpaceField/1").reply(404, []);
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false} />);
    expect(await screen.findByText("apps:externalWorkflows.error.title")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.error.notFound")).toBeInTheDocument();
  });
  test("displays error message if 403 returned", async () => {
    mockAxios.onGet("/apps/galaxy/getGalaxyInvocationCountForRSpaceField/1").reply(403, []);
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false} />);
    expect(await screen.findByText("apps:externalWorkflows.error.title")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.error.unauthorized")).toBeInTheDocument();
  });

  test("displays error message if 500 returned", async () => {
    mockAxios.onGet("/apps/galaxy/getGalaxyInvocationCountForRSpaceField/1").reply(500, []);
    render(<ExternalWorkflowInvocations fieldId={"1"} isForNotebookPage={false} />);
    expect(await screen.findByText("apps:externalWorkflows.error.title")).toBeInTheDocument();
    expect(await screen.findByText("apps:externalWorkflows.error.unknown")).toBeInTheDocument();
  });
});
