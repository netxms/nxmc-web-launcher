# nxmc-web-launcher

This is a small wrapper around Jetty10, which bundles NetXMS web interface.

Main purpose is to provide single runnable JAR file with simple HTTPS setup.

For production use it's recommended to setup reverse proxy ([Reproxy](https://github.com/umputun/reproxy), [nginx](https://nginx.org/en/), [Traefik](https://traefik.io/), etc.) in front.

## Build

```shell
./gradlew build

# override version (mainly for *DistTar/*DistZip tasks)
./gradlew clean build -Pversion=3.10.320-285
```

If file "nxmc.war" is found in the root folder of the project during build,
it content will be bundled into the resulting "fat" jar file.

Example:

```
$ ./gradlew build

BUILD SUCCESSFUL in 945ms
4 actionable tasks: 4 executed

$ ls -l build/libs/nxmc-web-launcher-1.0.jar
.rw-r--r-- 4.5M alk  3 Jun 10:55 build/libs/nxmc-web-launcher-1.0.jar

$ curl --output nxmc.war https://netxms.org/download/releases/4.1/nxmc-4.1.333.war
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100 24.1M  100 24.1M    0     0  10.4M      0  0:00:02  0:00:02 --:--:-- 10.5M

$ ls -l nxmc.war
.rw-r--r-- 25M alk  3 Jun 10:55 nxmc.war

$ ./gradlew build

BUILD SUCCESSFUL in 1s
4 actionable tasks: 1 executed, 3 up-to-date

$ ls -l build/libs/nxmc-web-launcher-1.0.jar
.rw-r--r-- 30M alk  3 Jun 10:56 build/libs/nxmc-web-launcher-1.0.jar
```

## Launch

```
$ java -jar build/libs/nxmc-web-launcher-1.0.jar -h
Usage: netxms-web-launcher options_list
Options:
    --httpPort [8080] -> Listen Port for HTTP connector, 0 will disable connector { Int }
    --httpsPort [0] -> Listen Port for HTTPS connector, 0 will disable connector { Int }
    --keystore [/var/lib/netxms/nxmc.pkcs12] -> Location of the keystore file with server's certificate and private key { String }
    --keystorePassword [] -> Keystore file password. If environment variable exists with this name - it's value will be used instead { String }
    --war -> nxmc.war file location { String }
    --log [System.err] -> Log file name or special names "System.err"/"System.out" { String }
    --logLevel [INFO] -> Verbosity level { String }
    --accessLog -> Write access log to separate file, otherwise will be sent to common log { String }
    --help, -h -> Usage info
```

By default, HTTPS connector is disabled and HTTP will be started on 8080.

WAR file is loaded from the resources (if it was bundled during build) or should be provided with `--war`

### How to enable SSL connector

To enable SSL connector, create a keystore in PKCS12 format with private key and certificate.

For testing purposes you can generate self-signed certificate:

```shell
openssl genrsa -out server-key.pem
openssl req -new -key server-key.pem -out server-cert.req
openssl x509 -req -signkey server-key.pem -in server-cert.req -out server-cert.pem
```

Once you have both private key and certificate (either self-signed or from CA), convert them to PKCS12:

```shell
openssl pkcs12 -export -inkey server-key.pem -in server-cert.pem -out server.pkcs12 -password pass:example1
```

Now you can start with HTTPS connector enabled (and without plain-text HTTP):

```shell
java -jar build/libs/nxmc-web-launcher-1.0.jar --httpsPort 8443 --httpPort 0 --keystore server.pkcs12 --keystorePassword example1
```

Keystore password can be read from the environment variables as well, just use variable name instead of the password:
```shell
export NXMC_KEYSTORE_PASSWD=example1
# or
read NXMC_KEYSTORE_PASSWD
export NXMC_KEYSTORE_PASSWD

# now run it
java -jar build/libs/nxmc-web-launcher-1.0.jar --httpsPort 8443 --httpPort 0 --keystore server.pkcs12 --keystorePassword NXMC_KEYSTORE_PASSWD
```
