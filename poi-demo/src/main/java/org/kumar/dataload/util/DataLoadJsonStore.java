package org.kumar.dataload.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
import java.util.Objects;


/**
 * DataLoadJsonStore
 *
 * - Reads default JSON from classpath resource: src/main/resources/dataload.json
 * - Copies to external writable config file on first run
 * - Uses file locks for safe concurrent access
 * - Creates timestamped backups on save + keeps last N backups
 * - Validates intervals: 26th->25th cycle, <=31 days, no overlaps
 */
public class DataLoadJsonStore {

    // ---------- Configuration defaults ----------
    private static final int DEFAULT_BACKUP_KEEP_COUNT = 20;
    private static final DateTimeFormatter BACKUP_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int MAX_BACKUP_NAME_ATTEMPTS = 1000;

    private final ObjectMapper mapper;
    private final String resourceName;
    private final Path configFile;
    private final int backupKeepCount;

    private Root root;

    // ---------- Factory / load ----------
    public static DataLoadJsonStore loadFromResourceToDefaultConfig(String resourceName) throws IOException {
        Path defaultPath = defaultConfigPath();
        return loadFromResourceToConfig(resourceName, defaultPath, DEFAULT_BACKUP_KEEP_COUNT);
    }

    public static DataLoadJsonStore loadFromResourceToConfig(String resourceName, Path configFile, int backupKeepCount)
            throws IOException {

        ObjectMapper mapper = createMapper();

        // Ensure parent directories exist
        Path parent = configFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        // If config file doesn't exist, copy from resource to config file
        if (Files.notExists(configFile)) {
            copyResourceToFile(resourceName, configFile);
        }

        // Read with shared lock
        Root root = readLocked(mapper, configFile);

        DataLoadJsonStore store = new DataLoadJsonStore(resourceName, configFile, backupKeepCount, mapper);
        store.root = (root == null) ? new Root() : root;

        // Validate after load (fail fast if file is invalid)
        store.validateAll();

        return store;
    }

    private DataLoadJsonStore(String resourceName, Path configFile, int backupKeepCount, ObjectMapper mapper) {
        this.resourceName = resourceName;
        this.configFile = configFile.toAbsolutePath();
        this.backupKeepCount = Math.max(0, backupKeepCount);
        this.mapper = mapper;
        this.root = new Root();
    }

    // ---------- Public getters ----------
    public Path getConfigFile() {
        return configFile;
    }

    public String getResourceName() {
        return resourceName;
    }

    public List<Account> getAccounts() {
        if (root.accounts == null) root.accounts = new ArrayList<>();
        return root.accounts;
    }

    public Optional<Account> findAccount(String accountName) {
        return getAccounts().stream()
                .filter(a -> a != null && Objects.equals(a.accountName, accountName))
                .findFirst();
    }

