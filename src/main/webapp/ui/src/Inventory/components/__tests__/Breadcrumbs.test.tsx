import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import Breadcrumbs from "../Breadcrumbs";

vi.mock("../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../stores/stores/RootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));
describe("Breadcrumbs", () => {
  describe("When the passed record is deleted", () => {
    test("In Trash should be shown.", () => {
      const subsample = makeMockSubSample({
        deleted: true,
      });

      render(<Breadcrumbs record={subsample} />);
      expect(screen.getByRole("navigation")).toHaveTextContent("In Trash");
    });
  });
});
