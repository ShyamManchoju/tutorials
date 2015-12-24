package org.baeldung.spring.data.cassandra.repository;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.utils.UUIDs;
import com.google.common.collect.ImmutableSet;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.thrift.transport.TTransportException;
import org.baeldung.spring.data.cassandra.config.CassandraConfig;
import org.baeldung.spring.data.cassandra.model.Book;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cassandra.core.cql.CqlIdentifier;
import org.springframework.data.cassandra.core.CassandraAdminOperations;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = CassandraConfig.class)
public class CassandraTemplateIntegrationTest {

    private static final Log LOGGER = LogFactory.getLog(CassandraTemplateIntegrationTest.class);

    public static final String KEYSPACE_CREATION_QUERY = "CREATE KEYSPACE IF NOT EXISTS testKeySpace " + "WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '3' };";

    public static final String KEYSPACE_ACTIVATE_QUERY = "USE testKeySpace;";

    public static final String DATA_TABLE_NAME = "book";

    @Autowired
    private CassandraAdminOperations adminTemplate;

    @Autowired
    private CassandraOperations cassandraTemplate;

    @BeforeClass
    public static void startCassandraEmbedded() throws InterruptedException, TTransportException, ConfigurationException, IOException {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        Cluster cluster = Cluster.builder().addContactPoints("127.0.0.1").withPort(9142).build();
        LOGGER.info("Server Started at 127.0.0.1:9142... ");
        Session session = cluster.connect();
        session.execute(KEYSPACE_CREATION_QUERY);
        session.execute(KEYSPACE_ACTIVATE_QUERY);
        LOGGER.info("KeySpace created and activated.");
        Thread.sleep(5000);
    }

    @Before
    public void createTable() throws InterruptedException, TTransportException, ConfigurationException, IOException {
        adminTemplate.createTable(true, CqlIdentifier.cqlId(DATA_TABLE_NAME), Book.class, new HashMap<String, Object>());
    }

    @Test
    public void whenSavingBook_thenAvailableOnRetrieval() {
        Book javaBook = new Book(UUIDs.timeBased(), "Head First Java", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        cassandraTemplate.insert(javaBook);
        Select select = QueryBuilder.select().from("book").where(QueryBuilder.eq("title", "Head First Java")).and(QueryBuilder.eq("publisher", "O'Reilly Media")).limit(10);
        Book retrievedBook = cassandraTemplate.selectOne(select, Book.class);
        assertEquals(javaBook.getId(), retrievedBook.getId());
    }

    @Test
    public void whenSavingBooks_thenAllAvailableOnRetrieval() {
        Book javaBook = new Book(UUIDs.timeBased(), "Head First Java", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        Book dPatternBook = new Book(UUIDs.timeBased(), "Head Design Patterns", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        List<Book> bookList = new ArrayList<>();
        bookList.add(javaBook);
        bookList.add(dPatternBook);
        cassandraTemplate.insert(bookList);

        Select select = QueryBuilder.select().from("book").limit(10);
        List<Book> retrievedBooks = cassandraTemplate.select(select, Book.class);
        assertThat(retrievedBooks.size(), is(2));
        assertEquals(javaBook.getId(), retrievedBooks.get(0).getId());
        assertEquals(dPatternBook.getId(), retrievedBooks.get(1).getId());
    }

    @Test
    public void whenUpdatingBook_thenShouldUpdatedOnRetrieval() {
        Book javaBook = new Book(UUIDs.timeBased(), "Head First Java", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        cassandraTemplate.insert(javaBook);
        Select select = QueryBuilder.select().from("book").limit(10);
        Book retrievedBook = cassandraTemplate.selectOne(select, Book.class);
        retrievedBook.setTags(ImmutableSet.of("Java", "Programming"));
        cassandraTemplate.update(retrievedBook);
        Book retrievedUpdatedBook = cassandraTemplate.selectOne(select, Book.class);
        assertEquals(retrievedBook.getTags(), retrievedUpdatedBook.getTags());
    }

    @Test
    public void whenDeletingASelectedBook_thenNotAvailableOnRetrieval() throws InterruptedException {
        Book javaBook = new Book(UUIDs.timeBased(), "Head First Java", "OReilly Media", ImmutableSet.of("Computer", "Software"));
        cassandraTemplate.insert(javaBook);
        cassandraTemplate.delete(javaBook);
        Select select = QueryBuilder.select().from("book").limit(10);
        Book retrievedUpdatedBook = cassandraTemplate.selectOne(select, Book.class);
        assertNull(retrievedUpdatedBook);
    }

    @Test
    public void whenDeletingAllBooks_thenNotAvailableOnRetrieval() {
        Book javaBook = new Book(UUIDs.timeBased(), "Head First Java", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        Book dPatternBook = new Book(UUIDs.timeBased(), "Head Design Patterns", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        cassandraTemplate.insert(javaBook);
        cassandraTemplate.insert(dPatternBook);
        cassandraTemplate.deleteAll(Book.class);
        Select select = QueryBuilder.select().from("book").limit(10);
        Book retrievedUpdatedBook = cassandraTemplate.selectOne(select, Book.class);
        assertNull(retrievedUpdatedBook);
    }

    @Test
    public void whenAddingBooks_thenCountShouldBeCorrectOnRetrieval() {
        Book javaBook = new Book(UUIDs.timeBased(), "Head First Java", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        Book dPatternBook = new Book(UUIDs.timeBased(), "Head Design Patterns", "O'Reilly Media", ImmutableSet.of("Computer", "Software"));
        cassandraTemplate.insert(javaBook);
        cassandraTemplate.insert(dPatternBook);
        long bookCount = cassandraTemplate.count(Book.class);
        assertEquals(2, bookCount);
    }

    @After
    public void dropTable() {
        adminTemplate.dropTable(CqlIdentifier.cqlId(DATA_TABLE_NAME));
    }

    @AfterClass
    public static void stopCassandraEmbedded() {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }
}