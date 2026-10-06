package hgloot.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import hgloot.plugin.util.HydraIcons;

final class StoreFulfillmentService {
    private static final String PRODUCT_HYDRA_PLUS = "hydra_plus";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HGLootPlugin plugin;

    StoreFulfillmentService(HGLootPlugin plugin) {
        this.plugin = plugin;
    }

    boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hgloot.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage store purchases.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("pending")) {
            sendPending(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "fulfill" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /admin store fulfill <transactionId>");
                    return true;
                }
                fulfill(sender, args[1]);
                return true;
            }
            case "fulfillall" -> {
                fulfillAll(sender);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Usage: /admin store <pending|fulfill <transactionId>|fulfillall>");
                return true;
            }
        }
    }

    private void sendPending(CommandSender sender) {
        List<PurchaseRecord> records = loadRecords();
        List<PurchaseRecord> pending = records.stream()
            .filter(PurchaseRecord::isPendingHydraPlus)
            .toList();
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
            + "Pending Hydra+ purchase(s): " + ChatColor.WHITE + pending.size());
        if (pending.isEmpty()) {
            return;
        }
        for (PurchaseRecord record : pending.stream().limit(10).toList()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + record.username()
                + ChatColor.GRAY + " | " + ChatColor.AQUA + record.transactionId());
        }
        if (pending.size() > 10) {
            sender.sendMessage(ChatColor.DARK_GRAY + "...and " + (pending.size() - 10) + " more.");
        }
    }

    private void fulfill(CommandSender sender, String transactionId) {
        List<PurchaseRecord> records = loadRecords();
        for (PurchaseRecord record : records) {
            if (record.transactionId().equalsIgnoreCase(transactionId)) {
                fulfillRecord(sender, record);
                saveRecords(records);
                return;
            }
        }
        sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "No matching transaction found.");
    }

    private void fulfillAll(CommandSender sender) {
        List<PurchaseRecord> records = loadRecords();
        int fulfilled = 0;
        int skipped = 0;
        for (PurchaseRecord record : records) {
            if (!record.isPendingHydraPlus()) {
                continue;
            }
            if (fulfillRecord(sender, record)) {
                fulfilled++;
            } else {
                skipped++;
            }
        }
        saveRecords(records);
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GRAY
            + "Fulfilled " + ChatColor.WHITE + fulfilled + ChatColor.GRAY
            + " purchase(s). Skipped " + ChatColor.WHITE + skipped + ChatColor.GRAY + ".");
    }

    private boolean fulfillRecord(CommandSender sender, PurchaseRecord record) {
        if (!record.isPendingHydraPlus()) {
            sender.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + "Transaction is not pending Hydra+.");
            return false;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(record.username());
        UUID targetId = target.getUniqueId();
        record.incrementAttempts();
        record.updatedAt(Instant.now().toString());

        if (plugin.hasHydraPlusRank(targetId)) {
            record.status("already_owned");
            sender.sendMessage(HydraIcons.EXCLAMATION_ORANGE + " " + ChatColor.YELLOW + record.username()
                + " already has Hydra+. Marked transaction as already owned.");
            return false;
        }

        boolean ok = plugin.applyHydraRank(sender, targetId, record.username(), "tier1");
        if (!ok) {
            sender.sendMessage(HydraIcons.EXCLAMATION_RED + " " + ChatColor.RED + "Could not fulfill " + record.transactionId() + ".");
            return false;
        }
        record.status("fulfilled");
        plugin.getLogger().info("[Hydra Store] Fulfilled Hydra+ transaction "
            + record.transactionId() + " for " + record.username() + ".");
        sender.sendMessage(HydraIcons.EXCLAMATION + " " + ChatColor.GREEN
            + "Fulfilled Hydra+ for " + ChatColor.WHITE + record.username() + ChatColor.GREEN + ".");
        return true;
    }

    private List<PurchaseRecord> loadRecords() {
        Path path = storeFile();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonArray()) {
                return new ArrayList<>();
            }
            List<PurchaseRecord> records = new ArrayList<>();
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    records.add(PurchaseRecord.from(element.getAsJsonObject()));
                }
            }
            return records;
        } catch (Exception ex) {
            plugin.getLogger().warning("[Hydra Store] Could not read pending purchases: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveRecords(List<PurchaseRecord> records) {
        Path path = storeFile();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonArray array = new JsonArray();
            for (PurchaseRecord record : records) {
                array.add(record.toJson());
            }
            Files.writeString(path, GSON.toJson(array) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Hydra Store] Could not save pending purchases: " + ex.getMessage());
        }
    }

    private Path storeFile() {
        String configured = plugin.getConfig().getString(
            "store.pending_purchases_file",
            plugin.getDataFolder().toPath().resolve("pending-purchases.json").toString()
        );
        return Path.of(configured);
    }

    private static final class PurchaseRecord {
        private final String transactionId;
        private final String username;
        private final String product;
        private String status;
        private final String createdAt;
        private String updatedAt;
        private int fulfillmentAttempts;

        private PurchaseRecord(
            String transactionId,
            String username,
            String product,
            String status,
            String createdAt,
            String updatedAt,
            int fulfillmentAttempts
        ) {
            this.transactionId = transactionId;
            this.username = username;
            this.product = product;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.fulfillmentAttempts = fulfillmentAttempts;
        }

        static PurchaseRecord from(JsonObject object) {
            String now = Instant.now().toString();
            return new PurchaseRecord(
                stringValue(object, "transactionId"),
                stringValue(object, "username"),
                stringValue(object, "product"),
                stringValue(object, "status", "pending"),
                stringValue(object, "createdAt", now),
                stringValue(object, "updatedAt", now),
                intValue(object, "fulfillmentAttempts")
            );
        }

        boolean isPendingHydraPlus() {
            return PRODUCT_HYDRA_PLUS.equalsIgnoreCase(product)
                && "pending".equalsIgnoreCase(status)
                && !transactionId.isBlank()
                && !username.isBlank();
        }

        String transactionId() {
            return transactionId;
        }

        String username() {
            return username;
        }

        void status(String status) {
            this.status = status;
        }

        void updatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        void incrementAttempts() {
            fulfillmentAttempts++;
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("transactionId", transactionId);
            object.addProperty("username", username);
            object.addProperty("product", product);
            object.addProperty("status", status);
            object.addProperty("createdAt", createdAt);
            object.addProperty("updatedAt", updatedAt);
            object.addProperty("fulfillmentAttempts", fulfillmentAttempts);
            return object;
        }

        private static String stringValue(JsonObject object, String key) {
            return stringValue(object, key, "");
        }

        private static String stringValue(JsonObject object, String key, String fallback) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive()) {
                return fallback;
            }
            return value.getAsString();
        }

        private static int intValue(JsonObject object, String key) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive()) {
                return 0;
            }
            try {
                return value.getAsInt();
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }
}

