package com.researchspace.recordsandbox;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.model.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import javax.persistence.CascadeType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

@Inheritance(strategy = InheritanceType.JOINED)
public class Node {

  Set<RelnEdge> parents = new HashSet<RelnEdge>();

  private Long id;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE)
  public Long getId() {
    return id;
  }

  void setId(Long id) {
    this.id = id;
  }

  public Node() {}

  private String userNameRoot;

  public String getUserNameRoot() {
    return userNameRoot;
  }

  public void setUserNameRoot(String userNameRoot) {
    this.userNameRoot = userNameRoot;
  }

  private String data;

  public boolean isRootForUser(User u) {
    return userNameRoot != null && userNameRoot.equals(u.getUsername());
  }

  public Node(String data) {
    super();
    this.data = data;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  void setParents(Set<RelnEdge> parents) {
    this.parents = parents;
  }

  @Transient
  public Set<NodeContainer> getParentNodes() {
    Set<NodeContainer> rc = new HashSet<NodeContainer>();
    for (RelnEdge rel : parents) {
      rc.add(rel.getParent());
    }
    return rc;
  }

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "child")
  Set<RelnEdge> getParents() {
    return parents;
  }

  static final CollectionFilter<RelnEdge> DEFAULT_FILTER =
      new CollectionFilter<RelnEdge>() {
        public boolean filter(RelnEdge toFilter) {
          return true;
        }
      };
  static final CollectionFilter<RelnEdge> FILTER_DELETED =
      new CollectionFilter<RelnEdge>() {
        public boolean filter(RelnEdge toFilter) {
          if (toFilter.isMarkedDeleted()) {
            return false;
          } else {
            return true;
          }
        }
      };

  static final TerminatingCondition NULL_TERMINATOR =
      new TerminatingCondition() {

        @Override
        public boolean terminate(Node current, NodeContainer child) {
          return false;
        }
      };

  static final TerminatingCondition TERMINATE_IF_DELETED =
      new TerminatingCondition() {

        @Override
        public boolean terminate(Node current, NodeContainer parent) {
          return parent.isEdgeDeleted(current);
        }
      };

  public boolean hasParents() {
    return parents.size() > 0;
  }

  public boolean hasSingleParent() {
    return parents.size() == 1;
  }

  /**
   * @return
   * @throws IllegalStateException if has > 1 parent
   */
  @Transient
  public Node getSingleParent() {
    if (parents.size() > 1) {
      throw new IllegalStateException("there are " + parents.size() + " parents!");
    }
    if (!hasParents()) {
      return null;
    } else {
      return parents.iterator().next().getParent();
    }
  }

  @Transient
  RelnEdge getEdge(Node from, Node parent) {
    for (RelnEdge edge : parents) {
      if (edge.getChild().equals(from) && edge.getParent().equals(parent)) {
        return edge;
      }
    }
    return null;
  }

  public boolean move(NodeContainer from, NodeContainer to, User u) {
    if (from != null && !this.getParentNodes().contains(from)) {
      return false;
    }
    boolean removed = true;
    if (from != null) {
      removed = from.removeChild(this);
    }
    RelnEdge added = to.addChild(this, u);
    return added != null && removed;
  }

  @Transient
  public List<Node> getParentHierarchyForUser(User u) {
    return getParentHierarchyForUser(u, NULL_TERMINATOR);
  }

  /**
   * Does DFS up the node hierarchy until a terminating node owned by the user is reached.
   *
   * @param user
   * @return List of nodes in parent->child order.
   */
  @Transient
  public List<Node> getParentHierarchyForUser(User u, TerminatingCondition terminator) {
    Stack<Node> stack = new Stack<Node>();
    stack.push(this);
    List<Node> parents = new ArrayList<Node>();

    Set<Node> visited = new HashSet<Node>();
    // stores the path used toascend the hierarchy
    Map<Node, Node> predecessor = new HashMap<Node, Node>();
    Node terminal = null;
    while (!stack.isEmpty()) {
      Node curr = stack.pop();
      if (!visited.contains(curr)) {
        visited.add(curr);
        if (curr.isRootForUser(u)) {
          terminal = curr;
          break;
        }
        for (NodeContainer parent : curr.getParentNodes()) {
          if (!terminator.terminate(curr, parent)) {
            stack.push(parent);
            predecessor.put(parent, curr);
          }
        }
      }
    }
    // e.g., this will be null if search up to user root was blocked - e.g., by delettion.
    if (terminal == null) {
      return parents;
    }
    parents.add(terminal);
    Node key = terminal;
    // backtrack to get path from search node to parent
    while (predecessor.get(key) != null) {
      Node val = predecessor.get(key);
      parents.add(val);
      key = val;
    }
    return parents;
  }

  @Override
  public String toString() {
    return "Node [data=" + data + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((data == null) ? 0 : data.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Node other = (Node) obj;
    if (data == null) {
      if (other.data != null) return false;
    } else if (!data.equals(other.data)) return false;
    return true;
  }
}
