package net.suberic.pooka;
import javax.mail.*;
import java.util.*;
import java.io.*;
import javax.activation.*;
import javax.mail.internet.*;

import net.suberic.pooka.crypto.*;
import net.suberic.crypto.*;
import net.suberic.pooka.gui.NewMessageCryptoInfo;

import java.security.Key;

/**
 * A MessageInfo representing a new message.
 */
public class NewMessageInfo extends MessageInfo {

  Map mSendMessageMap = null;

  /**
   * Creates a NewMessageInfo to wrap the given Message.
   */
  public NewMessageInfo(Message newMessage) {
    message = newMessage;
    attachments = new AttachmentBundle();
  }

  /**
   * Sends the new message, using the given Profile, the given 
   * InternetHeaders, the given messageText, the given ContentType, and 
   * the attachments already set for this object.
   */
  public void sendMessage(UserProfile profile, InternetHeaders headers, NewMessageCryptoInfo cryptoInfo, String messageText, String messageContentType) throws MessagingException {
    
    try {
      net.suberic.pooka.gui.PookaUIFactory factory = Pooka.getUIFactory();
      
      MimeMessage mMsg = (MimeMessage) message;
      
      if (profile != null) {
	factory.showStatusMessage(Pooka.getProperty("info.sendMessage.popFromProfile", "Populating message from profile..."));
	profile.populateMessage(mMsg);
	mMsg.setHeader(Pooka.getProperty("Pooka.userProfileProperty", "X-Pooka-UserProfile"), profile.getName());
      }
      
      factory.showStatusMessage(Pooka.getProperty("info.sendMessage.settingHeaders", "Setting headers..."));
      
      Enumeration individualHeaders = headers.getAllHeaders();
      while(individualHeaders.hasMoreElements()) {
	Header currentHeader = (Header) individualHeaders.nextElement();
	mMsg.setHeader(currentHeader.getName(), currentHeader.getValue());
      }
      
      mMsg.setHeader("X-Mailer", Pooka.getProperty("Pooka.xmailer", "Pooka"));
      
      if (Pooka.getProperty("Pooka.lineWrap", "").equalsIgnoreCase("true"))
	messageText=net.suberic.pooka.MailUtilities.wrapText(messageText);

      // move this to another thread now.

      factory.showStatusMessage(Pooka.getProperty("info.sendMessage.changingThreads", "Sending to message thread..."));

      final UserProfile sProfile = profile;
      final MimeMessage sMimeMessage = mMsg;
      final String sMessageText = messageText;
      final String sMessageContentType = messageContentType;
      final NewMessageCryptoInfo sCryptoInfo = cryptoInfo;

      if (profile != null && profile.getMailServer() != null) {
	OutgoingMailServer mailServer = profile.getMailServer();
	mailServer.mailServerThread.addToQueue(new javax.swing.AbstractAction() {
	    public void actionPerformed(java.awt.event.ActionEvent ae) {
	      internal_sendMessage(sProfile, sMimeMessage, sMessageText, sMessageContentType, sCryptoInfo);
	    }
	  }, new java.awt.event.ActionEvent(this, 0, "message-send"));
      } else {
	// oh well.
	internal_sendMessage(sProfile, sMimeMessage, sMessageText, sMessageContentType, sCryptoInfo);
      } 
      
    } catch (MessagingException me) {
      throw me;
    } catch (Throwable t) {
      String cause = t.getMessage();
      if (cause == null)
	cause = t.toString();
      
      MessagingException me = new MessagingException(cause);
      me.initCause(t);
      throw me;
    }
  }

