/*
 * Each of the Repository components in this directory collect various pieces
 * of metadata to be attached to export and included by the backend in the
 * API calls to the repository services. These types model the metadata that
 * should be collected by each component as well as any information that the
 * system already knows about that may be of interest to the Repository
 * components.
 */

import type { DMPUserInternalId, Plan } from "@/eln-dmp-integration/linkedPlan";

/**
 * The definition of a person when collecting metadata such as author and
 * contact.
 */
export type Person = {
  uniqueName: string;
  email: string;
  type: string;
};

/**
 * The definition of a repository, for the purposes of selecting one to
 * deposit to.
 */
export type Repo = {
  repoName: string;
  displayName: string;
  subjects: Array<{
    id: string | null;
    name: string;
    parentSubject: number | null;
  }>;
  license: {
    licenseRequired: boolean;
    otherLicensePermitted: boolean;
    licenses: Array<{
      licenseDefinition: { url: string; name: string };
      defaultLicense: boolean | null;
    }>;
  };

  /*
   * The set of DMPs from the user's Gallery, from which they can pick some to
   * associate with the export.
   */
  linkedDMPs: Array<Plan> | null;
  metadataLanguages?: { title: string; locale: string }[];
  label?: string;
  repoCfg: unknown;
};

/**
 * These are the standard fields that each of the Repositories have to
 * collect metadata about the export that the user is making. For each,
 * ExportRepo performs some validations and then sets these booleans to true
 * if the current state of the field passes all of the validations,
 * returning false if any fail. Each of the Repo components in this
 * directory should render the respective fields in an error state when the
 * value in this object is false.
 */
export type StandardValidations = {
  description: boolean;
  title: boolean;
  author: boolean;
  contact: boolean;
  subject: boolean;
};

export const DEFAULT_REPO_CONFIG = {
  repoChoice: 0,
  meta: {
    title: "",
    description: "",
    subject: "",
    licenseName: "",
    authors: [] as Array<Person>,
    contacts: [] as Array<Person>,
    publish: false as boolean,
    otherProperties: {},
  },
  depositToRepository: false as boolean,
  exportToRaid: false,
  selectedDMPs: [] as Array<DMPUserInternalId>,
};

export type RepoDetails = typeof DEFAULT_REPO_CONFIG;

/*
 * The backend's RepoDepositMeta requires authors, contacts and a subject for
 * every deposit, but Zenodo and Digital Commons Data collect none of them and
 * their deposits ignore these values. Placeholders are added only to the
 * export request for these repositories, never to the dialog's shared state,
 * so they cannot leak into another repository's form.
 */
export const REPOS_WITHOUT_PEOPLE_OR_SUBJECT: ReadonlySet<string> = new Set(["app.zenodo", "app.digitalCommonsData"]);

export const PLACEHOLDER_PEOPLE_AND_SUBJECT: {
  subject: string;
  authors: Array<Person>;
  contacts: Array<Person>;
} = {
  subject: "DUMMY_VALUE",
  authors: [{ uniqueName: "DUMMY_VALUE", email: "DUMMY_VALUE@example.com", type: "Author" }],
  contacts: [{ uniqueName: "DUMMY_VALUE", email: "DUMMY_VALUE@example.com", type: "Contact" }],
};
