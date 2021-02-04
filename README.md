# KET

Helper tools to work with Elasticsearch and Kafka. The tool is best used as a CLI.

## Quick Start
                  
Start e.g. reindexing:
```shell script 
./ket reindex -f examples/reindex-settings.json
```

See available options:
```shell script 
$ ./ket -h
  -o, --operation OPERATION      A name of a supported operation. One of: [kafka-to-kafka, kafka-to-ndjson, kafka-to-elasticsearch, elasticsearch-to-elasticsearch, reindex, elasticsearch-to-kafka, elasticsearch-to-ndjson, krp-to-ndjson, replay, deep-replay, polyglot, server]
      --defaults                 Print to STDOUT the default configuration of the operation
      --docs                     Print to STDOUT the docstring of the operation
  -f, --config-file CONFIG_FILE  Path to the JSON file with operation config
  -h, --help

```

```
$ ./ket reindex --docs
```

```
$ ./ket reindex --defaults
```

## Native Executable

Either compile for yourself (for linux):
```shell script 
make build-ket
```      
Download binary for your architecture from [here](https://github.com/vinted/kafka-elasticsearch-tool/releases)

## Supported operations

- reindex
- profile slow queries
- replay slow queries with various profiles
- send data from one kafka topic to another (possibly between cluster)
- send data from a Kafka topic to Elasticsearch index
- send data from Elasticsearch to Kafka
- store data from Elasticsearch or Kafka as ndjson file
- polyglot transforms

### Reindex

```shell script 
./ket reindex -f examples/reindex-settings.json
```

Reindex operation config file basic example:
```json
{
  "max_docs" : 1200,
  "source" : {
    "remote" : {
      "host" : "http://localhost:9200"
    },
    "index" : ".kibana"
  },
  "dest" : {
    "index" : "destination-index-name",
    "remote" : {
      "host" : "http://localhost:9200"
    }
  }
}
```  
Config file format is based on the [Elasticsearch Reindex API](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html).

`reindex` operation supports:
- reindexing between clusters;
- reindexing between clusters that are running different versions of Elasticsearch.
- when starting disables `refresh` interval and at the end enables it.
### Copy Kafka topic(s) data to another Kafka topic

```shell script
./ket kafka-to-kafka -f examples/kafka-to-kafka.json
```

Example config:
```json
{
  "max_docs": 1,
  "source": {
    "topic": "source-topic",
    "bootstrap.servers": "127.0.0.1:9092"
  },
  "sink": {
    "topic": "dest-topic",
    "bootstrap.servers": "127.0.0.1:9092"
  }
}
```

`source` and `sink` maps are for Kafka consumer and producer options respectively. All available options are supported.

### Elasticsearch to Kafka

```shell script
./ket elasticsearch-to-kafka -f examples/es-to-kafka.json
```
Example configuration:
```json
{
  "max_docs": 10000,
  "source": {
    "remote": {
      "connect_timeout": "10s",
      "host": "http://localhost:9200",
      "socket_timeout": "1m"
    },
    "index": ".kibana",
    "query": {
      "sort": [
        "_doc"
      ],
      "size": 2000
    }
  },
  "sink": {
    "topic": "kibana-data",
    "bootstrap.servers": "127.0.0.1:9092"
  }
}

```
`source` is the same as in reindex,
`sink` is Kafka Producer option map.

## Elasticsearch to ndjson

```shell script
./ket elasticsearch-to-ndjson -f examples/es-to-ndjson.json
```
Example configuration:
```json
{
  "max_docs": 10000,
  "source": {
    "remote": {
      "host": "http://localhost:9200"
    },
    "index": ".kibana",
    "query": {
      "size": 2000
    }
  },
  "sink": {
    "filename ": "es-docs.ndjson"
  }
}
```

## Kafka to ndjson

```shell script
./ket kafka-to-ndjson -f examples/kafka-to-ndjson.json
```
Example configuration:
```json
{
  "max_docs": 10000,
  "source": {
    "bootstrap.servers": "127.0.0.1:9092",
    "topic": "topic-name",
    "impatient": true
  },
  "sink": {
    "filename ": "es-docs.ndjson"
  }
}
```

## ndjson to Elasticsearch

```shell script
curl -s -H "Content-Type: application/x-ndjson" -XPOST localhost:9200/_bulk --data-binary @file.ndjson
```

## Polyglot transforms

Try it for your self, e.g.:
```shell
./ket polyglot --data='{"foo":"bar"}' --file="my-script.js" --lang=js | jq '.result | fromjson'
{
  "foo": "bar",
  "a": 123
}
```
as seen in the example, the script can be stored in a file.

Supported languages are `['js' 'sci']`.

A useful trick is to provide data JSON string directly from a file.

```shell
./ket polyglot --data="$(jq "." data-file.json)" --file="my-script.js" --lang=js | jq '.result | fromjson'
```

Also, in the same fashion the script can be provided:
```shell
./ket polyglot --data="$(jq "." data-file.json)" --script="$(uglifyjs my-script.js)" --lang=js | jq '.result | fromjson'

```

## Logging

Logging is controlled by the [logback](http://logback.qos.ch/) library. The output layout is JSON (you can query it with `jq` or collect logs with logstash or beats).
Default logging level is `INFO`.
When executed as a binary, i.e. `./ket OPERATION`, then logging levels are controlled by an environment variable called: `ROOT_LOGGER_LEVEL`, e.g. `ROOT_LOGGER_LEVEL=WARN ./ket operation -f config.json`
Acceptable values of the `ROOT_LOGGER_LEVEL` are: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`.
When an unknown value is provided, e.g. `ROOT_LOGGER_LEVEL=foo ./ket operation -f config.json`, then logback defaults to `DEBUG` logging level.

## Supported Elasticsearch Versions

- 7.x.y

## Development

Development requires [GraalVM 20.3.0+](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-20.3.0),
Docker and Docker Compose, GNU Make, and [Clojure CLI tools](https://clojure.org/guides/getting_started).

## License

Copyright &copy; 2021 [Vinted](https://www.vinted.engineering).

Distributed under BSD 3-Clause License
