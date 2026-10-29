package dev.flip.moneyformat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/** Responsible for display-only money formatting. Never changes the stored balance. */
public final class MoneyFormatter {
    private final String symbol;
    private final boolean symbolSpace;
    private final int decimalPlaces;
    private final boolean trimTrailingZeros;
    private final boolean abbreviate;
    private final List<String> suffixes;
    private final char groupingSeparator;
    private final char decimalSeparator;

    public MoneyFormatter(FileConfiguration config) {
        this.symbol = config.getString("format.symbol", "$");
        this.symbolSpace = config.getBoolean("format.space-after-symbol", true);
        this.decimalPlaces = Math.max(0, Math.min(6, config.getInt("format.decimal-places", 2)));
        this.trimTrailingZeros = config.getBoolean("format.trim-trailing-zeros", true);
        this.abbreviate = config.getBoolean("format.abbreviate", true);

        List<String> configuredSuffixes = config.getStringList("format.suffixes");
        List<String> cleaned = new ArrayList<>();
        for (String suffix : configuredSuffixes) {
            if (suffix != null && !suffix.isBlank()) cleaned.add(suffix);
        }
        if (cleaned.isEmpty()) cleaned = List.of("k", "m", "b", "t", "q", "qq");
        this.suffixes = List.copyOf(cleaned);
        this.groupingSeparator = readSeparator(config.getString("format.grouping-separator", ","), ',');
        this.decimalSeparator = readSeparator(config.getString("format.decimal-separator", "."), '.');
    }

    private char readSeparator(String value, char fallback) {
        return value == null || value.isEmpty() ? fallback : value.charAt(0);
    }

    public String formatCompact(double amount, boolean withSymbol) {
        if (!Double.isFinite(amount)) return withSymbol ? symbolPrefix() + "0" : "0";
        BigDecimal value = BigDecimal.valueOf(amount);
        boolean negative = value.signum() < 0;
        value = value.abs();

        String number;
        if (!abbreviate || value.compareTo(BigDecimal.valueOf(1000)) < 0 || suffixes.isEmpty()) {
            number = formatNumber(value, true);
        } else {
            int suffixIndex = 0;
            BigDecimal thousand = BigDecimal.valueOf(1000);
            BigDecimal divisor = thousand;
            while (suffixIndex < suffixes.size() - 1
                    && value.divide(divisor, decimalPlaces + 2, RoundingMode.HALF_UP).compareTo(thousand) >= 0) {
                divisor = divisor.multiply(thousand);
                suffixIndex++;
            }

            BigDecimal abbreviatedValue = value.divide(divisor, decimalPlaces, RoundingMode.HALF_UP)
                    .setScale(decimalPlaces, RoundingMode.HALF_UP);
            if (abbreviatedValue.compareTo(thousand) >= 0 && suffixIndex < suffixes.size() - 1) {
                divisor = divisor.multiply(thousand);
                suffixIndex++;
                abbreviatedValue = value.divide(divisor, decimalPlaces, RoundingMode.HALF_UP)
                        .setScale(decimalPlaces, RoundingMode.HALF_UP);
            }
            number = formatNumber(abbreviatedValue, false) + suffixes.get(suffixIndex);
        }

        String prefix = withSymbol ? symbolPrefix() : "";
        return (negative ? "-" : "") + prefix + number;
    }

    public String formatFull(double amount, boolean withSymbol) {
        if (!Double.isFinite(amount)) return withSymbol ? symbolPrefix() + "0" : "0";
        BigDecimal value = BigDecimal.valueOf(amount);
        boolean negative = value.signum() < 0;
        value = value.abs();
        String prefix = withSymbol ? symbolPrefix() : "";
        return (negative ? "-" : "") + prefix + formatNumber(value, true);
    }

    public String formatRaw(double amount) {
        if (!Double.isFinite(amount)) return "0";
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private String symbolPrefix() {
        if (symbol == null || symbol.isEmpty()) return "";
        return symbol + (symbolSpace ? " " : "");
    }

    private String formatNumber(BigDecimal value, boolean grouping) {
        StringBuilder pattern = new StringBuilder();
        pattern.append(grouping ? "#,##0" : "0");
        if (decimalPlaces > 0) pattern.append('.').append("0".repeat(decimalPlaces));
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        symbols.setGroupingSeparator(groupingSeparator);
        symbols.setDecimalSeparator(decimalSeparator);
        DecimalFormat formatter = new DecimalFormat(pattern.toString(), symbols);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        formatter.setGroupingUsed(grouping);
        formatter.setMaximumFractionDigits(decimalPlaces);
        formatter.setMinimumFractionDigits(trimTrailingZeros ? 0 : decimalPlaces);
        return formatter.format(value);
    }
}
