import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { InEnglish } from "@/__tests__/realI18n";
import appTheme from "@/theme";
import OperationConfirmation from "../OperationConfirmation";
import type { InventoryOperation } from "../operationsConfig";
import type { TemplateSelection } from "../TemplateStep";
import type { OperationInputs } from "../types";
import { operations } from "./testOperations";

// i18n runs in cimode in tests, so t(key, params) renders the namespaced key with no interpolation;
// assertions match on the key, which still tells us which branch rendered. Values not passed through
// t() (the sample name, a picked template name, a document name) render as their raw string.
vi.mock("@/stores/use-stores", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) } }),
}));

const operation = {
  key: "derive",
  labelKey: "operations.derive.label",
  effect: {
    nameFrom: "sampleName",
    countFrom: "count",
    eachAmountFrom: "eachAmount",
    amountTakenFrom: "amountTaken",
    links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.derive.linkFieldName" }],
  },
} as unknown as InventoryOperation;

const values: OperationInputs = {
  sampleName: "New material",
  count: 2,
  eachAmount: { numericValue: 5, unitId: 3 },
  amountTaken: { numericValue: 1, unitId: 3 },
};

const renderConf = (overrides: {
  templateSelection: TemplateSelection;
  documentation?: { globalId: string; name: string } | null;
  op?: InventoryOperation;
  values?: OperationInputs;
  originHasAmount?: boolean;
  remember?: boolean;
  onRememberChange?: (remember: boolean) => void;
}) =>
  render(
    // ThemeProvider supplies the app theme so the confirmation card can read palette.record.sample.
    <ThemeProvider theme={appTheme}>
      <OperationConfirmation
        operation={overrides.op ?? operation}
        values={overrides.values ?? values}
        documentation={overrides.documentation ?? null}
        templateSelection={overrides.templateSelection}
        originSampleName="S1"
        originName="S1.01"
        originHasAmount={overrides.originHasAmount ?? true}
        remember={overrides.remember ?? false}
        onRememberChange={overrides.onRememberChange}
      />
    </ThemeProvider>,
  );

