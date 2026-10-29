package dev.flip.moneyformat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlipMoneyFormatPlugin extends JavaPlugin implements Listener {
    private MoneyFormatter formatter;
    private Economy proxyEconomy;
    private Economy delegateEconomy;
    private boolean placeholderExpansionRegistered;
    private boolean wrapperRegistered;
    private boolean refreshingServices;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadFormatter();
        getServer().getPluginManager().registerEvents(this, this);
        tryRegisterPlaceholderExpansion();
        refreshVaultWrapper();
        getLogger().info("FlipMoneyFormat enabled. Vault format override: " + isVaultFormatOverrideEnabled());
    }

    @Override
    public void onDisable() {
        if (proxyEconomy != null && wrapperRegistered) {
            getServer().getServicesManager().unregister(Economy.class, proxyEconomy);
        }
        proxyEconomy = null;
        delegateEconomy = null;
        wrapperRegistered = false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("PlaceholderAPI")) {
            tryRegisterPlaceholderExpansion();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (!isEconomyService(event.getProvider().getService())) return;
        if (event.getProvider().getProvider() == proxyEconomy || refreshingServices) return;
        Bukkit.getScheduler().runTask(this, this::refreshVaultWrapper);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (!isEconomyService(event.getProvider().getService())) return;
        if (event.getProvider().getProvider() == proxyEconomy || refreshingServices) return;
        Bukkit.getScheduler().runTask(this, this::refreshVaultWrapper);
    }

    private boolean isEconomyService(Class<?> service) {
        return service == Economy.class;
    }

    private void tryRegisterPlaceholderExpansion() {
        if (placeholderExpansionRegistered || !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            placeholderExpansionRegistered = new FlipMoneyExpansion(this).register();
            if (placeholderExpansionRegistered) {
                getLogger().info("PlaceholderAPI expansion registered: %flipmoneyformat_...%");
            } else {
                getLogger().warning("PlaceholderAPI is present, but the FlipMoneyFormat expansion could not register.");
            }
        } catch (NoClassDefFoundError error) {
            getLogger().warning("PlaceholderAPI API was not available while registering the expansion.");
        }
    }

    private void refreshVaultWrapper() {
        if (!isVaultFormatOverrideEnabled()) {
            if (proxyEconomy != null && wrapperRegistered) {
                getServer().getServicesManager().unregister(Economy.class, proxyEconomy);
            }
            wrapperRegistered = false;
            proxyEconomy = null;
            delegateEconomy = null;
            return;
        }
        if (refreshingServices) return;
        refreshingServices = true;
        try {
            ServicesManager services = getServer().getServicesManager();
            RegisteredServiceProvider<Economy> best = null;
            for (RegisteredServiceProvider<Economy> registration : services.getRegistrations(Economy.class)) {
                if (registration.getProvider() == proxyEconomy || registration.getPlugin() == this) continue;
                if (best == null || registration.getPriority().ordinal() > best.getPriority().ordinal()) {
                    best = registration;
                }
            }

            if (best == null) {
                delegateEconomy = null;
                if (proxyEconomy != null && wrapperRegistered) {
                    services.unregister(Economy.class, proxyEconomy);
                }
                proxyEconomy = null;
                wrapperRegistered = false;
                return;
            }

            delegateEconomy = best.getProvider();
            if (proxyEconomy == null) {
                InvocationHandler handler = this::invokeEconomy;
                proxyEconomy = (Economy) Proxy.newProxyInstance(
                        Economy.class.getClassLoader(), new Class<?>[]{Economy.class}, handler);
            }
            if (!wrapperRegistered) {
                services.register(Economy.class, proxyEconomy, this, ServicePriority.Highest);
                wrapperRegistered = true;
                getLogger().info("Vault economy format override hooked into " + safeEconomyName(delegateEconomy) + ".");
            }
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Could not install the Vault format wrapper; placeholders can still work.", exception);
        } finally {
            refreshingServices = false;
        }
    }

    private Object invokeEconomy(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "FlipMoneyFormatVaultWrapper[delegate=" + safeEconomyName(delegateEconomy) + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                default -> method.invoke(this, args);
            };
        }
        if (method.getName().equals("format") && args != null && args.length == 1 && args[0] instanceof Number number) {
            return formatter.formatCompact(number.doubleValue(), true);
        }

        Economy delegate = delegateEconomy;
        if (delegate == null) {
            throw new IllegalStateException("Vault economy provider is not available.");
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private String safeEconomyName(Economy economy) {
        if (economy == null) return "unavailable";
        try {
            return Objects.toString(economy.getName(), economy.getClass().getSimpleName());
        } catch (RuntimeException ignored) {
            return economy.getClass().getSimpleName();
        }
    }

    public Economy getEconomy() {
        if (isVaultFormatOverrideEnabled() && proxyEconomy != null && wrapperRegistered) return proxyEconomy;
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    public MoneyFormatter getFormatter() {
        return formatter;
    }

    public void reloadFormatter() {
        reloadConfig();
        formatter = new MoneyFormatter(getConfig());
    }

    public boolean isVaultFormatOverrideEnabled() {
        return getConfig().getBoolean("vault-format-override", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("flipmoneyformat")) return false;
        if (!sender.hasPermission("flipmoneyformat.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadFormatter();
            refreshVaultWrapper();
            sender.sendMessage("§aFlipMoneyFormat configuration reloaded.");
            return true;
        }
        sender.sendMessage("§eUsage: /flipmoneyformat reload");
        return true;
    }
}
