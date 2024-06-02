package com.researchspace.recordsandbox;

import com.researchspace.model.User;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

public class RelnEdge {
  public RelnEdge() {}

  public RelnEdge(Node child, NodeContainer parent, User owner) {
    super();
    this.child = child;
    this.parent = parent;
    this.owner = owner;
  }

  private boolean markedDeleted;

  public boolean isMarkedDeleted() {
    return markedDeleted;
  }

  private Long id;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE)
  public Long getId() {
    return id;
  }

  void setId(Long id) {
    this.id = id;
  }

  void setChild(Node child) {
    this.child = child;
  }

  void setParent(NodeContainer parent) {
    this.parent = parent;
  }

  void setOwner(User owner) {
    this.owner = owner;
  }

  public void setMarkedDeleted(boolean markedDeleted) {
    this.markedDeleted = markedDeleted;
  }

  @ManyToOne
  public Node getChild() {
    return child;
  }

  @Override
  public String toString() {
    return "RelnEdge [child="
        + child
        + ", parent="
        + parent
        + ", owner="
        + owner.getUsername()
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((child == null) ? 0 : child.hashCode());
    result = prime * result + ((parent == null) ? 0 : parent.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    RelnEdge other = (RelnEdge) obj;
    if (child == null) {
      if (other.child != null) return false;
    } else if (!child.equals(other.child)) return false;
    if (parent == null) {
      if (other.parent != null) return false;
    } else if (!parent.equals(other.parent)) return false;
    return true;
  }

  @ManyToOne
  public NodeContainer getParent() {
    return parent;
  }

  @OneToOne
  public User getOwner() {
    return owner;
  }

  private Node child;
  private NodeContainer parent;

  private User owner;
}