describe("OperationConfirmation", () => {
  it("shows the new sample name as the card title", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.getByText("New material")).toBeInTheDocument();
  });

  it("shows the remember checkbox (naming the process) and toggles it", async () => {
    const onRememberChange = vi.fn();
    render(
      <ThemeProvider theme={appTheme}>
        <OperationConfirmation
          operation={operation}
          values={{ ...values, processName: "dna" }}
          documentation={null}
          templateSelection={{ mode: "none", templateId: null, remember: false }}
          originSampleName="S1"
          originName="S1.01"
          remember={false}
          onRememberChange={onRememberChange}
        />
      </ThemeProvider>,
    );
    // the label references the chosen process name (values are remembered per process name);
    // anchor the match so it hits the label, not the sibling rememberProcessValuesHelp helper text
    expect(screen.getByText(/rememberProcessValues$/)).toBeInTheDocument();
    expect(screen.getByText(/rememberProcessValuesHelp/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("checkbox"));
    expect(onRememberChange).toHaveBeenCalledWith(true);
  });

  it("omits the remember checkbox when no handler is provided (terminal operations)", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("summarises a picked template, the amount taken, and a linked document", () => {
    renderConf({
      templateSelection: { mode: "pick", templateId: 5, templateName: "T5", remember: false },
      documentation: { globalId: "SD1", name: "My SOP" },
    });
    // a picked template shows its name verbatim (not through t())
    expect(screen.getByText("T5")).toBeInTheDocument();
    expect(screen.getByText(/confirm\.labels\.amountTaken/)).toBeInTheDocument();
    expect(screen.getByText(/confirm\.labels\.documentation/)).toBeInTheDocument();
    expect(screen.getByText("My SOP")).toBeInTheDocument();
  });

  it("uses the 'no template' value for mode 'none'", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.getByText(/template\.valueNone/)).toBeInTheDocument();
  });

  it("uses the 'from parent sample' value for mode 'fromSample'", () => {
    renderConf({ templateSelection: { mode: "fromSample", templateId: null, remember: false } });
    expect(screen.getByText(/template\.valueFromSample/)).toBeInTheDocument();
  });

  it("omits the amount-taken and documentation rows when there is neither", () => {
    const op = { ...operation, effect: { ...operation.effect, amountTakenFrom: undefined } } as InventoryOperation;
    renderConf({ op, templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.queryByText(/confirm\.labels\.amountTaken/)).not.toBeInTheDocument();
    expect(screen.queryByText(/confirm\.labels\.documentation/)).not.toBeInTheDocument();
  });

  it("shows the process-name row when the operation has a process name", () => {
    const op = { ...operation, effect: { ...operation.effect, processNameFrom: "processName" } } as InventoryOperation;
    render(
      <ThemeProvider theme={appTheme}>
        <OperationConfirmation
          operation={op}
          values={{ ...values, processName: "dna extraction" }}
          documentation={null}
          templateSelection={{ mode: "none", templateId: null, remember: false }}
          originSampleName="S1"
          originName="S1.01"
        />
      </ThemeProvider>,
    );
    expect(screen.getByText(/confirm\.labels\.process/)).toBeInTheDocument();
    expect(screen.getByText("dna extraction")).toBeInTheDocument();
  });

  it("omits the process row when the operation has no process name", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.queryByText(/confirm\.labels\.process/)).not.toBeInTheDocument();
  });

  // Cryopreserve-shaped op: declares a storage temperature and lists it in its confirmSummary.
  const cryoOp = {
    key: "cryopreserve",
    labelKey: "operations.cryopreserve.label",
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      storageTempFrom: "storageTemp",
      links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.cryopreserve.linkFieldName" }],
    },
    confirmSummary: ["template", "subsamples", "amountTaken", "storageTemp", "linkBack", "documentation"],
  } as unknown as InventoryOperation;

  it("shows the storage temperature for cryopreserve (a configured summary field)", () => {
    render(
      <ThemeProvider theme={appTheme}>
        <OperationConfirmation
          operation={cryoOp}
          values={{ ...values, storageTemp: { numericValue: -80, unitId: 8 } }}
          documentation={null}
          templateSelection={{ mode: "none", templateId: null, remember: false }}
          originSampleName="S1"
          originName="S1.01"
        />
      </ThemeProvider>,
    );
    expect(screen.getByText(/confirm\.labels\.storageTemp/)).toBeInTheDocument();
  });

  it("shows only the fields listed in the operation's confirmSummary", () => {
    const op = { ...cryoOp, confirmSummary: ["storageTemp"] } as unknown as InventoryOperation;
    render(
      <ThemeProvider theme={appTheme}>
        <OperationConfirmation
          operation={op}
          values={{ ...values, storageTemp: { numericValue: -80, unitId: 8 } }}
          documentation={null}
          templateSelection={{ mode: "none", templateId: null, remember: false }}
          originSampleName="S1"
          originName="S1.01"
        />
      </ThemeProvider>,
    );
    expect(screen.getByText(/confirm\.labels\.storageTemp/)).toBeInTheDocument();
    // template is not in this confirmSummary, so it is not shown
    expect(screen.queryByText(/confirm\.labels\.template/)).not.toBeInTheDocument();
  });

  // Pool-shaped op: multi-origin (requiresMultiple), its linkBack field name interpolates each origin.
  const poolOp = {
    key: "pool",
    labelKey: "operations.pool.label",
    requiresMultiple: true,
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      links: [{ relationType: "HasPart", fieldNameKey: "operations.pool.linkFieldName" }],
    },
    confirmSummary: ["subsamples", "amountTaken", "linkBack"],
  } as unknown as InventoryOperation;

  it("lists a linkBack line for every pooled subsample, not just the representative one", () => {
    render(
      <ThemeProvider theme={appTheme}>
        <OperationConfirmation
          operation={poolOp}
          values={values}
          documentation={null}
          templateSelection={{ mode: "none", templateId: null, remember: false }}
          originSampleName="S1"
          originName="Vial A"
          origins={[
            { globalId: "SS1", name: "Vial A" },
            { globalId: "SS2", name: "Vial B" },
          ]}
        />
      </ThemeProvider>,
    );
    expect(screen.getByText(/confirm\.labels\.linkBack/)).toBeInTheDocument();
    // The row fans out one line per origin (in cimode each renders the field-name key); the bug showed
    // only one. Two origins -> two lines.
    expect(screen.getAllByText(/pool\.linkFieldName/)).toHaveLength(2);
    // A per-subsample operation labels its amount-taken row "from each subsample", not just "Amount
    // taken" (the plain label, anchored so it does not also match ...amountTakenEach, is absent).
    expect(screen.getByText(/confirm\.labels\.amountTakenEach/)).toBeInTheDocument();
    expect(screen.queryByText(/confirm\.labels\.amountTaken$/)).not.toBeInTheDocument();
  });

  // Destroy-shaped op (DevDocs/adr/0007): noOutput, so the card names the origin subsample, and the summary is
  // the origin being emptied plus the disposed field it adds to the origin.
  const destroyOp = {
    key: "destroy",
    labelKey: "operations.destroy.label",
    descriptionKey: "operations.destroy.description",
    noOutput: true,
    effect: {
      emptiesOrigin: true,
      computed: [{ fn: "today", into: "disposedDate", args: {} }],
      links: [],
      originFields: [{ nameKey: "operations.destroy.disposedField", contentFrom: "disposedDate", type: "text" }],
    },
    confirmSummary: ["originEmptied", "originFields"],
  } as unknown as InventoryOperation;

  it("names the origin subsample and summarises the emptying and disposed field for a terminal op", () => {
    renderConf({ op: destroyOp, values: {}, templateSelection: { mode: "none", templateId: null, remember: false } });
    // A terminal operation creates no sample, so the card title is the origin subsample name.
    expect(screen.getByText("S1.01")).toBeInTheDocument();
    // ...and the subheader must not claim a "New sample": it uses the terminal-op variant.
    expect(screen.getByText(/confirm\.cardSubheaderTerminal/)).toBeInTheDocument();
    expect(screen.queryByText(/confirm\.cardSubheader$/)).not.toBeInTheDocument();
    // The description shows here (moved off the now-skipped details step) as an info panel.
    expect(screen.getByText(/operations\.destroy\.description/)).toBeInTheDocument();
    expect(screen.getByText(/confirm\.labels\.originEmptied/)).toBeInTheDocument();
    // The origin-field row is labelled by the field name (its i18n key in cimode) and shows today's
    // date, computed for the preview.
    expect(screen.getByText(/operations\.destroy\.disposedField/)).toBeInTheDocument();
    expect(screen.getByText(/^\d{4}-\d{2}-\d{2}$/)).toBeInTheDocument();
  });

  it("blocks a terminal operation on an empty origin, showing why (the details-step guard moved here)", () => {
    renderConf({
      op: destroyOp,
      values: {},
      originHasAmount: false,
      templateSelection: { mode: "none", templateId: null, remember: false },
    });
    expect(screen.getByText(/fields\.originAmountZero/)).toBeInTheDocument();
  });
});

