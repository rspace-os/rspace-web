import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { omit } from "es-toolkit";
import { describe, expect, it, vi } from "vitest";
import { InEnglish } from "@/__tests__/realI18n";
import appTheme from "@/theme";
import OperationConfirmation from "../OperationConfirmation";
import type { InventoryOperation } from "../operationsConfig";
import type { OriginBlockedReason } from "../operationValidation";
import type { TemplateSelection } from "../TemplateStep";
import type { AmountMode, OperationInputs, PerSubsampleAmounts } from "../types";
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
  confirmSummary: ["process", "template", "subsamples", "amountTaken", "linkBack", "documentation"],
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
  originName?: string;
  originBlocked?: OriginBlockedReason | null;
  amountMode?: AmountMode;
  perSubsampleAmounts?: PerSubsampleAmounts;
  origins?: Array<{ globalId: string; name: string }>;
  remember?: boolean;
  onRememberChange?: (remember: boolean) => void;
  english?: boolean;
}) => {
  const card = (
    <ThemeProvider theme={appTheme}>
      <OperationConfirmation
        operation={overrides.op ?? operation}
        values={overrides.values ?? values}
        documentation={overrides.documentation ?? null}
        templateSelection={overrides.templateSelection}
        originSampleName="S1"
        originName={overrides.originName ?? "S1.01"}
        originBlocked={overrides.originBlocked ?? null}
        amountMode={overrides.amountMode ?? "same"}
        perSubsampleAmounts={overrides.perSubsampleAmounts ?? {}}
        origins={overrides.origins ?? []}
        remember={overrides.remember ?? false}
        onRememberChange={overrides.onRememberChange}
      />
    </ThemeProvider>
  );
  return render(overrides.english ? <InEnglish>{card}</InEnglish> : card);
};

