# Proof Of Concept: A fully type safe version of [Anorm](http://www.playframework.org/documentation/2.0.4/ScalaAnorm)

What if Play could:

- Validate the syntax of your SQL statements?
- Validate the structure of your SQL statements over the real database schema?
- Extract the type informations from this schema and use them to enrich the Scala types?

Now, with Scala 2.10 and macro __it is possible__. This demo project is a demonstration of what a typed version of Anorm would look like.

## Prerequisites

Just install the latest [sbt](http://www.scala-sbt.org), or download the latest [Play package](http://www.playframework.org/). 

Then clone the repo and launch the Play console:

```
$ cd anormtyped-demo
$ sbt

[info] Loading project definition from /Volumes/Data/gbo/Desktop/anormtyped-demo/project        
[info] Set current project to anormtyped-demo (in build file:/Volumes/Data/gbo/Desktop/anormtyped-demo/)
       _            _
 _ __ | | __ _ _  _| |
| '_ \| |/ _' | || |_|
|  __/|_|\____|\__ (_)
|_|            |__/

play! 2.1-RC2 (using Java 1.6.0_37 and Scala 2.10.0), http://www.playframework.org

> Type "help play" or "license" for more information.
> Type "exit" or use Ctrl+D to leave this console.

[anormtyped-demo] $
```

You also need to install [PostgresSQL](http://www.postgresql.org/), and to create a new database that you will initialize with the content of the `world.sql` file available in the application root folder.

> Unfortunately in this POC we can't use Play _evolutions_ to update the database schema, because the evolution scripts are applied on the application start, and queries are type checked at compilation time... This is one of the several problems to solve

Check the main Play configuration file (in `conf/application.conf`) and adjust the JDBC settings if needed:

```properties
db.default.driver="org.postgresql.Driver"
db.default.url="jdbc:postgresql:world"
db.default.user="gbo"
db.default.password=""
```

Once the database is ready, you can run the application:

```
[anormtyped-demo] $ run
```

And open the running application at http://localhost:9000/

![](https://raw.github.com/guillaumebort/anormtyped-demo/master/screenshots/app.png)

Nothing really fancy here, it's a very basic Play application. It doesn't even use the template engine to keep everything into a single file.

## Meet the `TypedSQL` queries

The interesting parts are the `TypedSQL` expressions that can be found at the top of the controller code (in `app/controllers/Application.scala`):

```scala
val allCountries = TypedSQL(
  """
    select code, name, continent, indepyear, population, headofstate
    from country
  """
).map(Country.tupled)

val findPopulationByCode = TypedSQL(
  """
    select population from country where code = ?
  """
)

val findAllCitiesInContinent = TypedSQL(
  """
    select city.id, city.name from city 
    join country on city.countrycode = country.code 
    where country.continent like ?
  """ 
)

val insertCity = TypedSQL(
  """
    insert into city (name, countrycode) 
    values (?,?)
  """
)

val deleteCity = TypedSQL(
  """
    delete from city where id = ?
  """
)
```

They look a bit like the plain old Anorm `SQL` queries, but you see that they don't define any _parser_. This is because the __in__ and __out__ types for the queries are inferred from the database itself.

For example, the `findPopulationByCode` query, will act as a function taking a single `String` parameter, and returning a `List[Int]`. Here `String` has been automatically inferred from the database schema where `country.code` is defined as `VARCHAR`. The same apply for `country.population` which has been inferred as `Int`:

```scala
scala> findPopulationByCode(88)
error: type mismatch; found : Int(88) required: String

scala> findPopulationByCode("FRA").singleOpt
res0: Option[Int] = 59225700
```

## See the magic

Now let's check that everything is type safe, and properly checked at compilation time.

Try to make a syntax error in any SQL statement, such as:

```scala
val findPopulationByCode = TypedSQL(
  """
    selXect population from country where code = ?
  """
)
```

Refresh your browser and see the error message:

![](https://raw.github.com/guillaumebort/anormtyped-demo/master/screenshots/syntax_error.png)

It will also detect a mistake in the schema, such as:

```scala
val findPopulationByCode = TypedSQL(
  """
    select population from country where id = ?
  """
)
```

![](https://raw.github.com/guillaumebort/anormtyped-demo/master/screenshots/schema_error.png)

And of course the type errors:

```scala
val findPopulationByCode = TypedSQL(
  """
    select population from country where surfacearea = ?
  """
)
```

![](https://raw.github.com/guillaumebort/anormtyped-demo/master/screenshots/type_error.png)

Here the SQL statement compiles properly, but it's signature change accordingly to the new types used in the query.

## Detect incompatible database schema changes

It will also help you to avoid errors generated by an incompatible database schema change. First, fix all the pending errors you introduced just before, and alter the _world_ database schema in the __PSQL__ console:

```SQL
ALTER TABLE country ALTER COLUMN population DROP NOT NULL
``` 

Here we changed the `country.population` column to accept __null__ values. So of course, it should change something at our code level. Go to the Play console, and recompile the application (first clean the app to force the recompilation even if your source files didn't changed):

```
[anormtyped-demo] $ clean
[success] Total time: 0 s, completed Jan 16, 2013 11:08:38 PM
[anormtyped-demo] $ compile
[info] Updating {file:/Volumes/Data/gbo/Desktop/anormtyped-demo/}anormtyped-demo...
[info] Done updating.                                                             
[info] Compiling 5 Scala sources and 1 Java source to /Volumes/Data/gbo/Desktop/anormtyped-demo/target/scala-2.10/classes...
[error] /Volumes/Data/gbo/Desktop/anormtyped-demo/app/controllers/Application.scala:27: type mismatch;
[error]  found   : ((String, String, String, Option[Int], Int, Option[String])) => controllers.Application.Country
[error]  required: ((String, String, String, Option[Int], Option[Int], Option[String])) => ?
[error]   ).map(Country.tupled)
[error]                 ^
[error] one error found
[error] (anormtyped-demo/compile:compile) Compilation failed
[error] Total time: 2 s, completed Jan 16, 2013 11:08:43 PM
```

Yeah, our previous `Int` type has been changed to `Option[Int]` accordingly to the new database Schema. 

__Time to fix our code!__

