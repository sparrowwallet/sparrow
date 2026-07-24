package com.sparrowwallet.sparrow.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;

public class DbPersistenceTest {
    private Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sprw-persistence");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(tempDir != null) {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private File buildWalletFile(String... schemaObjects) throws Exception {
        String base = tempDir.resolve("wallet").toString();
        try(Connection connection = DriverManager.getConnection("jdbc:h2:" + base + ";DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=FALSE", "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("create schema wallet_master");
            statement.execute("create table wallet_master.wallet(id identity not null, name varchar(255) not null)");
            for(String ddl : schemaObjects) {
                statement.execute(ddl);
            }
        }
        return new File(base + ".mv.db");
    }

    private void assertRejected(File walletFile) {
        Storage storage = new Storage(PersistenceType.DB, walletFile);
        Assertions.assertThrows(StorageException.class, storage::loadUnencryptedWallet);
    }

    @Test
    public void linkedTableRejectedWithoutExecuting() throws Exception {
        File marker = tempDir.resolve("output.csv").toFile();
        String target = "jdbc:h2:" + tempDir.resolve("external") + ";INIT=CREATE TABLE IF NOT EXISTS PUB(ID INT)\\;CALL CSVWRITE('" + marker.getAbsolutePath() + "','SELECT 1')";
        File walletFile = buildWalletFile("create force linked table wallet_master.remote('','" + target.replace("'", "''") + "','sa','','PUB')");
        marker.delete();

        assertRejected(walletFile);
        Assertions.assertFalse(marker.exists(), "linked table connected during load");
    }

    @Test
    public void triggerRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.t(id int)",
                "create force trigger wallet_master.tg before insert on wallet_master.t for each row call \"com.sparrowwallet.Missing\""));
    }

    @Test
    public void generatedColumnRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.g(id int, computed int generated always as (id + 1))"));
    }

    @Test
    public void columnDefaultFunctionRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.d(id int, x int default LENGTH(FILE_READ('/etc/hostname')))"));
    }

    @Test
    public void constantRejected() throws Exception {
        assertRejected(buildWalletFile("create constant wallet_master.k value 1"));
    }

    @Test
    public void javaObjectColumnRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.j(id int, obj OTHER)"));
    }

    @Test
    public void checkConstraintRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.c(id int check (LENGTH(FILE_READ('/etc/hostname')) > 0))"));
    }

    @Test
    public void domainRejected() throws Exception {
        assertRejected(buildWalletFile("create domain wallet_master.dm as int default 0"));
    }
}
