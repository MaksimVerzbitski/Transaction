package com.playtech.assignment;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

//import java.math.BigInteger;
// Tried to use BiGInteger => without success for CC and DC in order to CREDIT cards could withdraw and get negative balance => then read md => and realised no need for that


// This template shows input parameters format.
// It is otherwise not mandatory to use, you can write everything from scratch if you wish.
public class TransactionProcessorSample {
    // Global variables to track IBANs used for deposits and account number usage
    static Map<String, Set<String>> userIbans = new HashMap<>();
    static Map<String, String> accountUsers = new HashMap<>();

    public static void main(final String[] args) throws IOException {
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        TreeMap<Long, List<BinMapping>> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));

        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings);

        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
    }



    private static List<User> readUsers(final Path filePath) {
        List<User> users = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            boolean isFirstLine = true; // Flag to check if it's the first line (header)

            while ((line = br.readLine()) != null) {
                // Skip  header
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] attributes = line.split(",");
                User user = createUser(attributes);
                users.add(user);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return users;
    }

    private static User createUser(String[] metadata) {
        String userId = metadata[0];
        String username = metadata[1];
        double balance = Double.parseDouble(metadata[2]);
        String country = metadata[3];
        boolean frozen = Boolean.parseBoolean(metadata[4]);
        double depositMin = Double.parseDouble(metadata[5]);
        double depositMax = Double.parseDouble(metadata[6]);
        double withdrawMin = Double.parseDouble(metadata[7]);
        double withdrawMax = Double.parseDouble(metadata[8]);
        return new User(userId, username, balance, country, frozen, depositMin, depositMax, withdrawMin, withdrawMax);
    }

    private static List<Transaction> readTransactions(final Path filePath) {
        List<Transaction> transactions = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            boolean isFirstLine = true; // Flag to check if it's the first line (header)

            while ((line = br.readLine()) != null) {
                // Skip  header
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] attributes = line.split(",");
                Transaction transaction = createTransaction(attributes);
                transactions.add(transaction);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return transactions;
    }

    private static Transaction createTransaction(String[] metadata) {
        String transactionId = metadata[0];
        String userId = metadata[1];
        String type = metadata[2];
        double amount = Double.parseDouble(metadata[3]);
        String method = metadata[4];
        String accountNumber = metadata[5];
        return new Transaction(transactionId, userId, type, amount, method, accountNumber);
    }

    private static TreeMap<Long, List<BinMapping>> readBinMappings(final Path filePath) {
        TreeMap<Long, List<BinMapping>> binMappings = new TreeMap<>();
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] attributes = line.split(",");
                BinMapping binMapping = createBinMapping(attributes);
                binMappings.computeIfAbsent(binMapping.getRangeFrom(), k -> new ArrayList<>()).add(binMapping);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return binMappings;
    }

    private static BinMapping createBinMapping(String[] metadata) {
        String name = metadata[0];
        long rangeFrom = Long.parseLong(metadata[1]);
        long rangeTo = Long.parseLong(metadata[2]);
        String type = metadata[3];
        String country = metadata[4];
        return new BinMapping(name, rangeFrom, rangeTo, type, country);
    }




    private static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, TreeMap<Long, List<BinMapping>> binMappings) {
        List<Event> events = new ArrayList<>();
        Map<String, User> usersMap = users.stream().collect(Collectors.toMap(User::getUserId, user -> user));
        Set<String> processedTransactionIds = new HashSet<>();

        for (Transaction transaction : transactions) {
            // Check for previously processed transaction IDs
            if (!processedTransactionIds.add(transaction.getTransactionId())) {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "Transaction ID already processed"));
                continue;
            }

            // Check for account number usage by other users
            if (accountUsers.containsKey(transaction.getAccountNumber()) && !accountUsers.get(transaction.getAccountNumber()).equals(transaction.getUserId())) {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "Account " + transaction.getAccountNumber() + " is in use by other user"));
                continue;
            } else {
                accountUsers.put(transaction.getAccountNumber(), transaction.getUserId());
            }

            User user = usersMap.get(transaction.getUserId());
            if (user == null || user.isFrozen()) {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "User account is frozen or does not exist"));
                continue;
            }

            switch (transaction.getMethod()) {
                case "CARD":
                    processCardTransaction(transaction, user, binMappings, events);
                    break;
                case "TRANSFER":
                    processTransferTransaction(transaction, user, events);
                    break;
                default:
                    events.add(new Event(transaction.getTransactionId(), "DECLINED", "Invalid transaction method"));
            }
        }
        return events;
    }

    private static void processCardTransaction(Transaction transaction, User user, TreeMap<Long, List<BinMapping>> binMappings, List<Event> events) {

        long cardPrefix = Long.parseLong(transaction.getAccountNumber().substring(0, 10)); // Use first 10 digits
        boolean isValid = false;

        for (List<BinMapping> mappings : binMappings.values()) {
            for (BinMapping mapping : mappings) {
                if (cardPrefix >= mapping.getRangeFrom() && cardPrefix <= mapping.getRangeTo()) {
                    if ("DC".equals(mapping.getType())) {
                        // Convert user's two-letter country code to three-letter code for comparison
                        String userCountryISO3 = new Locale("", user.getCountry()).getISO3Country();
                        if (userCountryISO3.equals(mapping.getCountry())) {
                            isValid = true;
                            break;
                        }
                    }
                }
            }
            if (isValid) break;
        }

        if (isValid) {
            processTransactionApproval(transaction, user, events);
        } else {
            events.add(new Event(transaction.getTransactionId(), "DECLINED", "Invalid card BIN or card type"));
        }
    }

    private static void processTransferTransaction(Transaction transaction, User user, List<Event> events) {
        // Track IBANs for deposits
        if ("DEPOSIT".equals(transaction.getType())) {
            userIbans.computeIfAbsent(user.getUserId(), k -> new HashSet<>()).add(transaction.getAccountNumber());
        }

        // Check for withdrawals using only previously used IBANs
        if ("WITHDRAW".equals(transaction.getType())) {
            if (!userIbans.getOrDefault(user.getUserId(), Collections.emptySet()).contains(transaction.getAccountNumber())) {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "Cannot withdraw with a new IBAN " + transaction.getAccountNumber()));
                return;
            }
        }

        // Validate IBAN for transfers
        if (isValidIBAN(transaction.getAccountNumber())) {
            String ibanCountryCode = transaction.getAccountNumber().substring(0, 2);
            if (!ibanCountryCode.equals(user.getCountry())) {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "Invalid account country for IBAN: " + ibanCountryCode + ", expected: " + user.getCountry()));
                return;
            }
            processTransactionApproval(transaction, user, events);
        } else {
            events.add(new Event(transaction.getTransactionId(), "DECLINED", "Invalid IBAN for transfer"));
        }
    }

    private static void processTransactionApproval(Transaction transaction, User user, List<Event> events) {
        if ("DEPOSIT".equals(transaction.getType())) {
            if (transaction.getAmount() >= user.getDepositMin() && transaction.getAmount() <= user.getDepositMax()) {
                user.setBalance(user.getBalance() + transaction.getAmount());
                events.add(new Event(transaction.getTransactionId(), "APPROVED", "OK"));
            } else {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "Deposit amount out of allowed range"));
            }
        } else if ("WITHDRAW".equals(transaction.getType())) {
            if (transaction.getAmount() >= user.getWithdrawMin() && transaction.getAmount() <= user.getWithdrawMax()) {
                if (transaction.getAmount() <= user.getBalance()) {
                    user.setBalance(user.getBalance() - transaction.getAmount());
                    events.add(new Event(transaction.getTransactionId(), "APPROVED", "OK"));
                } else {
                    events.add(new Event(transaction.getTransactionId(), "DECLINED", "Withdrawal exceeds balance"));
                }
            } else {
                events.add(new Event(transaction.getTransactionId(), "DECLINED", "Withdrawal amount out of allowed range"));
            }
        }
    }



    private static void writeBalances(final Path filePath, final List<User> users) throws IOException {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("userId,balance\n");
            for (final User user : users) {
                writer.append(String.valueOf(user.getUserId()))
                        .append(",").append(String.valueOf(user.getBalance()))
                        .append("\n");
            }
        }
    }

    private static void writeEvents(Path filePath, List<Event> events) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("transaction_id,status,message\n");
            for (Event event : events) {
                writer.append(event.getTransactionId())
                        .append(",").append(event.getStatus())
                        .append(",").append(event.getMessage())
                        .append("\n");
            }
        }
    }

    public static boolean isValidIBAN(String iban) {
        Map<String, String> ibanPatterns = new HashMap<>();

        // Inserting the full list of IBAN regex patterns
        ibanPatterns.put("AL", "^AL\\d{10}[0-9A-Z]{16}$");
        ibanPatterns.put("AD", "^AD\\d{10}[0-9A-Z]{12}$");
        ibanPatterns.put("AT", "^AT\\d{18}$");
        ibanPatterns.put("AZ", "^AZ\\d{2}[A-Z]{4}[0-9A-Z]{20}$");
        ibanPatterns.put("BH", "^BH\\d{2}[A-Z]{4}[0-9A-Z]{14}$");
        ibanPatterns.put("BE", "^BE\\d{14}$");
        ibanPatterns.put("BA", "^BA\\d{18}$");
        ibanPatterns.put("BR", "^BR\\d{25}[A-Z][0-9A-Z]$");
        ibanPatterns.put("BG", "^BG\\d{2}[A-Z]{4}\\d{6}[0-9A-Z]{8}$");
        ibanPatterns.put("CR", "^CR\\d{20}$");
        ibanPatterns.put("HR", "^HR\\d{19}$");
        ibanPatterns.put("CY", "^CY\\d{10}[0-9A-Z]{16}$");
        ibanPatterns.put("CZ", "^CZ\\d{22}$");
        ibanPatterns.put("FO", "^FO\\d{16}$");
        ibanPatterns.put("GL", "^GL\\d{16}$");
        ibanPatterns.put("DK", "^DK\\d{16}$");
        ibanPatterns.put("DO", "^DO\\d{2}[0-9A-Z]{4}\\d{20}$");
        ibanPatterns.put("EE", "^EE\\d{18}$");
        ibanPatterns.put("EG", "^EG\\d{27}$");
        ibanPatterns.put("FI", "^FI\\d{16}$");
        ibanPatterns.put("FR", "^FR\\d{12}[0-9A-Z]{11}\\d{2}$");
        ibanPatterns.put("GE", "^GE\\d{2}[A-Z]{2}\\d{16}$");
        ibanPatterns.put("DE", "^DE\\d{20}$");
        ibanPatterns.put("GI", "^GI\\d{2}[A-Z]{4}[0-9A-Z]{15}$");
        ibanPatterns.put("GR", "^GR\\d{9}[0-9A-Z]{16}$");
        ibanPatterns.put("GT", "^GT\\d{2}[0-9A-Z]{24}$");
        ibanPatterns.put("HU", "^HU\\d{26}$");
        ibanPatterns.put("IS", "^IS\\d{24}$");
        ibanPatterns.put("IE", "^IE\\d{2}[A-Z]{4}\\d{14}$");
        ibanPatterns.put("IL", "^IL\\d{21}$");
        ibanPatterns.put("IT", "^IT\\d{2}[A-Z]\\d{10}[0-9A-Z]{12}$");
        ibanPatterns.put("JO", "^JO\\d{2}[A-Z]{4}\\d{4}[0-9A-Z]{18}$");
        ibanPatterns.put("KZ", "^KZ\\d{5}[0-9A-Z]{13}$");
        ibanPatterns.put("XK", "^XK\\d{18}$");
        ibanPatterns.put("KW", "^KW\\d{2}[A-Z]{4}[0-9A-Z]{22}$");
        ibanPatterns.put("LV", "^LV\\d{2}[A-Z]{4}[0-9A-Z]{13}$");
        ibanPatterns.put("LB", "^LB\\d{6}[0-9A-Z]{20}$");
        ibanPatterns.put("LI", "^LI\\d{7}[0-9A-Z]{12}$");
        ibanPatterns.put("LT", "^LT\\d{18}$");
        ibanPatterns.put("LU", "^LU\\d{5}[0-9A-Z]{13}$");
        ibanPatterns.put("MK", "^MK\\d{5}[0-9A-Z]{10}\\d{2}$");
        ibanPatterns.put("MT", "^MT\\d{2}[A-Z]{4}\\d{5}[0-9A-Z]{18}$");
        ibanPatterns.put("MR", "^MR\\d{25}$");
        ibanPatterns.put("MU", "^MU\\d{2}[A-Z]{4}\\d{19}[A-Z]{3}$");
        ibanPatterns.put("MD", "^MD\\d{2}[0-9A-Z]{20}$");
        ibanPatterns.put("MC", "^MC\\d{12}[0-9A-Z]{11}\\d{2}$");
        ibanPatterns.put("ME", "^ME\\d{20}$");
        ibanPatterns.put("NL", "^NL\\d{2}[A-Z]{4}\\d{10}$");
        ibanPatterns.put("NO", "^NO\\d{13}$");
        ibanPatterns.put("PK", "^PK\\d{2}[A-Z]{4}[0-9A-Z]{16}$");
        ibanPatterns.put("PS", "^PS\\d{2}[A-Z]{4}[0-9A-Z]{21}$");
        ibanPatterns.put("PL", "^PL\\d{26}$");
        ibanPatterns.put("PT", "^PT\\d{23}$");
        ibanPatterns.put("QA", "^QA\\d{2}[A-Z]{4}[0-9A-Z]{21}$");
        ibanPatterns.put("RO", "^RO\\d{2}[A-Z]{4}[0-9A-Z]{16}$");
        ibanPatterns.put("SM", "^SM\\d{2}[A-Z]\\d{10}[0-9A-Z]{12}$");
        ibanPatterns.put("LC", "^LC\\d{2}[A-Z]{4}[0-9A-Z]{24}$");
        ibanPatterns.put("ST", "^ST\\d{23}$");
        ibanPatterns.put("SA", "^SA\\d{4}[0-9A-Z]{18}$");
        ibanPatterns.put("RS", "^RS\\d{20}$");
        ibanPatterns.put("SK", "^SK\\d{22}$");
        ibanPatterns.put("SI", "^SI\\d{17}$");
        ibanPatterns.put("ES", "^ES\\d{22}$");
        ibanPatterns.put("SE", "^SE\\d{22}$");
        ibanPatterns.put("CH", "^CH\\d{7}[0-9A-Z]{12}$");
        ibanPatterns.put("TL", "^TL\\d{21}$");
        ibanPatterns.put("TN", "^TN\\d{22}$");
        ibanPatterns.put("TR", "^TR\\d{7}[0-9A-Z]{17}$");
        ibanPatterns.put("AE", "^AE\\d{21}$");
        ibanPatterns.put("GB", "^GB\\d{2}[A-Z]{4}\\d{14}$");
        ibanPatterns.put("VA", "^VA\\d{20}$");
        ibanPatterns.put("VG", "^VG\\d{2}[A-Z]{4}\\d{16}$");
        ibanPatterns.put("UA", "^UA\\d{8}[0-9A-Z]{19}$");
        ibanPatterns.put("SC", "^SC\\d{2}[A-Z]{4}\\d{20}[A-Z]{3}$");
        ibanPatterns.put("IQ", "^IQ\\d{2}[A-Z]{4}[0-9A-Z]{15}$");
        ibanPatterns.put("BY", "^BY\\d{2}[A-Z]{4}[0-9A-Z]{20}$");
        ibanPatterns.put("SV", "^SV\\d{2}[A-Z]{4}[0-9A-Z]{20}$");
        ibanPatterns.put("LY", "^LY\\d{23}$");
        ibanPatterns.put("SD", "^SD\\d{16}$");
        ibanPatterns.put("BI", "^BI\\d{2}[0-9A-Z]{10}[0-9A-Z]{12}$");
        ibanPatterns.put("DJ", "^DJ\\d{25}$");
        ibanPatterns.put("RU", "^RU\\d{2}[0-9A-Z]{20}[0-9A-Z]{9}$");

        // Validate IBAN format
        if (iban.length() < 2) return false; // IBAN too short to contain a valid country code
        String countryCode = iban.substring(0, 2);
        String regexPattern = ibanPatterns.get(countryCode);
        return regexPattern != null && iban.matches(regexPattern);
    }
}


