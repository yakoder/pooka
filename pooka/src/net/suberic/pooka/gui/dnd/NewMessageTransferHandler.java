package net.suberic.pooka.gui.dnd;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;

import net.suberic.pooka.*;
import net.suberic.pooka.gui.*;

/**
 * A TransferHandler for a MessageProxy object.
 */
public class NewMessageTransferHandler extends TransferHandler {
  private DataFlavor[] usableDataFlavors = new DataFlavor[] {
    MessageProxyTransferable.sMessageProxyDataFlavor,
    DataFlavor.javaFileListFlavor,
    DataFlavor.stringFlavor
  };

  public boolean importData(JComponent c, Transferable t) {
    DataFlavor matchedFlavor = DndUtils.matchDataFlavor(usableDataFlavors, t.getTransferDataFlavors());
    if (matchedFlavor != null) {
      if (matchedFlavor == MessageProxyTransferable.sMessageProxyDataFlavor) {
	return importMessageProxy(c, t);
      } else if (matchedFlavor == DataFlavor.javaFileListFlavor) {
	return importFileList(c, t);
      } else if (matchedFlavor == DataFlavor.stringFlavor) {
	try {
	  String value = (String) t.getTransferData(DataFlavor.stringFlavor);
	  return importFileList(c, t);
	  
	} catch (Exception e) {
	  e.printStackTrace();
	}
	
	return false;
      } else {
	// weird
	return false;
      }
    } else {
      return false;
    }
  }

  /**
   * Imports a MessageProxy.
   */
  public boolean importMessageProxy(JComponent c, Transferable t) {
    try {
      NewMessageDisplayPanel nmdp = (NewMessageDisplayPanel) SwingUtilities.getAncestorOfClass(Class.forName("net.suberic.pooka.gui.NewMessageDisplayPanel"), c);
      if (nmdp != null && isMessageProxy(t)) {
	MessageProxy proxy = (MessageProxy) t.getTransferData(MessageProxyTransferable.sMessageProxyDataFlavor);
	
	javax.mail.internet.MimeBodyPart mbp = new javax.mail.internet.MimeBodyPart();
	mbp.setDataHandler(proxy.getMessageInfo().getRealMessage().getDataHandler());
	nmdp.getNewMessageProxy().getNewMessageInfo().addAttachment(new MBPAttachment(mbp));
	
	nmdp.attachmentAdded(nmdp.getNewMessageProxy().getNewMessageInfo().getAttachments().size() -1);
	return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return false;
  }
  
  /**
   * Imports a File or list of Files.
   */
  public boolean importFileList(JComponent c, Transferable t) {
    try {
      NewMessageDisplayPanel nmdp = (NewMessageDisplayPanel) SwingUtilities.getAncestorOfClass(Class.forName("net.suberic.pooka.gui.NewMessageDisplayPanel"), c);
      if (nmdp != null) {
	java.util.List fileList = DndUtils.extractFileList(t);
	
	if (fileList != null) {
	  Iterator it = fileList.iterator();
	  while (it.hasNext()) {
	    File f = (File) it.next();
	    nmdp.getNewMessageProxy().getNewMessageInfo().attachFile(f);
	    
	    nmdp.attachmentAdded(nmdp.getNewMessageProxy().getNewMessageInfo().getAttachments().size() -1);
	  }
	
	  return true;
	}
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return false;
  }

  public boolean canImport(JComponent c, DataFlavor[] flavors) {
    if (DndUtils.matchDataFlavor(usableDataFlavors, flavors) != null) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns if this is a MessageProxy.
   */
  public boolean isMessageProxy(Transferable t) {
    DataFlavor[] flavors = t.getTransferDataFlavors();
    if (flavors == null)
      return false;

    for (int i = 0; i < flavors.length; i++) {
      if (flavors[i] == MessageProxyTransferable.sMessageProxyDataFlavor)
	return true;
    }

    return false;
  }
}