describe("OperationConfirmation", () => {
  it("shows the new sample name as the card title", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.getByText("New material")).toBeInTheDocument();
  });

  it("shows the remember checkbox (naming the process) and toggles it", async () => {
    const onRememberChange = vi.fn();
    renderConf({
      values: { ...values, processName: "dna" },
      templateSelection: { mode: "none", templateId: null, remember: false },
      onRememberChange,
    });
    // anchored so it hits the label, not the sibling rememberProcessValuesHelp helper text
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
    renderConf({
      op,
      values: { ...values, processName: "dna extraction" },
      templateSelection: { mode: "none", templateId: null, remember: false },
    });
    expect(screen.getByText(/confirm\.labels\.process/)).toBeInTheDocument();
    expect(screen.getByText("dna extraction")).toBeInTheDocument();
  });

  it("omits the process row when the operation has no process name", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.queryByText(/confirm\.labels\.process/)).not.toBeInTheDocument();
  });
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
    renderConf({
      op: cryoOp,
      values: { ...values, storageTemp: { numericValue: -80, unitId: 8 } },
      templateSelection: { mode: "none", templateId: null, remember: false },
    });
    expect(screen.getByText(/confirm\.labels\.storageTemp/)).toBeInTheDocument();
  });

  it("shows only the fields listed in the operation's confirmSummary", () => {
    const op = { ...cryoOp, confirmSummary: ["storageTemp"] } as unknown as InventoryOperation;
    renderConf({
      op,
      values: { ...values, storageTemp: { numericValue: -80, unitId: 8 } },
      templateSelection: { mode: "none", templateId: null, remember: false },
    });
    expect(screen.getByText(/confirm\.labels\.storageTemp/)).toBeInTheDocument();
    expect(screen.queryByText(/confirm\.labels\.template/)).not.toBeInTheDocument();
  });
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
    renderConf({
      op: poolOp,
      templateSelection: { mode: "none", templateId: null, remember: false },
      originName: "Vial A",
      origins: [
        { globalId: "SS1", name: "Vial A" },
        { globalId: "SS2", name: "Vial B" },
      ],
    });
    expect(screen.getByText(/confirm\.labels\.linkBack/)).toBeInTheDocument();
    expect(screen.getAllByText(/pool\.linkFieldName/)).toHaveLength(2);
    // Anchored so it does not also match ...amountTakenEach.
    expect(screen.getByText(/confirm\.labels\.amountTakenEach/)).toBeInTheDocument();
    expect(screen.queryByText(/confirm\.labels\.amountTaken$/)).not.toBeInTheDocument();
  });

  it("reads the amount-taken row as 'take all' for a Pool in 'all' mode, with no per-origin lines", () => {
    renderConf({
      op: poolOp,
      templateSelection: { mode: "none", templateId: null, remember: false },
      originName: "Vial A",
      amountMode: "all",
      origins: [
        { globalId: "SS1", name: "Vial A" },
        { globalId: "SS2", name: "Vial B" },
      ],
      perSubsampleAmounts: { SS1: { numericValue: 1, unitId: 3 } },
    });
    expect(screen.getByText(/confirm\.labels\.amountTakenEach/)).toBeInTheDocument();
    expect(screen.getByText(/confirm\.values\.takeAll/)).toBeInTheDocument();
    expect(screen.queryByText(/confirm\.values\.originAmount/)).not.toBeInTheDocument();
    expect(screen.queryByText(/confirm\.values\.amountTaken$/)).not.toBeInTheDocument();
  });

  it("does not render the literal 'undefined' as the title when the sample name is absent", () => {
    renderConf({
      values: omit(values, ["sampleName"]),
      templateSelection: { mode: "none", templateId: null, remember: false },
    });
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
  });

  const destroyOp = {
    key: "destroy",
    labelKey: "operations.destroy.label",
    descriptionKey: "operations.destroy.description",
    noOutput: true,
    effect: {
      emptiesOrigin: true,
      computed: [{ fn: "today", into: "disposedDate", args: {} }],
      links: [],
      originFields: [{ nameKey: "operations.destroy.disposedField", contentFrom: "disposedDate" }],
    },
    confirmSummary: ["originEmptied", "originFields"],
  } as unknown as InventoryOperation;

  it("names the origin subsample and summarises the emptying and disposed field for a terminal op", () => {
    renderConf({ op: destroyOp, values: {}, templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.getByText("S1.01")).toBeInTheDocument();
    expect(screen.getByText(/confirm\.cardSubheaderTerminal/)).toBeInTheDocument();
    expect(screen.queryByText(/confirm\.cardSubheader$/)).not.toBeInTheDocument();
    expect(screen.getByText(/operations\.destroy\.description/)).toBeInTheDocument();
    expect(screen.getByText(/confirm\.labels\.originEmptied/)).toBeInTheDocument();
    expect(screen.getByText(/operations\.destroy\.disposedField/)).toBeInTheDocument();
    expect(screen.getByText(/^\d{4}-\d{2}-\d{2}$/)).toBeInTheDocument();
  });

  it("blocks a terminal operation on an empty origin, showing why (the details-step guard moved here)", () => {
    renderConf({
      op: destroyOp,
      values: {},
      originBlocked: "empty",
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
    // code. cimode renders the key and drops every parameter, so only the
    // real catalogs can show that the origin name and its amount both arrive.
    renderConf({
      op: poolOp,
      templateSelection: { mode: "none", templateId: null, remember: false },
      originName: "Vial A",
      amountMode: "perSubsample",
      origins: [
        { globalId: "SS1", name: "Vial A" },
        { globalId: "SS2", name: "Vial B" },
      ],
      perSubsampleAmounts: {
        SS1: { numericValue: 1, unitId: 3 },
        SS2: { numericValue: 2.5, unitId: 3 },
      },
      english: true,
    });
    expect(screen.getByText("Vial A: 1 ml")).toBeInTheDocument();
    expect(screen.getByText("Vial B: 2.5 ml")).toBeInTheDocument();
  });
});

// The server builds the record from the typed inputs while this card is computed from them
// separately, so the two can drift. These tests spell out the names the server stores as literals
// and assert the card shows them, on the real definitions, rather than compute them with a second TS
// implementation of the server's build that could itself drift.
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
    renderConf({
      op: pool,
      values: poolValues,
      templateSelection: noTemplate,
      originName: "Aliquot",
      origins: origins.map(({ globalId, name }) => ({ globalId, name })),
      english: true,
    });
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
    renderConf({
      op: derive,
      values: deriveValues,
      templateSelection: noTemplate,
      originName: origin.name,
      english: true,
    });
    expect(screen.getByText("Is Derived From using process: PCR")).toBeInTheDocument();
    expect(screen.getByText("Derived")).toBeInTheDocument();
  });
});
