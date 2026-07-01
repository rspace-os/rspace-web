import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { mockIGSNAttrs } from "../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import IdentifierPublicPage from "../IdentifierPublicPage";

vi.mock("../../../Inventory/components/Fields/Identifiers/MapViewer", () => {
  const MockMapViewer = () => <></>;
  MockMapViewer.displayName = "MockMapViewer";
  return { default: MockMapViewer };
});

const mockAxios = new MockAdapter(axios);
describe("IdentifierPublicPage", () => {
  describe("Optional Fields", () => {
    test("Should render dates correctly", async () => {
      const identifier = mockIGSNAttrs();
      identifier.dates = [
        {
          value: "2024-09-05T12:56:35.965Z",
          type: "ACCEPTED",
        },
      ];
      mockAxios.onGet("/api/inventory/v1/public/view/1").reply(200, {
        identifiers: [identifier],
        description: null,
        tags: [],
      });

      render(<IdentifierPublicPage publicId={"1"} />);
      await waitFor(() => {
        expect(screen.getByText("public:headings.dates")).toBeVisible();
      });
      expect(screen.getByRole("group", { name: "public:labels.dates" })).toHaveTextContent("2024-09-05");
    });
  });
});
