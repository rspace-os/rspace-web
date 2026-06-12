import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { describe, expect, test } from "vitest";
import axios from "@/common/axios";
import { Optional } from "../../../../util/optional";
import DMPAssistant from "../DMPAssistant";

import "@/__tests__/__mocks__/matchMedia";
describe("DMPAssistant", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const { baseElement } = render(
        <DMPAssistant
          integrationState={{
            mode: "DISABLED",
            credentials: {
              ACCESS_TOKEN: Optional.empty(),
            },
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  test("Should have a connect button when the user is not authenticated.", () => {
    render(
      <DMPAssistant
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.empty(),
          },
        }}
        update={() => {}}
      />,
    );

    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();
  });
  test("Should have a disconnect button when the user is authenticated.", () => {
    render(
      <DMPAssistant
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.present("some token"),
          },
        }}
        update={() => {}}
      />,
    );

    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: /disconnect/i })).toBeVisible();
  });
  test("Should flip to connected when the DMPASSISTANT_CONNECTED event fires.", async () => {
    render(
      <DMPAssistant
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.empty(),
          },
        }}
        update={() => {}}
      />,
    );

    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();

    // the OAuth popup's connected.jsp dispatches this event on the opener window
    act(() => {
      window.dispatchEvent(new Event("DMPASSISTANT_CONNECTED"));
    });

    expect(await screen.findByRole("button", { name: /disconnect/i })).toBeVisible();
  });
  test("Clicking disconnect issues the DELETE call and flips back to connect.", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios.onDelete("/apps/dmpassistant/connect").reply(200);
    render(
      <DMPAssistant
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.present("some token"),
          },
        }}
        update={() => {}}
      />,
    );

    fireEvent.click(screen.getByRole("button"));
    fireEvent.click(screen.getByRole("button", { name: /disconnect/i }));

    await waitFor(() => expect(mockAxios.history.delete.length).toBe(1));
    expect(await screen.findByRole("button", { name: /connect/i })).toBeVisible();
  });
});
