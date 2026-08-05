import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import type { PidinstPublishingState } from "../../../../../stores/definitions/Identifier";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import { IdentifiersList } from "../Identifiers";
import { mockIGSNIdentifier } from "./mocking";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";

import materialTheme from "../../../../../theme";

const sample1: InventoryRecord = makeMockSample();
sample1.identifiers = [mockIGSNIdentifier("sample")];
const container1: InventoryRecord = makeMockContainer();

container1.identifiers = [mockIGSNIdentifier("container")];
describe("Identifiers section", () => {
  describe("When an identifier exists", () => {
    test("Identifier fields sections are rendered", () => {
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={sample1} />
        </ThemeProvider>,
      );
      expect(container).toHaveTextContent("fields.identifiers.wrapper.required.title");
      expect(container).toHaveTextContent("fields.identifiers.wrapper.recommended.title");
    });
  });
  describe("When an identifier exists for container", () => {
    test("Required fields are rendered", () => {
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={container1} />
        </ThemeProvider>,
      );
      expect(container).toHaveTextContent("Material Sample");
    });
  });
  describe("When viewing a historical version", () => {
    test("Preview, Publish/Republish and Delete/Retract are all disabled", () => {
      const historicalSample: InventoryRecord = makeMockSample({
        version: 1,
        historicalVersion: true,
      });
      historicalSample.identifiers = [mockIGSNIdentifier("sample")];
      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={historicalSample} />
        </ThemeProvider>,
      );
      expect(screen.getByRole("button", { name: "inventory:fields.identifiers.list.preview" })).toBeDisabled();
      expect(screen.getByRole("button", { name: /republish|publish/i })).toBeDisabled();
      expect(screen.getByRole("button", { name: /delete|retract/i })).toBeDisabled();
    });
  });

  /*
   * B2INST returns the Invenio review-request status verbatim (submitted, accepted, declined,
   * cancelled, expired), none of which are DataCite states. Rendering must never throw on one.
   */
  describe("When a PIDINST identifier carries a B2INST review state", () => {
    test.each<PidinstPublishingState>(["submitted", "accepted", "declined", "cancelled", "expired", "created"])(
      "state '%s' renders without crashing",
      (state) => {
        const instrument: InventoryRecord = makeMockSample();
        instrument.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "PIDINST_B2INST", state }];

        expect(() =>
          render(
            <ThemeProvider theme={materialTheme}>
              <IdentifiersList activeResult={instrument} />
            </ThemeProvider>,
          ),
        ).not.toThrow();
      },
    );

    test("the submitted state is shown to the user", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "PIDINST_B2INST", state: "submitted" }];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(screen.getByTestId("identifier-state")).toHaveTextContent("fields.identifiers.list.stateLabels.submitted");
    });

    /*
     * The reason the state label ends in a catch-all rather than an exhaustive match: the server
     * passes the provider's status through verbatim, so a value this frontend has never heard of
     * must degrade to showing the raw state. Before that catch-all existed the match threw during
     * render and the error boundary blanked the whole Identifiers section. Anyone restoring an
     * exhaustive match should fail here rather than in production.
     */
    test("a state this frontend does not model degrades to the raw value instead of throwing", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [
        {
          ...mockIGSNIdentifier("sample"),
          doiType: "PIDINST_B2INST",
          // deliberately outside PublishingState: a status only a future provider would send
          state: "under_embargo" as PidinstPublishingState,
        },
      ];

      expect(() =>
        render(
          <ThemeProvider theme={materialTheme}>
            <IdentifiersList activeResult={instrument} />
          </ThemeProvider>,
        ),
      ).not.toThrow();

      expect(screen.getByTestId("identifier-state")).toHaveTextContent("under_embargo");
    });

    test("Publish is disabled while a community review is open", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "PIDINST_B2INST", state: "submitted" }];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(screen.getByRole("button", { name: /republish|publish/i })).toBeDisabled();
    });

    /*
     * B2INST has no retract operation, so retractDoi throws for every review state, and Delete is
     * only offered for "draft". The button rendering at all is new: these states used to throw during
     * render. It must therefore be disabled rather than offer an action that always errors.
     */
    test.each<PidinstPublishingState>(["created", "submitted", "accepted", "declined", "cancelled", "expired"])(
      "Retract is disabled for the '%s' review state, since B2INST cannot retract",
      (state) => {
        const instrument: InventoryRecord = makeMockSample();
        instrument.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "PIDINST_B2INST", state }];

        render(
          <ThemeProvider theme={materialTheme}>
            <IdentifiersList activeResult={instrument} />
          </ThemeProvider>,
        );

        // exact key, not /retract/i: the label key "deleteOrRetract.retract" contains both words,
        // so a loose regex would pass for the Delete label too
        expect(
          screen.getByRole("button", { name: "inventory:fields.identifiers.list.deleteOrRetract.retract" }),
        ).toBeDisabled();
      },
    );

    /*
     * The gate is provider-keyed, so it must also catch a state this frontend does not model: the
     * server stores whatever status the provider reported, and only when the response carries one.
     * A state-keyed gate left these on the old path with an enabled "Retract" that always errors.
     */
    test("Retract is disabled for an unmodelled B2INST state, not just the known six", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [
        {
          ...mockIGSNIdentifier("sample"),
          doiType: "PIDINST_B2INST",
          state: "under_embargo" as PidinstPublishingState,
        },
      ];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(
        screen.getByRole("button", { name: "inventory:fields.identifiers.list.deleteOrRetract.retract" }),
      ).toBeDisabled();
    });

    /*
     * The other half of the gate, and the mutation the B2INST tests cannot catch: broadening the
     * predicate to a bare `state !== "draft"` would keep every B2INST test green while silently
     * killing Retract for published DataCite DOIs, which is the main production use of this button.
     */
    test("Retract stays enabled for a published DataCite IGSN, which B2INST gating must not affect", () => {
      const sample: InventoryRecord = makeMockSample();
      sample.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "DATACITE_IGSN", state: "findable" }];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={sample} />
        </ThemeProvider>,
      );

      expect(
        screen.getByRole("button", { name: "inventory:fields.identifiers.list.deleteOrRetract.retract" }),
      ).toBeEnabled();
    });

    /* Pre-existing rule on the same expression, previously unpinned. */
    test("Retract is disabled for a registered DataCite IGSN", () => {
      const sample: InventoryRecord = makeMockSample();
      sample.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "DATACITE_IGSN", state: "registered" }];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={sample} />
        </ThemeProvider>,
      );

      expect(
        screen.getByRole("button", { name: "inventory:fields.identifiers.list.deleteOrRetract.retract" }),
      ).toBeDisabled();
    });

    /* The draft of a B2INST record can be deleted, so that button must stay enabled. */
    test("Delete stays enabled for a draft PIDINST identifier", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "PIDINST_B2INST", state: "draft" }];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(
        screen.getByRole("button", { name: "inventory:fields.identifiers.list.deleteOrRetract.delete" }),
      ).toBeEnabled();
    });

    /*
     * B2INST only mints a resolvable Handle once a curator accepts the submission, so publicUrl
     * stays null through "submitted". The identifier value (the B2INST record id) must still be
     * visible: it is the only handle the user has on the record.
     */
    test("the identifier value is still shown once submitted, when there is no public URL yet", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [
        {
          ...mockIGSNIdentifier("sample"),
          doiType: "PIDINST_B2INST",
          state: "submitted",
          doi: "4bj4n-92921",
          publicUrl: null,
        },
      ];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(screen.getByText("4bj4n-92921")).toBeInTheDocument();
    });

    test.each<PidinstPublishingState | "draft">(["draft", "submitted", "accepted", "declined"])(
      "state '%s' links the identifier to its B2INST record page",
      (state) => {
        const instrument: InventoryRecord = makeMockSample();
        instrument.identifiers = [
          {
            ...mockIGSNIdentifier("sample"),
            doiType: "PIDINST_B2INST",
            state,
            doi: "wp4pm-n0r55",
            publicUrl: null,
            providerUrl: "https://b2inst-test.gwdg.de/uploads/wp4pm-n0r55",
          },
        ];

        render(
          <ThemeProvider theme={materialTheme}>
            <IdentifiersList activeResult={instrument} />
          </ThemeProvider>,
        );

        expect(screen.getByRole("link", { name: "wp4pm-n0r55" })).toHaveAttribute(
          "href",
          "https://b2inst-test.gwdg.de/uploads/wp4pm-n0r55",
        );
      },
    );

    test("the identifier is plain text when the provider gave no record page", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [
        {
          ...mockIGSNIdentifier("sample"),
          doiType: "PIDINST_B2INST",
          state: "draft",
          doi: "wp4pm-n0r55",
          publicUrl: null,
          providerUrl: null,
        },
      ];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(screen.getByText("wp4pm-n0r55")).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "wp4pm-n0r55" })).not.toBeInTheDocument();
    });

    test("a published identifier with a public URL still renders it as a link", () => {
      const publishedSample: InventoryRecord = makeMockSample();
      publishedSample.identifiers = [
        { ...mockIGSNIdentifier("sample"), state: "findable", publicUrl: "https://doi.org/10.82316/2z52-vx20" },
      ];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={publishedSample} />
        </ThemeProvider>,
      );

      expect(screen.getByRole("link", { name: "https://doi.org/10.82316/2z52-vx20" })).toHaveAttribute(
        "href",
        "https://doi.org/10.82316/2z52-vx20",
      );
    });

    test("Publish stays enabled for a draft PIDINST identifier", () => {
      const instrument: InventoryRecord = makeMockSample();
      instrument.identifiers = [{ ...mockIGSNIdentifier("sample"), doiType: "PIDINST_B2INST", state: "draft" }];

      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={instrument} />
        </ThemeProvider>,
      );

      expect(screen.getByRole("button", { name: /republish|publish/i })).toBeEnabled();
    });
  });
});
