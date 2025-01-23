# radio-sdk-sample
Sample app for the Radio SDK

## Setup
Make sure you have a `local.properties` file created.

To be able to import goTenna libraries you must add your Artifactory credentials.

```
artifactory.user=email
artifactory.password=xyz
```

The file must contain the `sdk.token` and `sdk.appid` provided by goTenna.

```
sdk.token=
sdk.appid=
```

### Initialize

```
GotennaClient.initialize(
    context = applicationContext,
    sdkToken = SDK_TOKEN,
    appId = APP_ID,
    preProcessAction = null,
    postProcessAction = null
)
```

### Listen for Radio Status Changes

Will let you know if the connection status of any of the connected radios changes.

```
GotennaClient.observeRadios().collect { radios ->
}
```

### Scan for Radios

Will return a list of radios found near by.

```
val radioList = GotennaClient.scan(ConnectionType.BLE)
```

### Connect to Radio

Take the radio from the scan list and call `.connect()` on it.

```
val result = radio.connect()
```

## Communication Suggestions

What we normally do is this:
- Node A sends everyone on the mesh network its `LocationModel` as a broadcast message every minute.
- Node B receives `LocationModel` from A and other nodes.
- Node B stores (app logic) the GID of each node as a contact list.
- Node B uses the contact list to get the GID of the node they want to contact.

