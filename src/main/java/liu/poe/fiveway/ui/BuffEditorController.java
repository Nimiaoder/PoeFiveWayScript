package liu.poe.fiveway.ui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.Buff;
import liu.poe.fiveway.model.KeyCombo;
import liu.poe.fiveway.ui.component.KeyRecorder;

import java.util.UUID;

/** Buff 新增 / 編輯 視窗 (含檢核)；「啟用」與「啟動立即施放」請於主畫面 Buff 列表調整 */
public class BuffEditorController {

    private static final int NAME_MAX = 40;

    @FXML private StackPane backdrop;
    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private KeyRecorder keyRecorder;
    @FXML private TextField intervalField;
    @FXML private Button saveBtn;

    private Buff existing;      // 編輯時的原 Buff；新增為 null
    private KeyCombo key;
    private int intervalSec = 10;
    private Scene escScene;
    private final EventHandler<KeyEvent> escHandler = e -> {
        if (e.getCode() == KeyCode.ESCAPE) close();
    };

    @FXML
    private void initialize() {
        nameField.setTextFormatter(new TextFormatter<String>(
                c -> c.getControlNewText().length() <= NAME_MAX ? c : null));
        UiUtil.bindInt(intervalField, () -> intervalSec, v -> intervalSec = v, false, 1);
        keyRecorder.setOnChange(k -> {
            key = k;
            keyRecorder.setValue(k);
        });
    }

    /** buffId 為 null 代表新增 */
    public void open(String buffId) {
        AppContext ctx = AppContext.get();
        existing = null;
        if (buffId != null) {
            existing = ctx.settings().buffs.stream().filter(b -> buffId.equals(b.id)).findFirst().orElse(null);
        }
        boolean isEdit = existing != null;

        titleLabel.setText(I18n.t(isEdit ? "buff.editor.edit" : "buff.editor.add"));
        saveBtn.setText(I18n.t(isEdit ? "btn.save" : "btn.addConfirm"));
        nameField.setText(isEdit && existing.name != null ? existing.name : "");
        key = isEdit ? existing.key : null;
        keyRecorder.setValue(key);
        intervalSec = isEdit ? existing.intervalSec : 10;
        intervalField.setText(String.valueOf(intervalSec));

        UiUtil.show(backdrop, true);
        escScene = backdrop.getScene();
        if (escScene != null) escScene.addEventFilter(KeyEvent.KEY_PRESSED, escHandler);
        Platform.runLater(nameField::requestFocus);
    }

    private void close() {
        UiUtil.show(backdrop, false);
        if (escScene != null) {
            escScene.removeEventFilter(KeyEvent.KEY_PRESSED, escHandler);
            escScene = null;
        }
    }

    @FXML
    private void onBackdropClick() {
        close();
    }

    @FXML
    private void consume(MouseEvent e) {
        e.consume();
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        AppContext ctx = AppContext.get();
        boolean isEdit = existing != null;

        // === 檢核 ===
        String trimmed = nameField.getText() == null ? "" : nameField.getText().trim();
        if (trimmed.isEmpty()) {
            ctx.ui().showError(I18n.t("buff.err.nameRequired"));
            return;
        }
        if (trimmed.length() > NAME_MAX) {
            ctx.ui().showError(I18n.t("buff.err.nameTooLong"));
            return;
        }
        boolean nameDup = ctx.settings().buffs.stream().anyMatch(b ->
                b.name != null && b.name.trim().equals(trimmed) && (!isEdit || !b.id.equals(existing.id)));
        if (nameDup) {
            ctx.ui().showError(I18n.t("buff.err.nameDup", trimmed));
            return;
        }
        if (key == null) {
            ctx.ui().showError(I18n.t("buff.err.keyRequired"));
            return;
        }
        String dup = ctx.duplicateOf(key, isEdit ? "buff:" + existing.id : "buff:__new__");
        if (dup != null) {
            ctx.ui().showError(I18n.t("dup.message", dup));
            return;
        }
        int sec = intervalSec;
        if (sec < 1) {
            ctx.ui().showError(I18n.t("buff.err.intervalMin"));
            return;
        }

        // 新增時預設: 啟用=true, 啟動立即施放=false；編輯時保留既有值 (在主畫面調整)
        Buff nb = new Buff();
        nb.id = isEdit ? existing.id : "buff_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4);
        nb.name = trimmed;
        nb.key = key;
        nb.intervalSec = sec;
        nb.castOnStart = isEdit && existing.castOnStart;
        nb.enabled = !isEdit || existing.enabled;

        ctx.update(s -> {
            if (isEdit) {
                for (int i = 0; i < s.buffs.size(); i++) {
                    if (nb.id.equals(s.buffs.get(i).id)) {
                        s.buffs.set(i, nb);
                        break;
                    }
                }
            } else {
                s.buffs.add(nb);
            }
        });
        close();
    }
}
