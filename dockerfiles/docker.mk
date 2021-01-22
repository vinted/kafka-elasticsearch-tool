.PHONY: start-stack
start-stack:
	docker-compose \
		-p es-kibana-kafka \
		-f dockerfiles/docker-compose.es.yml \
		-f dockerfiles/docker-compose.kafka-base.yml \
		-f dockerfiles/docker-compose.kafka.yml \
		up --remove-orphans
