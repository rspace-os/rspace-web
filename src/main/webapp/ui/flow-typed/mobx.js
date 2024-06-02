//@flow

/*
 * This type declaration file declares the types of the mobx dependencies, so
 * that will get a little more type safety when making calls to the library.
 * Getting these right is quite tricky, so most have been left as `any` which
 * is no different than if there were no declaration at all. Those that can be
 * (relatively) easily typed have been and thus will provide greater type
 * guarantees.
 */

declare module "mobx" {
  declare type Computed<A> = {|
    get(): A,
  |};

  declare function action<A, B = void>((A) => B): (A) => B;
  declare function observable<A>(A): A;
  declare function computed<A>(() => A): Computed<A>;
  declare function when(any): any;
  declare function override<A>(A): A;
  declare function makeObservable(
    any,
    {
      [string]:
        | typeof observable
        | typeof computed
        | typeof action
        | typeof override,
    }
  ): void;
  declare function makeAutoObservable<A>(A): A;
  declare function runInAction<A = void>(() => A): A;
  declare function autorun(() => void): void;
}

declare module "mobx-react-lite" {
  import type { Node, ComponentType } from "react";

  declare function useLocalObservable<A>(() => A): A;
  declare function observer<P, T: ComponentType<P>>(T): T;
  declare function Observer({ children: Node | (() => Node) }): Node;
}
