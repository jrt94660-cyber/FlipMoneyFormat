package dev.flip.moneyformat;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class FlipMoneyExpansion extends PlaceholderExpansion {
    private final FlipMoneyFormatPlugin plugin;

    public FlipMoneyExpansion(FlipMoneyFormatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "flipmoneyformat";
    }

    @Override
    public String getAuthor() {
        return "jrt94660-cyber";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) return "";
        String lower = params.toLowerCase(Locale.ROOT);
        Economy economy = plugin.getEconomy();

        if (lower.startsWith("format_") || lower.startsWith("formatshort_")
                || lower.startsWith("formatfull_") || lower.startsWith("formatraw_")) {
            String mode;
            String amountText;
            if (lower.startsWith("formatshort_")) {
                mode = "short";
                amountText = params.substring("formatshort_".length());
            } else if (lower.startsWith("formatfull_")) {
                mode = "full";
                amountText = params.substring("formatfull_".length());
            } else if (lower.startsWith("formatraw_")) {
                mode = "raw";
                amountText = params.substring("formatraw_".length());
            } else {
                mode = "compact";
                amountText = params.substring("format_".length());
            }
            Double amount = parseAmount(amountText);
            return amount == null ? "" : formatAmount(amount, mode);
        }

        if (lower.equals("balance") || lower.equals("balance_short") || lower.equals("balance_full")
                || lower.equals("balance_raw") || lower.equals("balance_number")) {
            if (player == null || economy == null) return "";
            return formatBalance(player, economy, modeFrom(lower));
        }

        String mode = "compact";
        String playerName = null;
        if (lower.startsWith("balance_short_")) {
            mode = "short";
            playerName = params.substring("balance_short_".length());
        } else if (lower.startsWith("balance_full_")) {
            mode = "full";
            playerName = params.substring("balance_full_".length());
        } else if (lower.startsWith("balance_raw_")) {
            mode = "raw";
            playerName = params.substring("balance_raw_".length());
        } else if (lower.startsWith("balance_number_")) {
            mode = "number";
            playerName = params.substring("balance_number_".length());
        } else if (lower.startsWith("balance_")) {
            playerName = params.substring("balance_".length());
        }

        if (playerName != null && !playerName.isBlank() && economy != null) {
            OfflinePlayer target = findOfflinePlayer(playerName);
            return target == null ? "" : formatBalance(target, economy, mode);
        }
        return "";
    }

    private String modeFrom(String parameter) {
        return switch (parameter) {
            case "balance_short" -> "short";
            case "balance_full" -> "full";
            case "balance_raw" -> "raw";
            case "balance_number" -> "number";
            default -> "compact";
        };
    }

    private OfflinePlayer findOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayer(name);
    }

    private String formatBalance(OfflinePlayer player, Economy economy, String mode) {
        try {
            return formatAmount(economy.getBalance(player), mode);
        } catch (RuntimeException exception) {
            plugin.getLogger().fine("Could not retrieve Vault balance for " + player.getName() + ": " + exception.getMessage());
            return "";
        }
    }

    private String formatAmount(double amount, String mode) {
        MoneyFormatter formatter = plugin.getFormatter();
        return switch (mode) {
            case "short", "number" -> formatter.formatCompact(amount, false);
            case "full" -> formatter.formatFull(amount, true);
            case "raw" -> formatter.formatRaw(amount);
            default -> formatter.formatCompact(amount, true);
        };
    }

    private Double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double amount = Double.parseDouble(raw.replace(",", "").replace("_", ""));
            return Double.isFinite(amount) ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
