# reliqua
[ ![Download](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fduncte123.jfrog.io%2Fartifactory%2Fmaven%2Fme%2Fduncte123%2Freliqua%2Fmaven-metadata.xml) ](https://duncte123.jfrog.io/ui/packages/gav:%2F%2Fme.duncte123:weebJava)

A lightweight framework to build REST API wrappers in Java, with built in rate limiting and common API for both sync and async requests. The name is latin for "rest"

## Usage

```java
public class MyAPI extends Reliqua {
    public PendingRequest<Thing> getThing() {
        return createRequest(new Request.Builder().url("https://some.site/thing"))
            .setStatusCodeValidator(StatusCodeValidator.ACCEPT_200)
            .setRateLimiter(getRateLimiter("/thing"))
            .build(response->new Thing(getDataFromResponse(response)), context->handleError(context));
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

Future<Thing> futureThing = r.submit();
```

## Rate Limiting

Reliqua includes a built in rate limiting API. Check the RateLimiterFactory class for more details.


More information can be found on the javadocs
