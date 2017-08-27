package nz.co.appelation.offlinepasswordmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.inject.Inject;

import nz.co.appelation.offlinepasswordmanager.model.AccountCategory;
import nz.co.appelation.offlinepasswordmanager.model.display.AccountEntry;

public class ImportExportHelper {

    private static final String TAG = ImportExportHelper.class.getSimpleName();

    /**
     * The magic byte header-
     * This header is encrypted along with the backup file.
     * If we attempt a decrypt (restore), and the magic header decrypts correctly, then we know the supplied password key was correct.
     */
    private static final byte[] MAGIC = "22f7cfe7ddb0d6bc9798ca50883853c0".getBytes(StandardCharsets.UTF_8); //Magic bytes used to identify a correctly decrypted stream.

    public static final String TEMPLATE_FILE = "ImportTemplate.csv";

    public static final String CSV_EXPORT_FILE = "Export.csv";

    public static final String BACKUP_DB_FILE = "OPM";

    public static final String BACKUP_DB_EXT = "bak";

    private static final String HEADER_ACCOUNT_NAME = "account_name";
    private static final String HEADER_USERNAME = "username";
    private static final String HEADER_PASSWORD = "password";
    private static final String HEADER_URL = "url";
    private static final String HEADER_NOTES = "notes";
    private static final String HEADER_CATEGORY = "category";

    private static final String[] CSV_HEADER = {HEADER_ACCOUNT_NAME, HEADER_USERNAME, HEADER_PASSWORD, HEADER_URL, HEADER_NOTES, HEADER_CATEGORY};

    @Inject
    DBManager dbman;

    @Inject
    CategoryHelper categoryHelper;

    @Inject
    Context context;

    @Inject
    SharedPreferences prefs;

    @Inject
    ModelMapper modelMapper;

    @Inject
    CryptoUtil cryptoUtil;

    /**
     * Create a CSV import template, with some sample records.
     *
     * @param uri the location of where to write the CSV file.
     */
    public void createCSVImportTemplate(Uri uri) throws Exception {
        Log.i(TAG, "Create CSV Import Template URI: " + uri.toString());

        Writer streamWriter = null;
        OutputStream outputStream = null;
        CSVPrinter csvPrinter = null;

        try {
            outputStream = context.getContentResolver().openOutputStream(uri, "w");
            streamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            csvPrinter = CSVFormat.DEFAULT.withHeader(CSV_HEADER).withQuoteMode(QuoteMode.ALL).print(streamWriter);

            Object[] sampleRecordValues = new Object[]{
                    "Sample account name (account_name is the only mandatory field)",
                    "sampleuser@example.com",
                    "samplepassword123",
                    "www.example.com",
                    "Sample note line",
                    "Sample custom category name"
            };
            csvPrinter.printRecord(sampleRecordValues);

            sampleRecordValues = new Object[]{
                    "Notes:",
                    "-",
                    "-",
                    "-",
                    "You may open this template in a text editor, and observe field encoding and quoting in the sample records below. If you have issues with multi-line note-fields in MS Excel, try OpenOffice Calc. Be careful of numeric fields starting with a zero - spreadsheet applications often trim the zeros.",
                    ""
            };
            csvPrinter.printRecord(sampleRecordValues);

            sampleRecordValues = new Object[]{
                    "Multi line note sample",
                    "-",
                    "-",
                    "-",
                    "Sample record note has two lines. Line1\nLine 2.",
                    ""
            };
            csvPrinter.printRecord(sampleRecordValues);

            sampleRecordValues = new Object[]{
                    "Comma sample",
                    "-",
                    "pass,word",
                    "-",
                    "This sample record has a comma character in the password",
                    ""
            };
            csvPrinter.printRecord(sampleRecordValues);

            sampleRecordValues = new Object[]{
                    "Quote sample",
                    "-",
                    "pass\"word",
                    "-",
                    "This sample record has quote character in the password",
                    ""
            };
            csvPrinter.printRecord(sampleRecordValues);

            csvPrinter.flush();
            streamWriter.flush();
            outputStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create csv template file: " + uri.getPath(), e);
            throw new Exception(e);
        } finally {
            try {
                csvPrinter.close();
            } catch (Throwable t){
                //consume
            }

            try {
                streamWriter.close();
            } catch (Throwable t){
                //consume
            }

            try {
                outputStream.close();
            } catch (Throwable t){
                //consume
            }
        }
    }

