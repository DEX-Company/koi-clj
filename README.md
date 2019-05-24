# Koi-clj 

A reference implementation of Invokable services for Ocean, implemented in Clojure. It implements four operations.

## Usage

For development, you will need to run the latest copy of [Barge](https://github.com/DEX-Company/barge). Starting the default Barge would give you koi-clj running on port 3000.


### Prerequisites:

- Install jdk1.8+
- Install [lein](https://leiningen.org)
 
Steps:

``` bash 
lein ring server
```

Navigate to localhost:3000

### Surfer

The default configuration expects Surfer to be running on `http://localhost:8080/`. To change this, change resources/config.edn

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
```json
{"to-hash":{"did":"asset_id_of_content_to_hash"}}
```

Note that the asset did must exist on Ocean.
 
```json
{"to-hash":{"did":"45a8cebe88ad5d8161e19bf2f201af772ad3c6613be9d60f7663a8c33646b203"}}
```

  

## License

Copyright © 2019 DEX

Distributed under the Apache License version 2.0.