describe("OperationConfirmation in English", () => {
  const poolOp = {
    key: "pool",
    labelKey: "operations.pool.label",
    requiresMultiple: true,
    effect: {
      nameFrom: "sampleName",
      amountTakenFrom: "amountTaken",
      links: [{ relationType: "HasPart", fieldNameKey: "operations.pool.linkFieldName" }],
    },
    confirmSummary: ["amountTaken"],
  } as unknown as InventoryOperation;

  it("reads each per-subsample line as 'origin: amount unit'", () => {
    // The line is assembled by i18n from the originAmount and amountTaken keys, not concatenated in
    // code (code review, finding 10). cimode renders the key and drops every parameter, so only the
    // real catalogs can show that the origin name and its amount both arrive.
    render(
      <InEnglish>
        <ThemeProvider theme={appTheme}>
          <OperationConfirmation
            operation={poolOp}
            values={values}
            documentation={null}
            templateSelection={{ mode: "none", templateId: null, remember: false }}
            originSampleName="S1"
            originName="Vial A"
            amountMode="perSubsample"
            origins={[
              { globalId: "SS1", name: "Vial A" },
              { globalId: "SS2", name: "Vial B" },
            ]}
            perSubsampleAmounts={{
              SS1: { numericValue: 1, unitId: 3 },
              SS2: { numericValue: 2.5, unitId: 3 },
            }}
          />
        </ThemeProvider>
      </InEnglish>,
    );
    expect(screen.getByText("Vial A: 1 ml")).toBeInTheDocument();
    expect(screen.getByText("Vial B: 2.5 ml")).toBeInTheDocument();
  });
});

