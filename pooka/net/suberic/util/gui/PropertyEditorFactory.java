package net.suberic.util.gui;
import javax.swing.*;
import net.suberic.util.*;
import java.awt.*;
import java.util.Vector;

public class PropertyEditorFactory {

    VariableBundle sourceBundle;

    /**
     * Here are the ones that I can think of:

     basic property:  text field
     property with choices:  drop down list
     boolean:  check box
     entire field:  jtabbedpane
     ... one property with subproperties:  that's tough.  left window with
        entries, right with values?

     (add password -jphekman)
    */

    public PropertyEditorFactory(VariableBundle bundle) {
	sourceBundle = bundle;
    }

    /**
     * This method returns an EditorWindow (a JFrame in this
     * implementation) which has an editor for each property in the
     * properties Vector.  The title string is the title of the 
     * JInternalFrame.
     */
    public Container createEditorWindow(String title, Vector properties) {
	JFrame jf = new JFrame(title);
	jf.getContentPane().add(new PropertyEditorPane(this, properties, jf));
	jf.setSize(100,100);
	jf.pack();
	return jf;
    }

    /**
     * This returns a DefaultPropertyEditor for the property passed.
     * If there is a value set for property.propertyType, it will return
     * the proper editor for that property type.  If there is no such
     * property set, then this will return a BasicEditor.
     */
    public DefaultPropertyEditor createEditor(String property) {
	String test = sourceBundle.getProperty(property + ".propertyType", "");
	if (test.equals("String"))
	    return createStringEditor(property);
	if (test.equals("Password"))
	    return createPasswordEditor(property);
	else if (test.equals("List"))
	    return createListEditor(property);
	else if (test.equals("Boolean"))
	    return createBooleanEditor(property);
	else if (test.equals("Multi"))
	    return createMultiEditor(property);
	else if (test.equals("Tabbed"))
	    return createTabbedEditor(property, property);
	else
	    return createBasicEditor(property);
    }

    /**
     * This returns a DefaultPropertyEditor for the property passed.
     * This method uses the typeTemplate parameter to determine what
     * type of property should be created.  Specifically, this method
     * looks for the property typeTemplate.propertyType, and, if it is
     * set, creates an editor appropriate for that type for the 
     * property.
     */
    public DefaultPropertyEditor createEditor(String property, String typeTemplate) {
	String test = sourceBundle.getProperty(typeTemplate + ".propertyType", "");
	if (test.equals("String"))
	    return createStringEditor(property);
	if (test.equals("Password"))
	    return createPasswordEditor(property);
	else if (test.equals("List"))
	    return createListEditor(property, typeTemplate);
	else if (test.equals("Boolean"))
	    return createBooleanEditor(property);
	else if (test.equals("Multi"))
	    return createMultiEditor(property);
	else if (test.equals("Tabbed"))
	    return createTabbedEditor(property, typeTemplate);
	else
	    return createBasicEditor(property);
    }

    private DefaultPropertyEditor createBasicEditor(String property) {
	return createStringEditor(property);
    }

    private DefaultPropertyEditor createStringEditor(String property) {
	return new StringEditorPane(property, sourceBundle);
    }

    private DefaultPropertyEditor createPasswordEditor(String property) {
	return new PasswordEditorPane(property, sourceBundle);
    }

    private DefaultPropertyEditor createListEditor(String property) {
	return new ListEditorPane(property, sourceBundle);
    }

    private DefaultPropertyEditor createListEditor(String property, String templateType) {
	return new ListEditorPane(property, templateType, sourceBundle);
    }

    private DefaultPropertyEditor createBooleanEditor(String property) {
	return new BooleanEditorPane(property, sourceBundle);
    }

    private DefaultPropertyEditor createMultiEditor(String property) {
	return new MultiEditorPane(property,this);
    }

    private DefaultPropertyEditor createTabbedEditor(String property, String typeTemplate) {
	return new TabbedEditorPane(property, typeTemplate, this);
    }

    public VariableBundle getBundle() {
	return sourceBundle;
    }

    public String showInputDialog(DefaultPropertyEditor dpe, String query) {
	if (dpe instanceof java.awt.Component) 
	    return JOptionPane.showInputDialog(dpe, query);
	else
	    return null;
    }
	    
}

     
    



