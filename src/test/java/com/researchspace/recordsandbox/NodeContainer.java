package com.researchspace.recordsandbox;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.model.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

@Inheritance(strategy = InheritanceType.JOINED)
public class NodeContainer extends Node {

  public NodeContainer(String data) {
    super(data);
  }

  public NodeContainer() {}

  private Set<RelnEdge> children = new HashSet<RelnEdge>();

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "parent", orphanRemoval = true)
  Set<RelnEdge> getChildren() {
    return children;
  }

  @Transient
  public Set<Node> getChildrens() {
    return getChildrens(DEFAULT_FILTER);
  }

  @Transient
  protected boolean isEdgeDeleted(Node child) {
    for (RelnEdge edge : children) {
      if (edge.getChild().equals(child)) {
        return edge.isMarkedDeleted();
      }
    }
    return false;
  }

  RelnEdge addChild(Node child, User owner) {
    // no self edges
    if (this.equals(child)) {
      return null;
    }
    // no cycles for cycles
    if (!checkParents(this, child)) {
      return null;
    }

    RelnEdge newedge = new RelnEdge(child, this, owner);
    boolean addedP = child.parents.add(newedge);
    boolean addedC = children.add(newedge);
    if (addedC && addedP) return newedge;
    else return null;
  }

  public boolean removeChild(Node child) {

    RelnEdge toRemove = findNodeInChildRelations(child);

    if (toRemove != null) {
      boolean removed1 = children.remove(toRemove);
      boolean removed2 = child.parents.remove(toRemove);
      return removed1 && removed2;
    }
    return false;
  }

  RelnEdge findNodeInChildRelations(Node child) {
    RelnEdge toRemove = null;
    for (RelnEdge reln : children) {
      if (reln.getChild().equals(child)) {
        toRemove = reln;
        break;
      }
    }
    return toRemove;
  }

  /**
   * @param child of this node
   * @param markDeleted <code>true</code> to deleted, <code>false</code>otherwise
   * @return The deletion state of this parent-child relation
   */
  public boolean toggleDeleted(Node child, boolean markDeleted) {

    RelnEdge toRemove = findNodeInChildRelations(child);
    if (toRemove == null) {
      throw new IllegalArgumentException(child + " is not a child of this node");
    }
    if (toRemove != null) {
      toRemove.setMarkedDeleted(markDeleted);
    }
    return toRemove.isMarkedDeleted();
  }

  // checks if existing parents are child.
  private boolean checkParents(Node curr, Node child) {
    boolean ok = true;
    for (RelnEdge parent : curr.parents) {
      if (parent.getParent().equals(child)) {
        ok = false;
        return ok;
      }
      ok = checkParents(parent.getParent(), child);
    }
    return ok;
  }

  void setChildren(Set<RelnEdge> children) {
    this.children = children;
  }

  @Transient
  public Set<Node> getChildrens(CollectionFilter<RelnEdge> rf) {
    Set<Node> rc = new HashSet<Node>();
    for (RelnEdge rel : children) {
      if (rf.filter(rel)) rc.add(rel.getChild());
    }
    return rc;
  }

  public void getFlatListOfNodes(List<Node> rc) {
    if (!rc.contains(this)) rc.add(this);
    for (Node c : getChildrens()) {

      if (c instanceof NodeContainer) {
        ((NodeContainer) c).getFlatListOfNodes(rc);
      }
    }
    return;
  }
}