// The server builds the record from the typed inputs (DevDocs/adr/0007) while
// this card is computed from them separately, so the two can drift. The names the server stores are
// pinned server-side by InventoryOperationsInputsShapeMVCIT and InventoryOperationRequestBuilderTest;
// these tests spell those names out as literals and assert the card shows them, in English, on the
// real definitions. They used to call a TS buildOperationRequest as an oracle, but that function had
// no production caller: it was a second implementation of the server's build that could drift from
// it while both suites stayed green, and the assertions already hardcoded the expected names anyway
// (parallel review).
describe("the confirmation preview matches the names the server stores", () => {
  const real = (key: string): InventoryOperation => {
    const found = operations.find((o) => o.key === key);
    if (!found) throw new Error(`no configured operation ${key}`);
    return found;
  };
  const noTemplate: TemplateSelection = { mode: "none", templateId: null, remember: false };

  it("shows the link names the server stores for Pool, disambiguated when two origins share a name", () => {
    const pool = real("pool");
    const origins = [
      { id: 1, globalId: "SS1", name: "Aliquot", quantity: { numericValue: 5, unitId: 3 } },
      { id: 2, globalId: "SS2", name: "Aliquot", quantity: { numericValue: 5, unitId: 3 } },
    ];
    const poolValues: OperationInputs = {
      sampleName: "Pooled",
      count: 1,
      eachAmount: { numericValue: 2, unitId: 3 },
      amountTaken: { numericValue: 1, unitId: 3 },
    };
    render(
      <InEnglish>
        <ThemeProvider theme={appTheme}>
          <OperationConfirmation
            operation={pool}
            values={poolValues}
            documentation={null}
            templateSelection={noTemplate}
            originSampleName="S1"
            originName="Aliquot"
            origins={origins.map(({ globalId, name }) => ({ globalId, name }))}
          />
        </ThemeProvider>
      </InEnglish>,
    );
    // Both origins are named "Aliquot", so the interpolated link name collides and each member of
    // the colliding group is suffixed with the global id it targets (withUniqueFieldNames, applied
    // identically by InventoryOperationRequestBuilder server-side).
    for (const name of ["Pooled from: Aliquot (SS1)", "Pooled from: Aliquot (SS2)"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
    expect(screen.getByText("Pooled")).toBeInTheDocument();
  });

  it("shows the process-interpolated link name the server stores for Derive", () => {
    const derive = real("derive");
    const origin = { id: 1, globalId: "SS1", name: "Vial A", quantity: { numericValue: 5, unitId: 3 } };
    const deriveValues: OperationInputs = {
      processName: "PCR",
      sampleName: "Derived",
      count: 1,
      eachAmount: { numericValue: 2, unitId: 3 },
      amountTaken: { numericValue: 1, unitId: 3 },
    };
    render(
      <InEnglish>
        <ThemeProvider theme={appTheme}>
          <OperationConfirmation
            operation={derive}
            values={deriveValues}
            documentation={null}
            templateSelection={noTemplate}
            originSampleName="S1"
            originName={origin.name}
          />
        </ThemeProvider>
      </InEnglish>,
    );
    expect(screen.getByText("Is Derived From using process: PCR")).toBeInTheDocument();
    expect(screen.getByText("Derived")).toBeInTheDocument();
  });
});
