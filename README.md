# dialog-play-bot-for-layer

Client for Layer and Dialog Play.

* https://layer.com/
* https://www.dialogplay.jp/

## Developing

### Setup

When you first clone this repository, run:

```sh
lein duct setup
```

This will create files for local configuration, and prep your system
for the project.

Next connect the repository to the [Heroku][] app:

```sh
heroku git:remote -a FIXME
```

[heroku]: https://www.heroku.com/

### Heroku Deploy

```
heroku login
heroku create dialog-play-bot-for-layer

heroku config:set DIALOG_PLAY_APP_TOKEN=*************
heroku config:set LAYER_APP_ID=*************
heroku config:set LAYER_API_TOKEN=*************
heroku config:set LAYER_BOT_USER_ID=*************
heroku config:set YELP_CLIENT_ID=*************
heroku config:set YELP_CLIENT_SECRET=*************

git push heroku master
heroku logs -t
```

### Environment

To begin developing, start with a REPL.

```sh
lein repl
```

Then load the development environment.

```clojure
user=> (dev)
:loaded
```

Run `go` to prep and initiate the system.

```clojure
dev=> (go)
:duct.server.http.jetty/starting-server {:port 3000}
:initiated
```

By default this creates a web server at <http://localhost:3000>.

When you make changes to your source files, use `reset` to reload any
modified files and reset the server.

```clojure
dev=> (reset)
:reloading (...)
:resumed
```

#### Environment Variables

```
export DIALOG_PLAY_APP_TOKEN=*************
export LAYER_APP_ID=*************
export LAYER_API_TOKEN=*************
export LAYER_BOT_USER_ID=*************
export YELP_CLIENT_ID=*************
export YELP_CLIENT_SECRET=*************
```

### Testing

Testing is fastest through the REPL, as you avoid environment startup
time.

```clojure
dev=> (test)
...
```

But you can also run tests through Leiningen.

```sh
lein test
```

## Legal

Copyright © 2017 Kazuki Tsutsumi