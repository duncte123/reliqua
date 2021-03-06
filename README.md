# reliqua
[ ![Download][badge] ][download]

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

## Installing
Current version: [ ![][badge] ][download]

### With gradle

```groovy
repositories {
    maven {
        name 'duncte123-jfrog'
        url 'https://duncte123.jfrog.io/artifactory/maven'
    }
}

dependencies {
    implementation group: 'me.duncte123', name: 'reliqua', version: '[VERSION]'
}
```

### With maven

```xml
<repository>
    <id>jfrog-duncte123</id>
    <name>jfrog-duncte123</name>
    <url>https://duncte123.jfrog.io/artifactory/maven</url>
</repository>

<dependency>
  <groupId>me.duncte123</groupId>
  <artifactId>reliqua</artifactId>
  <version>[VERSION]</version>
</dependency>
```


[badge]: https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fduncte123.jfrog.io%2Fartifactory%2Fmaven%2Fme%2Fduncte123%2Freliqua%2Fmaven-metadata.xml
[download]: https://duncte123.jfrog.io/ui/packages/gav:%2F%2Fme.duncte123:reliqua