// flow-typed signature: 6168687222dfcafc9c4a07fbbef813ad
// flow-typed version: bb2f6ae824/history_v5.x.x/flow_>=v0.104.x

declare module 'history' {
  declare type Unregister = () => void;

  declare export type Action = 'PUSH' | 'REPLACE' | 'POP';

  declare export type Location = {|
    pathname: string,
    search: string,
    hash: string,
    state: { ... },
    key: string,
  |};

  declare type History<HistoryLocation = Location> = {|
    length: number,
    location: HistoryLocation,
    action: Action,
    push: ((path: string, state?: { ... }) => void) &
      ((location: Partial<HistoryLocation>) => void),
    replace: ((path: string, state?: { ... }) => void) &
      ((location: Partial<HistoryLocation>) => void),
    go(n: number): void,
    back(): void,
    forward(): void,
    listen(({| location: HistoryLocation, action: Action |}) => void): Unregister,
    block(
      blocker: (transition: {|
        action: Action,
        location: HistoryLocation,
        retry: () => void,
      |}) => void,
    ): Unregister,
    createHref(location: Partial<HistoryLocation>): string,
  |};

  declare export type BrowserHistory = History<>;

  declare type BrowserHistoryOpts = {|
    basename?: string,
    forceRefresh?: boolean,
  |};

  declare function createBrowserHistory(
    opts?: BrowserHistoryOpts
  ): BrowserHistory;

  declare export type MemoryHistory = {|
    ...History<>,
    index: number,
    entries: Array<string | Location>,
    canGo(n: number): boolean,
  |};

  declare type MemoryHistoryOpts = {|
    initialEntries?: Array<string>,
    initialIndex?: number,
  |};

  declare function createMemoryHistory(opts?: MemoryHistoryOpts): MemoryHistory;

  declare export type HashLocation = {|
    ...Location,
    state: void,
    key: void,
  |};

  declare export type HashHistory = History<HashLocation>;

  declare type HashHistoryOpts = {|
    basename?: string,
  |};

  declare function createHashHistory(opts?: HashHistoryOpts): HashHistory;

  // PathUtils
  declare function parsePath(path: string): Location;

  declare function createPath(location: Partial<Location>): string;

  // LocationUtils
  declare function locationsAreEqual(
    a: Partial<Location>,
    b: Partial<Location>
  ): boolean;

  declare function createLocation(
    path: string | Partial<Location>,
    state?: { ... },
    key?: string,
    currentLocation?: Location
  ): Location;
}
