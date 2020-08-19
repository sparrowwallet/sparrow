package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.scene.Node;

import java.io.IOException;

public class PageForm extends IndexedTransactionForm {
    public static final int PAGE_SIZE = 50;

    private final TransactionView view;
    private final int pageStart;
    private final int pageEnd;

    public PageForm(TransactionView view, int pageStart, int pageEnd) {
        super(new TransactionData(pageStart + "-" + pageEnd, ElectrumServer.UNFETCHABLE_BLOCK_TRANSACTION), pageStart);
        this.view = view;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    @Override
    public Node getContents() throws IOException {
        return null;
    }

    @Override
    public TransactionView getView() {
        return view;
    }

    public int getPageStart() {
        return pageStart;
    }

    public int getPageEnd() {
        return pageEnd;
    }

    @Override
    public String toString() {
        return "...";
    }
}
