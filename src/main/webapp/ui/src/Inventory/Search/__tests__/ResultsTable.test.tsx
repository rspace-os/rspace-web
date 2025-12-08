/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "../../../../__mocks__/matchMedia";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import ApiServiceBase from "../../../common/ApiServiceBase";
import SearchContext from "../../../stores/contexts/Search";
import { containerAttrs } from "../../../stores/models/__tests__/ContainerModel/mocking";
import { personAttrs } from "../../../stores/models/__tests__/PersonModel/mocking";
import MemoisedFactory from "../../../stores/models/Factory/MemoisedFactory";
import Search from "../../../stores/models/Search";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../stores/stores-context";
import materialTheme from "../../../theme";
import { menuIDs } from "../../../util/menuIDs";
import ResultsTable from "../ResultsTable";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("Results Table", () => {
    describe("Pagination", () => {
        test('When there are fewer items than the page size, the page size menu should show the count as "ALL"', async () => {
            const search = new Search({
                factory: new MemoisedFactory(),
            });

            jest.spyOn(ApiServiceBase.prototype, "query").mockImplementation(
                () =>
                    Promise.resolve({
                        data: {
                            containers: [
                                containerAttrs({
                                    globalId: "IC0",
                                    id: 0,
                                    owner: personAttrs(),
                                }),
                                containerAttrs({
                                    globalId: "IC1",
                                    id: 1,
                                    owner: personAttrs(),
                                }),
                                containerAttrs({
                                    globalId: "IC2",
                                    id: 2,
                                    owner: personAttrs(),
                                }),
                                containerAttrs({
                                    globalId: "IC3",
                                    id: 3,
                                    owner: personAttrs(),
                                }),
                                containerAttrs({
                                    globalId: "IC4",
                                    id: 4,
                                    owner: personAttrs(),
                                }),
                                containerAttrs({
                                    globalId: "IC5",
                                    id: 5,
                                    owner: personAttrs(),
                                }),
                            ],
                        },
                    }) as ReturnType<typeof ApiServiceBase.prototype.query>,
            );
            await waitFor(() => {
                void search.setupAndPerformInitialSearch({});
            });

            const rootStore = makeMockRootStore({});

            render(
                <ThemeProvider theme={materialTheme}>
                    <storesContext.Provider value={rootStore}>
                        <SearchContext.Provider
                            value={{
                                search,
                                differentSearchForSettingActiveResult: search,
                                scopedResult: null,
                            }}
                        >
                            <ResultsTable contextMenuId={menuIDs.RESULTS} />
                        </SearchContext.Provider>
                    </storesContext.Provider>
                </ThemeProvider>,
            );

            within(screen.getByRole("navigation")).getByRole("combobox");
        });
    });
});
