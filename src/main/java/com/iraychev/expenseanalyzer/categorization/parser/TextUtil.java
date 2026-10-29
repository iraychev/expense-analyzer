package com.iraychev.expenseanalyzer.categorization.parser;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalisation and redaction helpers shared by the parsers. */
public final class TextUtil {

    static final int MAX_KEY_LENGTH = 120;

    private static final Pattern URL_PREFIX = Pattern.compile("^(?:https?:)?/*(?:www\\.)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern MASKED_PAN = Pattern.compile("(?i)PAN:\\S*|\\b\\d{4,6}[*xX]{2,}\\d{2,4}\\b|\\*{2,}\\d{2,4}\\b");
    private static final Pattern TERMINAL = Pattern.compile("(?i)\\bTID:\\S*|\\bCT:\\S*|\\bNOM::\\S*|\\bMID:\\S*");
    private static final Pattern AUTH_CODE = Pattern.compile("(?i)авт\\.код:\\d+-?");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Za-z]{2}\\d{2}[A-Za-z0-9]{10,30}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\S+@\\S+\\.\\S+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{7,}\\d");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{6,}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextUtil() {
    }

    /** Collapses whitespace and trims; null/blank becomes null. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = SPACES.matcher(value).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    /**
     * Canonical merchant key: lower case, URL scheme and "www." dropped, everything that is not a letter (any
     * script) or digit becomes a single space.
     */
    public static String merchantKey(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null) {
            return null;
        }
        String withoutUrl = URL_PREFIX.matcher(cleaned).replaceFirst("");
        String key = NON_ALNUM.matcher(withoutUrl.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (key.isEmpty()) {
            return null;
        }
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH).trim() : key;
    }

    /**
     * Removes everything that must not leave the system: masked card numbers, IBANs, terminal ids, e-mail
     * addresses, phone numbers, long digit sequences and the account holder's name.
     */
    public static String redact(String text, List<String> holderNames) {
        if (text == null) {
            return "";
        }
        String result = text;
        result = MASKED_PAN.matcher(result).replaceAll(" ");
        result = TERMINAL.matcher(result).replaceAll(" ");
        result = AUTH_CODE.matcher(result).replaceAll(" ");
        result = IBAN.matcher(result).replaceAll(" [iban] ");
        result = EMAIL.matcher(result).replaceAll(" [email] ");
        result = PHONE.matcher(result).replaceAll(" [number] ");
        result = LONG_DIGITS.matcher(result).replaceAll(" [id] ");
        if (holderNames != null) {
            for (String name : holderNames) {
                if (name == null) {
                    continue;
                }
                for (String token : SPACES.split(name.trim())) {
                    if (token.length() >= 3) {
                        result = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(token) + "(?![\\p{L}\\p{N}])")
                                .matcher(result).replaceAll(" ");
                    }
                }
            }
        }
        String cleaned = clean(result);
        return cleaned == null ? "" : cleaned;
    }
}
