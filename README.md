# New MapRoulette
Version 2 of MapRoulette

[![Build Status](https://travis-ci.org/maproulette/maproulette2.svg?branch=master)](https://travis-ci.org/maproulette/maproulette2)

[![Heroku](http://heroku-badge.herokuapp.com/?app=maproulette2&style=flat&svg=1)](http://maproulette2.herokuapp.com/)

Welcome to New MapRoulette, the powerful & popular bug fixing tool (or is it a game?) for OpenStreetMap.

This README deals with development related topics only. If you are interested in contributing to OpenStreetMap by fixing some bugs through MapRoulette, just head over to [the MapRoulette web site](http://maproulette.org) and get started - it should be pretty self explanatory.

That said, read on if you want to contribute to MapRoulette development and are ready to deploy your local instance.

## Contributing

Please fork the project and submit a pull request.

### Frameworks used

New MapRoulette is built upon the Play Framework using Scala. You can find more information about the Play Framework at https://www.playframework.com
It uses the following core technologies:

* Postgres 9.5 with PostGIS 2.2.1
* Play Framework 2.5.0 with Scala 2.11.7

## Deploying MapRoulette

### Mac OSX

* These instructions assume you have at least Mac OS 10.10 (Mavericks) and [Homebrew](http://brew.sh/) installed. We also assume that you have PostgreSQL 9.5 and PostGIS 2.2.1 installed. Homebrew provides packages for both, which we recommend.
* Make sure you have a Java 8 JDK. Check with `java -version` which should mention an 1.8.x version number. [Get](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and install a Java 8 JDK if necessary.
* Install the [Play Framework activator](https://www.playframework.com/documentation/2.5.x/Installing): `brew install typesafe-activator`.
* Create a PostgreSQL superuser `osm`: `createuser -sW osm`. Use `osm` as the password.
* Create a new PostgreSQL database `mp_dev` owned by `osm`: `createdb -O osm mp_dev`.
* Set the database connection JDBC string as environment variable `DATABASE_URL`: `export DATABASE_URL=jdbc:postgresql://osm:osm@localhost:5432/mp_dev`
* Clone New MapRoulette: `git clone https://github.com/maproulette/maproulette2.git`.
* Navigate into the newly created `maproulette2` directory and run the local development server: `activator run`. This will take some time the first run as dependencies are downloaded.
* Head to [http://localhost:9000/](http://localhost:9000/) and confirm you can see the New MapRoulette front end. This also may take a while as artifacts are compiled.

## Creating new Challenges

Coming Soon...

In the mean time see [here](docs/api.md) for current API documentation (not guaranteed to be stable yet).

## Contact

Bug and feature requests are best left as an issue right here on Github. For other things, contact maproulette@maproulette.org

MapRoulette now also has its very own [Slack community](http://maproulette.slack.com). Invite yourself [here](https://maproulette-slack-selfinvite.herokuapp.com)!
