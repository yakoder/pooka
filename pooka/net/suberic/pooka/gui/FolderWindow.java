package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import javax.mail.event.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.TextAction;
import java.util.*;
import net.suberic.util.gui.*;
import net.suberic.util.event.*;
import net.suberic.util.thread.*;
import net.suberic.util.swing.*;

/**
 * This basically is just the GUI representation of the Messages in
 * a Folder.  Most of the real work is done by the FolderInfo
 * class.
 */

public class FolderWindow extends JInternalFrame implements FolderDisplayUI {
    JTable messageTable = null;
    JScrollPane scrollPane = null;
    FolderInfo folderInfo = null;
    StatusBar statusBar = null;
    MessagePanel messagePanel = null;
    ConfigurableToolbar toolbar;
    ConfigurableKeyBinding keyBindings;
    boolean enabled = true;

    public class StatusBar extends JPanel implements MessageCountListener, MessageChangedListener {
	JLabel folderLabel;
	JLabel messageCount;
	JPanel loaderPanel;

	public StatusBar() {
	    this.setLayout(new FlowLayout(FlowLayout.LEFT));
	    folderLabel = new JLabel(getFolderInfo().getFolderName());
	    this.add(folderLabel);
	    this.add(new JSeparator(SwingConstants.VERTICAL));
	    messageCount = new JLabel();
	    updateMessageCount();
	    this.add(messageCount);
	    this.add(new JSeparator(SwingConstants.VERTICAL));
	    loaderPanel = new JPanel();
	    this.add(loaderPanel);
	}
	
	public void messageChanged(MessageChangedEvent mce) {
	    updateMessageCount();
	}

	public void messagesAdded(MessageCountEvent e) {
	    updateMessageCount();
	}

	public void messagesRemoved(MessageCountEvent e) {
	    updateMessageCount();
	}

	public void updateMessageCount() {
	    SwingUtilities.invokeLater(new RunnableAdapter() {
		    public void run() {
			messageCount.setText(getFolderInfo().getUnreadCount() + " " + Pooka.getProperty("FolderStatusBar.unreadMessages", "Unread") + " / " + getFolderInfo().getMessageCount() + " " + Pooka.getProperty("FolderStatusBar.totalMessages", "Total"));
		    }
		});
	}
	} // end internal class StatusBar

    /**
     * Creates a Folder window from the given Folder.
     */

    public FolderWindow(FolderInfo newFolderInfo, MessagePanel newMessagePanel) {
	super(newFolderInfo.getFolderName() + " - " + newFolderInfo.getParentStore().getStoreID(), true, true, true, true);

	messagePanel = newMessagePanel;

	setFolderInfo(newFolderInfo);

	getFolderInfo().setFolderDisplayUI(this);

	defaultActions = new Action[] {
	    new CloseAction(),
	    new ActionWrapper(new ExpungeAction(), getFolderInfo().getFolderThread()),
	    new SearchAction()
		};

	initWindow();
	toolbar = new ConfigurableToolbar("FolderWindowToolbar", Pooka.getResources());
	this.getContentPane().add("North", toolbar);

	keyBindings = new ConfigurableKeyBinding(this, "FolderWindow.keyBindings", Pooka.getResources());

	keyBindings.setActive(getActions());
	toolbar.setActive(getActions());

	// if the FolderWindow itself gets the focus, pass it on to
	// the messageTable

	this.addFocusListener(new FocusAdapter() {
		public void focusGained(FocusEvent e) {
		    messageTable.requestFocus();
		    if (getFolderInfo().hasNewMessages()) {
			getFolderInfo().setNewMessages(false);
			FolderNode fn = getFolderInfo().getFolderNode();
			if (fn != null)
			    fn.getParentContainer().repaint();
		    }
		    
		}
	    });

    }

    /**
     * Initializes the window. 
     *
     * TODO:  should get these values from the Resources...
     */

