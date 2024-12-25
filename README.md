# cedi [tsedi] – cats-effect dependency injection library

## Checklist what you want from a DI library

* [ ] Singleton semantics: dependencies are instantiated only once.
* [ ] Dependencies that are registered in DI container are lazy and instantiated on the first access.
* [ ] Dependencies are instantiated in the order of their dependencies and shut down in reverse order.
* [ ] Dependencies are instantiated in parallel if they don't depend on each other.
* [ ] Dependencies are exposed and can be accessed outside the container.
* [ ] When no dependencies are accessed, no dependencies are instantiated.
* [ ] Dependencies can be queried by their type / by the handle that is created when a dependency is registered.