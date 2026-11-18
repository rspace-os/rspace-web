import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import workspaceEn from "@/modules/common/i18n/locales/en-US/workspace.json";
import materialTheme from "@/theme";
import BondStrengthsDialog from "../BondStrengthsDialog";
import type { BondStrengthsDocumentContent } from "../insertIntoDocument";

const method = {
  name: "RSpace bond strength model",
  version: "0.1.0-poc",
  weightsSha256: "af085c02bbbc372c177ff0b256b25aa7f0f6a8d9988276ca02e5c85ba0a9e890",
  meanAbsoluteErrorKcalMol: 0.8,
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
  ],
  supportedBondCount: 3,
  totalBondCount: 4,
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

function respondWith(result: unknown) {
  server.use(http.get("/chemical/ajax/bondStrengths", () => HttpResponse.json({ data: result })));
}

function renderDialog(props: { onClose?: () => void; onInsert?: (content: BondStrengthsDocumentContent) => void }) {
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

  it("lists the bonds weakest first, with bonds that have no value last", async () => {
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
    ).toEqual([["89.3", "373.5"], ["101.0", "422.6"], ["110.4", "461.9"], ["n/a (ring bond)"]]);
    expect(rows.map((row) => within(row).getByRole("rowheader").textContent)).toEqual([
      "C1-H (×3)Weakest",
      "C1-C2",
      "C3-H",
      "C2:C3",
    ]);
    expect(screen.getByRole("dialog", { name: "Bond strengths" })).toBeVisible();
    await expectAccessible(baseElement);
  });

  it("shows the picture as an image and says where the values come from", async () => {
    respondWith(toluene);
    await renderDialog({});

    const picture = await screen.findByRole("img", { name: /^Structure with atom numbers/ });

    expect(picture).toHaveAttribute("src", expect.stringMatching(/^data:image\/svg\+xml;charset=utf-8,%3Csvg/));
    expect(screen.getByText("3 of 4 bonds have a value.")).toBeVisible();
    expect(
      screen.getByText(
        "Model: RSpace bond strength model 0.1.0-poc. The values are predicted and are not validated against experiment. Mean absolute error against the DFT reference: 0.8 kcal/mol.",
      ),
    ).toBeVisible();
    expect(screen.getByText("Model weights: af085c02bbbc")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Insert into document" })).not.toBeInTheDocument();
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
    const onInsert = vi.fn<(content: BondStrengthsDocumentContent) => void>();
    const onClose = vi.fn();
    respondWith(toluene);
    await renderDialog({ onInsert, onClose });

    await user.click(await screen.findByRole("button", { name: "Insert into document" }));

    expect(onInsert).toHaveBeenCalledExactlyOnceWith({
      imageSrc: expect.stringMatching(/^data:image\/svg\+xml;charset=utf-8,%3Csvg/) as string,
      imageAlt:
        "Structure with atom numbers and predicted bond strengths in kcal/mol. The weakest bond is highlighted.",
      caption:
        "Predicted bond strengths (bond dissociation enthalpy). Model: RSpace bond strength model 0.1.0-poc. Mean absolute error against the DFT reference: 0.8 kcal/mol. Not validated against experiment. Calculated on November 18, 2026.",
      columns: { bond: "Bond", kcalMol: "kcal/mol", kjMol: "kJ/mol" },
      rows: [
        { bond: "C1-H (×3), weakest bond", kcalMol: "89.3", kjMol: "373.5" },
        { bond: "C1-C2", kcalMol: "101.0", kjMol: "422.6" },
        { bond: "C3-H", kcalMol: "110.4", kjMol: "461.9" },
        { bond: "C2:C3", notAvailable: "n/a (ring bond)" },
      ],
    });
    expect(onClose).toHaveBeenCalledOnce();
  });
});
