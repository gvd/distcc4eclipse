package com.ghvandoorn.distcc.views;


import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;



public class DistccStatusView extends ViewPart implements IPartListener {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.ghvandoorn.distcc.views.DistccStatusView";
	public static final String DISTCC_STATE_LOCATION = System.getProperty("user.home") + "/.distcc/state/";

	private TableViewer viewer;
	private Timer mTimer = null;
	
	// See http://distcc.sourcearchive.com/documentation/3.1-3.1build1/src_2state_8h-source.html
	private enum DccPhase {
		STARTUP, BLOCKED, CONNECT, CPP, SEND, COMPILE, RECEIVE, DONE, UNKNOWN
	}

	static class DccState {
		
		private String stateFilename = null;
		private long mSize = -1;
		private long mMagic = -1;
		private long mPID = -1;
		private String mFilename = null;
		private String mHost = null;
		private int mSlot = -1;
		private DccPhase phase = DccPhase.UNKNOWN;
		private static final ByteOrder mByteOrder = ByteOrder.nativeOrder();

		public DccState(String filename) {
			this.setStateFilename(filename);
			DataInputStream in = null;
			try {
				in = new DataInputStream(
						new BufferedInputStream(
								new FileInputStream(filename)));
							
				setSize(reverse(in.readLong()));			
				setMagic(reverse(in.readLong()));
				setCpid(reverse(in.readLong()));
				byte[] str = new byte[128];
				in.read(str);
				setFilename(new String(str));
				in.read(str);
				setHost(new String(str));
				setSlot(reverse(in.readInt()));
				int phase_nr = reverse(in.readInt());
				if (phase_nr < DccPhase.values().length) {
					setPhase(DccPhase.values()[phase_nr]);
				}

			} catch (IOException e) {
				try {
					in.close();
				} catch (IOException e1) {
				}
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
					}
				}			
			}
		}
		
		long reverse(long val) {
			ByteBuffer bbuf = ByteBuffer.allocate(8);
			return bbuf.order(ByteOrder.BIG_ENDIAN).putLong(val).order(mByteOrder).getLong(0);
		}
		int reverse(int val) {
			ByteBuffer bbuf = ByteBuffer.allocate(4);
			return bbuf.order(ByteOrder.BIG_ENDIAN).putInt(val).order(mByteOrder).getInt(0);
		}

		public String getFilename() {
			return mFilename;
		}

		public void setFilename(String filename) {
			this.mFilename = filename;
		}

		public long getSize() {
			return mSize;
		}

		public void setSize(long size) {
			this.mSize = size;
		}

		public long getCpid() {
			return mPID;
		}

		public void setCpid(long cpid) {
			this.mPID = cpid;
		}

		public long getMagic() {
			return mMagic;
		}

		public void setMagic(long magic) {
			this.mMagic = magic;
		}

		public String getHost() {
			return mHost;
		}

		public void setHost(String host) {
			this.mHost = host;
		}

		public int getSlot() {
			return mSlot;
		}

		public void setSlot(int slot) {
			this.mSlot = slot;
		}

		public DccPhase getPhase() {
			return phase;
		}

		public void setPhase(DccPhase phase) {
			this.phase = phase;
		}

		public String getStateFilename() {
			return stateFilename;
		}

		public void setStateFilename(String stateFilename) {
			this.stateFilename = stateFilename;
		}

		public boolean isValid() {
			if (mSize > 0 && mMagic >= 0 && mPID >= 0 && mSlot >= 0
					&& !mHost.trim().isEmpty() && !mFilename.trim().isEmpty()) {
				return true;
			}
			return false;
		}
	}
	 
	class ViewContentProvider implements IStructuredContentProvider {
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}
		public void dispose() {
		}
		public Object[] getElements(Object parent) {
			return (Object[]) parent;
		}
	}
	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			return getText(obj);
		}
		public Image getColumnImage(Object obj, int index) {
			return getImage(obj);
		}
		public Image getImage(Object obj) {
			return PlatformUI.getWorkbench().
					getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
	}
	class StateSorter extends ViewerComparator {
		@Override
	    public int compare(Viewer viewer, Object e1, Object e2) {
	        if (e1 instanceof DccState && e2 instanceof DccState) {
	        	DccState state1 = (DccState) e1;
	        	DccState state2 = (DccState) e2;
	        	if (state1.getHost().compareToIgnoreCase(state2.getHost()) == 0) {
	        		if (state1.getSlot() == state2.getSlot()) {
	        			return 0;
	        		} else if (state1.getSlot() > state2.getSlot()) {
	        			return 1;
	        		} else {
	        			return -1;
	        		}
	        	} else {
	        		return state1.getHost().compareToIgnoreCase(state2.getHost());
	        	}
	            
	        }
	        throw new IllegalArgumentException("Not comparable: " + e1 + " " + e2);
	    }
	}
	
	class UpdateTask extends TimerTask {
        public void run() {
			File dir = new File(DISTCC_STATE_LOCATION);
			File[] files = dir.listFiles();
			final List<DccState> list = new ArrayList<DccState>();
			for (File file : files) {
				DccState state = new DccState(file.getAbsolutePath());
				if (state.isValid()) {
					File process = new File("/proc/" + state.getCpid());
					if (process.exists()) {
						list.add(state);
					}
				}
			}
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					if (!viewer.getControl().isDisposed()) {
						viewer.setInput(list.toArray());
					}
				}
			});
        }
    }

	/**
	 * The constructor.
	 */
	public DistccStatusView() {
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());
		viewer.setComparator(new StateSorter());
		
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		setupColumns();

		// Create the help context id for the viewer's control
		PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(), "com.ghvandoorn.distcc.viewer");

		File distccDir = new File(DISTCC_STATE_LOCATION);
		mTimer = new Timer();
		if (distccDir.exists()) {
			mTimer.schedule(new UpdateTask(), 0, 1000);
		} else {
			System.err.println("Distcc state directory not found: " + DISTCC_STATE_LOCATION);
		}
	}
	
	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		IPartService service = (IPartService) getSite().getService(IPartService.class);
		service.addPartListener(this);
	}
	
	public void setupColumns() {
		TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
		col.getColumn().setWidth(200);
		col.getColumn().setText("Host");
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				DccState state = (DccState) element;
				return state.getHost();
			}
		});
		col = new TableViewerColumn(viewer, SWT.NONE);
		col.getColumn().setWidth(100);
		col.getColumn().setText("Slot");
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				DccState state = (DccState) element;
				return String.valueOf(state.getSlot());
			}
		});
		col = new TableViewerColumn(viewer, SWT.NONE);
		col.getColumn().setWidth(250);
		col.getColumn().setText("File");
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				DccState state = (DccState) element;
				return state.getFilename();
			}
		});
		col = new TableViewerColumn(viewer, SWT.NONE);
		col.getColumn().setWidth(150);
		col.getColumn().setText("Phase");
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				DccState state = (DccState) element;
				return state.getPhase().toString();
			}
		});
		col = new TableViewerColumn(viewer, SWT.NONE);
		col.getColumn().setWidth(100);
		col.getColumn().setText("PID");
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				DccState state = (DccState) element;
				return String.valueOf(state.getCpid());
			}
		});
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	@Override
	public void partActivated(IWorkbenchPart part) {
	}

	@Override
	public void partBroughtToTop(IWorkbenchPart part) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void partClosed(IWorkbenchPart part) {
		mTimer.cancel();		
	}

	@Override
	public void partDeactivated(IWorkbenchPart part) {
	}

	@Override
	public void partOpened(IWorkbenchPart part) {
		// TODO Auto-generated method stub
		
	}
}