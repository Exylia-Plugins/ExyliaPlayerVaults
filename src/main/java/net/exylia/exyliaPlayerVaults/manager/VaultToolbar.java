package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.lib.action.ActionContext;
import net.exylia.lib.action.ActionTemplate;
import net.exylia.lib.config.Languages;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.item.Item;
import net.exylia.lib.item.ItemValues;
import net.exylia.lib.item.Items;
import net.exylia.lib.item.PluginItems;
import net.exylia.lib.ui.ClickBindings;
import net.exylia.lib.ui.ClickKind;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The row of buttons under a vault, when {@code vaults.toolbar} is on.
 *
 * <p>Read from {@code menus/user/toolbar.yml}: each button is an ordinary item
 * with menu-style {@code actions} ({@code left:}, {@code right:}...), placed on
 * a column of the row. The buttons are real items in the vault's window, so
 * every one is tagged and stacks to one: a stack from the player's inventory
 * can never merge into one, the listener refuses every click and drag on the
 * row, and {@link #isButton} lets anything that escaped be found and removed.
 * The row is never part of what a vault stores.
 */
public final class VaultToolbar {

    public static final String ID = "menus/user/toolbar";

    /** The button leading to the vault before this one; drawn muted on the first. */
    private static final String PREVIOUS = "previous";
    /** The button leading to the vault after this one; drawn muted on the last unlocked. */
    private static final String NEXT = "next";
    private static final String TAG = "toolbar";

    private final ExyliaPlayerVaults core;
    private final PluginItems items;
    private volatile Map<Integer, Button> buttons = Map.of();
    private volatile Item filler;

    public VaultToolbar(ExyliaPlayerVaults core) {
        this.core = core;
        this.items = Items.of(core.getPlugin());
    }

    private record Button(String id, Item item, Item muted, String sound, ClickBindings bindings) {
    }

    /** Reads the file again; a broken one leaves an empty row of filler, and says why. */
    public void load() {
        File file = Languages.file(core.getPlugin(), ID + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            core.getDebug().error("The vault toolbar could not be read; vaults show an empty row. Fix "
                    + file.getPath() + " and reload.", failure);
        }
        ConfigurationSection fillerSection = config.getConfigurationSection("filler");
        filler = fillerSection == null ? null : items.parse(fillerSection);

        Map<Integer, Button> read = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("buttons");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection button = section.getConfigurationSection(id);
                if (button == null) continue;
                int column = button.getInt("slot", -1);
                if (column < 0 || column > 8) {
                    core.getDebug().warn("Vault toolbar button \"" + id + "\" needs a slot from 0 to 8.");
                    continue;
                }
                ClickBindings.Builder bindings = new ClickBindings.Builder();
                try {
                    for (String line : button.getStringList("actions")) bindings.add(line, core.getActions()::template);
                } catch (RuntimeException broken) {
                    core.getDebug().warn("Vault toolbar button \"" + id + "\" has an action that does not compile: "
                            + broken.getMessage());
                    continue;
                }
                ConfigurationSection muted = button.getConfigurationSection("muted");
                read.put(column, new Button(id, items.parse(button), muted == null ? null : items.parse(muted),
                        button.getString("sound"), bindings.build()));
            }
        }
        buttons = Map.copyOf(read);
    }

    /**
     * Draws the row into the window's last nine slots.
     *
     * @param values what the buttons' names, lore and actions may say
     */
    void draw(Inventory inventory, int first, Player viewer, Map<String, String> values,
              boolean hasPrevious, boolean hasNext) {
        for (int column = 0; column < 9; column++) {
            Button button = buttons.get(column);
            Item item = button == null ? filler : button.item();
            if (button != null && button.muted() != null
                    && (PREVIOUS.equals(button.id()) && !hasPrevious || NEXT.equals(button.id()) && !hasNext)) {
                item = button.muted();
            }
            inventory.setItem(first + column, tag(item == null ? null : items.render(item, viewer, values)));
        }
    }

    /**
     * Runs what a click on a slot of the row does.
     *
     * <p>A muted button does nothing. The window is closed first unless every
     * action only opens another vault, which swaps the window itself: a list,
     * a search or a rename must not leave the vault open behind it.
     */
    public void click(Player player, VaultHolder holder, int rawSlot, ClickType type) {
        Button button = buttons.get(rawSlot - holder.storage());
        ClickKind kind = ClickKind.of(type);
        if (button == null || kind == null || holder.toolbar() == null) return;
        if (PREVIOUS.equals(button.id()) && !holder.toolbar().hasPrevious()
                || NEXT.equals(button.id()) && !holder.toolbar().hasNext()) return;
        List<ActionTemplate> actions = button.bindings().forClick(kind);
        if (actions.isEmpty()) return;
        if (button.sound() != null && !button.sound().isBlank()) Effects.soundFrom(button.sound()).show(player);

        boolean swaps = actions.stream().allMatch(action -> action.raw().trim().startsWith("exyliaplayervaults:open "));
        Map<String, Object> data = new HashMap<String, Object>(holder.toolbar().values());
        core.getTasks().runAtEntity(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) != holder) return;
            if (!swaps) player.closeInventory();
            ActionContext context = ActionContext.forPlayer(player).origin("vault-toolbar").build();
            for (ActionTemplate action : actions) action.resolveOrNoop(player, data).execute(context);
        });
    }

    private ItemStack tag(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = stack.getItemMeta();
            meta.setHideTooltip(true);
            stack.setItemMeta(meta);
        }
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        // One to a stack: nothing from the player's inventory can ever merge into a button.
        meta.setMaxStackSize(1);
        stack.setItemMeta(meta);
        values().set(stack, TAG, true);
        return stack;
    }

    /** Whether this is a toolbar button, wherever it turned up. */
    public boolean isButton(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && values().has(stack, TAG);
    }

    private ItemValues values() {
        return items.values();
    }

    /** What a vault's row was drawn for: the values its actions read, and where its arrows lead. */
    record Drawn(Map<String, String> values, boolean hasPrevious, boolean hasNext) {
    }
}
