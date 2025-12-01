[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/octaviospain/transgressoft-commons)
![Maven Central Version](https://img.shields.io/maven-central/v/net.transgressoft/transgressoft-commons-api)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/octaviospain/transgressoft-commons/.github%2Fworkflows%2Fmaster.yml?logo=github)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=net.transgressoft%3Atransgressoft-commons&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=net.transgressoft%3Atransgressoft-commons)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=net.transgressoft%3Atransgressoft-commons&metric=bugs)](https://sonarcloud.io/summary/new_code?id=net.transgressoft%3Atransgressoft-commons)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=net.transgressoft%3Atransgressoft-commons&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=net.transgressoft%3Atransgressoft-commons)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=net.transgressoft%3Atransgressoft-commons&metric=coverage)](https://sonarcloud.io/summary/new_code?id=net.transgressoft%3Atransgressoft-commons)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=net.transgressoft%3Atransgressoft-commons&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=net.transgressoft%3Atransgressoft-commons)
# Transgressoft Commons

A reactive library for Kotlin & Java projects that implements the Publisher-Subscriber pattern, enabling more maintainable and decoupled systems through reactive programming principles.

## 📖 Overview

Transgressoft Commons provides a framework for entities that follow a 'reactive' approach based on the Publisher-Subscriber pattern. This allows objects to subscribe to changes in others while maintaining clean boundaries and separation of concerns.

The approach is inspired by object-oriented design principles where entities aren't merely passive data structures, but active objects with their own behaviors and responsibilities. Instead of other objects directly manipulating an entity's state, they subscribe to its changes, creating a more decoupled and maintainable system.

### What Makes Transgressoft Commons Different?

While libraries like [Kotlin Flow](https://github.com/Kotlin/kotlinx.coroutines), [RxJava](https://github.com/ReactiveX/RxJava), and [Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained) offer powerful tools for reactive programming and event handling, Transgressoft Commons provides a higher-level, opinionated framework designed to accelerate development with out-of-the-box features.

Instead of being a general-purpose toolkit, it offers a specific, entity-centric approach to reactivity:

*   **Entity-First Reactivity:** At its core, the library is built around the concept of "Reactive Entities." This encourages a more object-oriented design where your domain objects are inherently reactive, automatically publishing events when their state changes.
*   **Automated Persistence:** A key innovation is the `JsonRepository`, which provides automatic, thread-safe, and debounced JSON serialization. This means you can have a persistent, reactive collection of objects with minimal boilerplate code.
*   **Simplified API:** By providing a more focused API, Transgressoft Commons simplifies the development of reactive systems. It offers a clear path for building event-driven applications without the steep learning curve of more complex reactive libraries.

In essence, Transgressoft Commons is a lightweight framework that builds upon the power of libraries like [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) and [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) to provide a ready-to-use solution for building reactive, persistent applications in a clean, decoupled, and object-oriented way.

**Requirements:**
* Java 17+
* Kotlin 2.1.10+

**Key Features:**

* **Event-Driven Architecture:** Built around the Publisher-Subscriber pattern for loosely coupled communication
* **Reactive Entities:** Objects that automatically notify subscribers when their state changes
* **Automated JSON Serialization:** Repository implementations that persist entity changes to JSON files automatically
* **Thread-Safe Operations:** Concurrent operations are handled safely with debounced file I/O
* **Repository Pattern:** Flexible data access through repositories with powerful querying capabilities
* **Reactive Primitives:** Wrapper types that make primitive values observable
* **Asynchronous Processing:** Non-blocking operations using Kotlin coroutines
* **Java Interoperability:** Designed to work seamlessly from both Kotlin and Java code

## 📑 Table of Contents

- [Core Concepts: Reactive Event System](#-core-concepts-reactive-event-system)
- [Core Concepts: JSON Serialization](#-core-concepts-json-serialization)
- [Java Interoperability](#java-interoperability)
- [Contributing](#-contributing)
- [License and Attributions](#-license-and-attributions)

## 🔄 Core Concepts: Reactive Event System

The heart of Transgressoft Commons is its reactive event system, where objects communicate through events rather than direct manipulation.

### Reactive Primitives

The simplest way to understand the reactive approach is through the primitive wrappers. These allow basic values to participate in the reactive system:

```kotlin
// Create a reactive primitive with an ID and initial value
val appName: ReactivePrimitive<String> = ReactiveString("MyApp")

// Subscribe to changes with a simple lambda function
val subscription = appName.subscribe { event ->
    val oldValue = event.oldEntities.values.first().value
    val newValue = event.entities.values.first().value
    println("Config changed: $oldValue -> $newValue")
}

// When value changes, subscribers are automatically notified
appName.value = "NewAppName"  
// Output: Config changed: MyApp -> NewAppName

// Later, if needed, you can cancel the subscription
subscription.cancel()
```

### Reactive Entities

Any object can become reactive by implementing the `ReactiveEntity` interface, typically by extending `ReactiveEntityBase`:

```kotlin
// Define a reactive entity
data class Person(override val id: Int, var name: String) : ReactiveEntityBase<Int, Person>() {
    var salary: Double = 0.0
        set(value) {
            // mutateAndPublish handles the notification logic
            mutateAndPublish(value, field) { field = it }
        }

    override val uniqueId: String = "person-$id"
    override fun clone(): Person = copy()
}

// Create a person and subscribe to changes using a Consumer
val person: ReactiveEntity<Int, Person> = Person(1, "Alice")
val subscription = person.subscribe { event ->
    val entity = event.entities.values.first()
    val oldEntity = event.oldEntities.values.first()
    println("Salary updated from ${oldEntity.salary} to ${entity.salary}")
}

// Changes trigger notifications
person.salary = 75000.0
// Output: Salary updated from 0.0 to 75000.0
```

### Repository Subscriptions

Repositories manage collections of entities while maintaining the reactive behavior:

```kotlin
// Create a repository for Person entities
val repository: Repository<Int, Person> = VolatileRepository<Int, Person>("PersonRepository")

// Subscribe to CRUD events with lambda functions
val createSubscription = repository.subscribe(StandardCrudEvent.Type.CREATE) { event ->
    println("Entities created: ${event.entities.values}")
}

val updateSubscription = repository.subscribe(StandardCrudEvent.Type.UPDATE) { event ->
    val changeEvent = event as EntityChangeEvent<Int, Person>
    println("Entities updated:")
    changeEvent.entities.forEach { (id, entity) ->
        val oldEntity = changeEvent.oldEntities[id]
        println("  $id: $oldEntity -> $entity")
    }
}

val deleteSubscription = repository.subscribe(StandardCrudEvent.Type.DELETE) { event ->
    println("Entities deleted: ${event.entities.values}")
}

// Repository operations trigger events
repository.add(Person(1, "Alice"))
// Output: Entities created: [Person(id=1, name=Alice)]

// Entity changes are propagated through the repository
repository.runForSingle(1) { person ->
    person.salary = 80000.0
}
// Output: Entities updated:
//   1: Person(id=1, name=Alice, salary=0.0) -> Person(id=1, name=Alice, salary=80000.0)
```

### Extensibility

The library is designed to be extensible, allowing you to create custom publishers and subscribers:

1. **Custom Publishers:** Implement `TransEventPublisher<E>` or extend `TransEventPublisherBase<E>` to create new event sources
2. **Custom Subscribers:** Implement `TransEventSubscriber<T, E>` or extend `TransEventSubscriberBase<T, E>` to handle events
3. **Custom Events:** Create new event types by implementing the `TransEvent` interface

For Java compatibility or more complex subscription handling, you can also implement a full subscriber:

```kotlin
// Create a subscriber with more control over lifecycle events
val repositorySubscriber: TransEventSubscriber<Person, CrudEvent<Int, Person>> = 
    object : TransEventSubscriberBase<Person, CrudEvent<Int, Person>>("RepositorySubscriber") {
        init {
            // Set up subscription actions
            addOnNextEventAction(StandardCrudEvent.Type.CREATE) { event ->
                println("Entities created: ${event.entities.values}")
            }
            
            addOnErrorEventAction { error ->
                println("Error occurred: $error")
            }
            
            addOnCompleteEventAction {
                println("Publisher has completed sending events")
            }
        }
    }

// Subscribe using the full subscriber
repository.subscribe(repositorySubscriber)
```

The core API classes that library consumers will typically use:

- `TransEventPublisher` - Interface for objects that publish events
- `TransEventSubscriber` - Interface for objects that subscribe to events
- `ReactiveEntity` - Interface for entities that can be observed
- `Repository` - Interface for collections of entities with CRUD operations
- `CrudEvent` - Events representing repository operations
- `JsonRepository` - Interface for repositories with JSON persistence

## 💾 Core Concepts: JSON Serialization

Transgressoft Commons provides automatic JSON serialization for repository operations, making persistence seamless. The library uses [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for JSON processing, so users should familiarize themselves with this library to effectively create serializers for their entity types.

### JSON File Repositories

The library includes implementations that automatically persist entities to JSON files:

```kotlin
// Define a serializer for your entity type using kotlinx.serialization
@Serializable
data class Person(val id: Int, val name: String, var salary: Double = 0.0) : ReactiveEntityBase<Int, Person>() {
    override val uniqueId: String = "person-$id"
    override fun clone(): Person = copy()
}

// Create a map serializer for your entities
object MapIntPersonSerializer : KSerializer<Map<Int, Person>> {
    // Serialization implementation details
}

// Define your repository class
class PersonJsonFileRepository(file: File) : JsonFileRepository<Int, Person>(
    name = "PersonRepository",
    file = file,
    mapSerializer = MapIntPersonSerializer
)

// Create and use the repository
val jsonRepository: JsonRepository<Int, Person> = PersonJsonFileRepository(File("persons.json"))
jsonRepository.add(Person(1, "Alice"))
jsonRepository.add(Person(2, "Bob"))

// When entities change, the JSON file is automatically updated
jsonRepository.runForSingle(1) { person ->
    person.salary = 85000.0
}

// Changes are debounced to prevent excessive file operations
```

### Flexible JSON Repository

For simpler use cases, the library provides a flexible repository for primitive values:

```kotlin
// Create a repository for configuration values
val configRepository  = FlexibleJsonFileRepository(File("config.json"))

// Create reactive primitives in the repository
val serverName: ReactivePrimitive<String> = configRepository.createReactiveString("server.name", "MainServer")
val maxConnections: ReactivePrimitive<Int> = configRepository.createReactiveInt("max.connections", 100)
val debugMode: ReactivePrimitive<Boolean> = configRepository.createReactiveBoolean("debug.mode", false)

// When values change, they are automatically persisted
maxConnections.value = 150
debugMode.value = true
// The JSON file is updated with the new values
```

### Key Benefits of Automatic Serialization

1. **Transparent Persistence** - No need to manually save changes
2. **Optimized I/O** - Changes are debounced to reduce disk operations
3. **Thread Safety** - Concurrent operations are handled safely
4. **Consistency** - Repository and file are always in sync

### Java Interoperability

Transgressoft Commons is designed to work seamlessly from both Kotlin and Java code. Below are examples demonstrating how to use the library from Java:

#### 1. Working with Reactive Primitives in Java

```java
// Create a reactive primitive with an ID and initial value
var appName = new ReactiveString("app.name", "MyApp");

// Subscribe to changes
var subscription = appName.subscribe(event -> {
    var oldValue = event.getOldEntities().values().iterator().next().getValue();
    var newValue = event.getEntities().values().iterator().next().getValue();
    System.out.println("Config changed: " + oldValue + " -> " + newValue);
});

// When value changes, subscribers are automatically notified
appName.setValue("NewAppName");
// Output: Config changed: MyApp -> NewAppName

// Later, if needed, you can cancel the subscription
subscription.cancel();
```

#### 2. Working with Reactive Entities in Java

```java
// Use an existing Kotlin-defined reactive entity class
// This example uses the Person class which implements ReactiveEntityBase
var person = new Person(1, "Alice", 0L, true);

// Subscribe to changes
var subscription = person.subscribe(event -> {
    var newPerson = event.getEntities().values().iterator().next();
    var oldPerson = event.getOldEntities().values().iterator().next();
    System.out.println("Name changed from " + oldPerson.getName() + " to " + newPerson.getName());
});

// Changes trigger notifications
person.setName("John");
// Output: Name changed from Alice to John

subscription.cancel();
```

#### 3. Using Repository Subscriptions in Java

```java
// Create a repository for Person entities
var repository = new VolatileRepository<Int, Person>();

// Subscribe to all events from the repository
var subscription = repository.subscribe(event -> {
    System.out.println("Entities affected: " + event.getEntities().values());
});

// Repository operations trigger events
repository.add(new Person(1, "Alice", 0L, true));
// Output: Entities affected: [Person(id=1, name=Alice, money=0, morals=true)]

// Entity changes are propagated through the repository
repository.runForSingle(1, person -> person.setName("John"));
// Output: Entities affected: [Person(id=1, name=John, money=0, morals=true)]

subscription.cancel();
```

#### 4. Working with Flexible JSON Repository in Java

```java
// Create a JSON file repository
var configFile = new File("config.json");
var configRepository = new FlexibleJsonFileRepository(configFile);

// Create reactive primitives in the repository
var serverName = configRepository.createReactiveString("server.name", "MainServer");
var maxConnections = configRepository.createReactiveInt("max.connections", 100);
var debugMode = configRepository.createReactiveBoolean("debug.mode", false);

// When values change, they are automatically persisted
maxConnections.setValue(150);
debugMode.setValue(true);
serverName.setValue("BackupServer");

// Close to ensure all changes are written
configRepository.close();

// Changes persist across repository instances
var reloadedRepo = new FlexibleJsonFileRepository(configFile);
// Values remain: serverName="BackupServer", maxConnections=150, debugMode=true
```

For complete working examples, see [JavaInteroperabilityTest.java](https://github.com/octaviospain/transgressoft-commons/blob/master/transgressoft-commons-core/src/test/java/net/transgressoft/commons/JavaInteroperabilityTest.java) in the repository.

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## 📄 License and Attributions

Copyright (c) 2025 Octavio Calleya García.

Transgressoft Commons is free software under GNU GPL version 3 license and is available [here](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text).

This project uses:
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) for asynchronous programming
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) for JSON processing
- [Kotest](https://kotest.io/) for testing

The approach is inspired by books including [Object Thinking by David West](https://www.goodreads.com/book/show/43940.Object_Thinking) and [Elegant Objects by Yegor Bugayenko](https://www.yegor256.com/elegant-objects.html).
