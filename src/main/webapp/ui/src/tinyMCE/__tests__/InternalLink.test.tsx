import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import InternalLink from "../InternalLink";

describe("InternalLink", () => {
  it("shows each revision's modifier and sorts by username, including missing modifiers", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/workspace/revisionHistory/ajax/42/versions", () =>
        HttpResponse.json({
          data: [
            { version: 1, revision: 11, modifiedBy: "zoe" },
            { version: 2, revision: 12, modifiedBy: null },
            { version: 3, revision: 13, modifiedBy: "amy" },
          ].map((revision) => ({
            ...revision,
            id: 42,
            name: `Document version ${revision.version}`,
            oid: { idString: "SD42" },
            ownerId: 7,
            ownerFullName: "Original Owner",
            modificationDate: "2026-11-11T12:00:00Z",
          })),
        }),
      ),
    );
    render(<InternalLink id={42} />);
    const table = screen.getByRole("table", { name: "workspace:tinymce.internalLink.versionTableLabel" });
    const latest = await screen.findByRole("table", { name: "workspace:tinymce.internalLink.dialogTitle" });
    expect(within(latest).getByText("amy")).toBeInTheDocument();
    const rows = () => within(table).getAllByRole("checkbox");
    expect(within(rows()[0]).getByText("amy")).toBeInTheDocument();
    expect(within(rows()[2]).getByText("zoe")).toBeInTheDocument();
    expect(within(rows()[1]).getAllByRole("cell")[3]).toBeEmptyDOMElement();
    const sort = within(table).getByRole("button", { name: "workspace:tinymce.internalLink.columns.modifiedBy" });
    await user.click(sort);
    expect(within(rows()[0]).getByText("zoe")).toBeInTheDocument();
    expect(within(rows()[1]).getByText("amy")).toBeInTheDocument();
    expect(within(rows()[2]).getAllByRole("cell")[3]).toBeEmptyDOMElement();
    await user.click(sort);
    expect(within(rows()[0]).getAllByRole("cell")[3]).toBeEmptyDOMElement();
    expect(within(rows()[1]).getByText("amy")).toBeInTheDocument();
    expect(within(rows()[2]).getByText("zoe")).toBeInTheDocument();
  });
});
