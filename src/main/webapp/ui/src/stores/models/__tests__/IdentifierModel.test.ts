import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import type InvApiService from "../../../common/InvApiService";
import { mockIGSNAttrs } from "../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import type { ExternalMetadataUpdate, ExternalMetadataUpdateOutcome } from "../../definitions/Identifier";
import { isPublishedState } from "../../definitions/Identifier";
import IdentifierModel, { externalMetadataUpdateAlerts } from "../IdentifierModel";

describe("IdentifierModel.toJson() — Dates serialization", () => {
  test("date values are plain yyyy-MM-dd strings, not Date objects or ISO timestamps", () => {
    const model = new IdentifierModel({ ...mockIGSNAttrs(), dates: [{ value: "2027-05-25", type: "CREATED" }] }, "SA1");
    const json = model.toJson() as { dates: Array<{ value: unknown }> };
    expect(typeof json.dates[0].value).toBe("string");
    expect(json.dates[0].value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  test("serialized date matches local calendar date, not the UTC-shifted equivalent", () => {
    /*
     * new Date(year, monthIndex, day) creates midnight in LOCAL time.
     * format() from date-fns also uses local time, so the round-trip must
     * preserve the calendar date regardless of the host timezone offset.
     * This guards against the prior bug where JSON.stringify serialised Date
     * objects as UTC ISO strings, shifting the date back by the UTC offset.
     */
    const localMidnight = new Date(2027, 4, 25); // May 25 local midnight
    const model = new IdentifierModel({ ...mockIGSNAttrs(), dates: [] }, "SA1");
    runInAction(() => {
      model.dates = [{ value: localMidnight, type: "CREATED" }];
    });
    const json = model.toJson() as { dates: Array<{ value: string }> };
    const expectedLocalDateString = [
      localMidnight.getFullYear(),
      String(localMidnight.getMonth() + 1).padStart(2, "0"),
      String(localMidnight.getDate()).padStart(2, "0"),
    ].join("-");
    expect(json.dates[0].value).toBe(expectedLocalDateString);
  });
});

describe("isPublishedState", () => {
  test("covers DataCite findable and B2INST accepted, nothing else", () => {
    expect(isPublishedState("findable")).toBe(true);
    expect(isPublishedState("accepted")).toBe(true);
    expect(isPublishedState("draft")).toBe(false);
    expect(isPublishedState("submitted")).toBe(false);
    expect(isPublishedState("declined")).toBe(false);
  });
});

describe("IdentifierModel.refresh()", () => {
  test("posts to the refresh endpoint and applies state and URLs", async () => {
    const post = vi.fn().mockResolvedValue({
      data: {
        state: "accepted",
        url: null,
        publicUrl: "http://hdl.handle.net/21.T11975/k2j9p-7yh21",
        providerUrl: "https://b2inst-test.gwdg.de/records/k2j9p-7yh21",
      },
    });
    const model = new IdentifierModel({ ...mockIGSNAttrs(), doiType: "PIDINST_B2INST", state: "submitted" }, "IN1", {
      post,
    } as unknown as typeof InvApiService);
    const addAlert = vi.fn();

    await model.refresh({ addAlert });

    expect(post).toHaveBeenCalledWith("/identifiers/1/refresh", {});
    expect(model.state).toBe("accepted");
    expect(model.publicUrl).toBe("http://hdl.handle.net/21.T11975/k2j9p-7yh21");
    expect(model.providerUrl).toBe("https://b2inst-test.gwdg.de/records/k2j9p-7yh21");
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
  });

  test("alerts on failure without rethrowing", async () => {
    const post = vi.fn().mockRejectedValue(new Error("boom"));
    const model = new IdentifierModel({ ...mockIGSNAttrs(), doiType: "PIDINST_B2INST", state: "submitted" }, "IN1", {
      post,
    } as unknown as typeof InvApiService);
    const addAlert = vi.fn();

    await model.refresh({ addAlert });

    expect(model.state).toBe("submitted");
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "error" }));
  });
});

