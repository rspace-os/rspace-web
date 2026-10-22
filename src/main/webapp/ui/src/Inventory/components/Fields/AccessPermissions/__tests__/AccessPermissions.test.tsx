import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import { SampleCollection } from "@/stores/models/SampleModel";
import materialTheme from "@/theme";
import RsSet from "@/util/set";
import AccessPermissions from "..";

vi.mock("@/common/InvApiService", () => ({ default: {} }));

function renderFor(fieldOwner: ReturnType<typeof makeMockSample> | SampleCollection) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <AccessPermissions fieldOwner={fieldOwner} />
    </ThemeProvider>,
  );
}

describe("AccessPermissions", () => {
  test("explains that ownership control is required and keeps the controls disabled", () => {
    const sample = makeMockSample({ permittedActions: ["READ", "UPDATE"] });
    sample.setEditable(new Set(["sharingMode", "sharedWith"]), true);
    renderFor(sample);
    expect(screen.getByRole("alert")).toHaveTextContent("fields.accessPermissions.ownershipControlRequired");
    expect(screen.getByRole("radio", { name: /ownerGroups\.title/ })).toBeDisabled();
  });

  test("asks for edit mode when the user has ownership control but is not editing", () => {
    renderFor(makeMockSample());
    expect(screen.getByRole("alert")).toHaveTextContent("fields.accessPermissions.editFirst");
  });

  test("explains that edit access is also required when the user can transfer but not edit", () => {
    renderFor(makeMockSample({ permittedActions: ["READ", "CHANGE_OWNER"] }));
    expect(screen.getByRole("alert")).toHaveTextContent("fields.accessPermissions.ownershipControlRequired");
  });

  test("in batch edit, cannot be chosen for editing when one item is outside ownership control", () => {
    renderFor(
      new SampleCollection(
        new RsSet([makeMockSample({ id: 1 }), makeMockSample({ id: 2, permittedActions: ["READ", "UPDATE"] })]),
      ),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("fields.accessPermissions.ownershipControlRequired");
    expect(screen.queryByRole("checkbox", { name: "common:inputs.chooseToEdit.label" })).not.toBeInTheDocument();
  });

  test("in batch edit, can be chosen for editing when every item is under ownership control", () => {
    renderFor(new SampleCollection(new RsSet([makeMockSample({ id: 1 }), makeMockSample({ id: 2 })])));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "common:inputs.chooseToEdit.label" })).toBeInTheDocument();
  });
});
