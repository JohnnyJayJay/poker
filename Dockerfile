FROM johnnyjayjay/leiningen:openjdk11 AS deps
WORKDIR /usr/src/poker
COPY ./project.clj .
RUN lein deps

FROM deps
WORKDIR /usr/src/poker
COPY . .
CMD ["lein", "run"]


