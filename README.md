# Hydra

Hydra is the server plugin project used by the Hydra Network.

## Building

Use JDK 25 and the included Gradle wrapper.

Windows:

```powershell
.\gradlew.bat clean build
```

Linux and macOS:

```sh
bash ./gradlew clean build
```

The plugin JAR is written to `build/libs/HydraMain.jar`. The build also packages and validates the resource pack at `build/deploy/resourcepacks/hydra_server_pack.zip`.

Server-specific settings belong in the plugin configuration. The included `config.yml` uses placeholders for integration secrets.
