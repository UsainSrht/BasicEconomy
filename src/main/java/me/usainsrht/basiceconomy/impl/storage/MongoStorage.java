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
import me.usainsrht.basiceconomy.api.transaction.*;
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
    private MongoCollection<Document> transactionCollection;

    public MongoStorage(ConfigManager config) {
        this.config = config;
    }

    @Override
    public void connect() throws Exception {
        String uri = config.getStorageMongoUri();
        mongoClient = MongoClients.create(uri);
        database = mongoClient.getDatabase(config.getStorageDatabase());
        collection = database.getCollection("balances");
        transactionCollection = database.getCollection("transactions");

        // Ensure indexes
        collection.createIndex(Indexes.ascending("uuid"));
        collection.createIndex(Indexes.ascending("currency"));
        collection.createIndex(Indexes.descending("balance"));
        // Unique index on uuid + currency
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("uuid"), Indexes.ascending("currency")), new IndexOptions().unique(true));

        // Transaction indexes
        transactionCollection.createIndex(Indexes.compoundIndex(Indexes.ascending("sender_uuid"), Indexes.descending("timestamp")));
        transactionCollection.createIndex(Indexes.compoundIndex(Indexes.ascending("receiver_uuid"), Indexes.descending("timestamp")));
        transactionCollection.createIndex(Indexes.descending("timestamp"));
        transactionCollection.createIndex(Indexes.ascending("currency"));
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
                Object balanceObj = doc.get("balance");
                Currency currency = config.getCurrencies().get(currName.toLowerCase());
                if (currency != null && balanceObj != null) {
                    BigDecimal balance;
                    if (balanceObj instanceof org.bson.types.Decimal128) {
                        balance = ((org.bson.types.Decimal128) balanceObj).bigDecimalValue();
                    } else {
                        balance = new BigDecimal(balanceObj.toString());
                    }
                    balances.put(currency, balance);
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

    @Override
    public CompletableFuture<Void> recordTransaction(TransactionRecord record) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document("timestamp", record.timestamp())
                    .append("sender_uuid", record.senderUuid() != null ? record.senderUuid().toString() : null)
                    .append("sender_name", record.senderName())
                    .append("receiver_uuid", record.receiverUuid().toString())
                    .append("receiver_name", record.receiverName())
                    .append("currency", record.currency().toLowerCase())
                    .append("amount", new org.bson.types.Decimal128(record.amount()))
                    .append("type", record.type().name())
                    .append("server", record.server() != null ? record.server() : "default");
            transactionCollection.insertOne(doc);
        });
    }

    private Bson buildMongoFilter(TransactionFilter filter) {
        List<Bson> filters = new ArrayList<>();
        if (filter.playerUuid() != null) {
            String uuidStr = filter.playerUuid().toString();
            TransactionDirection dir = filter.direction() != null ? filter.direction() : TransactionDirection.ALL;
            switch (dir) {
                case SENT -> filters.add(Filters.eq("sender_uuid", uuidStr));
                case RECEIVED -> filters.add(Filters.eq("receiver_uuid", uuidStr));
                case ALL -> filters.add(Filters.or(Filters.eq("sender_uuid", uuidStr), Filters.eq("receiver_uuid", uuidStr)));
            }
        }
        if (filter.currency() != null) {
            filters.add(Filters.eq("currency", filter.currency().toLowerCase()));
        }
        if (filter.sinceMillis() != null) {
            filters.add(Filters.gte("timestamp", filter.sinceMillis()));
        }
        if (filter.untilMillis() != null) {
            filters.add(Filters.lte("timestamp", filter.untilMillis()));
        }
        return filters.isEmpty() ? new Document() : Filters.and(filters);
    }

    @Override
    public CompletableFuture<List<TransactionRecord>> getTransactions(TransactionFilter filter, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TransactionRecord> list = new ArrayList<>();
            Bson query = buildMongoFilter(filter);
            for (Document doc : transactionCollection.find(query)
                    .sort(Indexes.descending("timestamp"))
                    .skip(offset)
                    .limit(limit)) {
                long timestamp = doc.getLong("timestamp");
                String senderStr = doc.getString("sender_uuid");
                UUID senderUuid = senderStr != null ? UUID.fromString(senderStr) : null;
                String senderName = doc.getString("sender_name");
                UUID receiverUuid = UUID.fromString(doc.getString("receiver_uuid"));
                String receiverName = doc.getString("receiver_name");
                String currency = doc.getString("currency");
                Object amountObj = doc.get("amount");
                BigDecimal amount = amountObj instanceof org.bson.types.Decimal128 d ? d.bigDecimalValue() : new BigDecimal(amountObj.toString());
                TransactionType type = TransactionType.fromString(doc.getString("type"));
                String server = doc.getString("server");

                long id = doc.getObjectId("_id") != null ? doc.getObjectId("_id").getTimestamp() : 0L;
                list.add(new TransactionRecord(id, timestamp, senderUuid, senderName, receiverUuid, receiverName, currency, amount, type, server));
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Integer> countTransactions(TransactionFilter filter) {
        return CompletableFuture.supplyAsync(() -> {
            Bson query = buildMongoFilter(filter);
            return (int) transactionCollection.countDocuments(query);
        });
    }

    @Override
    public CompletableFuture<TransactionStats> getTransactionStats(UUID playerUuid, Currency currency, long sinceMillis) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerUuid == null) return TransactionStats.EMPTY;
            String uuidStr = playerUuid.toString();
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.or(Filters.eq("sender_uuid", uuidStr), Filters.eq("receiver_uuid", uuidStr)));
            if (currency != null) {
                filters.add(Filters.eq("currency", currency.name().toLowerCase()));
            }
            if (sinceMillis > 0) {
                filters.add(Filters.gte("timestamp", sinceMillis));
            }

            BigDecimal totalSent = BigDecimal.ZERO;
            int countSent = 0;
            BigDecimal totalRecv = BigDecimal.ZERO;
            int countRecv = 0;

            for (Document doc : transactionCollection.find(Filters.and(filters))) {
                String sender = doc.getString("sender_uuid");
                String receiver = doc.getString("receiver_uuid");
                Object amountObj = doc.get("amount");
                BigDecimal amount = amountObj instanceof org.bson.types.Decimal128 d ? d.bigDecimalValue() : new BigDecimal(amountObj.toString());

                if (uuidStr.equals(sender)) {
                    totalSent = totalSent.add(amount);
                    countSent++;
                }
                if (uuidStr.equals(receiver)) {
                    totalRecv = totalRecv.add(amount);
                    countRecv++;
                }
            }

            return new TransactionStats(totalSent, countSent, totalRecv, countRecv);
        });
    }

    @Override
    public CompletableFuture<Integer> purgeTransactions(long cutoffMillis) {
        return CompletableFuture.supplyAsync(() -> {
            com.mongodb.client.result.DeleteResult result = transactionCollection.deleteMany(Filters.lt("timestamp", cutoffMillis));
            return (int) result.getDeletedCount();
        });
    }
}
