package im.marin.onyxtv;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * locale: the language the launcher shows itself in, instead of the TV's. A China-market Xiaomi offers Chinese and
 * English only, so a Vietnamese owner saw the launcher in Chinese although it is translated into Vietnamese.
 * Each activity wraps its base context here; an empty or malformed tag leaves the TV's language.
 */
final class LauncherLocale {
    // Subtags after the language, so "sr-Latn" and "ar-EG-u-nu-latn" (Arabic with Western digits) pass too.
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    static final class LanguageOption {
        final String tag;
        final String label;

        LanguageOption(String tag, String label) {
            this.tag = tag;
            this.label = label;
        }
    }

    private static final List<LanguageOption> SUPPORTED_LANGUAGES = Collections.unmodifiableList(Arrays.asList(
            new LanguageOption("en", "English"),
            new LanguageOption("es", "Español"),
            new LanguageOption("es-419", "Español (Latinoamérica)"),
            new LanguageOption("ca", "Català"),
            new LanguageOption("eu", "Euskara"),
            new LanguageOption("gl", "Galego"),
            new LanguageOption("ast", "Asturianu"),
            new LanguageOption("de", "Deutsch"),
            new LanguageOption("fr", "Français"),
            new LanguageOption("it", "Italiano"),
            new LanguageOption("pt", "Português (Brasil)"),
            new LanguageOption("pt-PT", "Português (Portugal)"),
            new LanguageOption("nl", "Nederlands"),
            new LanguageOption("da", "Dansk"),
            new LanguageOption("nb", "Norsk bokmål"),
            new LanguageOption("sv", "Svenska"),
            new LanguageOption("fi", "Suomi"),
            new LanguageOption("pl", "Polski"),
            new LanguageOption("cs", "Čeština"),
            new LanguageOption("sr-Latn", "Srpski (latinica)"),
            new LanguageOption("ru", "Русский"),
            new LanguageOption("uk", "Українська"),
            new LanguageOption("tr", "Türkçe"),
            new LanguageOption("ar", "العربية"),
            new LanguageOption("iw", "עברית"),
            new LanguageOption("uz", "Oʻzbek"),
            new LanguageOption("bn", "বাংলা"),
            new LanguageOption("th", "ไทย"),
            new LanguageOption("vi", "Tiếng Việt"),
            new LanguageOption("in", "Bahasa Indonesia"),
            new LanguageOption("zh-CN", "简体中文"),
            new LanguageOption("zh-TW", "繁體中文"),
            new LanguageOption("ko", "한국어")));

    private LauncherLocale() {
    }

    /** Supported UI languages with each language shown in its own native name. */
    static List<LanguageOption> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /** Native display label for {@code tag}, or {@code null} if {@code tag} is empty or unknown. */
    static String nativeLabel(String tag) {
        if (tag == null || tag.trim().isEmpty()) return null;
        String clean = tag.trim();
        for (LanguageOption option : SUPPORTED_LANGUAGES) {
            if (option.tag.equalsIgnoreCase(clean)) return option.label;
        }
        Locale parsed = parse(clean);
        return parsed != null ? parsed.getDisplayName(parsed) : null;
    }

    /** The Locale for a config tag such as "vi", "pt-BR" or "ar-EG-u-nu-latn", or null for "" or anything that is not a plain tag. */
    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    /** base with the configured language, or base itself when locale is off. */
    static Context wrap(Context base) {
        Locale locale = parse(Config.load(base).locale);
        if (locale == null) {
            LocaleList systemLocales = Resources.getSystem().getConfiguration().getLocales();
            if (!systemLocales.isEmpty()) Locale.setDefault(systemLocales.get(0));
            return base;
        }
        // Formatters that take no Context (the clock's date, the lunar line) follow the default locale.
        Locale.setDefault(locale);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(locale);
        return base.createConfigurationContext(c);
    }
}
