package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.JavaFxTestSupport;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CoinTreeTableTest {
    @BeforeAll
    public static void startJavaFx() throws InterruptedException {
        JavaFxTestSupport.startJavaFx();
    }

    @Test
    public void selectedRowsAreDistinctByTreeItem() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            TreeItem<String> root = new TreeItem<>("root");
            root.getChildren().add(new TreeItem<>("first"));
            root.getChildren().add(new TreeItem<>("second"));
            root.setExpanded(true);

            TreeTableView<String> treeTableView = new TreeTableView<>(root);
            treeTableView.setShowRoot(false);
            TreeTableColumn<String, String> firstColumn = new TreeTableColumn<>("First");
            TreeTableColumn<String, String> secondColumn = new TreeTableColumn<>("Second");
            treeTableView.getColumns().add(firstColumn);
            treeTableView.getColumns().add(secondColumn);
            treeTableView.getSelectionModel().setCellSelectionEnabled(true);
            treeTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            treeTableView.getSelectionModel().select(0, firstColumn);
            treeTableView.getSelectionModel().select(0, secondColumn);
            treeTableView.getSelectionModel().select(1, firstColumn);

            Assertions.assertEquals(List.of("first", "second"), CoinTreeTable.getSelectedRows(treeTableView));
        });
    }
}
