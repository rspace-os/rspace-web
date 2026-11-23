// Side-effect mock imports must precede the component import so vi.mock registers first.
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/muiTransitions";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import IntegrationCard from "../IntegrationCard";

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
          docLink="teams"
          website=""
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          setupSection={<></>}
        />
      </ThemeProvider>,
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
          docLink="teams"
          website=""
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          setupSection={<></>}
        />
      </ThemeProvider>,
    );
    expect(screen.getByText("Something, something, something...")).toBeVisible();
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
          docLink="teams"
          website=""
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          setupSection={<></>}
        />
      </ThemeProvider>,
    );
    expect(screen.getByRole("presentation")).toHaveAttribute("src", "image url");
  });
  test("When card is tapped, a dialog should be shown.", async () => {
    const user = userEvent.setup();
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
          docLink="teams"
          website=""
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          setupSection={<></>}
        />
      </ThemeProvider>,
    );

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "SomeIntegration" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
  test("DialogContent should be shown once card has been tapped.", async () => {
    const user = userEvent.setup();
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
          docLink="teams"
          website=""
          setupSection="Some dialog content"
        />
      </ThemeProvider>,
    );

    expect(screen.queryByText("Some dialog content")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "SomeIntegration" }));
    expect(screen.getByText("Some dialog content")).toBeInTheDocument();
  });
  test("When tapped, the enable button should invoke update.", async () => {
    const user = userEvent.setup();
    const update = vi.fn();
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
          docLink="teams"
          website=""
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          setupSection={<></>}
        />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "SomeIntegration" }));

    await user.click(screen.getByRole("button", { name: "apps:integrationCard.enable" }));
    expect(update).toHaveBeenCalledWith("ENABLED");
  });
  test("When tapped, the disable button should invoke update.", async () => {
    const user = userEvent.setup();
    const update = vi.fn();
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
          docLink="teams"
          website=""
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          setupSection={<></>}
        />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "SomeIntegration" }));

    await user.click(screen.getByRole("button", { name: "apps:integrationCard.disable" }));
    expect(update).toHaveBeenCalledWith("DISABLED");
  });
});
