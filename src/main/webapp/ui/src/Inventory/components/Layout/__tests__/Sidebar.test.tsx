/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "../../../../../__mocks__/matchMedia";
import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import MockAdapter from "axios-mock-adapter";
import { axe, toHaveNoViolations } from "jest-axe";
import axios from "@/common/axios";
import { LandmarksProvider } from "../../../../components/LandmarksContext";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import Sidebar from "../Sidebar";

expect.extend(toHaveNoViolations);

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("Sidebar", () => {
    test("Should have no axe violations.", async () => {
        mockAxios.onGet("livechatProperties").reply(200, {
            livechatEnabled: false,
        });
        const rootStore = makeMockRootStore({
            uiStore: {
                alwaysVisibleSidebar: true,
                sidebarOpen: true,
            },
            searchStore: {
                search: {
                    benchSearch: true,
                },
            },
        });
        const { container } = render(
            <ThemeProvider theme={materialTheme}>
                <LandmarksProvider>
                    <storesContext.Provider value={rootStore}>
                        <Sidebar id="foo" />
                    </storesContext.Provider>
                </LandmarksProvider>
            </ThemeProvider>,
        );

        expect(await axe(container)).toHaveNoViolations();
    });
});
