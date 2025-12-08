/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import type React from "react";
import "@testing-library/jest-dom";
import fc from "fast-check";
import ContextMenuAction from "../ContextMenuAction";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

function OuterComponent({ onKeyDown, children }: { onKeyDown: () => void; children: React.ReactNode }) {
    return <div onKeyDown={onKeyDown}>{children}</div>;
}

function InnerComponent() {
    return <input />;
}

describe("ContextMenuAction", () => {
    test("When as a menuitem, keyDown events should not propagated through ContextMenuAction", async () => {
        const onKeyDown = jest.fn();
        render(
            <OuterComponent onKeyDown={onKeyDown}>
                <ContextMenuAction as="menuitem" onClick={() => {}} icon={<></>} label="Foo" disabledHelp="">
                    <InnerComponent />
                </ContextMenuAction>
            </OuterComponent>,
        );

        const input = await screen.findByRole("textbox");
        fireEvent.keyDown(input, {});

        expect(onKeyDown).not.toHaveBeenCalled();
    });

    describe("Disabled state", () => {
        test("When disabled, should render aria-disabled.", () => {
            fc.assert(
                fc.property(fc.string(), (disabledHelp) => {
                    cleanup();
                    render(
                        <ContextMenuAction
                            as="menuitem"
                            icon={<></>}
                            disabledHelp={disabledHelp}
                            label="Foo"
                            onClick={() => {}}
                        />,
                    );
                    expect(screen.getByRole("menuitem", { name: /^Foo/ })).toHaveAttribute(
                        "aria-disabled",
                        (disabledHelp !== "").toString(),
                    );
                }),
            );
        });
    });
});