    private void initWindow() {
	scrollPane = new JScrollPane();
	this.getContentPane().setLayout(new BorderLayout());
	this.getContentPane().add("Center", scrollPane);
	setStatusBar(new StatusBar());
	getFolderInfo().addMessageCountListener(getStatusBar());
	getFolderInfo().addMessageChangedListener(getStatusBar());
	getFolderInfo().addMessageCountListener(new MessageCountAdapter() {
		public void messagesRemoved(MessageCountEvent e) {
		    //		    net.suberic.util.swing.RunnableAdapter updateAdapter = new net.suberic.util.swing.RunnableAdapter() {
		    Runnable updateAdapter = new Runnable() {
			    public void run() {
		    getMessagePanel().getMainPanel().refreshActiveMenus();
		    if (toolbar != null)
			toolbar.setActive(getActions());
		    if (keyBindings != null)
			keyBindings.setActive(getActions());
			    }
			};
		    if (SwingUtilities.isEventDispatchThread())
			updateAdapter.run();
		    else
			SwingUtilities.invokeLater(updateAdapter);
		}
	    });
		    

	this.getContentPane().add("South", getStatusBar());

	this.setPreferredSize(new Dimension(Integer.parseInt(Pooka.getProperty("folderWindow.height", "570")), Integer.parseInt(Pooka.getProperty("folderWindow.width","380"))));
	messageTable=new JTable(getFolderInfo().getFolderTableModel());
	if (!Pooka.getProperty("FolderTable.showLines", "true").equals("true")) {
	    messageTable.setShowVerticalLines(false);
	    messageTable.setShowHorizontalLines(false);
	}

	//messageTable.sizeColumnsToFit(JTable.AUTO_RESIZE_NEXT_COLUMN);
	
	for (int i = 0; i < messageTable.getColumnCount(); i++) {
	    messageTable.getColumnModel().getColumn(i).setPreferredWidth(getFolderInfo().getFolderTableModel().getColumnSize(i));
	}

	getFolderInfo().getFolderTableModel().addTableModelListener(messageTable);

	messageTable.addMouseListener(new MouseAdapter() {
	    public void mouseClicked(MouseEvent e) {
		if (e.getClickCount() == 2) {
		    int rowIndex = getMessageTable().rowAtPoint(e.getPoint());
		    if (rowIndex != -1) {
			getMessageTable().setRowSelectionInterval(rowIndex, rowIndex);
			MessageProxy selectedMessage = getSelectedMessage();
			String actionCommand = Pooka.getProperty("MessagePanel.2xClickAction", "file-open");
			if (selectedMessage != null) {
			    Action clickAction = selectedMessage.getAction(actionCommand);
			    if (clickAction != null && isEnabled()) {
				clickAction.actionPerformed (new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand));
				
			    }
			}
		    }
		}
	    }

	    public void mousePressed(MouseEvent e) {
		if (SwingUtilities.isRightMouseButton(e)) {
		    // see if anything is selected
		    int rowIndex = getMessageTable().rowAtPoint(e.getPoint());
		    if (rowIndex == -1 || !getMessageTable().isRowSelected(rowIndex) ) {
			getMessageTable().setRowSelectionInterval(rowIndex, rowIndex);
		    }
		    
		    MessageProxy selectedMessage = getSelectedMessage();
		    if (selectedMessage != null && isEnabled())
			selectedMessage.showPopupMenu(getMessageTable(), e);
		}
	    }
	});

	messageTable.getSelectionModel().addListSelectionListener(new SelectionListener());
	messageTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

	messageTable.setDefaultRenderer(Object.class, new DefaultFolderCellRenderer());
	messageTable.setDefaultRenderer(Number.class, new DefaultFolderCellRenderer());

	// add sorting by header.