const pidinstAttrs = (externalMetadataUpdate?: ExternalMetadataUpdate) => ({
  ...mockIGSNAttrs(),
  doiType: "PIDINST_B2INST",
  state: "draft" as const,
  externalMetadataUpdate,
});

/*
 * i18next runs in cimode in tests, so a translated title is its catalog key. Asserting the key
 * proves the wording comes from the catalogue (acceptance criterion 6) without pinning English.
 */
describe("externalMetadataUpdateAlerts", () => {
  test("an identifier without an outcome raises nothing: absent means nothing was attempted", () => {
    const model = new IdentifierModel(pidinstAttrs(), "IN1");
    expect(model.externalMetadataUpdate).toBeNull();
    expect(externalMetadataUpdateAlerts([model])).toEqual([]);
  });

  test("a successful update stays quiet", () => {
    const model = new IdentifierModel(
      pidinstAttrs({
        outcome: "UPDATED",
        reason: "The instrument metadata held by B2INST was updated.",
      }),
      "IN1",
    );
    expect(externalMetadataUpdateAlerts([model])).toEqual([]);
  });

  test("a provider failure is an error that does not auto-dismiss, carrying the server's reason", () => {
    const reason =
      "Could not update the instrument metadata held by B2INST. The instrument itself was saved, so saving it again will try the update once more. Record is not editable.";
    const model = new IdentifierModel(pidinstAttrs({ outcome: "FAILED", reason }), "IN1");

    const alerts = externalMetadataUpdateAlerts([model]);

    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toMatchObject({ variant: "error", message: reason, isInfinite: true });
    expect(alerts[0].title).toBe("inventory:identifierModel.alerts.externalUpdateFailed");
  });

  test("a record frozen by its own state is a notice, not an error", () => {
    const reason =
      "The instrument metadata held by B2INST could not be updated because its community review has been accepted, so the record no longer has a draft open for changes. The instrument itself was saved.";
    const model = new IdentifierModel(pidinstAttrs({ outcome: "NOT_UPDATABLE", reason }), "IN1");

    const alerts = externalMetadataUpdateAlerts([model]);

    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toMatchObject({ variant: "notice", message: reason, isInfinite: true });
    expect(alerts[0].title).toBe("inventory:identifierModel.alerts.externalUpdateNotPossible");
  });

  test("the variant is decided by outcome, never by the wording of the reason", () => {
    const sameWords = "identical wording for both outcomes";
    const failed = new IdentifierModel(pidinstAttrs({ outcome: "FAILED", reason: sameWords }), "IN1");
    const frozen = new IdentifierModel(pidinstAttrs({ outcome: "NOT_UPDATABLE", reason: sameWords }), "IN1");

    expect(externalMetadataUpdateAlerts([failed, frozen]).map((a) => a.variant)).toEqual(["error", "notice"]);
  });

  test("several identifiers each get their own alert, in order", () => {
    const b2inst = new IdentifierModel(pidinstAttrs({ outcome: "FAILED", reason: "B2INST said no" }), "IN1");
    const dataCite = new IdentifierModel(
      {
        ...pidinstAttrs({ outcome: "FAILED", reason: "DataCite said no" }),
        doiType: "PIDINST_DATACITE",
      },
      "IN1",
    );

    expect(externalMetadataUpdateAlerts([b2inst, dataCite]).map((a) => a.message)).toEqual([
      "B2INST said no",
      "DataCite said no",
    ]);
  });

  /*
   * The outcome union mirrors a Java enum by hand, so the server can outrun it. An outcome the UI
   * does not recognise has to fail loud: staying quiet would reinstate exactly the silent drift
   * this feature exists to close, which is the one outcome worse than an unexpected error toast.
   */
  test("an unrecognised outcome is reported as a failure rather than swallowed", () => {
    const reason = "An outcome this UI has no case for.";
    const model = new IdentifierModel(
      pidinstAttrs({
        outcome: "SOME_FUTURE_OUTCOME" as ExternalMetadataUpdateOutcome,
        reason,
      }),
      "IN1",
    );

    const alerts = externalMetadataUpdateAlerts([model]);

    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toMatchObject({ variant: "error", message: reason, isInfinite: true });
  });
});
