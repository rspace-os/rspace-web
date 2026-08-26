import { runInAction } from "mobx";
import { describe, expect, test, vi } from "vitest";
import type InvApiService from "../../../common/InvApiService";
import { mockIGSNAttrs } from "../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import { isPublishedState } from "../../definitions/Identifier";
import IdentifierModel from "../IdentifierModel";

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
