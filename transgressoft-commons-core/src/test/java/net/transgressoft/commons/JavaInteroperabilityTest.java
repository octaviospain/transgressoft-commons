package net.transgressoft.commons;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.TestCoroutineDispatchersKt;
import kotlinx.coroutines.test.TestCoroutineScheduler;
import net.transgressoft.commons.event.ReactiveScope;
import net.transgressoft.commons.persistence.json.FlexibleJsonFileRepository;
import net.transgressoft.commons.persistence.json.primitives.ReactiveString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class that verifies the Java interoperability examples from the README.md.
 * This demonstrates that the library can be used effectively from Java code.
 */
class JavaInteroperabilityTest {

    @TempDir
    Path tempDir;
    
    private static TestCoroutineScheduler scheduler;

    @BeforeAll
    @ExperimentalCoroutinesApi
    static void setupTestDispatchers() {
        // Setup a TestCoroutineScheduler that allows us to control the virtual time in tests
        scheduler = new TestCoroutineScheduler();
        // Create an UnconfinedTestDispatcher which processes coroutines eagerly and can be controlled by the scheduler
        CoroutineDispatcher testDispatcher = TestCoroutineDispatchersKt.UnconfinedTestDispatcher(scheduler, null);
        // Create a test scope with the controlled dispatcher for deterministic testing
        CoroutineScope testScope = CoroutineScopeKt.CoroutineScope(testDispatcher);
        // Override the default reactive scopes to use our test scope for predictable test execution
        ReactiveScope.INSTANCE.setFlowScope(testScope);
        ReactiveScope.INSTANCE.setIoScope(testScope);
    }
    
    @AfterAll
    static void resetDispatchers() {
        ReactiveScope.INSTANCE.resetDefaultFlowScope();
        ReactiveScope.INSTANCE.resetDefaultIoScope();
    }

    /**
     * Test reactive primitives as shown in the README.md Java example.
     */
    @Test
    @DisplayName("Reactive primitives test")
    void reactivePrimitivesTest() {
        // Create a reactive primitive with an ID and initial value
        var appName = new ReactiveString("app.name", "MyApp");
        
        // Verify initial value
        assertEquals("MyApp", appName.getValue());
        
        // Set up test verification
        String[] oldValueHolder = new String[1];
        String[] newValueHolder = new String[1];
        
        // Subscribe to changes using a Java Consumer
        var subscription = appName.subscribe(event -> {
            oldValueHolder[0] = event.getOldEntities().values().iterator().next().getValue();
            newValueHolder[0] = event.getEntities().values().iterator().next().getValue();
        });

        // When value changes, subscriber action is triggered
        appName.setValue("NewAppName");
        
        // Advance the test scheduler to process all pending coroutines
        scheduler.advanceUntilIdle();

        // Verify
        await().atMost(3, SECONDS).untilAsserted(() -> {
            assertEquals("MyApp", oldValueHolder[0]);
            assertEquals("NewAppName", newValueHolder[0]);
            assertEquals("NewAppName", appName.getValue());
        });

        subscription.cancel();
    }

    /**
     * Tests reactive entity pattern shown in the README.md Java example.
     */
    @Test
    @DisplayName("Reactive entity test")
    void reactiveEntityTest() {
        // Create a person and subscribe to changes
        var person = new Person(1, "Alice", 0L, true);
        
        // Verify initial state
        assertEquals(1, person.getId());
        assertEquals("Alice", person.getName());
        assertEquals(0L, person.getMoney());
        
        var oldName = new String[1];
        var newName = new String[1];

        var subscription = person.subscribe(
            event -> {
                var newPerson = event.getEntities().values().iterator().next();
                newName[0] = newPerson.getName();
                var oldPerson = event.getOldEntities().values().iterator().next();
                oldName[0] = oldPerson.getName();
            }
        );

        // Changes trigger notifications
        person.setName("John");
        
        // Advance the test scheduler to process all pending coroutines
        scheduler.advanceUntilIdle();

        // Wait for notification and verify
        await().atMost(3, SECONDS).untilAsserted(() -> {
            assertEquals("Alice", oldName[0]);
            assertEquals("John", newName[0]);
        });

        subscription.cancel();
    }

    /**
     * Tests repository pattern shown in the README.md Java example.
     */
    @Test
    @DisplayName("Repository test")
    void repositoryTest() {
        // Create a repository for Person entities
        var repository = new VolatilePersonRepository();
        
        var eventEntities = new ArrayList<Person>();

        // Subscribe to events
        var subscription = repository.subscribe(
            event -> eventEntities.addAll(event.getEntities().values()));

        repository.add(new Person(1, "Alice", 0L, true));
        
        // Advance the test scheduler to process all pending coroutines
        scheduler.advanceUntilIdle();

        // Wait for notification and verify
        await().atMost(3, SECONDS).untilAsserted(() -> {
            assertEquals(1, eventEntities.size());
            assertEquals("Alice", eventEntities.get(0).getName());
        });

        eventEntities.clear();

        // Update through repository
        repository.runForSingle(1, person -> person.setName("John"));
        
        // Advance the test scheduler to process all pending coroutines
        scheduler.advanceUntilIdle();

        // Wait for notification and verify
        await().atMost(3, SECONDS).untilAsserted(() ->
                assertEquals("John", eventEntities.get(0).getName()));

        // Check the repository directly
        var storedPerson = repository.findById(1).get();
        assertEquals("John", storedPerson.getName());

        subscription.cancel();
    }

    /**
     * Tests JSON repository functionality shown in the README.md Java example.
     */
    @Test
    @DisplayName("Flexible Json repository test")
    void flexibleRepositoryTest() throws Exception {
        var configFile = new File(tempDir.toFile(), "config.json");
        assertTrue(configFile.createNewFile());
        
        var configRepository = new FlexibleJsonFileRepository(configFile);
        
        // Create reactive primitives in the repository
        var serverName = configRepository.createReactiveString("server.name", "MainServer");
        var maxConnections = configRepository.createReactiveInt("max.connections", 100);
        var debugMode = configRepository.createReactiveBoolean("debug.mode", false);
        
        assertEquals("MainServer", serverName.getValue());
        assertEquals(100, maxConnections.getValue());
        assertFalse(debugMode.getValue());
        
        // When values change, they are automatically persisted
        maxConnections.setValue(150);
        debugMode.setValue(true);
        serverName.setValue("BackupServer");
        
        // Advance the test scheduler to process all pending coroutines
        scheduler.advanceUntilIdle();

        // Verify changes
        await().atMost(3, SECONDS).untilAsserted(() -> {
            assertEquals("BackupServer", serverName.getValue());
            assertEquals(150, maxConnections.getValue());
            assertTrue(debugMode.getValue());
        });

        configRepository.close();

        // Verify persistence by creating a new repository instance
        var reloadedRepo = new FlexibleJsonFileRepository(configFile);
        
        // Advance the test scheduler to process all pending coroutines
        scheduler.advanceUntilIdle();
        
        // Check that values were persisted
        await().atMost(3, SECONDS).untilAsserted(() -> {
            assertEquals("BackupServer", reloadedRepo.findById("server.name").get().getValue());
            assertEquals(150, reloadedRepo.findById("max.connections").get().getValue());
            assertEquals(Boolean.TRUE, reloadedRepo.findById("debug.mode").get().getValue());
        });

        reloadedRepo.close();
        configFile.deleteOnExit();
    }
}