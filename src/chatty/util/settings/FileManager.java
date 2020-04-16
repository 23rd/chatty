
package chatty.util.settings;

import chatty.gui.components.settings.BackupManager;
import chatty.util.DateTime;
import chatty.util.MiscUtil;
import chatty.util.StringUtil;
import chatty.util.settings.FileManager.SaveResult.CancelReason;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author tduva
 */
public class FileManager {
    
    private static final Logger LOGGER = Logger.getLogger(FileManager.class.getName());
    
    private static final String BACKUP_PREFIX = "auto_";
    
    private static final Charset CHARSET = Charset.forName("UTF-8");
    
    private final Map<String, FileSettings> files = new HashMap<>();
    private final Set<String> backupLoaded = new HashSet<>();
    private final Map<String, String> knownContent = new HashMap<>();
    private final Path basePath;
    private final Path backupPath;
    
    private boolean savingPaused;
    
    public FileManager(Path basePath, Path backupPath) {
        this.basePath = basePath;
        this.backupPath = backupPath;
    }
    
    public synchronized void add(String id, String fileName, boolean backupEnabled,
                                 FileContentInfoProvider provider) {
        FileSettings settings = new FileSettings(id, basePath.resolve(fileName), backupEnabled, provider);
        files.put(id, settings);
    }
    
    public synchronized void setSavingPaused(boolean paused) {
        this.savingPaused = paused;
        LOGGER.info("Saving paused: "+paused);
    }
    
    public synchronized SaveResult save(String id, String content, boolean force) {
        SaveResult.Builder result = new SaveResult.Builder(id);
        FileSettings fileSettings = files.get(id);
        if (savingPaused) {
            result.setCancelled(CancelReason.SAVING_PAUSED);
            return result.make();
        }
        if (fileSettings == null) {
            LOGGER.warning("[Save] Invalid file id: "+id);
            result.setCancelled(CancelReason.INVALID_ID);
            return result.make();
        }
        if (!force && knownContent.containsKey(id) && Objects.equals(knownContent.get(id), content)) {
            if (content != null) {
                LOGGER.info("Not writing "+id+" (known content)");
            }
            result.setCancelled(CancelReason.KNOWN_CONTENT);
            return result.make();
        }
        if (!force && backupLoaded.contains(id)) {
            LOGGER.info("Not writing "+id+" (backup loaded this session)");
            result.setCancelled(CancelReason.BACKUP_LOADED);
            return result.make();
        }
        try {
            Path target = fileSettings.path;
            if (content == null) {
                boolean removed = removeFile(target);
                if (removed) {
                    result.setRemoved();
                }
            }
            else {
                saveToFile(target, content); // IOException
                result.setWritten(target);
            }
            // Only count as written if no exception was thrown
            knownContent.put(id, content);
        }
        catch (IOException ex) {
            result.setWriteError(ex);
        }

        if (fileSettings.backupEnabled && content != null) {
            try {
                Path backupTarget = backupPath.resolve(BACKUP_PREFIX+"session__" + fileSettings.path.getFileName());
                saveToFile(backupTarget, content);
                result.setBackupWritten(backupTarget);
            }
            catch (IOException ex) {
                result.setBackupError(ex);
            }
        }
        return result.make();
    }
    
    public synchronized String load(String id) throws IOException {
        FileSettings fileSettings = files.get(id);
        if (fileSettings == null) {
            LOGGER.warning("[Load] Invalid file id: "+id);
            return null;
        }
        String content = loadFromFile(fileSettings.path);
        knownContent.put(id, content);
        return content;
    }
    
