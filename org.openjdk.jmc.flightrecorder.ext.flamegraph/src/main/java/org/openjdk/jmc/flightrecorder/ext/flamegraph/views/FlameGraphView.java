/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the Universal Permissive License
 * v 1.0 as shown at http://oss.oracle.com/licenses/upl
 *
 * or the following license:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjdk.jmc.flightrecorder.ext.flamegraph.views;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCMethod;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.util.FormatToolkit;
import org.openjdk.jmc.common.util.StringToolkit;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator.FrameCategorization;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceFrame;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel.Branch;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel.Fork;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;
import org.openjdk.jmc.ui.common.util.AdapterUtil;
import org.openjdk.jmc.ui.handlers.MCContextMenuManager;
import org.openjdk.jmc.ui.misc.DisplayToolkit;

public class FlameGraphView extends ViewPart implements ISelectionListener {
	private IItemCollection itemsToShow;
	private FrameSeparator frameSeparator;

	private Browser browser;
	private SashForm container;

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		frameSeparator = new FrameSeparator(FrameCategorization.METHOD, false);
		//methodFormatter = new MethodFormatter(null, () -> viewer.refresh());
		IMenuManager siteMenu = site.getActionBars().getMenuManager();
		siteMenu.add(new Separator(MCContextMenuManager.GROUP_TOP));
		siteMenu.add(new Separator(MCContextMenuManager.GROUP_VIEWER_SETUP));
		// addOptions(siteMenu);
		IToolBarManager toolBar = site.getActionBars().getToolBarManager();
		toolBar.add(new Separator());
		toolBar.add(new Separator());
		getSite().getPage().addSelectionListener(this);
	}

	@Override
	public void dispose() {
		getSite().getPage().removeSelectionListener(this);
		super.dispose();
	}

	@Override
	public void createPartControl(Composite parent) {
		container = new SashForm(parent, SWT.HORIZONTAL);
		browser = new Browser(container, SWT.NONE);
		container.setMaximizedControl(browser);
	}

	@Override
	public void setFocus() {
		browser.setFocus();
	}

	@Override
	public void saveState(IMemento memento) {
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			Object first = ((IStructuredSelection) selection).getFirstElement();
			IItemCollection items = AdapterUtil.getAdapter(first, IItemCollection.class);
			if (items != null && !items.equals(itemsToShow)) {
				setItems(items);
			}
		}
	}

	private void setItems(IItemCollection items) {
		itemsToShow = items;
		rebuildModel();
	}

	private StacktraceModel createStacktraceModel() {
		return new StacktraceModel(false, frameSeparator, itemsToShow);
	}

	private void rebuildModel() {
		// Release old model before building the new
		setViewerInput(null);
		CompletableFuture<StacktraceModel> modelPreparer = getModelPreparer(createStacktraceModel(), true);
		modelPreparer.thenAcceptAsync(this::setModel, DisplayToolkit.inDisplayThread())
				.exceptionally(FlameGraphView::handleModelBuildException);
	}

	private static CompletableFuture<StacktraceModel> getModelPreparer(
		StacktraceModel model, boolean materializeSelectedBranches) {
		return CompletableFuture.supplyAsync(() -> {
			return model;
		});
	}

	private static Void handleModelBuildException(Throwable ex) {
		FlightRecorderUI.getDefault().getLogger().log(Level.SEVERE, "Failed to build stacktrace view model", ex); //$NON-NLS-1$
		return null;
	}

	private void setModel(StacktraceModel model) {
		if (!browser.isDisposed()) {
			setViewerInput(model.getRootFork());
		}
	}

	private void setViewerInput(Fork rootFork) {
		try {
			System.out.println("Is JavaScript enabled? = " + browser.getJavascriptEnabled());
			browser.execute("data.json = '" + toPlayJSon(rootFork) + "'");
			browser.setText(StringToolkit.readString(FlameGraphView.class.getResourceAsStream("page.html")));
		} catch (IOException e) {
			browser.setText(e.getMessage());
			e.printStackTrace();
		}
	}

	private Object toPlayJSon(Fork rootFork) throws IOException {
		return StringToolkit.readString(FlameGraphView.class.getResourceAsStream("test.json"));
	}

	private static String toJSon(Fork rootFork) {
		if (rootFork == null) {
			return "";
		}
		return render(rootFork);
	}

	private static String render(Fork rootFork) {
		StringBuilder builder = new StringBuilder();
		addFlameData(builder, "", rootFork);
		return builder.toString();
	}

	private static void addFlameData(StringBuilder builder, String parentFrameNames, Fork fork) {
		for (Branch branch : fork.getBranches()) {
			StacktraceFrame countedFrame = branch.getFirstFrame();
			int itemCount = countedFrame.getItemCount();
			String branchFrameNames = parentFrameNames + format(branch.getFirstFrame());
			for (StacktraceFrame tailFrame : branch.getTailFrames()) {
				// Look for non-branching leafs
				if (tailFrame.getItemCount() < itemCount) {
					builder.append(branchFrameNames + " " + (itemCount - tailFrame.getItemCount()));
					countedFrame = tailFrame;
					itemCount = tailFrame.getItemCount();
				}
				branchFrameNames = branchFrameNames + ";" + format(tailFrame);
			}
			Fork endFork = branch.getEndFork();
			if (itemCount - endFork.getItemsInFork() > 0) {
				// No need to print forking frame if it is not a leaf
				builder.append(branchFrameNames + " " + (itemCount - endFork.getItemsInFork()));
			}
			addFlameData(builder, branchFrameNames + ";", endFork);
		}
	}

	private static String format(StacktraceFrame sFrame) {
		IMCFrame frame = sFrame.getFrame();
		IMCMethod method = frame.getMethod();
		return FormatToolkit.getHumanReadable(method);
	}
}
