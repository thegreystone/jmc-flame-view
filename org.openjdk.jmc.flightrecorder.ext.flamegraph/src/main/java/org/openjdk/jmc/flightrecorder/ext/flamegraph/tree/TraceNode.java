package org.openjdk.jmc.flightrecorder.ext.flamegraph.tree;

import java.util.ArrayList;
import java.util.List;

public class TraceNode {
	private final int value;
	private final String name;
	private final List<TraceNode> children = new ArrayList<>();

	public TraceNode(String name, int value) {
		this.name = name;
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public String getName() {
		return name;
	}

	public List<TraceNode> getChildren() {
		return children;
	}

	public void addChild(TraceNode child) {
		children.add(child);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((children == null) ? 0 : children.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + value;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TraceNode other = (TraceNode) obj;
		if (children == null) {
			if (other.children != null)
				return false;
		} else if (!children.equals(other.children))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (value != other.value)
			return false;
		return true;
	}

	public String toString() {
		return "TraceNode [name: " + name + ", value: " + value + ", children: " + children.size() + "]";
	}
}