    /**
     * Exports the accounts to a CSV file.
     *
     * @param uri the location of where to write the CSV file.
     * @return the number of accounts exported.
     */
    public int exportCSV(Uri uri) throws Exception {
        Log.i(TAG, "CSV Export URI: " + uri.toString());

        Writer streamWriter = null;
        OutputStream outputStream = null;
        CSVPrinter csvPrinter = null;

        int count = 0;

        try {
            outputStream = context.getContentResolver().openOutputStream(uri, "w");
            streamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            csvPrinter = CSVFormat.DEFAULT.withHeader(CSV_HEADER).withQuoteMode(QuoteMode.ALL).print(streamWriter);

            count = writeCSVData(csvPrinter);

            csvPrinter.flush();
            streamWriter.flush();
            outputStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Unable to export csv file: " + uri.getPath(), e);
            throw new Exception(e);
        } finally {
            try {
                csvPrinter.close();
            } catch (Throwable t){
                //consume
            }

            try {
                streamWriter.close();
            } catch (Throwable t){
                //consume
            }

            try {
                outputStream.close();
            } catch (Throwable t){
                //consume
            }
        }

        return count;
    }

    /**
     * Iterated accounts, and write to CSVPrinter.
     *
     * @param csvPrinter the printer
     * @return number of accounts processed
     * @throws IOException thrown on exception
     */
    private int writeCSVData(CSVPrinter csvPrinter) throws IOException {
        int count = 0;

        for (AccountEntry account : dbman.getAllAccounts()){
            String categoryName = null;
            if (account.categoryId != null && account.categoryId.trim().length() != 0 && !AccountCategory.ID_UNSPECIFIED.equals(account.categoryId)) {
                categoryName = categoryHelper.getAccountCategory(account.categoryId).name;
            }

            StringBuilder username = new StringBuilder();
            username.append(account.username);

            StringBuilder password = new StringBuilder();
            password.append(account.password);

            Object[] recordValues = new Object[]{
                    account.name,
                    username,
                    password,
                    account.url,
                    account.notes,
                    categoryName
            };

            csvPrinter.printRecord(recordValues);

            count++;
        }

        return count;
    }

