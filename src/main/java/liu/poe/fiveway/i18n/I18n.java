package liu.poe.fiveway.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * 多語系管理：文字集中在 resources/i18n/messages_xx.properties (UTF-8)。
 * <ul>
 *   <li>FXML 內以 <code>%key</code> 取用 (FXMLLoader 傳入 {@link #bundle()})</li>
 *   <li>Java 內以 {@link #t(String)} / {@link #t(String, Object...)} 取用</li>
 * </ul>
 */
public final class I18n {

    /** 支援的語言 (顯示名稱固定使用該語言本身的寫法，不隨介面語言翻譯) */
    public enum Language {
        ZH_TW("zh_TW", "繁體中文", Locale.TRADITIONAL_CHINESE),
        ZH_CN("zh_CN", "简体中文", Locale.SIMPLIFIED_CHINESE);

        public final String code;
        public final String displayName;
        public final Locale locale;

        Language(String code, String displayName, Locale locale) {
            this.code = code;
            this.displayName = displayName;
            this.locale = locale;
        }

        @Override
        public String toString() {
            return displayName;
        }

        public static Language fromCode(String code) {
            if (code != null) {
                for (Language l : values()) {
                    if (l.code.equalsIgnoreCase(code)) return l;
                }
            }
            return null;
        }
    }

    private static Language current = Language.ZH_TW;
    private static ResourceBundle bundle;

    private I18n() {
    }

    /** 依儲存的語言代碼初始化；null 代表依系統語言 (簡體地區用簡中，其餘繁中) */
    public static void init(String savedCode) {
        Language lang = Language.fromCode(savedCode);
        setLanguage(lang != null ? lang : detectSystemLanguage());
    }

    public static Language detectSystemLanguage() {
        Locale sys = Locale.getDefault();
        if ("zh".equalsIgnoreCase(sys.getLanguage())) {
            String country = sys.getCountry();
            String script = sys.getScript();
            if ("CN".equalsIgnoreCase(country) || "SG".equalsIgnoreCase(country) || "Hans".equalsIgnoreCase(script)) {
                return Language.ZH_CN;
            }
        }
        return Language.ZH_TW;
    }

    public static synchronized void setLanguage(Language lang) {
        current = lang;
        bundle = load(lang);
        Locale.setDefault(Locale.Category.DISPLAY, lang.locale);
    }

    public static Language current() {
        return current;
    }

    public static synchronized ResourceBundle bundle() {
        if (bundle == null) setLanguage(current);
        return bundle;
    }

    private static ResourceBundle load(Language lang) {
        String path = "/i18n/messages_" + lang.code + ".properties";
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("找不到語系檔: " + path);
            return new PropertyResourceBundle(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 取字串；找不到 key 時直接回傳 key 方便除錯 */
    public static String t(String key) {
        ResourceBundle b = bundle();
        return b.containsKey(key) ? b.getString(key) : key;
    }

    /** 取字串並以 {0} {1} ... 代入參數 */
    public static String t(String key, Object... args) {
        return new MessageFormat(t(key), current.locale).format(args);
    }
}
