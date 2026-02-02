import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import OwnersGroupsTable from "../OwnersGroupsTable";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("OwnersGroupsTable", () => {
  test("Each name should be a link to the groups page.", () => {
    render(
      <OwnersGroupsTable
        groups={[
          {
            id: 1,
            globalId: "GP1",
            name: "A group",
            uniqueName: "A group",
            _links: [],
          },
        ]}
      />
    );

    expect(screen.getByText("A group")).toHaveAttribute(
      "href",
      "/groups/view/1"
    );
  });
});