  /**
   * Does the part of message sending that should really not happen on 
   * the AWTEventThread.
   */
  private void internal_sendMessage(UserProfile profile, MimeMessage mMsg, String messageText, String messageContentType, NewMessageCryptoInfo cryptoInfo) {
    net.suberic.pooka.gui.PookaUIFactory factory = Pooka.getUIFactory();

    try {
      factory.showStatusMessage(Pooka.getProperty("info.sendMessage.attachingKeys", "Attaching crypto keys (if any)..."));
      
      // see if we need to add any keys.
      List keyParts = cryptoInfo.createAttachedKeyParts();
      
      factory.showStatusMessage(Pooka.getProperty("info.sendMessage.addingMessageText", "Parsing message text..."));
      if (keyParts.size() > 0 || (attachments.getAttachments() != null && attachments.getAttachments().size() > 0)) {
	MimeBodyPart mbp = new MimeBodyPart();
	mbp.setContent(messageText, messageContentType);
	MimeMultipart multipart = new MimeMultipart();
	multipart.addBodyPart(mbp);
	
	if (attachments.getAttachments() != null) {
	  // i should really use the text parsing code for this, but...
	  String attachmentMessage=Pooka.getProperty("info.sendMessage.addingAttachment.1", "Adding attachment ");
	  String ofMessage = Pooka.getProperty("info.sendMessage.addingAttachment.2", " of ");
	  int attachmentCount = attachments.getAttachments().size();
	  for (int i = 0; i < attachmentCount; i++) {
	    factory.showStatusMessage(attachmentMessage + i + ofMessage + attachmentCount);
	    multipart.addBodyPart(((MBPAttachment)attachments.getAttachments().elementAt(i)).getMimeBodyPart());
	  }
	}
	
	for (int i = 0; i < keyParts.size(); i++) {
	  multipart.addBodyPart((MimeBodyPart) keyParts.get(i));
	}
	
	factory.showStatusMessage(Pooka.getProperty("info.sendMessage.savingChangesToMessage", "Saving changes to message..."));
	multipart.setSubType("mixed");
	getMessage().setContent(multipart);
	getMessage().saveChanges();
      } else {
	factory.showStatusMessage(Pooka.getProperty("info.sendMessage.savingChangesToMessage", "Saving changes to message..."));
	getMessage().setContent(messageText, messageContentType);
      }
    
      getMessage().setSentDate(new java.util.Date(System.currentTimeMillis()));

      // do encryption stuff, if necessary.
      
      // sigh
      
      factory.showStatusMessage(Pooka.getProperty("info.sendMessage.encryptMessage", "Handing encryption..."));
      
      mSendMessageMap = cryptoInfo.createEncryptedMessages((MimeMessage) getMessage());
      
      if (mSendMessageMap.keySet().size() < 1) {
	throw new MessagingException("failed to send message--no encrypted or unencrypted messages created.");
      }
      
      if (mSendMessageMap.keySet().size() == 1) {
	message = (Message) mSendMessageMap.keySet().iterator().next();
      }
      
      boolean sent = false;
      if (profile != null) {
	OutgoingMailServer mailServer = profile.getMailServer();
	if (mailServer != null) {
	  factory.showStatusMessage(Pooka.getProperty("info.sendMessage.sendingMessage", "Sending message to mailserver..."));
	  mailServer.sendMessage(this);
	  sent = true;
	}
      } 
      
      if (! sent) {
	if (profile != null) {
	  URLName urlName = profile.getSendMailURL();
	  String sendPrecommand = profile.getSendPrecommand();
	  factory.showStatusMessage(Pooka.getProperty("info.sendMessage.sendingMessage", "Sending message to mailserver..."));
	  Pooka.getMainPanel().getMailQueue().sendMessage(this, urlName, sendPrecommand);
	  sent = true;
	}
      } else {
	saveToSentFolder(profile);
      }
      
      if (! sent) {
	throw new MessagingException(Pooka.getProperty("error.noSMTPServer", "Error sending Message:  No mail server configured."));
      }
    } catch (MessagingException me) {
      ((net.suberic.pooka.gui.NewMessageProxy)getMessageProxy()).sendFailed(me);	  
    } catch (Throwable t) {
      String cause = t.getMessage();
      if (cause == null)
	cause = t.toString();
      
      MessagingException me = new MessagingException(cause);
      me.initCause(t);
      ((net.suberic.pooka.gui.NewMessageProxy)getMessageProxy()).sendFailed(me);	  
    }
  }

  /**
   * Converts the given address line into an address line suitable for
   * this NewMessageInfo.  Specifically, this goes through each address
   * in the list and adds the UserProfile's defaultDomain to each entry
   * which doesn't have a domain already.
   */
  public String convertAddressLine(String oldLine, UserProfile p) throws javax.mail.internet.AddressException {
    StringBuffer returnValue = new StringBuffer();
    InternetAddress[] addresses = InternetAddress.parse(oldLine, false);
    for (int i = 0; i < addresses.length; i++) {
      String currentAddress = addresses[i].getAddress();
      if (currentAddress.lastIndexOf('@') < 0) {
	currentAddress = currentAddress + "@" + p.getDefaultDomain();
	addresses[i].setAddress(currentAddress);
      }

      returnValue.append(addresses[i].toString());
      if (i+1 < addresses.length)
	returnValue.append(", ");
    }

    return returnValue.toString();
  }

