# Node Explorer

Stand alone desktop app for Win/Osx/Linux that allows connecting to a local or remote Corda node. Examine transactions, run flows and view node and network properties in a few simple clicks.


### Downloading the Node Explorer Binaries

If you're looking to download the node explorer and get started using it; download the latest packaged release on this repo's [releases](https://github.com/corda/node-explorer/releases) page.

There are install packages for Linux, OSX and Windows. 

To login to your Corda node, you will need to provide the:

- Hostname (domain, IP, or localhost)
- Rpc Port
- Username
- Password

Notes: You can find the Rpc port in your node config under `rpcSettings->address` or as displayed in a `CraSH shell` instance. If you have not explicitly set a username/password project defaults are `user1` and `test`.

Full instructions for using the Node-Explorer application at the [documentation](https://docs.corda.net/docs/corda-os/4.5/node-explorer.html)

#### Running the Node Explorer w/o packaging

To test your changes or run the node explorer without packaging you must:

1 Build and run the server jar first
- `cd ../node-server`
- `./gradlew assemble`
- `java -jar ./server/build/libs/explorer-server-<version>.jar .`

2 Then run the front end from the main project directory
- `npm start`

3 The application can then be used through your browser at http://localhost:3000

#### Packaging electron installers / app files(haven't try)

Note: You must have a compiled server jar in the directory root 
 - `cd ./server && ./gradlew bootJar`, move compiled jar in `./server/build/libs` to the root.

Then run: ``npm run electron-pack``

---

### Deploy or Run Node Explorer with Docker(haven't try)

You can build docker images using the provided DockerFile(s). You will need to build two images, one for the Node Explorer front end ./DockerFile and another for the backend Node Server ./server/DockerFile (the Node Server DockerFile utilizes layers so you will need to extract the jar - see [A Better Dockerfile](https://spring.io/guides/topicals/spring-boot-docker/))

If you DO NOT want to build your own images you can simply run Node Explorer using the docker-compose.yml and with contributed images. Follow the steps below:

#####1.  Set cordapps path

```
export CORDAPPS_PATH=Your cordapps path here
```

example

```
export CORDAPPS_PATH=/home/karthik/github/samples-java/Basic/yo-cordapp/build/nodes/PartyA/cordapps
```


#####2. Run docker-compose file

```
docker-compose up -d
```

#####3. Browse to Node explorer UI

explorer runs at port 3000

```
http://localhost:3000/
```

#####4. Set cordapps directory in settings

Provide cordapps path as /cordapps in settings tab.

```
/cordapps
```
