package com.sparrowwallet.sparrow.control;

import tornadofx.control.Form;

public class DynamicForm extends Form {
    private DynamicUpdate dynamicUpdate;

    public DynamicForm() {
        super();
    }

    public void setDynamicUpdate(DynamicUpdate dynamicUpdate) {
        this.dynamicUpdate = dynamicUpdate;
    }

    @Override
    protected void layoutChildren() {
        if(dynamicUpdate != null) {
            dynamicUpdate.update();
        }

        super.layoutChildren();
    }
}
