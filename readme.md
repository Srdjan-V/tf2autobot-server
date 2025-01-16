## AutobotServer

An api server for [Tf2Autobot's](https://github.com/TF2Autobot/tf2autobot).
Currently limited to linux, `Node-Ipc` has no support for unix sockets on windows and `Junixsocket` has no support for
Windows pipes.
But Windows has support for unix sockets

### Config

All configuration options can be found
in [Config.java](https://github.com/Srdjan-V/tf2autobot-server/blob/master/src/main/java/io/github/srdjanv/autobotserver/Config.java)

Pem files and `server_config.json` should be placed in ./config

Pem files gen [link](https://javalin.io/tutorials/javalin-ssl-tutorial#generating-a-self-signed-certificate)

`server_config.json` config with ssl

```json
{
  "ssl_password": "",
  "auth_token": ""
}
```

`server_config.json` config with no auth

```json
{
  "use_ssl": false,
  "use_auth": false
}
```

### Api endpoints

All endpoints can be found
in [JavalinApp.java](https://github.com/Srdjan-V/tf2autobot-server/blob/master/src/main/java/io/github/srdjanv/autobotserver/javalin/JavalinApp.java)

#### V1

Requests can be directed to specific bots by specifying query params `?bot_id="steamid"` or`?bot_name="acount name"`

Authenticated request need the `Authorization` header, this should match the `auth_token` config variable

### Running

Install java 21, and launch with pm2 just like Tf2Autobot `pm2 start ecosystem.json && pm2 save`