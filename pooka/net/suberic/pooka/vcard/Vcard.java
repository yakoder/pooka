package net.suberic.pooka.vcard;
import java.io.*;
import java.util.Properties;
import javax.mail.internet.InternetAddress;

/**
 * A class which represents a vcard address book entry.
 */
public class Vcard implements Comparable {

    public static final int SORT_BY_ADDRESS = 0;

    public static final int SORT_BY_LAST_NAME = 1;

    public static final int SORT_BY_FIRST_NAME = 2;

    public static final int SORT_BY_PERSONAL_NAME = 3;
    
    Properties properties;

    private int sortingMethod = 0;

    private InternetAddress address;

    private String firstName;

    private String lastName;

    /**
     * Creates a new Vcard from a BufferedReader.
     */
    protected Vcard(Properties newProps) {
	properties = newProps;
    }

    /**
     * Gets a property on the Vcard.
     */
    public String getProperty(String propertyName) {
	return properties.getProperty(propertyName);
    }
    
    /**
     * Gets the InternetAddress associated with this Vcard.
     */
    public InternetAddress getAddress() {
	return address;
    }

    /**
     * Gets the PersonalName property associated with this Vcard.
     */
    public String getPersonalName() {
	return address.getPersonal();
    }

    /**
     * Gets the email address (as a string) associated with this Vcard.
     */
    public String getEmailAddress() {
	return address.getAddress();
    }

    //----  Comparable  ----//

    /**
     * Compares this Vcard either to another Vcard, or a String which matches
     * the Vcard.  This checks the sortingMethod setting to decide how to 
     * compare to the Vcard or String.
     *
     * @param   o the Object to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *		is less than, equal to, or greater than the specified object.
     * 
     * @throws ClassCastException if the specified object's type prevents it
     *         from being compared to this Object.
     */
    
    public int compareTo(Object o) {
	if (o instanceof Vcard) {
	    Vcard target = (Vcard) o;

	    switch (sortingMethod) {
	    case (SORT_BY_ADDRESS):
		return getAddress().toString().compareTo(target.getAddress().toString());
	    case (SORT_BY_LAST_NAME):
		return getLastFirst().compareTo(target.getLastFirst());
	    case (SORT_BY_FIRST_NAME):
		return getFirstLast().compareTo(target.getFirstLast());
	    case (SORT_BY_PERSONAL_NAME):
		return getPersonalName().compareTo(target.getPersonalName());
	    }

	} else if (o instanceof String) {
	    String compareString = null;
	    String matchString = (String) o;

	    switch (sortingMethod) {
	    case (SORT_BY_ADDRESS):
		compareString = getAddress().toString();
	    case (SORT_BY_LAST_NAME):
		compareString = getLastFirst();
	    case (SORT_BY_FIRST_NAME):
		compareString = getFirstLast();

	    }
	    
	    // see if the string to be matched is shorter; if so, match
	    // with just that length.

	    int origSize = compareString.size();
	    int matchSize = matchString.size();
	    if (matchSize < origSize) {
		return compareString.substring(0,matchSize).compareTo(matchString);
	    } else {
		return compareString.compareTo(matchString);
	    }
	}
    }

    //----  parser  ----//

    /**
     * Parses a vcard from a BufferedReader.
     */
    public static Vcard parse(BufferedReader reader) throws java.text.ParseException {

	try {
	    Properties newProps = new Properties();
	    
	    boolean isDone = false;
	    
	    String line = getNextLine(reader);
	    if (line != null) {
		String[] current = parseLine(line);
		if (current[0] != null && current[1] != null) {
		    if (! (current[0].equalsIgnoreCase("begin") && current[1].equalsIgnoreCase("vcard")))
			throw new java.text.ParseException("No beginning", 0);
		}
		else 
		    newProps.put(current[0], current[1]);
	    }
	    
	    while (!isDone) {
		line = getNextLine(reader);
		if (line != null) {
		    String[] current = parseLine(line);
		    if (current[0] != null && current[1] != null) {
			if (current[0].equalsIgnoreCase("end")) {
			    isDone = true;
			    if (current[1].equalsIgnoreCase("vcard"))
				newProps.put(current[0], current[1]);
			    else
				throw new java.text.ParseException("incorrect end tag", 0);
			}
		    }
		}
	    }
	    return new Vcard(newProps);
	} catch (IOException ioe) {
	    throw new java.text.ParseException(ioe.getMessage(), 0);
	}
    }

    /**
     * Parses a name/value pair from an rfc2425 stream.
     */
    private static String getNextLine(BufferedReader reader) throws IOException {
	String firstLine = reader.readLine();
	boolean isDone = false;
	while (! isDone) {
	    reader.mark(256);
	    String nextLine = reader.readLine();
	    if (nextLine != null) {
		if (! Character.isWhitespace(nextLine.charAt(0))) {
		    isDone = true;
		    reader.reset();
		} else {
		    firstLine = firstLine + nextLine.substring(1);
		}
	    } else {
		isDone = true;
	    }
	}

	return firstLine;
    }

    private static String[] parseLine(String firstLine) {
	String[] returnValue = new String[2];
	
	int dividerLoc = firstLine.indexOf(':');
	returnValue[0] = firstLine.substring(0, dividerLoc);
	returnValue[1] = firstLine.substring(dividerLoc);
	
	return returnValue;
    }
}
