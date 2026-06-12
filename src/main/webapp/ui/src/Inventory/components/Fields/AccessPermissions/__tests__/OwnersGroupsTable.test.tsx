import { render, screen } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test } from "vitest";

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
      />,
    );
    expect(screen.getByText("A group")).toHaveAttribute("href", "/groups/view/1");
  });
});
