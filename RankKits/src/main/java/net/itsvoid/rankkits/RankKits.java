package net.itsvoid.rankkits;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class RankKits extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "&8[&dKits&8] &r";

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private File cooldownFile;
    private FileConfiguration cooldownData;
    private long cooldownMillis;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        cooldownData = new YamlConfiguration();
        cooldownMillis = getConfig().getLong("cooldown-hours", 24) * 60L * 60L * 1000L;
        if (cooldownMillis < 1) {
            throw new IllegalStateException("cooldown-hours must be greater than zero.");
        }

        try {
            if (cooldownFile.exists()) {
                cooldownData.load(cooldownFile);
            }
            loadCooldowns();
            loadKits();
        } catch (IOException | InvalidConfigurationException | IllegalStateException exception) {
            getLogger().severe("Could not load kit data: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("kit") == null) {
            throw new IllegalStateException("The /kit command is missing from plugin.yml.");
        }
        getCommand("kit").setExecutor(this);
        getCommand("kit").setTabCompleter(this);
        getLogger().info("Loaded " + kits.size() + " rank kits.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(PREFIX + "&cOnly players can claim kits."));
            return true;
        }

        if (args.length != 1) {
            List<String> available = new ArrayList<>();
            for (Kit kit : kits.values()) {
                if (player.hasPermission(kit.permission())) {
                    available.add(kit.name());
                }
            }
            if (available.isEmpty()) {
                player.sendMessage(color(PREFIX + "&cYour rank does not include any kits."));
            } else {
                player.sendMessage(color(PREFIX + "&7Available kits: &f" + String.join(", ", available)));
            }
            player.sendMessage(color(PREFIX + "&7Usage: &f/kit <name>"));
            return true;
        }

        String kitName = args[0].toLowerCase(Locale.ROOT);
        Kit kit = kits.get(kitName);
        if (kit == null) {
            player.sendMessage(color(PREFIX + "&cThat kit does not exist."));
            return true;
        }
        if (!player.hasPermission(kit.permission())) {
            player.sendMessage(color(PREFIX + "&cYour rank does not have access to that kit."));
            return true;
        }

        long now = System.currentTimeMillis();
        String cooldownKey = player.getUniqueId() + "." + kit.name();
        long availableAt = cooldowns.getOrDefault(cooldownKey, 0L) + cooldownMillis;
        if (now < availableAt) {
            player.sendMessage(color(PREFIX + "&cYou can claim this kit again in &f"
                    + formatDuration(availableAt - now) + "&c."));
            return true;
        }

        Inventory preview = Bukkit.createInventory(null, player.getInventory().getStorageContents().length);
        preview.setStorageContents(player.getInventory().getStorageContents());
        Map<Integer, ItemStack> previewRemainder = preview.addItem(cloneItems(kit.items()));
        if (!previewRemainder.isEmpty()) {
            player.sendMessage(color(PREFIX + "&cMake room in your inventory first. No kit was given."));
            return true;
        }

        cooldowns.put(cooldownKey, now);
        cooldownData.set("cooldowns." + cooldownKey, now);
        try {
            cooldownData.save(cooldownFile);
        } catch (IOException exception) {
            cooldowns.remove(cooldownKey);
            cooldownData.set("cooldowns." + cooldownKey, null);
            getLogger().severe("Could not save cooldown for " + player.getUniqueId()
                    + ": " + exception.getMessage());
            player.sendMessage(color(PREFIX + "&cThe kit could not be claimed. Please contact staff."));
            return true;
        }

        Map<Integer, ItemStack> remainder = player.getInventory().addItem(cloneItems(kit.items()));
        if (!remainder.isEmpty()) {
            for (ItemStack item : remainder.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            getLogger().severe("Inventory changed while delivering kit '" + kit.name()
                    + "' to " + player.getUniqueId() + "; remaining items were dropped.");
        }
        player.sendMessage(color(PREFIX + "&aYou claimed the &f" + kit.displayName() + " &akit."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        return kits.values().stream()
                .filter(kit -> player.hasPermission(kit.permission()))
                .map(Kit::name)
                .filter(name -> name.startsWith(partial))
                .toList();
    }

    private void loadCooldowns() {
        ConfigurationSection section = cooldownData.getConfigurationSection("cooldowns");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            long timestamp = section.getLong(key);
            cooldowns.put(key, timestamp);
        }
    }

    private void loadKits() {
        ConfigurationSection section = getConfig().getConfigurationSection("kits");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalStateException("No kits are configured.");
        }

        for (String rawName : section.getKeys(false)) {
            String name = rawName.toLowerCase(Locale.ROOT);
            String path = "kits." + rawName;
            String permission = getConfig().getString(path + ".permission", "").trim();
            String displayName = getConfig().getString(path + ".display-name", rawName).trim();
            if (permission.isEmpty()) {
                throw new IllegalStateException("Missing permission for kit '" + rawName + "'.");
            }
            List<Map<?, ?>> itemConfigs = getConfig().getMapList(path + ".items");
            if (itemConfigs.isEmpty()) {
                throw new IllegalStateException("Kit '" + rawName + "' has no items.");
            }

            List<ItemStack> items = new ArrayList<>();
            for (Map<?, ?> itemConfig : itemConfigs) {
                items.add(createItem(rawName, itemConfig));
            }
            kits.put(name, new Kit(name, permission, color(displayName), items));
        }
    }

    private ItemStack createItem(String kitName, Map<?, ?> itemConfig) {
        Object materialValue = itemConfig.get("material");
        if (materialValue == null) {
            throw new IllegalStateException("An item in kit '" + kitName + "' has no material.");
        }
        Material material = Material.matchMaterial(materialValue.toString());
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalStateException("Invalid item material in kit '" + kitName + "': "
                    + materialValue);
        }

        int amount;
        try {
            Object amountValue = itemConfig.containsKey("amount") ? itemConfig.get("amount") : 1;
            amount = Integer.parseInt(String.valueOf(amountValue));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid item amount in kit '" + kitName + "'.", exception);
        }
        if (amount < 1 || amount > material.getMaxStackSize()) {
            throw new IllegalStateException("Item amount for " + material + " in kit '" + kitName
                    + "' must be between 1 and " + material.getMaxStackSize() + ".");
        }

        ItemStack item = new ItemStack(material, amount);
        Object enchantmentValue = itemConfig.get("enchantments");
        if (enchantmentValue instanceof Map<?, ?> enchantments) {
            for (Map.Entry<?, ?> enchantmentEntry : enchantments.entrySet()) {
                String enchantmentName = enchantmentEntry.getKey().toString().toLowerCase(Locale.ROOT);
                Enchantment enchantment =
                        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                                .get(NamespacedKey.minecraft(enchantmentName));
                if (enchantment == null) {
                    throw new IllegalStateException("Unknown enchantment '" + enchantmentName
                            + "' in kit '" + kitName + "'.");
                }
                int level;
                try {
                    level = Integer.parseInt(enchantmentEntry.getValue().toString());
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException("Invalid enchantment level in kit '" + kitName + "'.",
                            exception);
                }
                if (level < 1 || level > enchantment.getMaxLevel()
                        || !enchantment.canEnchantItem(item)) {
                    throw new IllegalStateException("Invalid enchantment level or item in kit '"
                            + kitName + "': " + enchantmentName + " " + level + ".");
                }
                item.addEnchantment(enchantment, level);
            }
        }
        return item;
    }

    private ItemStack[] cloneItems(List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toArray(ItemStack[]::new);
    }

    private String formatDuration(long millis) {
        long totalMinutes = Math.max(1, (millis + 59_999) / 60_000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + "m";
        }
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }

    private String color(String text) {
        return text.replaceAll("(?i)&([0-9a-fk-or])", "\u00A7$1");
    }

    private record Kit(String name, String permission, String displayName, List<ItemStack> items) {
    }
}
