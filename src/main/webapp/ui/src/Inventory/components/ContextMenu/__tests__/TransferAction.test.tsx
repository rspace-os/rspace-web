/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import Dialog from "@mui/material/Dialog";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import materialTheme from "../../../../theme";
import TransferAction from "../TransferAction";

jest.mock("@mui/material/Dialog", () =>
    jest.fn(({ children }) => {
        return <>{children}</>;
    }),
);
jest.mock("../../../../common/InvApiService", () => ({}));

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("TransferAction", () => {
    test("Dialog should close when cancel is tapped.", async () => {
        const user = userEvent.setup();
        render(
            <ThemeProvider theme={materialTheme}>
                <TransferAction as="button" disabled="" closeMenu={() => {}} selectedResults={[makeMockContainer()]} />
            </ThemeProvider>,
        );

        await waitFor(() => {
            expect(Dialog).toHaveBeenCalledWith(expect.objectContaining({ open: false }), expect.anything());
        });

        await user.click(screen.getAllByText("Transfer")[0]);

        await waitFor(() => {
            expect(Dialog).toHaveBeenCalledWith(expect.objectContaining({ open: true }), expect.anything());
        });

        await user.click(screen.getByText("Cancel"));

        await waitFor(() => {
            expect(Dialog).toHaveBeenLastCalledWith(expect.objectContaining({ open: false }), expect.anything());
        });
    });
});