    private String loadFromFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), CHARSET);
    }
    
    private void saveToFile(Path file, String content) throws IOException {
        LOGGER.info("Saving contents to file: " + file);
        try {
            Files.createDirectories(file.getParent());
        }
        catch (IOException ex) {
            // If e.g. a symbolic link dir already exists this may fail, but still be valid for writing
            LOGGER.warning("Failed to create "+file.getParent()+", let's try writing anyway..");
        }
        try {
            Path tempFile = file.resolveSibling(file.getFileName().toString() + "-temp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, CHARSET)) {
                writer.write(content);
            }
            MiscUtil.moveFile(tempFile, file);
        }
        catch (IOException ex) {
            LOGGER.warning("Error saving file: " + ex);
            throw ex;
        }
    }
    
    private boolean removeFile(Path file) {
        try {
            Files.delete(file);
            LOGGER.info("Removed unused file: " + file);
            return true;
        }
        catch (NoSuchFileException ex) {
            // Don't need to remove non-existing file
            LOGGER.info("Unused file doesn't exist: " + file);
        }
        catch (IOException ex) {
            LOGGER.warning("Error removing unused file: " + ex);
        }
        return false;
    }
    
    public synchronized void loadBackup(FileInfo info) throws IOException {
        Files.copy(info.file, info.settings.path, REPLACE_EXISTING);
        backupLoaded.add(info.settings.id);
    }
    
    public synchronized void backup(long backupDelay, int keepCount) throws IOException {
        //--------------------------
        // Check current backups
        //--------------------------
        List<FileInfo> backupFiles = getFileInfo();
        long latestTimestamp = 0;
        int toDelete = backupFiles.size() - keepCount;
        for (FileInfo file : backupFiles) {
            // Doesn't apply to "session" backups, since they don't contain a timestamp
            if (file.timestamp > latestTimestamp) {
                latestTimestamp = file.timestamp;
            }
            if (file.timestamp != -1 && toDelete > 0) {
                try {
                    Files.deleteIfExists(file.file);
                    LOGGER.info("Deleted old backup: "+file.file);
                    toDelete--;
                }
                catch (IOException ex) {
                    LOGGER.warning("Failed to delete backup: " + ex);
                }
            }
        }
        // Perform backup if enough time has passed since newest backup file with timestamp
        LOGGER.info("Latest backup performed "+DateTime.formatFullDatetime(latestTimestamp*1000));
        if (System.currentTimeMillis()/1000 - latestTimestamp > backupDelay) {
            doBackup();
        }
    }
    
    private void doBackup() throws IOException {
        for (FileSettings file : files.values()) {
            if (file.backupEnabled && Files.exists(file.path)) {
                String content = loadFromFile(file.path);
                FileContentInfo info = file.infoProvider.getInfo(content);
                if (info.isValid) {
                    Path backupTarget = backupPath.resolve(BACKUP_PREFIX+(System.currentTimeMillis()/1000)+"__" + file.path.getFileName());
                    Files.copy(file.path, backupTarget, REPLACE_EXISTING);
                    LOGGER.info("Backup performed: "+backupTarget);
                }
                else {
                    LOGGER.info("Didn't perform backup (invalid content): "+file.path);
                }
            }
        }
    }
    
    /**
     * Return a list of backup files, sorted by oldest first.
     * 
     * @return
     * @throws IOException 
     */
    public synchronized List<FileInfo> getFileInfo() throws IOException {
        List<FileInfo> result = new ArrayList<>();
        Set<FileVisitOption> options = new HashSet<>();
        options.add(FileVisitOption.FOLLOW_LINKS);
        Files.walkFileTree(backupPath, options, 1, new SimpleFileVisitor<Path>() {
            
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                String fileName = file.getFileName().toString();
                String origFileName = getOrigFileName(fileName);
                if (fileName.startsWith(BACKUP_PREFIX) && origFileName != null) {
                    FileSettings s = getFileSettingsByName(origFileName);
                    if (s != null) {
//                        System.out.println(file+" -> "+origFileName+" "+DateTime.agoText(attrs.lastModifiedTime().toMillis()));
                        try {
                            String content = loadFromFile(file);
                            FileContentInfo info = s.infoProvider.getInfo(content);
                            result.add(new FileInfo(s, file, attrs.lastModifiedTime().toMillis(), attrs.size(), getTimestamp(fileName), info.isValid, info.info));
                        }
                        catch (IOException ex) {
                            result.add(new FileInfo(s, file, attrs.lastModifiedTime().toMillis(), attrs.size(), getTimestamp(fileName), false, "Error: "+ex));
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
            
        });
        Collections.sort(result, (a, b) -> {
            if (a.modifiedTime > b.modifiedTime) {
                return 1;
            }
            if (a.modifiedTime < b.modifiedTime) {
                return -1;
            }
            if (a.timestamp > b.timestamp) {
                return 1;
            }
            if (a.timestamp < b.timestamp) {
                return -1;
            }
            return 0;
        });
        return result;
    }
    
    private FileSettings getFileSettingsByName(String fileName) {
        for (FileSettings s : files.values()) {
            if (s.path.getFileName().toString().equals(fileName)) {
                return s;
            }
        }
        return null;
    }
    
    private static String getOrigFileName(String fileName) {
        int index = fileName.indexOf("__");
        if (index == -1 || index+2 == fileName.length()) {
            return null;
        }
        return fileName.substring(index+2);
    }
    
    private static final Pattern FIND_TIMESTAMP = Pattern.compile(BACKUP_PREFIX+"([0-9]+)__.+");
    
    private static long getTimestamp(String fileName) {
        Matcher m = FIND_TIMESTAMP.matcher(fileName);
        if (m.matches()) {
            return Long.parseLong(m.group(1));
        }
        return -1;
    }
    
    public static class FileSettings {
        
        public final String id;
        public final Path path;
        public final boolean backupEnabled;
        public final FileContentInfoProvider infoProvider;
        
        public FileSettings(String id, Path path, boolean backupEnabled,
                            FileContentInfoProvider infoProvider) {
            this.id = id;
            this.path = path;
            this.backupEnabled = backupEnabled;
            this.infoProvider = infoProvider;
        }
        
    }
    
    public static class FileInfo {
        
        private final FileSettings settings;
        private final String info;
        private final long modifiedTime;
        private final boolean isValid;
        private final Path file;
        private final long timestamp;
        private final long size;

        public FileInfo(FileSettings settings, Path file, long modifiedTime, long size, long timestamp, boolean isValid, String info) {
            this.file = file;
            this.modifiedTime = modifiedTime;
            this.info = info;
            this.isValid = isValid;
            this.timestamp = timestamp;
            this.settings = settings;
            this.size = size;
        }
        
        public FileSettings getSettings() {
            return settings;
        }
        
        public String getInfo() {
            return info;
        }
        
        public boolean isValid() {
            return isValid;
        }
        
        public Path getFile() {
            return file;
        }
        
        public long getModifiedTime() {
            return modifiedTime;
        }
        
        public long getSize() {
            return size;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        /**
         * Session backups are created/modified at the same time. Other backups
         * are copied, so they retain their original modified time, but have
         * an additional created timestamp added in the name.
         * 
         * @return The timestamp, in milliseconds, when the backup was created
         * (written or copied)
         */
        public long getCreated() {
            return timestamp == -1 ? modifiedTime : timestamp*1000;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] Mod:%s Bu:%s valid: %s (%s)",
                    file, DateTime.ago(modifiedTime), DateTime.ago(timestamp*1000), isValid, info);
        }
        
    }
    
    public static interface FileContentInfoProvider {
        
        public FileContentInfo getInfo(String content);
        
    }
    
    public static class SaveResult {
        
        public enum CancelReason {
            BACKUP_LOADED, INVALID_ID, KNOWN_CONTENT, SAVING_PAUSED
        }
        
        private static class Builder {
            
            private String id;
            private boolean written;
            private boolean backupWritten;
            private Throwable writeError;
            private Throwable backupError;
            private Path filePath;
            private Path backupPath;
            private boolean removed;
            private CancelReason cancelReason;
            
            public Builder(String id) {
                this.id = id;
            }
            
            public Builder setWritten(Path path) {
                this.written = true;
                this.filePath = path;
                return this;
            }
            
            public Builder setBackupWritten(Path path) {
                this.backupWritten = true;
                this.backupPath = path;
                return this;
            }
            
            public Builder setWriteError(Throwable writeError) {
                this.writeError = writeError;
                return this;
            }
            
            public Builder setBackupError(Throwable backupError) {
                this.backupError = backupError;
                return this;
            }
            
            public Builder setRemoved() {
                this.removed = true;
                return this;
            }
            
            public Builder setCancelled(CancelReason reason) {
                this.cancelReason = reason;
                return this;
            }
            
            public SaveResult make() {
                return new SaveResult(this);
            }
            
        }
        
        public final String id;
        public final boolean written;
        public final boolean backupWritten;
        public final Throwable writeError;
        public final Throwable backupError;
        public final Path filePath;
        public final Path backupPath;
        public final boolean removed;
        public final CancelReason cancelReason;
        
        private SaveResult(Builder builder) {
            this.id = builder.id;
            this.written = builder.written;
            this.backupWritten = builder.backupWritten;
            this.writeError = builder.writeError;
            this.backupError = builder.backupError;
            this.filePath = builder.filePath;
            this.backupPath = builder.backupPath;
            this.removed = builder.removed;
            this.cancelReason = builder.cancelReason;
        }
        
    }
    
    public static class FileContentInfo {
        
        public final boolean isValid;
        public final String info;
        
        public FileContentInfo(boolean isValid, String info) {
            this.isValid = isValid;
            this.info = info;
        }
        
    }
    
    public static final void main(String[] args) throws IOException {
        FileManager m = new FileManager(Paths.get("H:\\test123"), Paths.get("H:\\test123\\backupp"));
        String content = "content\nabc\rblah\r\n";
        m.add("test", "filename", true, new FileContentInfoProvider() {

            @Override
            public FileContentInfo getInfo(String content) {
                if (content != null && !content.isEmpty()) {
                    return new FileContentInfo(true, content.length()+" characters");
                }
                return new FileContentInfo(false, "Empty file");
            }
        });
//        m.save("test", content);
//        
//        String read = m.loadFromFile(Paths.get("H:\\test123\\filename"));
//        System.out.println(read.equals(content));
        
//        for (FileInfo info : m.getFileInfo()) {
//            System.out.println(info);
//        }
//        
//        System.out.println(getTimestamp("abc"));
//        System.out.println(getTimestamp("auto_123456__"));
//        System.out.println(getTimestamp("auto_123456__abc"));
//        long a = -1;
//        long b = 3232323232323L;
//        System.out.println((a - System.currentTimeMillis()));
        
//        m.backup((int)DateTime.HOUR, 5);
        
//        BackupManager mg = new BackupManager(m);
//        mg.setModal(true);
//        mg.setLocationRelativeTo(null);
//        mg.open();
//        System.exit(0);
    }
    
}
