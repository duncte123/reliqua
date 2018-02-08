# reliqua
[ ![Download](https://api.bintray.com/packages/natanbc/maven/reliqua/images/download.svg?version=1.0) ](https://bintray.com/natanbc/maven/reliqua/1.0/link)

A lightweight framework to build REST API wrappers in Java, with built in rate limiting and common API for both sync and async requests. The name is latin for "rest"

## Usage

```java
public class MyAPI extends Reliqua {
    public PendingRequest<Thing> getThing() {
        return createRequest("/thing", new Request.Builder().url("https://some.site/thing"), body->{
            return new Thing(body.string());
        });
    }
}
```

More information can be found on the javadocs