    /**
     * Main import method - used to process either CSV import, or a restore operation.
     *
     * This method deletes the old DB, creates a new one, and populates it with the accounts from the CSV,
     * and also sets the custom categories, as per the six most popular custom categories in the import steam.
     *
     * @param csvParser the csv parser to read from
     * @return stats about success / fail count
     * @throws IOException thrown on exception
     */
    private ImportStats importCSVData(CSVParser csvParser) throws IOException {
        Map<String, List<AccountEntry>> accountsMap = new HashMap<>();
        Set<String> allCustomCategoryNames = new HashSet<>();

        ImportStats stats = new ImportStats();

        /**
         * Build map of key: category, value: account
         */
        Iterator<CSVRecord> recordIter = csvParser.iterator();
        while (recordIter.hasNext()) {
            CSVRecord accountRecord = recordIter.next();

            String category = accountRecord.get(HEADER_CATEGORY);
            if (category == null || category.trim().length() == 0) {
                category = AccountCategory.ID_UNSPECIFIED;
            } else {
                category = category.trim();

                /**
                 * Check standard category names:
                 */
                if (categoryHelper.getAccountCategory(AccountCategory.ID_SHOPPING).name.equalsIgnoreCase(category)) {
                    category = AccountCategory.ID_SHOPPING;
                } else if (categoryHelper.getAccountCategory(AccountCategory.ID_SOCIAL).name.equalsIgnoreCase(category)) {
                    category = AccountCategory.ID_SOCIAL;
                } else if (categoryHelper.getAccountCategory(AccountCategory.ID_BANKING).name.equalsIgnoreCase(category)) {
                    category = AccountCategory.ID_BANKING;
                } else {
                    allCustomCategoryNames.add(category);
                }
            }

            String accountName = accountRecord.get(HEADER_ACCOUNT_NAME);
            if (accountName == null || accountName.trim().length() == 0) {
                /**
                 * Account name is mandatory
                 */
                stats.failed++;
                continue;
            }

            if (!accountsMap.containsKey(category)) {
                accountsMap.put(category, new ArrayList<AccountEntry>());
            }

            List<AccountEntry> accounts = accountsMap.get(category);
            accounts.add(new AccountEntry(accountName, category, accountRecord.get(HEADER_USERNAME).toCharArray(), accountRecord.get(HEADER_PASSWORD).toCharArray(), accountRecord.get(HEADER_URL), accountRecord.get(HEADER_NOTES)));
        }

        /**
         * Check custom category names.
         * Only the six most used custom categories will be used and enabled. Any account using a custom category beyond this will be set to ID_UNSPECIFIED.
         */
        Map<String, Integer> allCustomCategoriesUsageCount = new HashMap<>();
        for (String categoryName : allCustomCategoryNames){
            allCustomCategoriesUsageCount.put(categoryName, accountsMap.get(categoryName).size());
        }

        int customCategoryIndex = 0;
        List<String> selectedCustomCategoryNames = new ArrayList<>(6); //The six that we'll use
        while (allCustomCategoriesUsageCount.size() > 0 && customCategoryIndex < 6) {
            String mostPopularCategory = getMostPopularCusomCategory(allCustomCategoriesUsageCount);
            selectedCustomCategoryNames.add(mostPopularCategory);

            String customCategoryId = categoryHelper.getCustomCategoryIdFromIndex(customCategoryIndex);

            List<AccountEntry> accountList = accountsMap.remove(mostPopularCategory);
            for (AccountEntry account : accountList){
                account.categoryId = customCategoryId;
            }

            accountsMap.put(customCategoryId, accountList);

            customCategoryIndex++;

            allCustomCategoriesUsageCount.remove(mostPopularCategory);
        }

        //If there were more than 6 new custom categories, go and mark those accounts as ID_UNSPECIFIED
        for (String customCategory : allCustomCategoriesUsageCount.keySet()){
            for (AccountEntry account : accountsMap.get(customCategory)){
                account.categoryId = AccountCategory.ID_UNSPECIFIED;
            }
        }

        //Delete old DB
        dbman.deleteCurrentVersionDB();

        //Create new DB
        dbman.initEmptyDB();

        //Reset all custom categories to not enabled
        SharedPreferences.Editor editPrefs = prefs.edit();
        for (int index = 0; index < 6; index++){
            editPrefs.remove(categoryHelper.getCustomCategoryEnabledPreferenceId(index));
        }

        //Reset Shopping, Social, Banking
        editPrefs.remove(categoryHelper.getCategoryEnabledPreferenceId(AccountCategory.ID_SHOPPING));
        editPrefs.remove(categoryHelper.getCategoryEnabledPreferenceId(AccountCategory.ID_SOCIAL));
        editPrefs.remove(categoryHelper.getCategoryEnabledPreferenceId(AccountCategory.ID_BANKING));

        //Add new selected custom categories back into new DB
        for (int index = 0; index < selectedCustomCategoryNames.size(); index++){
            dbman.updateCustomCategory(index, selectedCustomCategoryNames.get(index));
            editPrefs.putBoolean(categoryHelper.getCustomCategoryEnabledPreferenceId(index), true);
        }

        //Save preferences
        editPrefs.commit();

        //Add all accounts back into new DB
        for (List<AccountEntry> accounts : accountsMap.values()){
            for (AccountEntry account : accounts){
                dbman.updateAccount(account);
                modelMapper.killModel(account);
                stats.successful++;
            }
        }

        //Finally, save the new DB
        dbman.saveDB();

        return stats;
    }

