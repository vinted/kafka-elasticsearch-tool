FROM ubuntu:20.04

WORKDIR /app

RUN apt-get update && \
    apt-get install -y jq uglifyjs

COPY ket /app/ket

ENV PATH="/app:${PATH}"

CMD ["/app/ket", "-h"]
