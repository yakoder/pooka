package net.suberic.pooka.gui;
import java.awt.Component;
import javax.mail.Message;

/**
 * This is a definition which allows for a particular Icon to be specified
 * for multiple SearchTerm instances.  The way the class works is that 
 * each SearchTerm is tried in order.  The first one for which the current
 * message returns true is considered to be the value.  The Icon for that
 * value is then returned.
 */
public class SearchTermIcon implements TableCellIcon {

    SearchTermIconManager manager;
    Message message;
    int value = -1;

    public SearchTermIcon(SearchTermIconManager newManager, Message newMessage) {
	manager = newManager;
	message = newMessage;
	value = manager.getValue(message);
    }

    /**
     * This method should return the appropriate component depending on the
     * values of the particular TableCellIcon.  
     */
    public Component getIcon() {
	return manager.getIcon(value);
    }

    /**
     * Compares this SearchTermIcon to another SearchTermIcon.
     */
    public int compareTo(Object o) {
	if (o instanceof SearchTermIcon) {
	    int otherValue= ((SearchTermIcon) o).getIntValue();
	    if ( getIntValue() < otherValue)
		return -1;
	    else if (getIntValue() == otherValue)
		return 0;
	    else
		return 1;
	}

	throw new ClassCastException("object is not a SearchTermIcon.");
    }

    /**
     * Returns the integer value for this Icon.
     */
    public int getIntValue() {
	return value;
    }
}