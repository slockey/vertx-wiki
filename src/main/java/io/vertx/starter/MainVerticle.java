package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer primary key, Name char(255) unique, Content clob)";
    private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
    private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
    private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
    private static final String SQL_ALL_PAGES = "select Name from Pages";
    private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

    private JDBCClient dbClient;
    private FreeMarkerTemplateEngine templateEngine;

    //  @Override
    //  public void start() {
    //    vertx.createHttpServer()
    //        .requestHandler(req -> req.response().end("Hello Vert.x!"))
    //        .listen(8080);
    //  }

    @Override
    public void start(Promise<Void> promise) {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(promise);
    }

    // prepare the database connection
    public Future<Void> prepareDatabase() {
        Promise<Void> promise = Promise.promise();

        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:hsqldb:file:db/wiki")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30));

        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                logger.error("Could not open database connection", ar.cause());
                promise.fail(ar.cause());
            } else {
                SQLConnection connection = ar.result();
                connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                    connection.close();
                    if (create.failed()) {
                        logger.error("Failed to prepare database", create.cause());
                        promise.fail(create.cause());
                    } else {
                        promise.complete();
                    }
                });
            }
        });

        return promise.future();
    }

    public Future<Void> startHttpServer() {
        Promise<Void> promise = Promise.promise();

        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        //        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        //        router.post().handler(BodyHandler.create());
        //        router.post("/save").handler(this::pageUpdateHandler);
        //        router.post("/create").handler(this::pageCreateHandler);
        //        router.post("/delete").handler(this::pageDeletionHandler);

        templateEngine = FreeMarkerTemplateEngine.create(vertx);

        server
        .requestHandler(router)
        .listen(8080, ar -> {
            if (ar.succeeded()) {
                logger.info("HTTP Server running on port 8080");
                promise.complete();
            } else {
                logger.error("Could not start HTTP Server", ar.cause());
                promise.fail(ar.cause());
            }
        });
        return promise.future();
    }

    private void indexHandler(RoutingContext context) {
        // TODO: implement this handler
//        context.response().end("Success");

        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_ALL_PAGES, res -> {
                    connection.close();
                    if (res.succeeded()) {
                        List<String> pages = res.result()
                            .getResults()
                            .stream()
                            .map(json -> json.getString(0))
                            .sorted()
                            .collect(Collectors.toList());;

                        // build the response
                        context.put("title", "Wiki home");
                        context.put("pages", pages);
                        // apply the templating
                        templateEngine.render(context.data(), "templates/index.ftl", ar -> {
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type","text/html");
                                context.response().end(ar.result());
                            } else {
                              // TODO: log this error - async request failed
                              context.fail(ar.cause());
                            }
                        });
                    } else {
                        // TODO: log this error - failed to get results from query
                        context.fail(res.cause());
                    }
                });
            } else {
                // TODO: log this failure - no database connection
                context.fail(car.cause());
            }
        });

    }
}
