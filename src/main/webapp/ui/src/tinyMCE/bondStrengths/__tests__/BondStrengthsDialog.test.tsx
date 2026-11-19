import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import workspaceEn from "@/modules/common/i18n/locales/en-US/workspace.json";
import materialTheme from "@/theme";
import BondStrengthsDialog from "../BondStrengthsDialog";
import { svgToPngDataUri } from "../bondStrengthsApi";
import type { BondStrengthsDocumentContent, BondStrengthsInsertOutcome } from "../insertIntoDocument";

// jsdom has no canvas, so the PNG conversion is replaced.
vi.mock("../bondStrengthsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../bondStrengthsApi")>()),
  svgToPngDataUri: vi.fn(() => Promise.resolve("data:image/png;base64,AAAA")),
}));

const method = {
  name: "RSpace bond strength model",
  version: "0.1.0",
  weightsSha256: "af085c02bbbc372c177ff0b256b25aa7f0f6a8d9988276ca02e5c85ba0a9e890",
  meanAbsoluteErrorKcalMol: 0.8,
  crossCheck: { maxDifferenceKcalMol: 5 },
};

function bond(label: string, overrides: Record<string, unknown> = {}) {
  return {
    label,
    hydrogenCount: null,
    supported: true,
    unsupportedReason: null,
    bdeKcalMol: null,
    bdeKjMol: null,
    weakest: false,
    ...overrides,
  };
}

const toluene = {
  supported: true,
  unsupportedReason: null,
  unsupportedDetail: null,
  bonds: [
    bond("C1-C2", { bdeKcalMol: 101.0, bdeKjMol: 422.6 }),
    bond("C2:C3", { supported: false, unsupportedReason: "RING_BOND" }),
    bond("C1-H", { hydrogenCount: 3, bdeKcalMol: 89.3, bdeKjMol: 373.5, weakest: true }),
    bond("C3-H", { hydrogenCount: 1, bdeKcalMol: 110.4, bdeKjMol: 461.9 }),
    bond("C4-H", { hydrogenCount: 1, supported: false, unsupportedReason: "LOW_CONFIDENCE" }),
  ],
  supportedBondCount: 3,
  totalBondCount: 5,
  imageSvg: "<?xml version='1.0' encoding='iso-8859-1'?>\n<svg xmlns='http://www.w3.org/2000/svg'></svg>",
  method,
};

const sodiumAcetate = {
  supported: false,
  unsupportedReason: "ELEMENT_NOT_COVERED",
  unsupportedDetail: "Na",
  bonds: [],
  supportedBondCount: 0,
  totalBondCount: 0,
  imageSvg: null,
  method,
};

const noValueNotes = [
  "1 ring bond has no value. Ring bonds are not covered.",
  "No value for C4-H. Low confidence: two models disagreed by more than 5 kcal/mol, so the value is not shown.",
];
const legend =
  "Numbers on bonds are in kcal/mol. Atom numbers match the table. Red marks the weakest bond, or its atom when that bond is to a hydrogen. Bonds to hydrogen are only in the table.";

function respondWith(result: unknown) {
  server.use(http.get("/chemical/ajax/bondStrengths", () => HttpResponse.json({ data: result })));
}

type OnInsert = (content: BondStrengthsDocumentContent) => Promise<BondStrengthsInsertOutcome>;

function renderDialog(props: { onClose?: () => void; onInsert?: OnInsert }) {
  const queryClient = new QueryClient();
  return renderWithRealI18n(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>
        <BondStrengthsDialog open chemId={42} onClose={props.onClose ?? (() => {})} onInsert={props.onInsert} />
      </QueryClientProvider>
    </ThemeProvider>,
    { resources: { workspace: workspaceEn, common: commonEn }, defaultNS: "common" },
  );
}

