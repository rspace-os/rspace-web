import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import type { Identifier, PublishingState } from "../../../../../stores/definitions/Identifier";
import materialTheme from "../../../../../theme";
import PublishButton from "../PublishButton";
import { mockIGSNIdentifier } from "./mocking";
import "@/__tests__/__mocks__/matchMedia";

const PUBLISH = "common:actions.publish";
const REPUBLISH = "common:actions.republish";

const identifier = (overrides: Partial<Identifier>): Identifier => ({
  ...mockIGSNIdentifier("instrument"),
  ...overrides,
});

const renderButton = (id: Identifier) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <PublishButton identifier={id} />
    </ThemeProvider>,
  );

/*
 * RSDEV-1326. Two rules, from different sources:
 *  - a B2INST record whose review is over can never be published or republished, so the button is
 *    not offered at all rather than offered dead;
 *  - a LINKED identifier is a PID minted elsewhere, and every RSpace-owned operation on it is
 *    refused with 422 (ADR 0009), so it is never offered either, whichever provider it came from.
 * DataCite keeps its shipped behaviour, Republish on a findable DOI included.
 */
describe("PublishButton is not offered where publishing can never succeed", () => {
  test.each(["accepted", "expired", "declined", "cancelled"] as ReadonlyArray<PublishingState>)(
    "renders nothing for a B2INST identifier in %s",
    (state) => {
      renderButton(identifier({ doiType: "PIDINST_B2INST", state }));
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
    },
  );

  test("renders nothing for a linked identifier, whatever the provider", () => {
    renderButton(identifier({ doiType: "PIDINST_DATACITE", state: "findable", linked: true }));
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  test("still offers Publish for a B2INST draft", () => {
    renderButton(identifier({ doiType: "PIDINST_B2INST", state: "draft" }));
    expect(screen.getByRole("button", { name: PUBLISH })).toBeInTheDocument();
  });

  test("still offers Republish for a findable DataCite DOI RSpace minted", () => {
    renderButton(identifier({ doiType: "DATACITE_IGSN", state: "findable", linked: false }));
    expect(screen.getByRole("button", { name: REPUBLISH })).toBeInTheDocument();
  });
});
