package net.suberic.pooka;

import javax.mail.*;
import javax.mail.event.*;
import javax.swing.event.EventListenerList;
import java.util.*;
import net.suberic.pooka.gui.*;
import net.suberic.util.ValueChangeListener;
import net.suberic.util.thread.ActionThread;
import net.suberic.util.VariableBundle;

/**
 * This class does all of the work for a Store.  It keeps track of the
 * StoreNode for the Store, as well as keeping the children of the store
 * and the properties of the Store.
 */

public class StoreInfo implements ValueChangeListener {

    private Store store;

    // The is the store ID.
    private String storeID;

    // Information for the StoreNode
    private StoreNode storeNode;
    private Vector children;

    // the status indicators
    private boolean connected = false;
    private boolean authorized = false;
    private boolean available = false;

    // if this is a pop mailbox.
    private boolean popStore = false;

    private UserProfile defaultProfile;

    // the connection information.
    private String user;
    private String password;
    private String server;
    private String protocol;
    private URLName url;

    // the Thread for connections to this Store.
    private ActionThread storeThread;

    // the Trash folder for this Store, if any.
    private FolderInfo trashFolder;

    /**
     * Creates a new StoreInfo from a Store ID.
     */

    public StoreInfo(String sid) {
	setStoreID(sid);
	
	configureStore();
    }

