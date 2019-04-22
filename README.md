# Koi-clj 

An invokable Service Proxy for Ocean. Implemented in Clojure

## Usage

### Prerequisites:

- Install jdk1.8+
- Install [lein](https://leiningen.org)
 
Steps:

``` bash 
lein ring server
```

Navigate to localhost:3000

### Surfer

The default configuration expects surfer to be running on `http://localhost:8080/`. To change this, change resources/config.edn

### Executing an operation

Default operations supported are

#### hashing

  - accepts a string asset.  
  - returns hash of the input

Input payload:
- DID field: `hashing` 
- params field
```json
{"to-hash":"stringtohash"}
```
  
#### assethashing

  - accepts an ocean asset, defined by a DID
  - returns an ocean asset DID, the content of which is the hash of the input asset.
  
Example:

- DID field: `assethashing` 

- params field

Note that the asset did must exist on Surfer.
 
```json
{"to-hash":{"did":"45a8cebe88ad5d8161e19bf2f201af772ad3c6613be9d60f7663a8c33646b203"}}
```

  

## License

Copyright © 2019 DEX

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
