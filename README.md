Migration Guide
===============

Configuration
-------------

From [Akka Migration Guide](http://doc.akka.io/docs/akka/2.0/project/migration-guide-1.3.x-2.0.x.html#Configuration)

`In v1.3 the default name of the configuration file was akka.conf. In v2.0 the default name is application.conf.`

Akka Http properties has been removed from `akka-reference.conf (akka.conf)`.
So Akka Mist has own `reference.conf`.
However some of the properties were removed from mist at all, same happened with related functionality.

### Changed: ###

* `root-actor-id = "_httproot"` to `root-actor-path = "/http/root"`

### Removed: ###

* `hostname = "localhost"`
* `port = 9998`
* `filters = ["akka.security.AkkaSecurityFilterFactory"]`
* `resource-packages = []`
* `authenticator = "N/A"`
* `kerberos.servicePrincipal = "N/A"`
* `kerberos.keyTabLocation = "N/A"`
* `kerberos.kerberosDebug = "N/A"`
* `kerberos.realm = ""`