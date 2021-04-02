FROM johnnyjayjay/leiningen:openjdk11
WORKDIR /usr/src/poker
COPY . .
CMD ["lein", "run"]