    /**
     * This configures the store from the property information.
     */
    public void configureStore() {
	connected = false;
	authorized = false;
	available = false;

	protocol = Pooka.getProperty("Store." + storeID + ".protocol", "");

	if (protocol.equalsIgnoreCase("pop3")) {
	    user = "";
	    password = "";
	    server = "localhost";
	    protocol = "mbox";
	    popStore = true;
	} else {
	    popStore = false;
	    user = Pooka.getProperty("Store." + storeID + ".user", "");
	    password = Pooka.getProperty("Store." + storeID + ".password", "");
	    if (!password.equals(""))
		password = net.suberic.util.gui.PasswordEditorPane.descrambleString(password);
	    server = Pooka.getProperty("Store." + storeID + ".server", "");
	}
	
	url = new URLName(protocol, server, -1, "", user, password);
	
	try {
	    Properties p = loadProperties();
	    Session s = Session.getInstance(p, Pooka.defaultAuthenticator);
	    store = s.getStore(url);
	    available=true;
	} catch (NoSuchProviderException nspe) {
	    available=false;
	}
	
	// don't allow a StoreInfo to get created with an empty folderList.
	
	if (Pooka.getProperty("Store." + storeID + ".folderList", "").equals(""))
	    Pooka.setProperty("Store." + storeID + ".folderList", "INBOX");
	
	Pooka.getResources().addValueChangeListener(this, getStoreProperty());
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".folderList");
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".defaultProfile");
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".protocol");
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".user");
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".password");
	Pooka.getResources().addValueChangeListener(this, getStoreProperty() + ".server");

	
	if (available) {
	    store.addConnectionListener(new ConnectionAdapter() { 
		    
		    public void disconnected(ConnectionEvent e) {
			if (Pooka.isDebug())
			    System.out.println("Store " + getStoreID() + " disconnected.");
			/*
			if (connected == true) {
			    try {
				if (!(store.isConnected()))
				    store.connect();
			    } catch (MessagingException me) {
				System.out.println("Store " + getStoreID() + " disconnected and unable to reconnect:  " + me.getMessage());
			    }
			}
			*/

			try {
			    disconnectStore();
			} catch (MessagingException me) {
			    if (Pooka.isDebug())
				System.out.println("error disconnecting Store:  " + me.getMessage());
			}
			
		    }
		});
	}

	if (storeThread == null) {
	    storeThread = new ActionThread(this.getStoreID() + " - ActionThread");
	    storeThread.start();
	}

	defaultProfile = UserProfile.getProfile(Pooka.getProperty(getStoreProperty() + ".defaultProfile", ""));
	
	updateChildren();
	
	String trashFolderName = Pooka.getProperty(getStoreProperty() + ".trashFolder", "");
	if (trashFolderName.length() > 0) {
	    trashFolder = getChild(trashFolderName);
	    if (trashFolder != null)
		trashFolder.setTrashFolder(true);
	}
    }	

    /**
     * This loads in the default session properties for this Store's
     * Session.
     */
    public Properties loadProperties() {
	Properties p = new Properties(System.getProperties());
	p.setProperty("mail.imap.timeout", Pooka.getProperty(getStoreProperty() + ".timeout", Pooka.getProperty("Pooka.timeout", "-1")));
	p.setProperty("mail.imap.connectiontimeout", Pooka.getProperty(getStoreProperty() + ".connectionTimeout", Pooka.getProperty("Pooka.connectionTimeout", "-1")));
	if (Pooka.getProperty(getStoreProperty() + ".SSL", "false").equalsIgnoreCase("true")) {
	    p.setProperty("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

	    p.setProperty("mail.imap.socketFactory.fallback", Pooka.getProperty(getStoreProperty() + ".SSL.fallback", "false"));
	    
	    p.setProperty("mail.imap.socketFactory.port", Pooka.getProperty(getStoreProperty() + ".SSL.port", "993"));
	}

	return p;
    }
    
    /**
     * This updates the children of the current store.  Generally called
     * when the folderList property is changed.
     */

    public void updateChildren() {

	Vector newChildren = new Vector();

	StringTokenizer tokens = new StringTokenizer(Pooka.getProperty(getStoreProperty() + ".folderList", "INBOX"), ":");
	
	if (Pooka.isDebug())
	    System.out.println("Pooka.getProperty(" + getStoreProperty() + ".folderList = " + Pooka.getProperty(getStoreProperty() + ".folderList"));

	String newFolderName;

	for (int i = 0 ; tokens.hasMoreTokens() ; i++) {
	    newFolderName = (String)tokens.nextToken();
	    FolderInfo childFolder = getChild(newFolderName);
	    if (childFolder == null) {
		if (popStore && newFolderName.equalsIgnoreCase("INBOX")) 
		    childFolder = new PopInboxFolderInfo(this, newFolderName);
		else if (Pooka.getProperty(getStoreProperty() + "." + newFolderName + ".cacheMessages", "false").equalsIgnoreCase("true"))
		    childFolder = new net.suberic.pooka.cache.CachingFolderInfo(this, newFolderName);
		else if (Pooka.getProperty(getStoreProperty() + ".protocol", "mbox").equalsIgnoreCase("imap")) {
		    childFolder = new UIDFolderInfo(this, newFolderName);
		} else
		    childFolder = new FolderInfo(this, newFolderName);
		newChildren.add(childFolder);
	    }
	} 
	children = newChildren;
	if (Pooka.isDebug())
	    System.out.println(getStoreID() + ":  in configureStore.  children.size() = " + children.size());

	if (storeNode != null)
	    storeNode.loadChildren();
    }
    
    /**
     * This goes through the list of children of this store and
     * returns the FolderInfo for the given childName, if one exists.
     * If none exists, or if the children Vector has not been loaded
     * yet, or if this is a leaf node, then this method returns null.
     */
    public FolderInfo getChild(String childName) {
	FolderInfo childFolder = null;
	String folderName  = null, subFolderName = null;

	if (children != null) {
	    int divider = childName.indexOf('/');
	    if (divider > 0) {
		folderName = childName.substring(0, divider);
		if (divider < childName.length() - 1)
		    subFolderName = childName.substring(divider + 1);
	    } else 
		folderName = childName;
	    
	    for (int i = 0; i < children.size(); i++)
		if (((FolderInfo)children.elementAt(i)).getFolderName().equals(folderName))
		    childFolder = (FolderInfo)children.elementAt(i);
	}

	if (childFolder != null && subFolderName != null)
	    return childFolder.getChild(subFolderName);
	else
	    return childFolder;
    }


    /**
     * This goes through the list of children of this store and
     * returns the FolderInfo that matches this folderID.
     * If none exists, or if the children Vector has not been loaded
     * yet, or if this is a leaf node, then this method returns null.
     */
    public FolderInfo getFolderById(String folderID) {
	FolderInfo childFolder = null;

	if (children != null) {
	    for (int i = 0; i < children.size(); i++) {
		FolderInfo possibleMatch = ((FolderInfo)children.elementAt(i)).getFolderById(folderID);
		if (possibleMatch != null) {
		    return possibleMatch;
		}
	    }
	}

	return null;
    }

    /**
     * This handles the changes if the source property is modified.
     *
     * As defined in net.suberic.util.ValueChangeListener.
     */

    public void valueChanged(String changedValue) {
	if (changedValue.equals(getStoreProperty() + ".folderList")) {
	    updateChildren();
	} else if (changedValue.equals(getStoreProperty() + ".defaultProfile")) {
	    defaultProfile = UserProfile.getProfile(Pooka.getProperty(changedValue, ""));
	} else if (changedValue.equals(getStoreProperty() + ".protocol") || changedValue.equals(getStoreProperty() + ".user") || changedValue.equals(getStoreProperty() + ".password") || changedValue.equals(getStoreProperty() + ".server")) {

	    if (storeNode != null) {
		Enumeration enum = storeNode.children();
		Vector v = new Vector();
		while (enum.hasMoreElements())
		    v.add(enum.nextElement());
		
		storeNode.removeChildren(v);
	    }

	    children = null;

	    /*
	    String realChildren = Pooka.getProperty(getStoreProperty() + ".folderList", "");
	    Pooka.setProperty(getStoreProperty() + ".folderList", "");
	    Pooka.setProperty(getStoreProperty() + ".folderList", realChildren);
	    */

	    try {
		disconnectStore();
	    } catch (Exception e) { }
	    if (Pooka.isDebug())
		System.out.println("calling configureStore()");

	    configureStore();
	}
    }

    /**
     * Remove the given String from the folderList property.  
     *
     * Note that because this is also a ValueChangeListener to the
     * folderList property, this will also result in the FolderInfo being
     * removed from the children Vector.
     */
    void removeFromFolderList(String removeFolderName) {
	Vector folderNames = Pooka.getResources().getPropertyAsVector(getStoreProperty() + ".folderList", "");
	
	boolean first = true;
	StringBuffer newValue = new StringBuffer();
	String folderName;

	for (int i = 0; i < folderNames.size(); i++) {
	    folderName = (String) folderNames.elementAt(i);

	    if (! folderName.equals(removeFolderName)) {
		if (!first)
		    newValue.append(":");
		
		newValue.append(folderName);
		first = false;
	    }
	    
	}
	
	Pooka.setProperty(getStoreProperty() + ".folderList", newValue.toString());
    }
    
    /**
     * This adds the given folderString to the folderList property.
     */
    void addToFolderList(String addFolderName) {
	String folderName;
	Vector folderNames = Pooka.getResources().getPropertyAsVector(getStoreProperty() + ".folderList", "");
	
	boolean found = false;

	for (int i = 0; i < folderNames.size(); i++) {
	    folderName = (String) folderNames.elementAt(i);

	    if (folderName.equals(addFolderName)) {
		found=true;
	    }
	    
	}
	
	if (!found) {
	    String currentValue = Pooka.getProperty(getStoreProperty() + ".folderList");
	    if (currentValue.equals(""))
		Pooka.setProperty(getStoreProperty() + ".folderList", addFolderName);
	    else
		Pooka.setProperty(getStoreProperty() + ".folderList", currentValue + ":" + addFolderName);
	}
			      
    }
    
    /**
     * This subscribes the Folder described by the given String to this
     * StoreInfo.
     */

    public void subscribeFolder(String folderName) {
	String subFolderName = null;
	String childFolderName = null;
	int firstSlash = folderName.indexOf('/');
	while (firstSlash == 0) {
	    folderName = folderName.substring(1);
	    firstSlash = folderName.indexOf('/');
	}

	if (firstSlash > 0) {
	    childFolderName = folderName.substring(0, firstSlash);
	    if (firstSlash < folderName.length() -1)
		subFolderName = folderName.substring(firstSlash +1);
	    
	} else
	    childFolderName = folderName;

	this.addToFolderList(childFolderName);

	FolderInfo childFolder = getChild(childFolderName);

	if (childFolder != null && subFolderName != null)
	    childFolder.subscribeFolder(subFolderName);
    }
    
    /**
     * This method connects the Store, and sets the StoreInfo to know that
     * the Store should be connected.  You should use this method instead of
     * calling getStore().connect(), because if you use this method, then
     * the StoreInfo will try to keep the Store connected, and will try to
     * reconnect the Store if it gets disconnected before 
     * disconnectStore is called.
     *
     * This method also calls updateChildren() to load the children of 
     * the Store, if the children vector has not been loaded yet.
     */
    public void connectStore() throws MessagingException {
	if (store.isConnected()) {
	    connected=true;
	    return;
	} else { 
	    try {
		store.connect();
	    } catch (MessagingException me) {
		Exception e = me.getNextException();
		if (e != null && e instanceof java.io.InterruptedIOException) 
		    store.connect();
		else
		    throw me;
	    }
	    connected=true;

	    if (Pooka.getProperty("Pooka.openFoldersOnConnect", "true").equalsIgnoreCase("true"))
		for (int i = 0; i < children.size(); i++)
		    ((FolderInfo)children.elementAt(i)).openAllFolders(Folder.READ_WRITE);
	}
	
    }
    
    /**
     * This method disconnects the Store.  If you connect to the Store using 
     * connectStore() (which you should), then you should use this method
     * instead of calling getStore.disconnect().  If you don't, then the
     * StoreInfo will try to reconnect the store.
     */
    public void disconnectStore() throws MessagingException {
	MessagingException storeException = null;
	if (!(store.isConnected())) {
	    connected=false;
	    return;
	} else {
	    try {
		store.close();
	    } catch (MessagingException me) {
		storeException = me;
	    } finally {
		connected=false;
		try {
		    closeAllFolders(false);
		} catch (MessagingException folderMe) {
		    if (storeException != null)
			throw folderMe;
		    else {
			storeException.setNextException(folderMe);
			throw storeException;
		    }
		}
	    }
	}
    }

    /**
     * Closes all of the Store's children.
     */
    public void closeAllFolders(boolean expunge) throws MessagingException {
	if (Pooka.isDebug())
	    System.out.println("closing all folders of store " + getStoreID());
	Vector folders = getChildren();
	if (folders != null) {
	    for (int i = 0; i < folders.size(); i++) {
		((FolderInfo) folders.elementAt(i)).closeAllFolders(expunge);
	    }
	}
    }

    /**
     * Gets all of the children folders of this StoreInfo which are both
     * Open and can contain Messages.
     */
    public Vector getAllFolders() {
	Vector returnValue = new Vector();
	Vector subFolders = getChildren();
	for (int i = 0; i < subFolders.size(); i++) {
	    returnValue.addAll(((FolderInfo) subFolders.elementAt(i)).getAllFolders());
	}
	return returnValue;
    }

    // Accessor methods.

    public Store getStore() {
	return store;
    }

    private void setStore(Store newValue) {
	store=newValue;
    }

    /**
     * This returns the StoreID.
     */
    public String getStoreID() {
	return storeID;
    }

    /**
     * This sets the storeID.
     */
    private void setStoreID(String newValue) {
	storeID=newValue;
    }

    /**
     * This returns the property which defines this StoreNode, such as
     * "Store.myStore".
     */
    public String getStoreProperty() {
	return "Store." + getStoreID();
    }

    public Vector getChildren() {
	return children;
    }

    public StoreNode getStoreNode() {
	return storeNode;
    }

    public void setStoreNode(StoreNode newValue) {
	storeNode = newValue;
    }

    public boolean isConnected() {
	return connected;
    }

    public boolean isAvailable() {
	return available;
    }

    public boolean isAuthorized() {
	return authorized;
    }

    public UserProfile getDefaultProfile() {
	return defaultProfile;
    }

    public ActionThread getStoreThread() {
	return storeThread;
    }

    public void setStoreThread(ActionThread newValue) {
	storeThread=newValue;
    }

    public FolderInfo getTrashFolder() {
	return trashFolder;
    }

    /**
     * This returns whether or not this Store is set up to use the 
     * TrashFolder.  If StoreProperty.useTrashFolder is set, return that as
     * a boolean.  Otherwise, return Pooka.useTrashFolder as a boolean.
     */
    public boolean useTrashFolder() {
	if (getTrashFolder() == null)
	    return false;

	String prop = Pooka.getProperty(getStoreProperty() + ".useTrashFolder", "");
	if (!prop.equals(""))
	    return (! prop.equalsIgnoreCase("false"));
	else
	    return (! Pooka.getProperty("Pooka.useTrashFolder", "true").equalsIgnoreCase("true"));
	
    }


    public void setTrashFolder(FolderInfo newValue) {
	trashFolder = newValue;
    }

}
