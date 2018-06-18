package org.openjdk.jmc.flightrecorder.ext.flamegraph.tree;

import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCMethod;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.util.FormatToolkit;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel.Branch;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel.Fork;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator.FrameCategorization;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceFrame;

public class TraceTreeUtils {
	public final static String DEFAULT_ROOT_NAME = "__root";
	public final static FrameSeparator DEFAULT_FRAME_SEPARATOR = new FrameSeparator(FrameCategorization.METHOD, false);

	/**
	 * Traces a TraceTree from a {@link StacktraceModel}.
	 * 
	 * @param model
	 *            the model to trace the tree from.
	 * @return the root.
	 */
	public static TraceNode createTree(StacktraceModel model, String rootName) {
		Fork rootFork = model.getRootFork();
		TraceNode root = new TraceNode(rootName == null ? DEFAULT_ROOT_NAME : rootName, rootFork.getItemsInFork());
		for (Branch branch : rootFork.getBranches()) {
			addBranch(root, branch);
		}
		return root;
	}

	/**
	 * Traces a tree of stack frames from an {@link IItemCollection}.
	 * 
	 * @param items
	 *            the events to aggregate the traces from.
	 * @return the root of the resulting tree.
	 */
	public static TraceNode createTree(
		IItemCollection items, FrameSeparator frameSeparator, boolean threadRootAtTop, String rootName) {
		return createTree(new StacktraceModel(threadRootAtTop, frameSeparator, items), rootName);
	}

	private static void addBranch(TraceNode root, Branch branch) {
		StacktraceFrame firstFrame = branch.getFirstFrame();
		TraceNode currentNode = new TraceNode(format(firstFrame), firstFrame.getItemCount());
		root.addChild(currentNode);
		for (StacktraceFrame frame : branch.getTailFrames()) {
			TraceNode newNode = new TraceNode(format(frame), frame.getItemCount());
			currentNode.addChild(newNode);
			currentNode = newNode;
		}
		addFork(currentNode, branch.getEndFork());
	}

	private static void addFork(TraceNode node, Fork fork) {
		for (Branch branch : fork.getBranches()) {
			addBranch(node, branch);
		}
	}

	private static String format(StacktraceFrame sFrame) {
		IMCFrame frame = sFrame.getFrame();
		IMCMethod method = frame.getMethod();
		return FormatToolkit.getHumanReadable(method, false, false, true, false, true, false);
	}

	public static String printTree(TraceNode node) {
		StringBuilder builder = new StringBuilder();
		builder.append("=== Tree Printout ===\n");
		printTree(builder, 0, node);
		return builder.toString();
	}

	private static void printTree(StringBuilder builder, int indentation, TraceNode node) {
		builder.append(String.format("%s%s - %d\n", indent(indentation), node.getName(), node.getValue()));
		for (TraceNode child : node.getChildren()) {
			printTree(builder, indentation + 1, child);
		}
	}

	private static String indent(int indentation) {
		String result = "";
		for (int i = 0; i < indentation; i++) {
			result += "   ";
		}
		return result;
	}
}
