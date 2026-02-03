import { test, describe, expect } from 'vitest';
import React from "react";
import { render, screen } from "@testing-library/react";

import OwnersGroupsTable from "../OwnersGroupsTable";
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

