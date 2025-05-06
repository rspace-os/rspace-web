/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import ProtocolsIO from "../ProtocolsIO";
import { Optional } from "../../../../util/optional";
import { axe, toHaveNoViolations } from "jest-axe";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ProtocolsIO", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const { baseElement } = render(
        <ProtocolsIO
          integrationState={{
            mode: "DISABLED",
            credentials: {
              ACCESS_TOKEN: Optional.empty(),
            },
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(await screen.findByRole("dialog")).toBeVisible();

      expect(await axe(baseElement)).toHaveNoViolations();
    });
  });
  test("Should have a connect button when the user is not authenticated.", () => {
    render(
      <ProtocolsIO
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.empty(),
          },
        }}
        update={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByRole("button", { name: /connect/i })).toBeVisible();
  });
  test("Should have a disconnect button when the user is authenticated.", () => {
    render(
      <ProtocolsIO
        integrationState={{
          mode: "DISABLED",
          credentials: {
            ACCESS_TOKEN: Optional.present("some token"),
          },
        }}
        update={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByRole("button", { name: /disconnect/i })).toBeVisible();
  });
});
