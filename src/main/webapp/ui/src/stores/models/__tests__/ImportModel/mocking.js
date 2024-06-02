//@flow
import ImportModel from "../../ImportModel";
import { makeMockTemplate } from "../TemplateModel/mocking";

export const makeMockImportDataUsingExistingTemplate = (): ImportModel => {
  const importModel = new ImportModel("SAMPLES");
  importModel.setTemplate(makeMockTemplate());
  importModel.setCreateNewTemplate(false);
  return importModel;
};
