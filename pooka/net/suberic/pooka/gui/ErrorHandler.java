package net.suberic.pooka.gui;

public interface ErrorHandler {

    public void showError(String errorMessage);

    public void showError(String errorMessage, String title);

    public void showError(String errorMessage, String title, Exception e);

}
