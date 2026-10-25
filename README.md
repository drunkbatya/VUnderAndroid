# VUnderAndroid

IOS app inspired by (closed source :-(((9() [vkmp3mod](https://github.com/egormetlitsky/vkmp3mod-builds). Very simple VK client but it works.
Android version for IOS version see [VUnder](https://github.com/drunkbatya/VUnder)

## Build
### Generate keystore
```bash
PASSWORD=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')
keytool -genkeypair \
    -keystore "vunder.keystore" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -alias "vunder" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=VUnder"
```

### Build apks
```bash
docker build -t test \
    --secret id=keystore,type=file,src=./vunder.keystore \
    --secret id=keystorepass,env=PASSWORD \
    --output type=local,dest=./out .
```
