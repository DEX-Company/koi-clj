# Koi-clj 

A reference implementation of Invokable services implemented in Clojure. 


## Table of Contents

* [Overview](#overview)
* [Running locally](#running-locally)
* [Configuration](#configuration)
* [Documentation](#documentation)
* [Testing](#testing)
* [License](#license)

## Overview

Koi-clj is a reference implementation of the DEP-6 implemented in Clojure. 
It provides 

- a REST API Endpoints compliant with the methods described in DEP-6.
- Examples of operations
  - hashing: an Operation that returns a hash of an Asset
  - filterrows: an Operation that removes rows in a csv dataset if it has more than N empty columns, where N is a configuration option.

Example of execution two operations:

#### hashing

  - accepts a string asset.  
  - returns hash of the input

Input payload:
- DID field: `hashing` 
- params field
```json
{"to-hash":"stringtohash"}
```
  
#### filtering rows

  - accepts an asset, defined by a DID
  - returns an asset, the content of which is the filtered dataset.
  
Example:

- DID field: `filter-rows` 

- params field
```json
{"dataset":{"did":"asset_did_of_dataset_to_filter"}}
```

Response
 
```json
{"filtered-dataset":{"did":"asset_did_of_filtered_dataset"}}
```

  
# Running locally

### Prerequisites:

- Install jdk1.8+
- Install [lein](https://leiningen.org)
 
Steps:

``` bash 
git clone https://github.com/DEX-Company/koi-clj/
cd koi-clj
lein ring server
```

Navigate to localhost:8191

# Configuration

TBD

# Documentation 

TBD

# Testing

- `lein test` runs the unit tests

# License

```
Copyright 2018-2019 DEX Pte. Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