class User {
    private String userId;
    private String username;
    private double balance;
    private String country;
    private boolean frozen;
    private double depositMin;
    private double depositMax;
    private double withdrawMin;
    private double withdrawMax;

    // Constructor
    public User(
        String userId,
        String username,
        double balance,
        String country,
        boolean frozen,
        double depositMin,
        double depositMax,
        double withdrawMin,
        double withdrawMax) {

        this.userId = userId;
        this.username = username;
        this.balance = balance;
        this.country = country;
        this.frozen = frozen;
        this.depositMin = depositMin;
        this.depositMax = depositMax;
        this.withdrawMin = withdrawMin;
        this.withdrawMax = withdrawMax;
    }
    // Getters and setters for each field
    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public double getBalance() {
        return balance;
    }

    public String getCountry() {
        return country;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public double getDepositMin() {
        return depositMin;
    }

    public double getDepositMax() {
        return depositMax;
    }

    public double getWithdrawMin() {
        return withdrawMin;
    }

    public double getWithdrawMax() {
        return withdrawMax;
    }

    // Setters
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void setDepositMin(double depositMin) {
        this.depositMin = depositMin;
    }

    public void setDepositMax(double depositMax) {
        this.depositMax = depositMax;
    }

    public void setWithdrawMin(double withdrawMin) {
        this.withdrawMin = withdrawMin;
    }

    public void setWithdrawMax(double withdrawMax) {
        this.withdrawMax = withdrawMax;
    }
}

class Transaction {
    private String transactionId;
    private String userId;
    private String type;
    private double amount;
    private String method;
    private String accountNumber;

    // Adjusted constructor
    public Transaction(
        String transactionId,
        String userId,
        String type,
        double amount,
        String method,
        String accountNumber) {

        this.transactionId = transactionId;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.method = method;
        this.accountNumber = accountNumber;
    }

    // Getters
    public String getTransactionId() {
        return transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public String getMethod() {
        return method;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    // Setters
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}

class BinMapping {
    private String name;
    private long rangeFrom;
    private long rangeTo;
    private String type;
    private String country;

    // Constructor
    public BinMapping(
        String name,
        long rangeFrom,
        long rangeTo,
        String type,
        String country) {

        this.name = name;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.type = type;
        this.country = country;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public long getRangeFrom() {
        return rangeFrom;
    }

    public long getRangeTo() {
        return rangeTo;
    }

    public String getType() {
        return type;
    }

    public String getCountry() {
        return country;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setRangeFrom(long rangeFrom) {
        this.rangeFrom = rangeFrom;
    }

    public void setRangeTo(long rangeTo) {
        this.rangeTo = rangeTo;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}

class Event {
    private String transactionId;
    private String status;
    private String message;

    public Event(
        String transactionId,
        String status,
        String message) {

        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    // Getters and setters
    public String getTransactionId() { return transactionId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}