# MapRoulette Routes

MapRoulette uses Play Frameworks injection routing, which essentially uses dependency injection to build all the routes based in the route files. Play does allow you to split these into multiple route files, however due to our usage of Play-Swagger we cannot, as Play-Swagger does not support creating your swagger documentation with multiple route files. So MapRoulette has built it's own structure to do this.

### How?

So the process that MapRoulette is very simple, basically as part of the compile process it will take all the api files that are supplied in the [build.sbt](../../build.sbt) file and then concatenate those files together prior to compilation, creating one large Routes files called generated.routes which is then referenced in the primary [routes](../routes) file.

### Why?

Why not just use the one large file? Well this single large file was getting very large, close to 5000 lines. The reason is not particularly because there were a ton of API routes, but rather that the swagger documentation for it was getting very large. To make it more manageable we just create multiple api route files so that we can manage the building of our swagger documentation more effectively.

