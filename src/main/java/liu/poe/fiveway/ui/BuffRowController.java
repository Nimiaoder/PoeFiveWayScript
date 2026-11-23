package liu.poe.fiveway.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.Buff;
import liu.poe.fiveway.ui.component.ToggleSwitch;

/** Buff 清單中的一列 (buff_row.fxml)：啟用開關、名稱、標籤、編輯、刪除 */
public class BuffRowController {

    @FXML private ToggleSwitch enabledSwitch;
    @FXML private Label nameLabel;
    @FXML private Label keyTag;
    @FXML private Label intervalTag;
    @FXML private Button castTag;
    @FXML private Button editBtn;
    @FXML private Button deleteBtn;

    private String buffId;
    private String buffName;

    public void setup(Buff b, boolean disabled) {
        this.buffId = b.id;
        this.buffName = b.name;

        enabledSwitch.setSelected(b.enabled);
        enabledSwitch.setDisable(disabled);

        nameLabel.setText(b.name == null || b.name.isEmpty() ? I18n.t("buff.unnamed") : b.name);
        keyTag.setText(b.key != null && b.key.display != null && !b.key.display.isEmpty()
                ? b.key.display : I18n.t("buff.keyUnset"));
        intervalTag.setText(I18n.t("buff.seconds", b.intervalSec));

        castTag.setText(I18n.t(b.castOnStart ? "buff.castOnStartOn" : "buff.castOnStart"));
        castTag.getStyleClass().remove("tag-accent");
        if (b.castOnStart) castTag.getStyleClass().add("tag-accent");
        castTag.setTooltip(new Tooltip(I18n.t("buff.castOnStart.tip")));
        castTag.setDisable(disabled);

        editBtn.setDisable(disabled);
        deleteBtn.setDisable(disabled);
    }

    @FXML
    private void onToggleEnabled() {
        boolean v = enabledSwitch.isSelected();
        AppContext.get().update(s -> s.buffs.stream()
                .filter(x -> buffId.equals(x.id)).forEach(x -> x.enabled = v));
    }

    @FXML
    private void onToggleCast() {
        AppContext.get().update(s -> s.buffs.stream()
                .filter(x -> buffId.equals(x.id)).forEach(x -> x.castOnStart = !x.castOnStart));
    }

    @FXML
    private void onEdit() {
        AppContext.get().ui().openBuffEditor(buffId);
    }

    @FXML
    private void onDelete() {
        AppContext ctx = AppContext.get();
        String shown = (buffName == null || buffName.isEmpty()) ? I18n.t("buff.unnamed") : buffName;
        ctx.ui().confirm(I18n.t("buff.delete.title"), I18n.t("buff.delete.message", shown),
                () -> ctx.update(s -> s.buffs.removeIf(x -> buffId.equals(x.id))));
    }
}