describe("BondStrengthsDialog", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("lists the bonds that have a value weakest first, and sums up the rest", async () => {
    respondWith(toluene);
    const { baseElement } = await renderDialog({});

    const table = await screen.findByRole("table", { name: "Bond strengths, weakest first" });
    const [header, ...rows] = within(table).getAllByRole("row");

    expect(
      within(header)
        .getAllByRole("columnheader")
        .map((cell) => cell.textContent),
    ).toEqual(["Bond", "kcal/mol", "kJ/mol"]);
    expect(
      rows.map((row) =>
        within(row)
          .getAllByRole("cell")
          .map((cell) => cell.textContent),
      ),
    ).toEqual([
      ["89", "374"],
      ["101", "423"],
      ["110", "462"],
    ]);
    expect(rows.map((row) => within(row).getByRole("rowheader").textContent)).toEqual([
      "C1-H (×3)Weakest",
      "C1-C2",
      "C3-H",
    ]);
    expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual(noValueNotes);
    expect(screen.getByRole("dialog", { name: "Bond strengths" })).toBeVisible();
    await expectAccessible(baseElement);
  });

  it("rounds each value once, half up, so the table agrees with the picture", async () => {
    respondWith({
      ...toluene,
      bonds: [
        bond("C6-N8", { bdeKcalMol: 84.46410369873047, bdeKjMol: 353.3978098754883, weakest: true }),
        bond("C1-C2", { bdeKcalMol: 84.5, bdeKjMol: 353.548 }),
      ],
    });
    await renderDialog({});

    const table = await screen.findByRole("table", { name: "Bond strengths, weakest first" });
    expect(
      within(table)
        .getAllByRole("cell")
        .map((cell) => cell.textContent),
    ).toEqual(["84", "353", "85", "354"]);
  });

  it("shows the picture as an image and says where the values come from", async () => {
    respondWith(toluene);
    await renderDialog({});

    const picture = await screen.findByRole("img", { name: /^Structure with atom numbers/ });

    expect(picture).toHaveAttribute("src", expect.stringMatching(/^data:image\/svg\+xml;charset=utf-8,%3Csvg/));
    expect(screen.getByText(legend)).toBeVisible();
    expect(screen.getByText("3 of 5 bonds have a value. Hydrogens on the same atom share one row.")).toBeVisible();
    expect(
      screen.getByText("Predicted values. Typical error 0.8 kcal/mol against DFT, not checked against experiment."),
    ).toBeVisible();
    expect(screen.queryByRole("button", { name: "Insert into document" })).not.toBeInTheDocument();
  });

  it("always shows the model, accuracy and training data, and the rest on request", async () => {
    const user = userEvent.setup();
    respondWith({
      ...toluene,
      method: { ...method, referenceMethod: "DFT, M06-2X/def2-TZVP", trainingData: "BDE-db2 (CC BY 4.0)" },
    });
    await renderDialog({});

    const about = await screen.findByRole("region", { name: "About this calculation" });
    expect(within(about).getByText("RSpace bond strength model 0.1.0")).toBeVisible();
    expect(within(about).getByText("Mean absolute error 0.8 kcal/mol")).toBeVisible();
    expect(within(about).getByText("BDE-db2 (CC BY 4.0)")).toBeVisible();
    expect(within(about).queryByText("DFT, M06-2X/def2-TZVP")).not.toBeInTheDocument();

    const showAll = within(about).getByRole("button", { name: "All details" });
    expect(showAll).toHaveAttribute("aria-expanded", "false");
    await user.click(showAll);

    expect(within(about).getByRole("button", { name: "Fewer details" })).toHaveAttribute("aria-expanded", "true");
    expect(within(about).getByText("DFT, M06-2X/def2-TZVP")).toBeVisible();
    expect(within(about).getByText("af085c02bbbc")).toBeVisible();
  });

  it("explains why a structure is not supported, without a table", async () => {
    respondWith(sodiumAcetate);
    await renderDialog({});

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bond strengths cannot be calculated for this structure. The model does not cover these elements: Na.",
    );
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText(/kcal\/mol/)).not.toBeInTheDocument();
  });

  it("shows a plain message when the request fails", async () => {
    server.use(http.get("/chemical/ajax/bondStrengths", () => new HttpResponse(null, { status: 500 })));
    await renderDialog({});

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bond strengths could not be calculated. Try again later.",
    );
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("hands the result to the editor and closes", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 18, 12));
    const user = userEvent.setup();
    const onInsert = vi.fn<OnInsert>(() => Promise.resolve("inserted"));
    const onClose = vi.fn();
    respondWith({
      ...toluene,
      method: {
        ...method,
        quantity: "Homolytic bond dissociation enthalpy at 298 K",
        referenceMethod: "DFT, M06-2X/def2-TZVP",
        trainingData: "BDE-db2 (CC BY 4.0), https://doi.org/10.6084/m9.figshare.19367051",
      },
    });
    await renderDialog({ onInsert, onClose });

    await user.click(await screen.findByRole("button", { name: "Insert into document" }));

    await waitFor(() => expect(onInsert).toHaveBeenCalled());
    expect(onInsert).toHaveBeenCalledExactlyOnceWith({
      imageSrc: "data:image/png;base64,AAAA",
      imageAlt: "Structure with predicted bond strengths",
      legend: "Values in kcal/mol. Atom numbers match the table. Red marks the weakest bond.",
      forChemicalId: "42",
      caption:
        "Predicted bond strengths (bond dissociation enthalpy) from RSpace bond strength model 0.1.0. Typical error 0.8 kcal/mol against DFT, not checked against experiment. Calculated on November 18, 2026.",
      columns: { bond: "Bond", kcalMol: "kcal/mol", kjMol: "kJ/mol" },
      rows: [
        { bond: "C1-H (×3), weakest bond", kcalMol: "89", kjMol: "374" },
        { bond: "C1-C2", kcalMol: "101", kjMol: "423" },
        { bond: "C3-H", kcalMol: "110", kjMol: "462" },
      ],
      notes: [
        "Ring, double and triple bonds have no value (1 bond).",
        "No value for C4-H. Low confidence: two models disagreed by more than 5 kcal/mol, so the value is not shown.",
      ],
      methodDetails:
        "What is predicted: Homolytic bond dissociation enthalpy at 298 K · Reference method: DFT, M06-2X/def2-TZVP · Model weights: af085c02bbbc · Training data: BDE-db2 (CC BY 4.0), https://doi.org/10.6084/m9.figshare.19367051",
      provenance: {
        model: "RSpace bond strength model",
        version: "0.1.0",
        weightsSha256: "af085c02bbbc372c177ff0b256b25aa7f0f6a8d9988276ca02e5c85ba0a9e890",
        calculatedAt: new Date(2026, 10, 18, 12).toISOString(),
      },
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("inserts the table without a picture when the picture cannot be drawn, and says so", async () => {
    const user = userEvent.setup();
    const onInsert = vi.fn<OnInsert>(() => Promise.resolve("insertedWithoutPicture"));
    const onClose = vi.fn();
    vi.mocked(svgToPngDataUri).mockRejectedValueOnce(new Error("no canvas"));
    respondWith(toluene);
    await renderDialog({ onInsert, onClose });

    await user.click(await screen.findByRole("button", { name: "Insert into document" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The table is in the document, but the picture could not be added. Insert again to retry.",
    );
    expect(onInsert.mock.calls[0][0].imageSrc).toBeNull();
    expect(onInsert.mock.calls[0][0].rows).toHaveLength(3);
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Insert into document" })).toBeEnabled();
  });

  it("stays open with a message when the result cannot be inserted", async () => {
    const user = userEvent.setup();
    const restoreConsole = silenceConsole(["warn"], ["The bond strengths could not be inserted"]);
    const onClose = vi.fn();
    respondWith(toluene);
    await renderDialog({ onInsert: () => Promise.reject(new Error("no editor")), onClose });

    await user.click(await screen.findByRole("button", { name: "Insert into document" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The result could not be inserted into the document. Check that the structure is still in the field you are editing, then try again.",
    );
    expect(onClose).not.toHaveBeenCalled();
    restoreConsole();
  });

  it("says so and offers no insert when no bond has a value", async () => {
    respondWith({
      ...toluene,
      bonds: [
        bond("C1:C2", { supported: false, unsupportedReason: "RING_BOND" }),
        bond("C2:C3", { supported: false, unsupportedReason: "RING_BOND" }),
        bond("C1-H", { hydrogenCount: 1, supported: false, unsupportedReason: "LOW_CONFIDENCE" }),
      ],
      supportedBondCount: 0,
      totalBondCount: 3,
    });
    await renderDialog({ onInsert: vi.fn() });

    expect(await screen.findByRole("alert")).toHaveTextContent("No bond in this structure has a value.");
    expect(screen.getAllByRole("listitem")[0]).toHaveTextContent(
      "2 ring bonds have no value. Ring bonds are not covered.",
    );
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText(legend)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Insert into document" })).not.toBeInTheDocument();
  });
});
