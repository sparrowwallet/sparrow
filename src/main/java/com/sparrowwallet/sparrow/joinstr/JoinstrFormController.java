package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.BaseController;

public abstract class JoinstrFormController extends BaseController {

    public JoinstrForm joinstrForm;

    public JoinstrForm getJoinstrForm() {
        return joinstrForm;
    }

    public void setJoinstrForm(JoinstrForm joinstrForm) {
        this.joinstrForm = joinstrForm;
    }

    public abstract void initializeView();

}
