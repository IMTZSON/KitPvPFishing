package it.kitpvpfishing.testutil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Implementazioni minime (tramite {@link Proxy}) delle interfacce Bukkit usate dal plugin,
 * per testare la logica senza un server Minecraft.
 */
public final class Fakes {

    private static FakeServer server;

    private Fakes() {
    }

    // ------------------------------------------------------------------ infrastruttura proxy

    public interface Behavior {
        /** Restituisce {@link #UNHANDLED} per usare il valore predefinito. */
        Object invoke(String method, Object[] args) throws Throwable;
    }

    public static final Object UNHANDLED = new Object();

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, final Behavior behavior) {
        return (T) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[]{type}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object[] safeArgs = args == null ? new Object[0] : args;
                String name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    if (name.equals("equals")) {
                        return proxy == safeArgs[0];
                    }
                    if (name.equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (name.equals("toString")) {
                        return "Fake" + method.getDeclaringClass().getSimpleName() + "@" + System.identityHashCode(proxy);
                    }
                }
                Object result = behavior.invoke(name, safeArgs);
                if (result != UNHANDLED) {
                    return result;
                }
                return defaultValue(method.getReturnType());
            }
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    // ------------------------------------------------------------------ server

    /** Installa (una sola volta per JVM) un server finto con ItemFactory e dispatchCommand. */
    public static synchronized FakeServer server() {
        if (server == null) {
            server = new FakeServer();
            Bukkit.setServer(server.proxy);
        }
        server.dispatched.clear();
        server.unknownCommands.clear();
        return server;
    }

    public static final class FakeServer {
        public final List<String> dispatched = new ArrayList<String>();
        /** Comandi (radice) che il server finto dichiara inesistenti. */
        public final List<String> unknownCommands = new ArrayList<String>();
        public final Server proxy;
        private final ConsoleCommandSender console = proxy(ConsoleCommandSender.class, new Behavior() {
            @Override
            public Object invoke(String method, Object[] args) {
                if (method.equals("getName")) {
                    return "CONSOLE";
                }
                return UNHANDLED;
            }
        });
        private final ItemFactory itemFactory = proxy(ItemFactory.class, new Behavior() {
            @Override
            public Object invoke(String method, Object[] args) {
                if (method.equals("getItemMeta")) {
                    return newMeta(new MetaState());
                }
                if (method.equals("isApplicable")) {
                    return true;
                }
                if (method.equals("asMetaFor")) {
                    return args[0];
                }
                if (method.equals("equals") && args.length == 2) {
                    MetaState a = args[0] == null ? new MetaState() : state((ItemMeta) args[0]);
                    MetaState b = args[1] == null ? new MetaState() : state((ItemMeta) args[1]);
                    return a.sameAs(b);
                }
                return UNHANDLED;
            }
        });

        private FakeServer() {
            final Logger logger = Logger.getLogger("FakeServer");
            proxy = proxy(Server.class, new Behavior() {
                @Override
                public Object invoke(String method, Object[] args) {
                    if (method.equals("getLogger")) {
                        return logger;
                    }
                    if (method.equals("getName") || method.equals("getVersion") || method.equals("getBukkitVersion")) {
                        return "fake";
                    }
                    if (method.equals("getItemFactory")) {
                        return itemFactory;
                    }
                    if (method.equals("getConsoleSender")) {
                        return console;
                    }
                    if (method.equals("dispatchCommand")) {
                        String command = (String) args[1];
                        dispatched.add(command);
                        return !unknownCommands.contains(command.split(" ")[0]);
                    }
                    return UNHANDLED;
                }
            });
        }
    }

    // ------------------------------------------------------------------ ItemMeta

    public static final class MetaState {
        public String displayName;
        public List<String> lore;
        public final Map<Enchantment, Integer> enchants = new LinkedHashMap<Enchantment, Integer>();
        public final Set<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);

        MetaState copy() {
            MetaState copy = new MetaState();
            copy.displayName = displayName;
            copy.lore = lore == null ? null : new ArrayList<String>(lore);
            copy.enchants.putAll(enchants);
            copy.flags.addAll(flags);
            return copy;
        }

        boolean isEmpty() {
            return displayName == null && lore == null && enchants.isEmpty() && flags.isEmpty();
        }

        boolean sameAs(MetaState other) {
            if (isEmpty() && other.isEmpty()) {
                return true;
            }
            return eq(displayName, other.displayName) && eq(lore, other.lore)
                    && enchants.equals(other.enchants) && flags.equals(other.flags);
        }

        private static boolean eq(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    private static final Map<ItemMeta, MetaState> META_STATES = new java.util.IdentityHashMap<ItemMeta, MetaState>();

    public static synchronized MetaState state(ItemMeta meta) {
        return META_STATES.get(meta);
    }

    private static synchronized ItemMeta newMeta(final MetaState state) {
        ItemMeta meta = proxy(ItemMeta.class, new Behavior() {
            @Override
            @SuppressWarnings("unchecked")
            public Object invoke(String method, Object[] args) {
                if (method.equals("setDisplayName")) {
                    state.displayName = (String) args[0];
                    return null;
                }
                if (method.equals("getDisplayName")) {
                    return state.displayName;
                }
                if (method.equals("hasDisplayName")) {
                    return state.displayName != null;
                }
                if (method.equals("setLore")) {
                    state.lore = args[0] == null ? null : new ArrayList<String>((List<String>) args[0]);
                    return null;
                }
                if (method.equals("getLore")) {
                    return state.lore == null ? null : new ArrayList<String>(state.lore);
                }
                if (method.equals("hasLore")) {
                    return state.lore != null;
                }
                if (method.equals("addEnchant")) {
                    boolean ignoreLimits = (Boolean) args[2];
                    if (!ignoreLimits) {
                        throw new AssertionError("Il plugin deve ignorare i limiti vanilla degli incantesimi");
                    }
                    state.enchants.put((Enchantment) args[0], (Integer) args[1]);
                    return true;
                }
                if (method.equals("getEnchants")) {
                    return new HashMap<Enchantment, Integer>(state.enchants);
                }
                if (method.equals("getEnchantLevel")) {
                    Integer level = state.enchants.get(args[0]);
                    return level == null ? 0 : level;
                }
                if (method.equals("hasEnchants")) {
                    return !state.enchants.isEmpty();
                }
                if (method.equals("addItemFlags")) {
                    for (ItemFlag flag : (ItemFlag[]) args[0]) {
                        state.flags.add(flag);
                    }
                    return null;
                }
                if (method.equals("hasItemFlag")) {
                    return state.flags.contains(args[0]);
                }
                if (method.equals("clone")) {
                    return newMeta(state.copy());
                }
                return UNHANDLED;
            }
        });
        META_STATES.put(meta, state);
        return meta;
    }

    // ------------------------------------------------------------------ mondo, giocatore, oggetto pescato

    public static final class FakeWorld {
        public final String name;
        public final List<ItemStack> dropped = new ArrayList<ItemStack>();
        public final World proxy;

        public FakeWorld(String name) {
            this.name = name;
            this.proxy = proxy(World.class, new Behavior() {
                @Override
                public Object invoke(String method, Object[] args) {
                    if (method.equals("getName")) {
                        return FakeWorld.this.name;
                    }
                    if (method.equals("dropItem") || method.equals("dropItemNaturally")) {
                        dropped.add(((ItemStack) args[1]).clone());
                        return null;
                    }
                    return UNHANDLED;
                }
            });
        }
    }

    public static final class FakePlayer {
        public final String name;
        public final UUID uuid = UUID.randomUUID();
        public final FakeWorld world;
        public final List<String> messages = new ArrayList<String>();
        /** Oggetti effettivamente entrati nell'inventario. */
        public final List<ItemStack> inventory = new ArrayList<ItemStack>();
        /** Se true, addItem non accetta nulla e restituisce tutto come resto. */
        public boolean inventoryFull;
        public final Player proxy;

        public FakePlayer(String name, FakeWorld world) {
            this.name = name;
            this.world = world;
            final PlayerInventory playerInventory = proxy(PlayerInventory.class, new Behavior() {
                @Override
                public Object invoke(String method, Object[] args) {
                    if (method.equals("addItem")) {
                        HashMap<Integer, ItemStack> leftovers = new HashMap<Integer, ItemStack>();
                        ItemStack[] items = (ItemStack[]) args[0];
                        for (int i = 0; i < items.length; i++) {
                            if (inventoryFull) {
                                leftovers.put(i, items[i]);
                            } else {
                                inventory.add(items[i].clone());
                            }
                        }
                        return leftovers;
                    }
                    return UNHANDLED;
                }
            });
            final Location location = new Location(world.proxy, 10.5, 64, -3.5);
            this.proxy = proxy(Player.class, new Behavior() {
                @Override
                public Object invoke(String method, Object[] args) {
                    if (method.equals("getName")) {
                        return FakePlayer.this.name;
                    }
                    if (method.equals("getUniqueId")) {
                        return uuid;
                    }
                    if (method.equals("getWorld")) {
                        return FakePlayer.this.world.proxy;
                    }
                    if (method.equals("getLocation")) {
                        return location.clone();
                    }
                    if (method.equals("getInventory")) {
                        return playerInventory;
                    }
                    if (method.equals("sendMessage") && args[0] instanceof String) {
                        messages.add((String) args[0]);
                        return null;
                    }
                    if (method.equals("hasPermission")) {
                        return true;
                    }
                    return UNHANDLED;
                }
            });
        }
    }

    /** Entita' dell'oggetto pescato, come la crea il server prima di chiamare l'evento. */
    public static final class FakeCaughtItem {
        public ItemStack stack;
        public int removeCalls;
        public int setStackCalls;
        public final Item proxy;

        public FakeCaughtItem(ItemStack original) {
            this.stack = original;
            this.proxy = proxy(Item.class, new Behavior() {
                @Override
                public Object invoke(String method, Object[] args) {
                    if (method.equals("getItemStack")) {
                        return stack;
                    }
                    if (method.equals("setItemStack")) {
                        stack = (ItemStack) args[0];
                        setStackCalls++;
                        return null;
                    }
                    if (method.equals("remove")) {
                        removeCalls++;
                        return null;
                    }
                    if (method.equals("isDead")) {
                        return removeCalls > 0;
                    }
                    if (method.equals("getType")) {
                        return EntityType.DROPPED_ITEM;
                    }
                    return UNHANDLED;
                }
            });
        }

        public boolean isRemoved() {
            return removeCalls > 0;
        }
    }

    public static ItemStack vanillaLoot() {
        return new ItemStack(Material.BOW, 1);
    }

    // ------------------------------------------------------------------ log

    public static final class CapturingLogger {
        public final List<LogRecord> records = new ArrayList<LogRecord>();
        public final Logger logger;

        public CapturingLogger() {
            logger = Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }

        public int count(Level level) {
            int count = 0;
            for (LogRecord record : records) {
                if (record.getLevel().equals(level)) {
                    count++;
                }
            }
            return count;
        }

        public boolean contains(Level level, String text) {
            for (LogRecord record : records) {
                if (record.getLevel().equals(level) && record.getMessage().contains(text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
