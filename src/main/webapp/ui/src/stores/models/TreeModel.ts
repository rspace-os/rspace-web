import { action, observable, computed, makeObservable } from "mobx";
import Search from "./Search";
import Result from "./InventoryBaseRecord";
import getRootStore from "../stores/RootStore";
import { type GlobalId } from "../definitions/BaseRecord";
import {
  type RecordType,
  type InventoryRecord,
} from "../definitions/InventoryRecord";
import { type HasChildren } from "../definitions/HasChildren";
import { UserCancelledAction } from "../../util/error";
import { type TreeView } from "../definitions/TreeView";

export type TreeAttrs = {
  treeHolder: Search;
  filteredTypes?: Array<RecordType>;
};

export default class TreeModel implements HasChildren, TreeView {
  // @ts-expect-error is set by the constructor's call to setAttributes
  treeHolder: Search;
  expanded: Array<string> = [];
  filteredTypes: Array<RecordType> = ["container"];

  constructor(params: TreeAttrs) {
    makeObservable(this, {
      treeHolder: observable,
      expanded: observable,
      filteredTypes: observable,
      setAttributes: action,
      setExpanded: action,
      setSelected: action,
      selected: computed,
      results: computed,
      selectedNode: computed,
      children: computed,
    });
    this.setAttributes(params);
  }

  get selected(): GlobalId | null {
    if (this.treeHolder.activeResult === null) return null;
    return this.treeHolder.activeResult.globalId;
  }

  get results(): Array<InventoryRecord> {
    return this.treeHolder.filteredResults;
  }

  setAttributes(attrs: TreeAttrs) {
    Object.assign(this, attrs);
  }

  get selectedNode(): Result | null {
    return findNode(this.selected, this);
  }

  setExpanded(ids: Array<string>): void {
    this.expanded = ids;
  }

  setSelected(globalId: GlobalId | null) {
    this.treeHolder
      .setActiveResult(findNode(globalId, this))
      .then(() => {
        getRootStore().uiStore.setVisiblePanel("right");
      })
      .catch((e) => {
        if (e instanceof UserCancelledAction) return;
        throw e;
      });
  }

  get children(): Array<InventoryRecord> {
    return this.treeHolder?.results ?? [];
  }

  loadChildren() {}

  get canNavigateToChildren(): boolean {
    return false;
  }
}

/*
 * Recurse down tree of records, where the tree structure is dependent on the
 *  record type, to find the record with the passed Global ID. Null is returned
 *  if no such record exists in the tree.
 */
const findNode = (
  globalId: GlobalId | null,
  node: HasChildren
): Result | null => {
  if (node instanceof Result && node.globalId === globalId) return node;
  return node.children.reduce<Result | null>(
    (acc, result) => acc ?? findNode(globalId, result),
    null
  );
};
