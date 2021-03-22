# Configuration

`ket` is a CLI tool and can be configured in several ways:

- CLI arguments and flags
- configuration file;
- JQ overrides;
- environment variables.

The overall command format is: `[ENV_VARIABLES] ket [GLOBAL_FLAGS] [OPERATION] [OPERATION_FLAGS]`

## CLI arguments and flags

Every operation has a different set of supported flags and this list can be obtained by providing the `-h` or `--help` flag, e.g.:
```shell
./ket -h
```
or:
```shell
./ket reindex -h
```

## Configuration file

Configuration file is expected to be JSON. 
To specify a configuration file use a CLI flag `-f` or `--config-file`, e.g.:
```shell
./ket operation -f config.json
```
or equivalent:
```shell
./ket operation -config-file=config.json
```

## JQ Overrides

JQ overrides are applied on the configuration read from the configuration file. E.g. configuration file `test-conf.json` contents are:
```json
{"max_docs": 1}
```
When we apply `jq` query `.max_docs=2` e.g.: 
```shell
./ket reindex -f test-conf.json --override='.max_docs=2'
```
the configuration that will be passed to the `reindex` operation will look like this:
```json
{"max_docs":2}
```

Since the overrides are regular JQ scripts, it is possible to join configuration from different configuration files, e.g.:
```shell
./ket reindex \
  -f test-conf.json \
  --override='.source='$(jq -c '.source' examples/replay.json)'' \
  --dry-run
```
And whatever is in the `examples/replay.json` file under `source` key, it will be included into the configuration for the current conmmand.

Another trick is that `ket` supports the `--dry-run` flag, which instructs `ket` to prepare configuration, and (instead of invoking the operation) to print configuration JSON to the STDOUT, e.g.:
```shell
./ket reindex \
  -f test-conf.json \
  --override='.source='$(jq -c '.source' examples/replay.json)'' \
  --dry-run
```
You can inspect the config, or dump it to a file for later processing.

NOTE: this documents expects jq to be installed in the machine.

NOTE: if configuration file is not provided, then empty configuration `{}` is assumed, i.e. JQ overrides are applied on an empty hashmap.

## Environment variables

With environment variables only one aspect can be configured: logging level. E.g.:

```shell
ROOT_LOGGER_LEVEL=WARN ./ket operation -f config.json
```
