package net.suberic.pooka.gui;
import net.suberic.pooka.*;
import net.suberic.util.*;
import net.suberic.util.gui.*;
import javax.swing.*;

/**
 * A property editor which edits an AddressBook.
 */
public class AddressBookEditorPane extends DefaultPropertyEditor {
  
  String property;
  AddressBook book;
  JPanel searchEntryPanel;
  JTextField searchEntryField;
  JTable addressTable;

  public AddressBookEditorPane(String newProperty, String newTemplateType, VariableBundle bundle, boolean isEnabled) {
    configureEditor(newProperty, newTemplateType, bundle, isEnabled);
  }
  
  public void configureEditor(PropertyEditorFactory factory, String newProperty, String newTemplateType, VariableBundle bundle, boolean isEnabled) {
    property=newProperty;
    book = Pooka.getAddressManager().getAddressBook(property);

    this.setLayout(new BoxLayout(BoxLayout.Y_AXIS));
    this.setBorder(BorderFactory.createEtchedBorder());

    createSearchEntryPanel();
    createAddressTable();

    labelComponet = this;
    
    this.add(searchEntryPanel);
    this.add(addressTable);
  }

  /**
   * Creates the panel which has the entry fields -- i.e., "Enter string to
   * match", an entry field, and a search button.
   */
  public void createSearchEntryPanel() {
    searchEntryPanel = new JPanel();
    searchEntryPanel.add(new JLabel(resources.getProperty("AddressBookEditor.matchString", "Match String: ")));

    searchEntryField = new JTextField(30);
    searchEntryPanel.add(searchEntryField);

    Action a = new SearchAction();

    JButton searchButton = new JButton(resources.getProperty("AddressBookEditor.title.Search", "Search"));
    searchButton.addActionListener(a);
    searchEntryPanel.add(searchButton);
    
  }

  /**
   * Creates the AddressTable.
   */
  public void createAddressTable() {    
    addressTable = new JTable();
    addressTable.setCellSelectionEnabled(false);
    addressTable.setColumnSelectionAllowed(false);
    addressTable.setRowSelectionAllowed(true);

    messageTable.addMouseListener(new MouseAdapter() {
	public void mouseClicked(MouseEvent e) {
	  if (e.getClickCount() == 2) {
	    int rowIndex = addressTable.rowAtPoint(e.getPoint());
	    if (rowIndex != -1) {
	      addressTable.setRowSelectionInterval(rowIndex, rowIndex);
	      AddressBookEntry selectedEntry = getSelectedEntry();
	      if (selectedEntry != null) {
		editEntry(selectedEntry);
	      }
	    }
	  }
	}
	
	public void mousePressed(MouseEvent e) {
	  if (SwingUtilities.isRightMouseButton(e)) {
	    // see if anything is selected
	    int rowIndex = addressTable.rowAtPoint(e.getPoint());
	    if (rowIndex == -1 || !addressTable.isRowSelected(rowIndex) ) {
	      addressTable.setRowSelectionInterval(rowIndex, rowIndex);
	    }
	    
	    showPopupMenu();
	  }
	}
      });
    
    updateTableModel(new AddressBookEntry[0]);
    
  }
  
  /**
   * Performs a search using the string value in the searchTextField.  Updates
   * the addressTable with the results.
   */
  public void performSearch() {
    AddressBookEntry[] matchingEntries = book.match(searchTextField.getText());
    updateTableModel(matchingEntries);
  }

  /**
   * Updates the TableModel with the new entries.
   */
  public void updateTableModel(AddressBookEntry[] entries) {
    AddressTableModel newTableModel = new AddressTableModel(entries);
    addressTable.setModel(newTableModel);
  }

  public void setValue() {
    if (isEnabled() && isChanged())
      sourceBundle.setProperty(property, (String)inputField.getSelectedItem());
  }
  
  public java.util.Properties getValue() {
  }
  
  public void resetDefaultValue() {
    inputField.setSelectedIndex(originalIndex);
  }
  
  public boolean isChanged() {
    return (!(originalIndex == inputField.getSelectedIndex()));
  }

  public void setEnabled(boolean newValue) {
    if (inputField != null) {
      inputField.setEnabled(newValue);
      enabled=newValue;
    }
  }

  public class AddressBookTableModel extends javax.swing.table.AbstractTableModel {
    
    AddressBookEntry[] entries;

    public AddressBookTableModel(AddressBookEntry[] newEntries) {
      entries = newEntries;
    }

    public int getRowCount() {
      return entries.length;
    }

    public int getColumnCount() {
      return 4;
    }

    public String getColumnName(int index) {
      if (index == 0) {
	return Pooka.getProperty("AddressBookTable.personalName", "Name");
      } else if (index == 1) {
	return Pooka.getProperty("AddressBookTable.firstName", "First Name");
      } else if (index == 2) {
	return Pooka.getProperty("AddressBookTable.lastName", "Last Name");
      } else if (index == 3) {
	return Pooka.getProperty("AddressBookTable.address", "Email Address");
      } else {
	return null;
      }
    }

    public Object getValueAt(int row, int column) {
      if (row < 0 || column < 0 || row >= getRowCount() || column >= getColumnCount())
	return null;

      AddressBookEntry currentEntry = entries[row];

      if (column == 0) {
	return currentEntry.getPersonalName();
      }
      if (column == 1) {
	return currentEntry.getFirstName();
      }
      if (column == 2) {
	return currentEntry.getLastName();
      }
      if (column == 3) {
	return currentEntry.getAddress();
      }
    }
  }

  public class SearchAction extends AbstractAction {
    public SearchAction() {
      super("address-search");
    }

    public void actionPerformed(ActionEvent e) {
      this.setBusy(true);
      performSearch();
      this.setBusy(false);
    }
  }

}
