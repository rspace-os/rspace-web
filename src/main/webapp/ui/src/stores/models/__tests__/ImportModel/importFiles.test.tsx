import "@/stores/stores/RootStore";
import { runInAction } from "mobx";
import { beforeEach, describe, expect, test, vi } from "vitest";
import type { AxiosRequestHeaders, AxiosResponse, InternalAxiosRequestConfig } from "@/common/axios";
import InvApiService from "../../../../common/InvApiService";
import getRootStore from "../../../stores/getRootStore";
import { FieldTypes } from "../../FieldTypes";
import ImportModel, { ColumnFieldMap, Fields } from "../../ImportModel";
import { makeMockInstrumentTemplate } from "../InstrumentTemplateModel/mocking";
import { templateAttrs } from "../TemplateModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    post: vi.fn(),
  },
}));

function makeInstrumentNameMapping(model: ImportModel): ColumnFieldMap {
  return new ColumnFieldMap({
    recordType: "INSTRUMENTS",
    selected: true,
    columnName: "Name",
    field: Fields.name,
    fieldName: "Name",
    fieldType: FieldTypes.plain_text,
    quantityUnitId: null,
    options: null,
    fieldChangeCallback: () => {},
    isNameUnique: (c) => model.isNameUnique(c),
    columnsWithoutBlankValue: ["Name"],
  });
}

describe("importFiles — instrumentSettings serialization", () => {
  test("existing template: templateId is set and templateInfo is null", async () => {
    const model = new ImportModel("INSTRUMENTS");
    const template = makeMockInstrumentTemplate({ id: 42 });
    runInAction(() => {
      model.instrumentsFile = new File([""], "instruments.csv");
      model.instrumentsMappings = [makeInstrumentNameMapping(model)];
      model.instrumentCreateNewTemplate = false;
      model.instrumentTemplate = template;
    });
    vi.spyOn(model.state, "transitionTo").mockImplementation(() => {});
    vi.spyOn(InvApiService, "post").mockResolvedValueOnce({
      data: {
        status: "COMPLETED",
        containerResults: null,
        sampleResults: null,
        subSampleResults: null,
        instrumentResults: { type: "INSTRUMENT", status: "COMPLETED", results: [], templateResult: null },
      },
      status: 200,
      statusText: "OK",
      headers: {},
      config: { headers: {} as AxiosRequestHeaders } as InternalAxiosRequestConfig,
    });

    await model.importFiles();

    const [url, formData] = vi.mocked(InvApiService.post).mock.calls[0] as [string, FormData];
    expect(url).toBe("/import/importFiles");
    const { instrumentSettings } = JSON.parse(formData.get("importSettings") as string) as {
      instrumentSettings: { templateId: number; templateInfo: null; fieldMappings: Record<string, string> };
    };
    expect(instrumentSettings).toEqual({
      templateId: 42,
      templateInfo: null,
      fieldMappings: { Name: "name" },
    });
  });
});

const mockErrorMsg = "Unexpected number of values in CSV line, expected: 2, was: 3";
describe("method: importFile", () => {
  describe("When the server responds with some errors,", () => {
    beforeEach(() => {
      vi.spyOn(InvApiService, "post").mockImplementation((_resource: string, _params: object | FormData) => {
        const mockResponse: AxiosResponse = {
          data: {
            sampleResults: {
              errorCount: 3,
              results: [
                { error: null, record: {} },
                { error: { errors: [mockErrorMsg] }, record: null },
                { error: { errors: [mockErrorMsg] }, record: null },
              ],
              status: "PREVALIDATION_ERROR",
              successCount: 0,
              successCountBeforeFirstError: 1,
              templateResult: {
                error: null,
                record: templateAttrs(),
              },
              type: "SAMPLE",
            },
            containerResults: null,
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {
            headers: {} as AxiosRequestHeaders,
          } as InternalAxiosRequestConfig,
        };
        return Promise.resolve(mockResponse);
      });
      vi.spyOn(ImportModel.prototype, "transformTemplateInfoForSubmission").mockImplementation(() => ({
        fields: [],
        name: "Template",
      }));
      vi.spyOn(ImportModel.prototype, "makeMappingsObject").mockImplementation(() => ({}));
    });
    test("they should have the correct index.", async () => {
      const uploadModel = new ImportModel("SAMPLES");

      const addAlertSpy = vi.fn();
      runInAction(() => {
        getRootStore().uiStore.addAlert = addAlertSpy;
      });

      vi.spyOn(uploadModel.state, "transitionTo").mockImplementation(() => {});
      await uploadModel.importFiles();
      expect(addAlertSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          variant: "error",
          details: [
            {
              variant: "error",
              title: "inventory:import.records.row",
              help: mockErrorMsg,
            },
            {
              variant: "error",
              title: "inventory:import.records.row",
              help: mockErrorMsg,
            },
          ],
        }),
      );
    });
  });
});