	messageTable.getTableHeader().addMouseListener(new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
		    TableColumnModel columnModel = messageTable.getColumnModel();
		    int viewColumn = columnModel.getColumnIndexAtX(e.getX()); 
		    int column = messageTable.convertColumnIndexToModel(viewColumn); 
		    if (e.getClickCount() == 1 && column != -1) {
			//System.out.println("Sorting ..."); 
			int shiftPressed = e.getModifiers()&InputEvent.SHIFT_MASK; 
			boolean ascending = (shiftPressed == 0); 
			
			MessageProxy selectedMessage = null;

			int rowsSelected = messageTable.getSelectedRowCount();

			if (rowsSelected == 1) 
			    selectedMessage = getFolderInfo().getMessageProxy(messageTable.getSelectedRow());
			else if (rowsSelected > 1)
			    selectedMessage = getFolderInfo().getMessageProxy(messageTable.getSelectedRows()[0]);

			((FolderTableModel)messageTable.getModel()).sortByColumn(column, ascending); 

			if (selectedMessage != null) {
			    int selectedIndex = ((FolderTableModel)messageTable.getModel()).getRowForMessage(selectedMessage);
			    messageTable.setRowSelectionInterval(selectedIndex, selectedIndex);
			    String javaVersion = System.getProperty("java.version");
			    
			    if (javaVersion.compareTo("1.3") >= 0) {
				
				// this really is awful.  i hope that they fix getCellRect() and
				// scrollToRect in 1.3
				
				int rowHeight = messageTable.getRowHeight();
				JScrollBar vsb = scrollPane.getVerticalScrollBar();
				int newValue = Math.min(rowHeight * (selectedIndex - 1), vsb.getMaximum() - vsb.getModel().getExtent());
				vsb.setValue(newValue);
				newValue = Math.min(rowHeight * (selectedIndex - 1), vsb.getMaximum() - vsb.getModel().getExtent());
				vsb.setValue(newValue);
			    } else {
				messageTable.scrollRectToVisible(messageTable.getCellRect(selectedIndex, 1, true));
			    }
			}
		    }
		}
	    });
	
	
	scrollPane.getViewport().add(messageTable);

	int firstUnread = getFolderInfo().getFirstUnreadMessage();
	if (firstUnread < 0)
	    firstUnread = messageTable.getRowCount();
	else 
	    messageTable.setRowSelectionInterval(firstUnread, firstUnread);
	
	String javaVersion = System.getProperty("java.version");

        if (javaVersion.compareTo("1.3") >= 0) {

	    // this really is awful.  i hope that they fix getCellRect() and
	    // scrollToRect in 1.3

	    int rowHeight = messageTable.getRowHeight();
	    JScrollBar vsb = scrollPane.getVerticalScrollBar();
	    int newValue = Math.min(rowHeight * (firstUnread - 1), vsb.getMaximum() - vsb.getModel().getExtent());
	    vsb.setValue(newValue);
	    newValue = Math.min(rowHeight * (firstUnread - 1), vsb.getMaximum() - vsb.getModel().getExtent());
	    vsb.setValue(newValue);
	} else {
	    messageTable.scrollRectToVisible(messageTable.getCellRect(firstUnread, 1, true));
	}

	this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

	this.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
		public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
		    getFolderInfo().setFolderDisplayUI(null);
		}
	    });

    }

    /**
     * Searches the underlying FolderInfo's messages for messages matching
     * the search term.
     */
    public void searchFolder() {
	javax.mail.search.SearchTerm term = new javax.mail.search.SubjectTerm("info");
	try {
	    MessageInfo[] matches = getFolderInfo().search(term);
	} catch (Exception e) {
	    System.out.println("caught exception.");
	}
    }

    /**
     * This method takes the currently selected row(s) and returns the
     * appropriate MessageProxy object.
     *
     * If no rows are selected, null is returned.
     */
    public MessageProxy getSelectedMessage() {
	int rowsSelected = messageTable.getSelectedRowCount();

	if (rowsSelected == 1) 
	    return getFolderInfo().getMessageProxy(messageTable.getSelectedRow());
	else if (rowsSelected < 1) 
	    return null;
	else {
	    int[] selectedRows = messageTable.getSelectedRows();
	    MessageProxy[] msgSelected= new MessageProxy[selectedRows.length];
	    for (int i = 0; i < selectedRows.length; i++) 
		msgSelected[i] = getFolderInfo().getMessageProxy(selectedRows[i]);
	    return new MultiMessageProxy(selectedRows, msgSelected, this);
	}
    }

    /**
     * This resets the size to that of the parent component.
     */
    public void resize() {
	this.setSize(getParent().getSize());
    }

    /**
     * This opens the FolderWindow.
     */
    public void openFolderDisplay() {
	getMessagePanel().openFolderWindow(getFolderInfo());
    }

    /**
     * This closes the FolderWindow.
     */
    public void closeFolderDisplay(){
	try {
	    this.setClosed(true);
	} catch (java.beans.PropertyVetoException e) {
	}
    }

    /**
     * This expunges all the messages marked as deleted in the folder.
     */
    public void expungeMessages() {
	try {
	    getFolderInfo().getFolder().expunge();
	} catch (MessagingException me) {
	    showError(Pooka.getProperty("error.Message.ExpungeErrorMessage", "Error:  could not expunge messages.") +"\n" + me.getMessage());
	}   
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title) {
	JOptionPane.showInternalMessageDialog(Pooka.getMainPanel().getMessagePanel(), errorMessage, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring abou the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage) {
	showError(errorMessage, Pooka.getProperty("Error", "Error"));
    }

    /**
     * This shows an Error Message window.  We include this so that
     * the MessageProxy can call the method without caring about the
     * actual implementation of the Dialog.
     */
    public void showError(String errorMessage, String title, Exception e) {
	showError(errorMessage + e.getMessage(), title);
	e.printStackTrace();
    }

    /**
     * This shows an Input window.  We include this so that the 
     * MessageProxy can call the method without caring about the actual
     * implementation of the dialog.
     */
    public String showInputDialog(String inputMessage, String title) {
	return JOptionPane.showInternalInputDialog(Pooka.getMainPanel().getMessagePanel(), inputMessage, title, JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * As specified by interface net.suberic.pooka.gui.FolderDisplayUI.
     * 
     * This implementation sets the cursor to either Cursor.WAIT_CURSOR
     * if busy, or Cursor.DEFAULT_CURSOR if not busy.
     */
    public void setBusy(boolean newValue) {
	if (newValue)
	    this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	else
	    this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    // Accessor methods.

    public MessagePanel getMessagePanel() {
	return messagePanel;
    }

    public JTable getMessageTable() {
	return messageTable;
    }

    public Folder getFolder() {
	return getFolderInfo().getFolder();
    }

    public void setFolderInfo(FolderInfo newValue) {
	folderInfo=newValue;
    }

    public FolderInfo getFolderInfo() {
	return folderInfo;
    }

    public FolderTableModel getFolderTableModel() {
	return getFolderInfo().getFolderTableModel();
    }

    public StatusBar getStatusBar() {
	return statusBar;
    }

    public void setStatusBar(StatusBar newValue) {
	statusBar = newValue;
    }

    /**
     * gets the actions handled both by the FolderWindow and the 
     * selected Message(s).
     */

    public class SelectionListener implements javax.swing.event.ListSelectionListener {
	SelectionListener() {
	}

	public void valueChanged(javax.swing.event.ListSelectionEvent e) {
	    getMessagePanel().getMainPanel().refreshActiveMenus();
	    if (toolbar != null)
		toolbar.setActive(getActions());
	    if (keyBindings != null)
		keyBindings.setActive(getActions());
	}
    }

    /**
     * This registers the Keyboard action not only for the FolderWindow
     * itself, but also for pretty much all of its children, also.  This
     * is to work around something which I think is a bug in jdk 1.2.
     * (this is not really necessary in jdk 1.3.)
     *
     * Overrides JComponent.registerKeyboardAction(ActionListener anAction,
     *            String aCommand, KeyStroke aKeyStroke, int aCondition)
     */

    public void registerKeyboardAction(ActionListener anAction,
       	       String aCommand, KeyStroke aKeyStroke, int aCondition) {
	super.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);

	messageTable.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
	statusBar.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
	toolbar.registerKeyboardAction(anAction, aCommand, aKeyStroke, aCondition);
    }
    
    /**
     * This unregisters the Keyboard action not only for the FolderWindow
     * itself, but also for pretty much all of its children, also.  This
     * is to work around something which I think is a bug in jdk 1.2.
     * (this is not really necessary in jdk 1.3.)
     *
     * Overrides JComponent.unregisterKeyboardAction(KeyStroke aKeyStroke)
     */

    public void unregisterKeyboardAction(KeyStroke aKeyStroke) {
	super.unregisterKeyboardAction(aKeyStroke);

	messageTable.unregisterKeyboardAction(aKeyStroke);
	statusBar.unregisterKeyboardAction(aKeyStroke);
	toolbar.unregisterKeyboardAction(aKeyStroke);
    }

    /**
     * As specified by net.subeic.pooka.UserProfileContainer
     */

    public UserProfile getDefaultProfile() {
	if (getFolderInfo() != null) {
	    return getFolderInfo().getDefaultProfile();
	}
	else {
	    return null;
	}
    }

    /**
     * Returns whether or not this window is enabled.  This should be true
     * just about all of the time.  The only time it won't be true is if
     * the Folder is closed or disconnected, and the mail store isn't set
     * up to work in disconnected mode.
     */
    public boolean isEnabled() {
	return enabled;
    }

    /**
     * This sets whether or not the window is enabled.  This should only
     * be set to false when the Folder is no longer available.
     */
    public void setEnabled(boolean newValue) {
	enabled = newValue;
    }

    public Action[] getActions() {
	if (isEnabled()) {
	    Action[] returnValue;
	    MessageProxy m = getSelectedMessage();
	    
	    if (m != null) 
		returnValue = TextAction.augmentList(m.getActions(), getDefaultActions());
	    else 
		returnValue = getDefaultActions();
	    
	    if (folderInfo.getActions() != null)
		returnValue = TextAction.augmentList(folderInfo.getActions(), returnValue);
	    
	    return returnValue;
	} else {
	    return null;
	}
    }

    public Action[] getDefaultActions() {
	return defaultActions;
    }

    //-----------actions----------------

    // The actions supported by the window itself.

    private Action[] defaultActions;

    class CloseAction extends AbstractAction {

	CloseAction() {
	    super("file-close");
	}
	
        public void actionPerformed(ActionEvent e) {
	    closeFolderDisplay();
	}
    }

    public class ExpungeAction extends AbstractAction {

	ExpungeAction() {
	    super("message-expunge");
	}
	
        public void actionPerformed(ActionEvent e) {
	    expungeMessages();
	}
    }

    public class SearchAction extends AbstractAction {

	SearchAction() {
	    super("folder-search");
	}
	
        public void actionPerformed(ActionEvent e) {
	    searchFolder();
	}
    }

}





