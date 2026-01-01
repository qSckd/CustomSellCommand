package dev.customsell;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomSellCommand extends JavaPlugin implements Listener, CommandExecutor {

    private String guiTitle;
    private Map<Material, Integer> sellPrices;
    private String msgNotSellable;
    private String msgYouSold;
    private Material payoutMaterial;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        Objects.requireNonNull(getCommand("sell")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("CustomSellCommand enabled.");
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();

        guiTitle = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("gui.title", "&7ᴘʟᴀᴄᴇ ɪᴛᴇᴍꜱ ɪɴ ʜᴇʀᴇ ᴛᴏ ꜱᴇʟʟ"));

        msgNotSellable = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("messages.not_sellable", "&cThose items cannot be sold."));

        msgYouSold = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("messages.you_sold_prefix", "&aYou sold:"));

        payoutMaterial = Material.matchMaterial(cfg.getString("payout-material", "NETHERITE_SCRAP"));
        if (payoutMaterial == null) payoutMaterial = Material.NETHERITE_SCRAP;

        sellPrices = new HashMap<>();

        if (cfg.isConfigurationSection("prices")) {
            for (String key : cfg.getConfigurationSection("prices").getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                int amount = cfg.getInt("prices." + key, 0);

                if (mat != null && amount > 0) {
                    sellPrices.put(mat, amount);
                }
            }
        }

        if (sellPrices.isEmpty()) {
            sellPrices.put(Material.SUGAR_CANE, 64);
            sellPrices.put(Material.BAMBOO, 128);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use this.");
            return true;
        }
        openSellGUI((Player) sender);
        return true;
    }

    private void openSellGUI(Player player) {
        Inventory inv = Bukkit.createInventory(player, 45, guiTitle);

        ItemStack filler = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);

        for (int i = 36; i <= 44; i++) inv.setItem(i, filler);

        ItemStack info = new ItemStack(Material.WHEAT);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.LIGHT_PURPLE + "Sell crops here");
        info.setItemMeta(im);

        inv.setItem(40, info);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(guiTitle)) {
            if (e.getRawSlot() >= 36) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals(guiTitle)) return;

        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();

        int payout = 0;
        Map<Material, Integer> sold = new HashMap<>();
        List<ItemStack> leftovers = new ArrayList<>();

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            int price = sellPrices.getOrDefault(item.getType(), 0);
            if (price > 0) {
                int stacks = item.getAmount() / price;
                if (stacks > 0) {
                    payout += stacks;
                    int consumed = stacks * price;
                    sold.merge(item.getType(), consumed, Integer::sum);

                    int remain = item.getAmount() - consumed;
                    if (remain > 0) {
                        ItemStack r = item.clone();
                        r.setAmount(remain);
                        leftovers.add(r);
                    }
                } else leftovers.add(item.clone());
            } else leftovers.add(item.clone());

            inv.clear(i);
        }

        leftovers.forEach(it -> p.getInventory().addItem(it));

        if (payout > 0) {
            p.getInventory().addItem(new ItemStack(payoutMaterial, payout));
            p.sendMessage(msgYouSold);

            for (Entry<Material, Integer> e2 : sold.entrySet()) {
                p.sendMessage(ChatColor.YELLOW + "- " + e2.getValue() + "x " + prettify(e2.getKey()));
            }
        } else {
            p.sendMessage(msgNotSellable);
        }
    }

    private String prettify(Material m) {
        return m.name().toLowerCase().replace("_", " ");
    }
}
