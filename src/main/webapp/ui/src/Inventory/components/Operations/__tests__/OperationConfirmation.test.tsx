import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import appTheme from "@/theme";
import OperationConfirmation from "../OperationConfirmation";
import type { InventoryOperation } from "../operationsConfig";
import type { TemplateSelection } from "../TemplateStep";
import type { OperationInputs } from "../types";

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
      />
    </ThemeProvider>,
  );

describe("OperationConfirmation", () => {
  it("shows the new sample name as the card title", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.getByText("New material")).toBeInTheDocument();
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

  // Destroy-shaped op (adr/0008): noOutput, so the card names the origin subsample, and the summary is
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
