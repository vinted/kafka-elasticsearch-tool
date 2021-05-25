include dockerfiles/docker.mk

.PHONY: pom.xml
pom.xml:
	clojure -Spom

.PHONY: uberjar
uberjar: pom.xml
	clojure -X:uberjar :jar target/ket-uber.jar :main-class core

.PHONY: lint
lint:
	clojure -M:clj-kondo

.PHONY: unit-test
unit-test:
	clojure -M:test --exclude :integration

.PHONY: check-deps
check-deps:
	clojure -Sdeps '{:deps {antq/antq {:mvn/version "RELEASE"}}}' -M -m antq.core

.PHONY: integration-test
integration-test:
	clojure -M:test --include :integration

.PHONY: run-dev-env
run-dev-env: start-stack

ES_TEST:=-p integration-tests -f dockerfiles/docker-compose.es.test.yml -f dockerfiles/docker-compose.kafka-base.yml
.PHONY: run-integration-tests
run-integration-tests:
	docker-compose $(ES_TEST) pull
	docker-compose $(ES_TEST) down
	docker-compose $(ES_TEST) build
	docker-compose $(ES_TEST) up --remove-orphans --abort-on-container-exit --exit-code-from tools-test

docker_build = (docker build --build-arg $1 --build-arg $2 -f dockerfiles/Dockerfile.executable-builder -t ket-native-image .; \
				docker rm ket-native-image-build || true; \
				docker create --name ket-native-image-build ket-native-image; \
				docker cp ket-native-image-build:/usr/src/app/ket ket)

build:
	clojure -M:native-image

build-linux-static:
	$(call docker_build, KET_STATIC=true, KET_MUSL=true)
