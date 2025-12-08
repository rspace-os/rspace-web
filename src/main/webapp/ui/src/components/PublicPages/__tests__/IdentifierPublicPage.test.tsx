/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "../../../../__mocks__/matchMedia";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { mockIGSNAttrs } from "../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import IdentifierPublicPage from "../IdentifierPublicPage";

jest.mock("../../../Inventory/components/Fields/Identifiers/MapViewer", () => {
    const MockMapViewer = () => <></>;
    MockMapViewer.displayName = "MockMapViewer";
    return MockMapViewer;
});

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

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
                expect(screen.getByText("Dates")).toBeVisible();
            });
            expect(screen.getByRole("group", { name: /dates/ })).toHaveTextContent("2024-09-05");
        });
    });
});
