[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=net.transgressoft%3Atransgressoft-commons&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=net.transgressoft%3Atransgressoft-commons)
# Transgressoft Commons

A reactive library for Kotlin & Java projects that implements the Publisher-Subscriber pattern, enabling more maintainable and decoupled systems through reactive programming principles.

## 📖 Overview

Transgressoft Commons provides a layer of abstraction for entities that follow a 'reactive' approach based on the Publisher-Subscriber pattern. This allows objects to subscribe to changes in others while maintaining clean boundaries.

The approach is inspired by object-oriented design principles where objects aren't merely passive data structures to be manipulated, but active entities with
their own behaviors and responsibilities. Instead of other objects directly manipulating an entity's state, they subscribe to its changes, creating a more
decoupled and maintainable system. Some of the books that have inspired this library
are [Object Thinking by David West](https://www.goodreads.com/book/show/43940.Object_Thinking)
and [Elegant Objects by Yegor Bugayenko](https://www.yegor256.com/elegant-objects.html)

**Key Features:**

* **Reactive Entities:** Implement entities that can notify subscribers of state changes.
* **Event-Driven Architecture:** Utilizes the Publisher-Subscriber pattern for decoupled communication.
* **Repository Pattern:** Provides flexible repository implementations for data management.
* **Automatic Persistence:** Offers JSON file repositories for seamless data persistence.
* **Reactive Primitives:** Wraps primitive data types for reactive behavior.
* **Asynchronous Processing:** Many actions are performed asynchronously.
* **Thread safety:** JsonFileRepository and FlexibleJsonFileRepository are thread safe.

## 📑 Table of Contents

- [Quick Start](#-quick-start)
- [Core Concepts](#-core-concepts)
- [Repository Pattern](#-repository-pattern)
- [Advanced Features](#-advanced-features)
- [Reactive Primitives](#-reactive-primitives)

## 🚀 Quick Start

Here's a simple example demonstrating the core reactive capabilities:

```kotlin
// Define a reactive entity
data class Tenant(override val id: Int) : ReactiveEntityBase<Int, Tenant>() {
    var name: String? = null
        set(value) {
            setAndNotify(value, field) { field = it }   // This triggers notifications
        }

    override val uniqueId = "$id-$name"
    override fun clone(): Tenant = copy()
    override fun toString(): String = "Tenant(id=$id, name=$name)"
}

// Create and subscribe
val tenant = Tenant(1)
val subscriber = EntityChangeSubscriber<Tenant, EntityChangeEvent<Int, Tenant>, Int>("subscriber").apply {
    addOnNextEventAction(UPDATE) {
        println("Received update event: $it")
    }
}
tenant.subscribe(subscriber)

// Changes automatically notify subscribers
tenant.name = "John Doe"
// Output: Received update event: Update(entities={1=Tenant(id=1, name=John Doe)}, oldEntities={1=Tenant(id=1, name=null)})
```

## 🏗️ Core Concepts

### Reactive Architecture

Transgressoft Commons is built around a fundamental principle: **objects should communicate through events rather than direct manipulation**. This creates several advantages:

1. **Decoupling** - Objects don't need direct references to modify each other
2. **Traceability** - All changes are explicitly published as events
3. **Consistency** - State changes automatically trigger notifications
4. **Persistence** - Events can be captured for audit trails or persistence

### Publisher-Subscriber Pattern

The library implements a robust Publisher-Subscriber pattern where:

- **Publishers** (your domain objects) emit events when their state changes
- **Subscribers** register interest in specific events and react accordingly
- **Events** carry information about what changed and previous state

### Entity Hierarchy

The architecture builds on two key interfaces:

```kotlin
// Base for all entities that can be identified and subscribed to
interface IdentifiableEntity<K> : TransEntity, Cloneable where K : Comparable<K> {
    val id: K
    val uniqueId: String
}

// Entities that can publish change events to subscribers
interface ReactiveEntity<K, R : ReactiveEntity<K, R>> : 
    IdentifiableEntity<K>,
    TransEventPublisher<EntityChangeEvent<K, R>>
```

When you implement `ReactiveEntityBase`, you get automatic event publishing through the simple `setAndNotify()` method, which handles the complexity of cloning, comparing, and notifying subscribers.

## 📚 Repository Pattern

The library includes a powerful implementation of the Repository pattern that integrates seamlessly with the reactive approach.

### Repository Types

#### VolatileRepository

For in-memory storage without persistence:

```kotlin
class UserRepository : VolatileRepository<String, User>("UserRepository")
```

#### JsonFileRepository

For JSON file-based persistence with automatic updates:

```kotlin
// Create your repository class
class PersonJsonFileRepository(file: File) : GenericJsonFileRepository<Int, Person>(
    file = file,
    mapSerializer = MapIntPersonSerializer,
    repositorySerializersModule = SerializersModule { /* Configuration */ },
    name = "PersonRepository"
)

// Usage example
val repository = PersonJsonFileRepository(File("persons.json"))
repository.add(Person(1, "Jane", 50000L, true))

// Reactive updates
val jane = repository.findById(1).orElseThrow()
jane.money = 55000L  // JSON file is updated automatically
```

#### FlexibleJsonFileRepository

For simpler storage of primitive values:

```kotlin
val repository = FlexibleJsonFileRepository(File("config.json"))

// Create reactive primitives
val appName = repository.createReactiveString("app.name", "MyApp")
val maxConnections = repository.createReactiveInt("max.connections", 100)

// Subscribe and update
appName.subscribe(configSubscriber)
appName.value = "NewAppName"  // Subscribers notified, JSON updated
```

## 🧩 Advanced Features

### Event Subscription

Subscribe to specific events from repositories or entities:

```kotlin
val subscriber = object : TransEventSubscriberBase<Person, CrudEvent<Int, Person>>("MySubscriber") {
    init {
        addOnNextEventAction(StandardCrudEvent.Type.CREATE) { event ->
            println("Entity created: ${event.entities}")
        }
        addOnNextEventAction(StandardCrudEvent.Type.UPDATE) { event ->
            val entityChangeEvent = event as EntityChangeEvent<Int, Person>
            println("Entity updated from ${entityChangeEvent.oldEntities} to ${entityChangeEvent.entities}")
        }
    }
}
```

### Batch Operations

Perform operations on multiple entities efficiently:

```kotlin
repository.runForMany(setOf(1, 2)) { person ->
    person.money = person.money!! * 1.1  // Give everyone a 10% raise
}
```

### Querying Repositories

Search for entities based on criteria:

```kotlin
val richPeople = repository.search { it.money!! > 60000L }
```

## 🧪 Reactive Primitives

The library provides reactive wrappers for primitive values:

```kotlin
val appName = ReactiveString("app.name", "MyApp")
val maxConnections = ReactiveInt("max.connections", 100)
val debugMode = ReactiveBoolean("debug.mode", false)

appName.subscribe(configSubscriber)
appName.value = "NewAppName"  // Triggers notification
```

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## 📄 License and attributions

Copyright (c) 2025 Octavio Calleya García.

Transgressoft Commons is free software under GNU GPL version 3 license and is available [here](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text).

This project uses [Jetbrain's coroutines](https://github.com/Kotlin/kotlinx.coroutines) and [serialization](https://github.com/Kotlin/kotlinx.serialization) libraries, and [Kotest](https://kotest.io/) for testing.