package me.usainsrht.basiceconomy.impl.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MongoStorage implements Storage {

    private final ConfigManager config;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    public MongoStorage(ConfigManager config) {
        this.config = config;
    }

    @Override
    public void connect() throws Exception {
        String uri = config.getStorageMongoUri();
        mongoClient = MongoClients.create(uri);
        database = mongoClient.getDatabase(config.getStorageDatabase());
        collection = database.getCollection("balances");

        // Ensure indexes
        collection.createIndex(Indexes.ascending("uuid"));
        collection.createIndex(Indexes.ascending("currency"));
        collection.createIndex(Indexes.descending("balance"));
        // Unique index on uuid + currency
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("uuid"), Indexes.ascending("currency")), new IndexOptions().unique(true));
    }

    @Override
    public void disconnect() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Override
    public CompletableFuture<Map<Currency, BigDecimal>> loadBalances(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Currency, BigDecimal> balances = new HashMap<>();
            Bson filter = Filters.eq("uuid", uuid.toString());
            for (Document doc : collection.find(filter)) {
                String currName = doc.getString("currency");
                String balanceStr = doc.getString("balance");
                Currency currency = config.getCurrencies().get(currName);
                if (currency != null && balanceStr != null) {
                    balances.put(currency, new BigDecimal(balanceStr));
                }
            }
            return balances;
        });
    }

    @Override
    public CompletableFuture<Void> saveBalance(UUID uuid, Currency currency, BigDecimal amount) {
        return CompletableFuture.runAsync(() -> {
            Document query = new Document("uuid", uuid.toString())
                    .append("currency", currency.name().toLowerCase());
            
            Document document = new Document("uuid", uuid.toString())
                    .append("currency", currency.name().toLowerCase())
                    .append("balance", new org.bson.types.Decimal128(amount));

            collection.replaceOne(query, document, new ReplaceOptions().upsert(true));
        });
    }

    @Override
    public CompletableFuture<List<Map.Entry<UUID, BigDecimal>>> getTopBalances(Currency currency, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map.Entry<UUID, BigDecimal>> top = new ArrayList<>();
            Bson filter = Filters.eq("currency", currency.name().toLowerCase());
            // Sort by balance descending. Note: if balance is stored as a string, string sorting might fail for numbers
            // We should use MongoDB Decimal128 for actual sorting, but we'll convert string to BigDecimal.
            // Wait, if it's stored as string, it won't sort properly! 
            // So we need to fetch all and sort in memory, or use Decimal128. Let's use Decimal128!
            
            // Re-evaluating saving mechanism to use org.bson.types.Decimal128 for correct database sorting
            for (Document doc : collection.find(filter).sort(Indexes.descending("balance")).limit(limit)) {
                String uuidStr = doc.getString("uuid");
                // Fallback to string reading if it was saved as string earlier
                Object balanceObj = doc.get("balance");
                BigDecimal balance;
                if (balanceObj instanceof org.bson.types.Decimal128) {
                    balance = ((org.bson.types.Decimal128) balanceObj).bigDecimalValue();
                } else {
                    balance = new BigDecimal(balanceObj.toString());
                }
                top.add(new AbstractMap.SimpleEntry<>(UUID.fromString(uuidStr), balance));
            }
            return top;
        });
    }
}
