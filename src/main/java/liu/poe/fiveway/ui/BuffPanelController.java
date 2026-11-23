package liu.poe.fiveway.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.Buff;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.ui.component.Section;

import java.io.IOException;
import java.io.UncheckedIOException;

/** 區塊三：Buff 清單 (僅在「自動 Buff」勾選時顯示)；新增/編輯在 BuffEditorController */
public class BuffPanelController {

    @FXML private VBox root;
    @FXML private Section buffSection;
    @FXML private Button addBtn;
    @FXML private Label emptyHint;
    @FXML private VBox buffList;

    private AppContext ctx;
    private String lastSignature = null;

    public void init() {
        ctx = AppContext.get();
        ctx.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Settings s = ctx.settings();
        boolean running = ctx.isRunning();
        UiUtil.show(root, s.buffMode);
        addBtn.setDisable(running);
        UiUtil.show(emptyHint, s.buffs.isEmpty());

        // 清單內容沒變就不重建 (避免任何設定變動都重新載入所有列)
        String sig = running + "|" + signature(s);
        if (sig.equals(lastSignature)) return;
        lastSignature = sig;

        buffList.getChildren().clear();
        for (Buff b : s.buffs) {
            buffList.getChildren().add(createRow(b, running));
        }
    }

    private String signature(Settings s) {
        StringBuilder sb = new StringBuilder();
        for (Buff b : s.buffs) {
            sb.append(b.id).append('\u0001').append(b.name).append('\u0001')
                    .append(b.key == null ? "" : b.key.display).append('\u0001')
                    .append(b.intervalSec).append('\u0001')
                    .append(b.castOnStart).append('\u0001').append(b.enabled).append('\u0002');
        }
        return sb.toString();
    }

    private Node createRow(Buff b, boolean disabled) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/buff_row.fxml"), I18n.bundle());
            Node row = loader.load();
            BuffRowController c = loader.getController();
            c.setup(b, disabled);
            return row;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FXML
    private void onAdd() {
        ctx.ui().openBuffEditor(null);
    }
}
