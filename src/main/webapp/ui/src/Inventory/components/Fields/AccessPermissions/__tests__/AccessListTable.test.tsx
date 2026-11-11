import { render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import AccessListTable from "../AccessListTable";

test("names each sharing checkbox after its group", () => {
  render(
    <AccessListTable
      sharedWith={[
        {
          group: { id: 1, globalId: "GP1", name: "Imaging lab", uniqueName: "imaging", _links: [] },
          shared: true,
          itemOwnerGroup: false,
        },
      ]}
      disabled={false}
      onCheckboxClick={vi.fn()}
      onAdditionalGroup={vi.fn()}
    />,
  );
  expect(screen.getByRole("checkbox", { name: "Imaging lab" })).toBeChecked();
});
