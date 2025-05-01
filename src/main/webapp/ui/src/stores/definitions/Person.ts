import { type _LINK } from "../../util/types";
import { type Container, type WorkbenchId } from "../definitions/Container";

export type Username = string;
export type PersonId = number;
export type PersonName = string;
export type Email = string;

export type PersonAttrs = {
  id: PersonId;
  username: Username;
  firstName: PersonName;
  lastName: PersonName;
  hasPiRole: boolean;
  hasSysAdminRole: boolean;
  email: Email | null;
  workbenchId: WorkbenchId;
  _links: Array<_LINK>;
  bench?: Container;
};

export interface Person {
  id: PersonId;
  username: Username;
  firstName: PersonName;
  lastName: PersonName;
  hasPiRole: boolean;
  hasSysAdminRole: boolean;
  email: Email | null;
  bench: Container | null;
  workbenchId: WorkbenchId;

  getBench(): Promise<Container>;

  readonly isCurrentUser: boolean;
  readonly fullName: PersonName;
  readonly label: string;
}
