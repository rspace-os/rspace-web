import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import materialTheme from "@/theme";
import AccessPermissions from "..";

vi.mock("@/common/InvApiService", () => ({ default: {} }));

function renderFor(sample: ReturnType<typeof makeMockSample>) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <AccessPermissions fieldOwner={sample} />
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
});
