package com.sparrowwallet.sparrow.control;

import javafx.beans.NamedArg;
import javafx.scene.control.ChoiceBox;

public class EnumChoiceBox<E extends Enum<E>> extends ChoiceBox<E> {

    public EnumChoiceBox(@NamedArg("enumType") String enumType) throws Exception {
        Class<E> enumClass = (Class<E>) Class.forName(enumType);
        getItems().setAll(enumClass.getEnumConstants());
    }
}