  /**
   * Saves the NewMessageInfo to the sentFolder associated with the 
   * given Profile, if any.
   */
  public void saveToSentFolder(UserProfile profile) {
    final FolderInfo sentFolder = profile.getSentFolder();
    if (sentFolder != null) {
      try {
	final Message newMessage = new MimeMessage((MimeMessage) getMessage());
	
	sentFolder.getFolderThread().addToQueue(new net.suberic.util.thread.ActionWrapper(new javax.swing.AbstractAction() {
	    public void actionPerformed(java.awt.event.ActionEvent actionEvent) {
	      try {
		if (sentFolder.getFolder() == null) {
		  sentFolder.openFolder(Folder.READ_WRITE);
		}

		if (sentFolder.getFolder() == null) {
		  throw new MessagingException("failed to load sent folder " + sentFolder);
		}
		
		newMessage.setSentDate(java.util.Calendar.getInstance().getTime());
		sentFolder.getFolder().appendMessages(new Message[] {newMessage});
	      } catch (MessagingException me) {
		  me.printStackTrace();
		  Pooka.getUIFactory().showError(Pooka.getProperty("Error.SaveFile.toSentFolder", "Error saving file to sent folder."), Pooka.getProperty("error.SaveFile.toSentFolder.title", "Error storing message."));
	      }
	    }
	  }, sentFolder.getFolderThread()), new java.awt.event.ActionEvent(this, 1, "message-send"));
      } catch (MessagingException me) {
	me.printStackTrace();
	Pooka.getUIFactory().showError(Pooka.getProperty("Error.SaveFile.toSentFolder", "Error saving file to sent folder."), Pooka.getProperty("error.SaveFile.toSentFolder.title", "Error storing message."));

      }
    }
  }

  /**
   * Adds an attachment to this message.
   */
  public void addAttachment(Attachment attachment) {
    attachments.addAttachment(attachment);
  }
  
  /**
   * Removes an attachment from this message.
   */
  public int removeAttachment(Attachment part) {
    if (attachments != null) {
      int index = attachments.getAttachments().indexOf(part);	
      attachments.removeAttachment(part);
      return index;
    }
    
    return -1;
  }
  
  /**
   * Attaches the given File to the message.
   */
  public void attachFile(File f) throws MessagingException {
    // borrowing liberally from ICEMail here.
    
    MimeBodyPart mbp = new MimeBodyPart();
    
    FileDataSource fds = new FileDataSource(f);
    
    DataHandler dh = new DataHandler(fds);
    
    mbp.setFileName(f.getName());
    
    if (Pooka.getMimeTypesMap().getContentType(f).startsWith("text"))
      mbp.setDisposition(Part.ATTACHMENT);
    else
      mbp.setDisposition(Part.INLINE);
    
    mbp.setDescription(f.getName());
    
    mbp.setDataHandler( dh );
    
    String type = dh.getContentType();
    
    mbp.setHeader("Content-Type", type);
    
    addAttachment(new MBPAttachment(mbp));
  }
  
  /**
   * Returns the given header on the wrapped Message.
   */
  public String getHeader(String headerName, String delimeter) throws MessagingException {
    return ((MimeMessage)getMessage()).getHeader(headerName, delimeter);
  }
  
  /**
   * Gets the text part of the wrapped message.
   */
  public String getTextPart(boolean showFullHeaders) {
    try {
      return (String) message.getContent();
    } catch (java.io.IOException ioe) {
      // since this is a NewMessageInfo, there really shouldn't be an
      // IOException
      return null;
    } catch (MessagingException me) {
	    // since this is a NewMessageInfo, there really shouldn't be a
      // MessagingException
      return null;
    }
  }
  
  /**
   * Marks the message as a draft message and then saves it to the outbox
   * folder given.
   */
  public void saveDraft(FolderInfo outboxFolder) throws MessagingException {
    getMessage().setFlag(Flags.Flag.DRAFT, true);

    outboxFolder.appendMessages(new MessageInfo[] { this });
  }

  /**
   * The full map of Messages to be sent, which in turn map to the
   * recipients they will go to.  If there is no Address array as the
   * value in the map, then the message goes out to all recipients in 
   * the headers.
   */
  public Map getSendMessageMap() {
    return mSendMessageMap;
  }

  /**
   * The full map of Messages to be sent, which in turn map to the
   * recipients they will go to.  If there is no Address array as the
   * value in the map, then the message goes out to all recipients in 
   * the headers.
   */
  void setSendMessageMap(Map pSendMessageMap) {
    mSendMessageMap = pSendMessageMap;
  }

}