    /** Lookup an account by name or throw a clear exception. */
    public Account findAccountOrThrow(String accountName) {
        Objects.requireNonNull(accountName, "accountName");

        return findAccount(accountName)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + accountName));
    }

    /**
     * Lookup a LoadInterval for a given account by (startDate,endDate).
     * Returns Optional.empty() if account or interval not found.
     */
    public Optional<LoadInterval> findInterval(String accountName, LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");

        Optional<Account> accountOpt = findAccount(accountName);
        if (!accountOpt.isPresent()) return Optional.empty();

        Account a = accountOpt.get();
        if (a.loadIntervals == null) return Optional.empty();

        return a.loadIntervals.stream()
                .filter(li -> li != null
                        && startDate.equals(li.startDate)
                        && endDate.equals(li.endDate))
                .findFirst();
    }

    /** Same as findInterval(), but throws if not found. */
    public LoadInterval findIntervalOrThrow(String accountName, LocalDate startDate, LocalDate endDate) {
        return findInterval(accountName, startDate, endDate)
                .orElseThrow(() -> new IllegalStateException(
                        "LoadInterval not found for account=" + accountName
                                + " start=" + startDate + " end=" + endDate));
    }

    
    public Account upsertAccount(String accountName) {
        return findAccount(accountName).orElseGet(() -> {
            Account a = new Account();
            a.accountName = accountName;
            a.loadIntervals = new ArrayList<>();
            getAccounts().add(a);
            return a;
        });
    }

    /**
     * Upsert (add or update) an interval identified by (startDate,endDate).
     * Also validates after modification.
     */
    public boolean upsertInterval(String accountName,
                                 LocalDate startDate,
                                 LocalDate endDate,
                                 String status,
                                 Integer numberOfRecords) {

        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");

        Account a = upsertAccount(accountName);
        if (a.loadIntervals == null) a.loadIntervals = new ArrayList<>();

        LoadInterval existing = a.loadIntervals.stream()
                .filter(li -> startDate.equals(li.startDate) && endDate.equals(li.endDate))
                .findFirst()
                .orElse(null);

        boolean changed = false;

        if (existing == null) {
            LoadInterval li = new LoadInterval();
            li.startDate = startDate;
            li.endDate = endDate;
            li.status = status;
            li.numberOfRecords = numberOfRecords;
            a.loadIntervals.add(li);
            changed = true;
        } else {
            if (status != null && !Objects.equals(status, existing.status)) {
                existing.status = status;
                changed = true;
            }
            if (numberOfRecords != null && !Objects.equals(numberOfRecords, existing.numberOfRecords)) {
                existing.numberOfRecords = numberOfRecords;
                changed = true;
            }
        }

        if (changed) {
        	System.out.println(" updating the interals for " + accountName  + " " + startDate  + " " + endDate );
            normalizeSortAllIntervals();
            validateAccount(a); // validate only this account for speed
        }

        return changed;
    }

    public boolean markLoaded(String accountName, LocalDate startDate, LocalDate endDate, int numberOfRecords) {
        return upsertInterval(accountName, startDate, endDate, "Loaded", numberOfRecords);
    }

    public boolean removeInterval(String accountName, LocalDate startDate, LocalDate endDate) {
        Account a = findAccount(accountName).orElse(null);
        if (a == null || a.loadIntervals == null) return false;

        boolean removed = a.loadIntervals.removeIf(li ->
                Objects.equals(startDate, li.startDate) && Objects.equals(endDate, li.endDate));

        if (removed) {
            normalizeSortAllIntervals();
            validateAccount(a);
        }
        return removed;
    }

    // ---------- Persistence ----------
    /**
     * Save back to configFile with:
     * - exclusive lock
     * - backup/versioning
     * - validation
     */
    public void save() throws IOException {
        normalizeSortAllIntervals();
        validateAll();

        // Exclusive lock during backup+write
        try (FileChannel channel = FileChannel.open(configFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {

            // Backup before writing
            createBackupLocked(channel);

            // Write JSON (truncate)
            String json = mapper.writeValueAsString(root);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            channel.truncate(0);
            channel.position(0);
            channel.write(java.nio.ByteBuffer.wrap(bytes));
            channel.force(true);

            // Cleanup old backups
            cleanupBackupsLocked();
        }
    }

    // ---------- Validation ----------
    public void validateAll() {
        for (Account a : getAccounts()) {
            validateAccount(a);
        }
    }

    /**
     * Validations:
     * - startDate <= endDate
     * - cycle is exactly 26th -> 25th of next month
     * - duration days <= 31
     * - no overlaps within an account
     */
    public void validateAccount(Account account) {
        if (account == null) return;
        if (account.loadIntervals == null) account.loadIntervals = new ArrayList<>();

        // normalize sort
        account.loadIntervals.sort(Comparator.comparing(li -> li.startDate));

        // basic checks + cycle checks
        for (LoadInterval li : account.loadIntervals) {
            if (li == null) continue;

            if (li.startDate == null || li.endDate == null) {
                throw new IllegalStateException("Account '" + account.accountName + "' has interval with null dates.");
            }
            if (li.endDate.isBefore(li.startDate)) {
                throw new IllegalStateException("Account '" + account.accountName + "' has interval endDate < startDate: "
                        + li.startDate + " to " + li.endDate);
            }

            /** TODO ***** WE NEED TO FIX IT
            // Must be 26th to 25th cycle
            if (li.startDate.getDayOfMonth() != 26) {
                throw new IllegalStateException("Account '" + account.accountName + "' interval startDate must be 26th: "
                        + li.startDate);
            }
            if (li.endDate.getDayOfMonth() != 25) {
                throw new IllegalStateException("Account '" + account.accountName + "' interval endDate must be 25th: "
                        + li.endDate);
            }

            LocalDate expectedEnd = li.startDate.plusMonths(1).withDayOfMonth(25);
            if (!expectedEnd.equals(li.endDate)) {
                throw new IllegalStateException("Account '" + account.accountName + "' interval must end on 25th of next month. "
                        + "start=" + li.startDate + " expectedEnd=" + expectedEnd + " actualEnd=" + li.endDate);
            }

            */
            long daysInclusive = java.time.temporal.ChronoUnit.DAYS.between(li.startDate, li.endDate) + 1;
            if (daysInclusive > 31) {
                throw new IllegalStateException("Account '" + account.accountName + "' interval exceeds 31 days: "
                        + li.startDate + " to " + li.endDate + " (" + daysInclusive + " days)");
            }
        }

        // overlap checks (inclusive ranges)
        for (int i = 1; i < account.loadIntervals.size(); i++) {
            LoadInterval prev = account.loadIntervals.get(i - 1);
            LoadInterval curr = account.loadIntervals.get(i);
            if (prev == null || curr == null) continue;

            // Overlap if curr.startDate <= prev.endDate
            if (!curr.startDate.isAfter(prev.endDate)) {
                throw new IllegalStateException("Account '" + account.accountName + "' has overlapping intervals: "
                        + prev.startDate + " to " + prev.endDate + " overlaps " + curr.startDate + " to " + curr.endDate);
            }
        }
    }

    // ---------- Internals ----------
    private void normalizeSortAllIntervals() {
        for (Account a : getAccounts()) {
            if (a == null || a.loadIntervals == null) continue;
            a.loadIntervals.sort(Comparator.comparing(li -> li.startDate));
        }
    }

    private static Root readLocked(ObjectMapper mapper, Path file) throws IOException {
        // Shared lock for read
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {

            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return new Root(); // empty file -> treat as empty structure
            return mapper.readValue(new String(bytes, StandardCharsets.UTF_8), Root.class);
        }
    }

    private static void copyResourceToFile(String resourceName, Path destFile) throws IOException {
        try (InputStream is = DataLoadJsonStore.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found on classpath: " + resourceName);
            }
            Files.copy(is, destFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void createBackupLocked() throws IOException {
        if (backupKeepCount <= 0) return;

        Path backupDir = backupDir();
        Files.createDirectories(backupDir);

        if (!Files.exists(configFile) || Files.size(configFile) <= 0) {
            return;
        }

        String baseName = configFile.getFileName().toString();
        copyToUniqueBackup(configFile, backupDir, baseName);
    }

    /*
     * This version takes an exiting file channel that was used to open the file in the first place so that the write will not give you errors
     */
    private void createBackupLocked(FileChannel sourceChannel) throws IOException {
        if (backupKeepCount <= 0) return;

        Path backupDir = backupDir();
        Files.createDirectories(backupDir);

        // If file is empty, nothing to backup
        long size = sourceChannel.size();
        if (size <= 0) return;

        String baseName = configFile.getFileName().toString();

        // Copy bytes from the already-open channel to a unique backup file.
        // MonthlyLoad can save multiple times within the same second; CREATE_NEW
        // must not fail on timestamp collisions.
        long originalPos = sourceChannel.position();
        try (FileChannel out = openUniqueBackupChannel(backupDir, baseName)) {
            sourceChannel.position(0);
            long transferred = 0;
            while (transferred < size) {
                transferred += sourceChannel.transferTo(transferred, size - transferred, out);
            }
            out.force(true);
        } finally {
            // restore original position
            sourceChannel.position(originalPos);
        }
    }

    /**
     * Copy to a unique backup path, retrying with a numeric suffix when two saves
     * land on the same timestamp.
     */
    private void copyToUniqueBackup(Path source, Path backupDir, String baseName) throws IOException {
        String ts = LocalDateTime.now().format(BACKUP_TS);
        for (int n = 0; n < MAX_BACKUP_NAME_ATTEMPTS; n++) {
            String suffix = (n == 0) ? ts : (ts + "-" + n);
            Path backupFile = backupDir.resolve(baseName + "." + suffix + ".bak");
            try {
                Files.copy(source, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
                return;
            } catch (FileAlreadyExistsException ignored) {
                // another save already claimed this name; try next suffix
            }
        }
        throw new IOException("Unable to create unique backup for " + baseName + " under " + backupDir);
    }

    private FileChannel openUniqueBackupChannel(Path backupDir, String baseName) throws IOException {
        String ts = LocalDateTime.now().format(BACKUP_TS);
        for (int n = 0; n < MAX_BACKUP_NAME_ATTEMPTS; n++) {
            String suffix = (n == 0) ? ts : (ts + "-" + n);
            Path backupFile = backupDir.resolve(baseName + "." + suffix + ".bak");
            try {
                return FileChannel.open(backupFile,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // another save already claimed this name; try next suffix
            }
        }
        throw new IOException("Unable to create unique backup for " + baseName + " under " + backupDir);
    }
        
    private void cleanupBackupsLocked() throws IOException {
        if (backupKeepCount <= 0) return;

        Path backupDir = backupDir();
        if (Files.notExists(backupDir)) return;

        String baseName = configFile.getFileName().toString();

        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupDir, baseName + ".*.bak")) {
            for (Path p : ds) backups.add(p);
        }

        backups.sort((a, b) -> {
            try {
                // newest first
                return -Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
            } catch (IOException e) {
                return 0;
            }
        });

        for (int i = backupKeepCount; i < backups.size(); i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private Path backupDir() {
        Path parent = configFile.getParent();
        if (parent == null) parent = Paths.get(".");
        return parent.resolve("backups");
    }

    private static ObjectMapper createMapper() {
    	com.fasterxml.jackson.core.JsonFactory factory = com.fasterxml.jackson.core.JsonFactory.builder()
                // your sample has trailing commas; allow them
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();

        ObjectMapper om = new ObjectMapper(factory);
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.enable(SerializationFeature.INDENT_OUTPUT);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return om;
    }

    private static Path defaultConfigPath() {
        // A good default external location for persistence
        // e.g. C:\Users\<you>\.ledger\config\dataload.json  or  /home/<you>/.ledger/config/dataload.json
        String home = System.getProperty("user.home");
        return Paths.get(home, ".ledger", "config", "dataload.json");
    }

    // ---------- POJOs ----------
    public static class Root {
        public List<Account> accounts = new ArrayList<>();
    }

    public static class Account {
        public String accountName;
        public List<LoadInterval> loadIntervals = new ArrayList<>();
    }

    public static class LoadInterval {
        public LocalDate startDate;
        public LocalDate endDate;
        public String status;

        @JsonProperty("NumberOfRecords")
        public Integer numberOfRecords;
    }

    // ---------- Example usage ----------
    public static void main(String[] args) throws Exception {
        // Put dataload.json in src/main/resources
        DataLoadJsonStore store = DataLoadJsonStore.loadFromResourceToDefaultConfig("dataload.json");

        // Update an interval (must comply with 26->25 cycle)
        store.markLoaded("BOA CC",
                LocalDate.parse("2025-07-26"),
                LocalDate.parse("2025-08-25"),
                41);

        // Save safely (exclusive lock + backup + validation)
        store.save();

        System.out.println("Saved to: " + store.getConfigFile());
    }
}
