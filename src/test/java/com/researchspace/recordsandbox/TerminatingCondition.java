package com.researchspace.recordsandbox;

public interface TerminatingCondition {

  /**
   * @param current
   * @param descendant
   * @return true if DFS hierarchical search is to be terminated, <code>false</code> if it should
   *     continue.
   */
  boolean terminate(Node current, NodeContainer parent);
}
