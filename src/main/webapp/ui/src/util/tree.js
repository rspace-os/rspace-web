// @flow

import { computed, makeObservable } from "mobx";

export class TreeNode<T> {
  value: T;
  parent: ?TreeNode<T>;
  children: Array<TreeNode<T>>;

  constructor(
    value: T,
    parent: ?TreeNode<T> = null,
    children: Array<TreeNode<T>> = []
  ) {
    this.value = value;
    this.parent = parent;
    if (parent) parent.children.push(this);
    this.children = children;
    makeObservable(this, {
      pathToRoot: computed,
    });
  }

  get pathToRoot(): Array<TreeNode<T>> {
    return [this, ...(this.parent ? this.parent.pathToRoot : [])];
  }
}
