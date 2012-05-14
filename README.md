Overview
========

Akka Http is not a webframework. It's an Akka based implementation of asynchronous requests available since servlet 3.0
Each request will have own actor. Actor will live until async request is processed and completed or time is out.
Processing of async request is divided by different unit of work. All these work units will be processed one by one, however
Actor may receive one of the javax.servlet.AsyncEvent between. In case of Error, Timeout or Complete events actor will stop processing and shutdown.
So all work untis will be droped.

Examples
========

[HelloWorldServlet example](https://github.com/thenewmotion/akka-http-helloworld)

What's new
==========

Scopes
------

Async context has been divided on different scopes. Each scope is a synchronous unit of work

* Request scope. Expected to be the heaviest scope. All request logic shhould go here

* Response scope. Light scope for putting request result in response

* Callback scope. Simple callback function. Passes `TRUE` when response succeeded, `FALSE` otherwise.

We need scope to receive async event messages in between. Thus will allow us to avoid various exceptions
For example you may get `response time out exception` or `response already completed`, etc.
Also you can avoid odd operations when requrest has expired.

Migration Guide
===============

RequestMethod
-------------

RequestMethod was removed. However you are able to use HttpServletRequest/Response directly.

MistSettings
------------

MistSettings were moved to HttpExtension with other common stuff. Http properties will be defined per ActorSystem.

AkkaMistServlet/AkkaMistFilter
------------------------------

AkkaMistServlet was renamed to AkkaHttpServlet, AkkaMistFilter to AkkaHttpFilter. Both of them extend trait AkkaHttp

Configuration
-------------

From [Akka Migration Guide](http://doc.akka.io/docs/akka/2.0/project/migration-guide-1.3.x-2.0.x.html#Configuration)

`In v1.3 the default name of the configuration file was akka.conf. In v2.0 the default name is application.conf.`

Akka Http properties has been removed from `akka-reference.conf (akka.conf)`.
So Akka Mist has own `reference.conf`.
However some of the properties were removed from mist at all, same happened with related functionality.
Most of the properties you can retrieve from `HttpExtension.scala`.
[Extension](http://doc.akka.io/docs/akka/2.0/scala/extending-akka.html) mechanism is new, added in Akka 2.0

### Changed properties: ###

* `root-actor-id = "_httproot"` to `endpoints-actor-name = endpoints` - path of the actor to use for receiving and adding endpoints, call 'actorFor(/user/endpoints)'

### Added properties: ###

* `system-name = http` - name of the system used in servlet
* `endpoint-retrieval-timeout = 500` - HTTP 404 if no endpoint retrieved within defined timeout (ms), should be less then timeout
* `log-http-config-on-init = off` - Log akka.http configuration at INFO level on Servlet/Filter init.

### Removed properties: ###

* `hostname = "localhost"`
* `port = 9998`
* `filters = ["akka.security.AkkaSecurityFilterFactory"]`
* `resource-packages = []`
* `authenticator = "N/A"`
* `connection-close = true`
* `kerberos.servicePrincipal = "N/A"`
* `kerberos.keyTabLocation = "N/A"`
* `kerberos.kerberosDebug = "N/A"`
* `kerberos.realm = ""`