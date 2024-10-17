/*
 * @jest-environment jsdom
 */
//@flow strict
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import IntegrationCard from "../IntegrationCard";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import "../../../../__mocks__/matchMedia";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("IntegrationCard", () => {
  test("Name should be shown.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "ENABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={() => {}}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection={<></>}
        />
      </ThemeProvider>
    );

    expect(screen.getByText("SomeIntegration")).toBeVisible();
  });

  test("Explanatory text should be shown.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "ENABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={() => {}}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection={<></>}
        />
      </ThemeProvider>
    );

    expect(
      screen.getByText("Something, something, something...")
    ).toBeVisible();
  });

  test("Logo image should be shown.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "ENABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={() => {}}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection={<></>}
        />
      </ThemeProvider>
    );

    expect(screen.getByRole("presentation")).toHaveAttribute(
      "src",
      "image url"
    );
  });

  test("When card is tapped, a dialog should be shown.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "ENABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={() => {}}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection={<></>}
        />
      </ThemeProvider>
    );

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  test("DialogContent should be shown once card has been tapped.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "ENABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={() => {}}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection="Some dialog content"
        />
      </ThemeProvider>
    );

    expect(screen.queryByText("Some dialog content")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByText("Some dialog content")).toBeInTheDocument();
  });

  test("When tapped, the enable button should invoke update.", () => {
    const update = jest.fn<
      ["ENABLED" | "DISABLED" | "UNAVAILABLE" | "EXTERNAL"],
      void,
    >();

    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "DISABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={update}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection={<></>}
        />
      </ThemeProvider>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.click(screen.getByRole("button", { name: "ENABLE" }));

    expect(update).toHaveBeenCalledWith("ENABLED");
  });

  test("When tapped, the disable button should invoke update.", () => {
    const update = jest.fn<
      ["ENABLED" | "DISABLED" | "UNAVAILABLE" | "EXTERNAL"],
      void,
    >();

    render(
      <ThemeProvider theme={materialTheme}>
        <IntegrationCard
          name="SomeIntegration"
          integrationState={{ mode: "ENABLED", credentials: {} }}
          explanatoryText="Something, something, something..."
          image="image url"
          color={{ hue: 0, saturation: 100, lightness: 50 }}
          update={update}
          usageText=""
          helpLinkText="test"
          docLink=""
          website=""
          setupSection={<></>}
        />
      </ThemeProvider>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.click(screen.getByRole("button", { name: "DISABLE" }));

    expect(update).toHaveBeenCalledWith("DISABLED");
  });
});
