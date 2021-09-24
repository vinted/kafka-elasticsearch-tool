# Replay

This document describes group os supported operations that are about replaying queries from a query log.
There are 3 replay operations:

- replay, 
- deep-replay, 
- replay-for-impact.

The idea behind them all is to take some queries from a query log and re-run those (modified) queries against some Elasticsearch cluster.
This document describes each operation.

## `replay`

```shell
$ print $(ket replay --docs)
"Take some queries from an Elasticsearch cluster (transform them) replay the queries
to another Elasticsearch cluster, and store the responses in yet another Elasticsearch
cluster."
```

Configuration:
```shell
ket replay --defaults | jq  
=>   
{
  "max_docs": 10,
  "source": {
    "remote": {
      "host": "http://localhost:9200"
    },
    "index": "query_logs_index"
  },
  "replay": {
    "description": "Description of the query replay.",
    "uri_attr": "uri",
    "repeats": 1,
    "concurrency": 1,
    "uri-transforms": [],
    "query-transforms": [],
    "id": "id-of-the-replay",
    "connection.url": "http://localhost:9200",
    "replay_data_attr": "replay",
    "query_attr": "request"
  },
  "sink": {
    "connection.url": "http://localhost:9200",
    "dest.index": "replay_sink_index",
    "batch.size": 50
  }
}
```
Properties under the `replay` key:
- `description`: a plain text description of the replay, will be stored in the sink index;
- `uri_attr`: a key (or path) under which an uri is expected to be found in the `query_log` document.
- `repeats`: how many times to replay the same query from the `query_log`
- `concurrency`: the maximum number of concurrent requests to the `replay` cluster at any given moment.
- `uri-transforms`: a list of changes applied to the `uri` string before constructing request to the `replay` cluster.
- `query-transforms`: a list of scrips that are applied to the query body before constructing request to the `replay`
- `id`: a string identifier of the replay, will be stored in the sink index;
- `connection.url`: what is the hostname of the `replay` cluster.
- `replay_data_attr`: under which attribute to store the replay data in the `sink`.
- `query_attr`: a key (or path) under which the raw query string is expected to be found in the `query_log` document.

"Path" is a list that describes "how to get value from a nested Json".
The "key (or path)" has the same semantics as [Ruby dig method](https://apidock.com/ruby/v2_5_5/Hash/dig).
When only string is provided (i.e. only a key) it is treated as a path with one element, e.g.:
`"uri_attr": "uri"` is equal to ` "uri_attr": ["uri"]`. 

`uri-transforms` is a list of string transformations, 
where a part of the string matched by a regex under `match`
is replaced by a string under `replacement`, e.g.:

```json
[ 
  {
    "match" : "_count\\?",
    "replacement" : "_search?size=0&"
  } 
]
```

`query-transforms` is a list with transformation scripts
to be applied on the raw query Json, e.g.:
```json
[
  {
    "lang" : "js",
    "script" : "(q) => Object.assign(q, {'_source': true})"
  }
]
```

## `deep-replay`

```shell
$ printf "$(ket deep-replay --docs)\n"
"From an Elasticsearch cluster takes some queries, replays them
  to (another) Elasticsearch cluster with top-k results where k might be very big, like 1M.
  Each hit with the metadata is written to a specified Kafka topic.
  URIs can be transformed, queries can be transformed."
```

## `replay-for-impact`

```shell
$ printf "$(ket replay-for-impact --docs)\n"
"Fetches baseline query and for a list of query transforms and values, generates variations of the query,
  then invokes _rank_eval API for metrics on what is the impact of the query transforms to the ranking.
  Impact is defined as 1 minus precision-at-K."
```

This is the trickiest operation
