// @flow

/*
 * Each of the Repository components in this directory collect various pieces
 * of metadata to be attached to export and included by the backend in the
 * API calls to the repository services. These types model the metadata that
 * should be collected by each component as well as any information that the
 * system already knows about that may be of interest to the Repository
 * components.
 */

export type Person = {|
  uniqueName: string,
  email: string,
  type: string,
|};

export type DMPId = number | string;
export type DMPUserInternalId = number;

export type Plan = {
  dmpId: DMPId,
  dmpTitle: string,
  dmpUserInternalId: DMPUserInternalId,
};

export type Repo = {|
  repoName: string,
  displayName: string,
  subjects: Array<{ id: string, name: string, parentSubject: number }>,
  license: {
    licenseRequired: boolean,
    otherLicensePermitted: boolean,
    licenses: Array<{
      licenseDefinition: { url: string, name: string },
      defaultLicense: boolean,
    }>,
  },
  linkedDMPs: ?Array<Plan>,
  label?: string,
  repoCfg: mixed,
|};

/*
 * These are the standard fields that each of the Repositories have to
 * collect metadata about the export that the user is making. For each,
 * ExportRepo performs some validations and then sets these booleans to true
 * if the current state of the field passes all of the validations,
 * returning false if any fail. Each of the Repo components in this
 * directory should render the respective fields in an error state when the
 * value in this object is false.
 */
export type StandardValidations = {|
  description: boolean,
  title: boolean,
  author: boolean,
  contact: boolean,
  subject: boolean,
|};