    public ImportStats importCSV(Uri uri) throws Exception {
        Log.i(TAG, "CSV Import URI: " + uri.toString());

        InputStream inputStream = null;
        CSVParser csvParser = null;
        InputStreamReader inputStreamReader = null;

        ImportStats stats = null;

        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            csvParser = CSVFormat.DEFAULT.withHeader(CSV_HEADER).withSkipHeaderRecord(true).parse(inputStreamReader);

            stats = importCSVData(csvParser);
        } catch (Exception e) {
            Log.e(TAG, "Unable to export csv file: " + uri.getPath(), e);
            throw new Exception(e);
        } finally {
            try {
                inputStream.close();
            } catch (Throwable t){
                //consume
            }

            try {
                csvParser.close();
            } catch (Throwable t){
                //consume
            }
        }

        return stats;
    }

    private String getMostPopularCusomCategory(Map<String, Integer> customCategoryUsageCount){
        String mostPopularCategory = null;
        int mostPopularCategoryCount = 0;
        for (String categoryName : customCategoryUsageCount.keySet()){
            int currentCount = customCategoryUsageCount.get(categoryName);
            if (currentCount > mostPopularCategoryCount){
                mostPopularCategory = categoryName;
                mostPopularCategoryCount = currentCount;
            }
        }

        return mostPopularCategory;
    }

    /**
     * Creates an encrypted backup file.
     *
     * File structure:
     * 0..63: 512 bit salt
     * 64..80: 128 bit IV
     * 81..113: 32 byte magic number, encrypted
     * 114..: CSV export, encrypted
     *
     * Salt & IV random for each backup file.
     *
     * @param uri the uri to the file selected by user, authorised to write.
     * @param backupFilePassword the password from which to derive the PBKDF2 key.
     * @return the number of accounts written to the backup file.
     */
    public int backupDB(Uri uri, char[] backupFilePassword) throws Exception {
        Log.i(TAG, "DB Backup URI: " + uri.toString());

        OutputStream outputStream = null;
        CipherOutputStream encOutputStream = null;
        Writer streamWriter = null;
        CSVPrinter csvPrinter = null;

        int count = 0;

        try {
            //Get new salt
            byte[] salt = cryptoUtil.makeSalt();

            //Generate key from password and salt
            byte[] key = cryptoUtil.generateKeyFromPassword(backupFilePassword, salt);

            //Get new cipher
            Cipher cipher = cryptoUtil.createCipherForEncryption(key);

            //Get IV
            byte[] iv = cryptoUtil.getIVFromCipher(cipher);

            //Open file
            outputStream = context.getContentResolver().openOutputStream(uri, "w");

            //Write the unencrypted salt
            outputStream.write(salt);

            //Write the unencrypted IV
            outputStream.write(iv);

            outputStream.flush();

            //Create encrypted stream
            encOutputStream = new CipherOutputStream(outputStream, cipher);

            //Write encrypted magic bytes
            encOutputStream.write(MAGIC);

            //Wrap a writer around the encrypted output stream
            streamWriter = new OutputStreamWriter(encOutputStream, StandardCharsets.UTF_8);

            //Get CSV printer around writer, add header
            csvPrinter = CSVFormat.DEFAULT.withHeader(CSV_HEADER).print(streamWriter);

            //Generate CSV export, writing to CSV printer
            count = writeCSVData(csvPrinter);

            csvPrinter.flush();
            streamWriter.flush();
            encOutputStream.flush();
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Unable to create backup DB file: " + uri.getPath(), e);
            throw new Exception(e);
        } finally {
            try {
                csvPrinter.close();
            } catch (Throwable t) {
                //consume
            }

            try {
                streamWriter.close();
            } catch (Throwable t) {
                //consume
            }

            try {
                encOutputStream.close();
            } catch (Throwable t) {
                //consume
            }

            try {
                outputStream.close();
            } catch (Throwable t) {
                //consume
            }
        }

        return count;
    }

    /**
     * Restores DB from an encrypted backup file.
     *
     * File structure:
     * 0..63: 512 bit salt
     * 64..80: 128 bit IV
     * 81..113: 32 byte magic number, encrypted
     * 114..: CSV export, encrypted
     *
     * Salt & IV random for each backup file.
     *
     * @param uri the uri to the file selected by user, authorised to write.
     * @param backupFilePassword the password from which to derive the PBKDF2 key.
     * @return the number of accounts written to the backup file.
     */
    public ImportStats restoreDB(Uri uri, char[] backupFilePassword) throws WrongPasswordException, Exception {
        Log.i(TAG, "DB Restore URI: " + uri.toString());

        InputStream inputStream = null;
        CSVParser csvParser = null;
        InputStreamReader inputStreamReader = null;
        CipherInputStream encInputStream = null;

        ImportStats stats = null;

        try {
            //Open file
            inputStream = context.getContentResolver().openInputStream(uri);

            //Get salt
            byte[] salt = StreamUtil.readBytes(inputStream, CryptoUtil.HASH_SIZE / 8);

            //Get iv
            byte[] iv = StreamUtil.readBytes(inputStream, CryptoUtil.IV_SIZE / 8);

            //Generate key from password and salt
            byte[] key = cryptoUtil.generateKeyFromPassword(backupFilePassword, salt);

            //Get new cipher
            Cipher cipher = cryptoUtil.createCipherForDecryption(key, iv);

            //Create encrypted input stream
            encInputStream = new CipherInputStream(inputStream, cipher);

            //Read magic
            /**
             * Next 32 bytes are for the MAGIC header. The header is encrypted, and if we read it back correctly,
             * then we are decrypting ok...
             */
            byte[] magic = StreamUtil.readBytes(encInputStream, MAGIC.length);
            if (!Arrays.equals(MAGIC, magic)){
                throw new WrongPasswordException();
            }

            //Wrap a reader around the encrypted output stream
            inputStreamReader = new InputStreamReader(encInputStream, StandardCharsets.UTF_8);

            //Get CSV reader around reader, set header
            csvParser = CSVFormat.DEFAULT.withHeader(CSV_HEADER).withSkipHeaderRecord(true).parse(inputStreamReader);










//what happens when the header doesn't match?
//can we detect this?











            //Process CSV import, updating DB
            stats = importCSVData(csvParser);
        } catch (WrongPasswordException wpe) {
            throw wpe;
        } catch (Exception e) {
            Log.e(TAG, "Unable to restore DB from file: " + uri.getPath(), e);
            throw new Exception(e);
        } finally {
            try {
                inputStream.close();
            } catch (Throwable t){
                //consume
            }

            try {
                csvParser.close();
            } catch (Throwable t){
                //consume
            }
        }

        return stats;
    }

    public String getBackupFileName(){
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        return BACKUP_DB_FILE + " - " + formatter.format(new Date()) + "." + BACKUP_DB_EXT;
    }

    /**
     * Gets location of ext storage in FS. Handles cases where no SD card is present, emulated storage, etc.
     *
     * @return file of root of ext storage.
     */
    public File getExtStoragePath() {
        File fileList[] = new File("/storage/").listFiles();
        for (File file : fileList) {
            if (!file.getAbsolutePath().equalsIgnoreCase(Environment.getExternalStorageDirectory().getAbsolutePath()) && file.isDirectory() && file.canRead()) {
                return file;
            }
        }

        return Environment.getExternalStorageDirectory();
    }

    public static class ImportStats {
        public int successful = 0;
        public int failed = 0;

        public ImportStats(){
        }

        public ImportStats(int successful, int failed){
            this.successful = successful;
            this.failed = failed;
        }
    }

}
