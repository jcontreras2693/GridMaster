package edu.co.arsw.gridmaster.model.exceptions;

public class ColorSaveException extends GridMasterException {
    public ColorSaveException(String color) {
        super("Error saving the color: '" + color + "'.");
    }
}
