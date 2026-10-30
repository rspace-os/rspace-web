import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { PHISHING_DESCRIPTION } from "@/__tests__/mocks/inventoryRichTextMocks";
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
      expect(screen.getByRole("group", { name: "public:labels.dates" })).toMatchTextContent("2024-09-05");
    });
  });

  describe("Description", () => {
    test("shows the description's text and none of its form controls or images", async () => {
      mockAxios.onGet("/api/inventory/v1/public/view/1").reply(200, {
        identifiers: [{ ...mockIGSNAttrs(), customFieldsOnPublicPage: true }],
        description: PHISHING_DESCRIPTION,
        tags: [],
      });

      render(<IdentifierPublicPage publicId={"1"} />);

      expect(await screen.findByText("Calibrated light microscope.")).toBeInTheDocument();
      expect(screen.getByText("Your session expired. Sign in again to see the full record.")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Sign in" })).not.toBeInTheDocument();
      expect(screen.queryByPlaceholderText("Username")).not.toBeInTheDocument();
      expect(screen.queryByPlaceholderText("RSpace password")).not.toBeInTheDocument();
      expect(screen.queryByRole("img", { name: "tracking pixel" })).not.toBeInTheDocument();
    });
  });
});
