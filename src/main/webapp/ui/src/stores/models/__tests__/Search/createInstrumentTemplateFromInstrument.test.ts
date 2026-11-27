import { describe, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import InvApiService from "../../../../common/InvApiService";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import type { FieldModelAttrs } from "../../FieldModel";
import Search from "../../Search";
import { makeMockInstrument } from "../InstrumentModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: { post: vi.fn() },
}));

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    trackingStore: { trackEvent: vi.fn() },
    uiStore: { addAlert: vi.fn() },
  }),
}));

vi.mock("../../../stores/SearchStore", () => ({ default: class {} })); // break import cycle

const linkField = (id: number, link: FieldModelAttrs["link"]): FieldModelAttrs => ({
  id,
  name: `Link ${id}`,
  type: "link",
  content: null,
  selectedOptions: null,
  columnIndex: id,
  attachment: null,
  mandatory: false,
  allowedRelationTypes: [],
  link,
});

describe("createInstrumentTemplateFromInstrument with Link fields", () => {
  test("leaves out a filestore item, which a template's default link cannot hold, and keeps a Global ID", async () => {
    const restoreConsole = silenceConsole(["error"], ["Could not create instrument template from instrument."]);
    try {
      const instrument = makeMockInstrument({
        fields: [
          linkField(1, {
            relationType: "IsSourceOf",
            targetGlobalId: null,
            versionPin: null,
            filestoreTarget: { filestoreId: 12, remotePath: "/XRD/run.dat" },
          }),
          linkField(2, { relationType: "References", targetGlobalId: "SA42", versionPin: null }),
        ],
      });
      vi.spyOn(instrument, "fetchAdditionalInfo").mockResolvedValue(undefined);
      const post = InvApiService.post;
      // only the request body is under test, so the server refuses and the success path is skipped
      vi.mocked(post).mockRejectedValue(new Error("refused"));

      await expect(
        new Search({ factory: new AlwaysNewFactory() }).createInstrumentTemplateFromInstrument(
          "T",
          instrument,
          new Set(),
        ),
      ).rejects.toThrow("refused");

      const body = vi.mocked(post).mock.calls[0][1] as { fields: Array<{ link?: unknown }> };
      expect(body.fields[0].link).toBeNull();
      expect(body.fields[1].link).toEqual({ relationType: "References", targetGlobalId: "SA42", versionPin: null });
    } finally {
      restoreConsole();
    }
  });
});
