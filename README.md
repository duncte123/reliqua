# reliqua
[ ![Download](https://api.bintray.com/packages/natanbc/maven/reliqua/images/download.svg?version=1.0) ](https://bintray.com/natanbc/maven/reliqua/1.0/link)

A lightweight framework to build REST API wrappers in Java, with built in rate limiting and common API for both sync and async requests. The name is latin for "rest"

## Usage

```java
public class MyAPI extends Reliqua {
    public PendingRequest<Thing> getThing() {
        return createRequest("/thing", new Request.Builder().url("https://some.site/thing"), 200 /* expected HTTP code */, body->{
            return new Thing(body.string());
        });
    }
}
```
To use it:
```java
MyAPI api = new MyAPI();

//async
api.getThing().async(thing->System.out.println("got thing: " + thing), error->System.err.println("got error: " + error));

//blocking
Thing thing = api.getThing().execute();
System.out.println("got thing: " + thing);

//futures
Future<Thing> futureThing = api.getThing().submit();
```

PendingRequest objects may be reused:
```java
PendingRequest<Thing> r = api.getThing();

r.async(thing->System.out.println("got thing: " + thing), error->System.err.println("got error: " + error));

Thing thing = r.execute();
System.out.println("got thing: " + thing);

//futures
Future<Thing> futureThing = r.submit();
```

## Rate Limiting

Reliqua includes a built in rate limiting API. Currently, only an implementation for API-wide rate limits is supplied.

```java
public class MyAPI extends Reliqua {
    public MyAPI() {
        super(
            new GlobalRateLimiter(
                Executors.newSingleThreadedScheduledExecutor(), //schedules request execution and rate limit resets
                null, //we don't need to be notified
                10, //10 requests until blocking
                60_000 //60 second reset time
            ),
            new OkHttpClient(), //handles HTTP requests
            true //track call sites for async requests for accurate error reporting
        );
    }
}
```

More information can be found on the javadocs
